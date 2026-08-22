package com.orangeway.iptv.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orangeway.iptv.data.model.Channel
import com.orangeway.iptv.data.model.Country
import com.orangeway.iptv.data.model.RegionProvider
import com.orangeway.iptv.data.model.stateSourceUrl
import com.orangeway.iptv.data.model.usNationalSourceUrl
import com.orangeway.iptv.data.parser.EpgParser
import com.orangeway.iptv.data.repository.ChannelRepository
import com.orangeway.iptv.data.repository.EpgRepository
import com.orangeway.iptv.data.repository.RegionEntry
import com.orangeway.iptv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val channels: List<Channel> = emptyList(),
    val categories: List<String> = emptyList(),
    /** 所有可用分类（未过滤隐藏分类），供设置页使用 */
    val allCategories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val apiUrl: String = SettingsRepository.DEFAULT_API_URL,
    /** 当前地区筛选描述，如"湖南·长沙" */
    val regionFilter: String = "",
    /** 是否已设置地区筛选 */
    val hasRegionFilter: Boolean = false,
    /** 当前选择的国家/地区 */
    val country: Country = Country.CHINA,
    /** 美国已选州的代码（如 "CA"），国家非美国时为空 */
    val usStateCode: String = "",
    /** 当前隐藏的分类 */
    val hiddenCategories: List<String> = emptyList(),
    /** 已收藏的频道名称列表 */
    val favoriteChannels: List<String> = emptyList()
)

class HomeViewModel(
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository,
    private val epgRepository: EpgRepository
) : ViewModel() {

    companion object {
        /** 虚拟分类：收藏频道（置顶显示） */
        const val FAVORITE_CATEGORY = "收藏频道"
    }

    /**
     * 数据源配置：用户 API URL + 国家 + 美国州代码。
     * - 国家=美国且未选州：仅拉取 iptv-org 美国国家级节目单（CNN/MSNBC 等全国频道）
     * - 国家=美国且已选州：拉取全国节目单 + 该州节目单，合并去重
     * - 其它：仅拉取用户自定义 API URL
     */
    private data class SourceConfig(
        val apiUrl: String,
        val country: Country,
        val usStateCode: String
    ) {
        /** 实际用于拉取频道的数据源地址列表，依次拉取后合并 */
        val sourceUrls: List<String>
            get() = when {
                country != Country.USA -> listOf(apiUrl)
                usStateCode.isBlank() -> listOf(
                    usNationalSourceUrl()
                )
                else -> listOf(
                    usNationalSourceUrl(),
                    RegionProvider.usStates.find { it.code == usStateCode }
                        ?.let { stateSourceUrl(it) }
                        ?: usNationalSourceUrl()
                )
            }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 原始未筛选的频道列表 */
    private var rawChannels: List<Channel> = emptyList()

    init {
        // 监听 API URL 与 国家/州选择，三者共同决定实际拉取的数据源
        viewModelScope.launch {
            combine(
                settingsRepository.apiUrl,
                settingsRepository.country,
                settingsRepository.usStateCode
            ) { apiUrl, country, usStateCode ->
                SourceConfig(apiUrl, country, usStateCode)
            }.collect { source ->
                _uiState.value = _uiState.value.copy(
                    apiUrl = source.apiUrl,
                    country = source.country,
                    usStateCode = source.usStateCode
                )
                loadChannels()
            }
        }

        // 监听地区设置、隐藏分类和收藏列表变化，重新筛选
        viewModelScope.launch {
            combine(
                settingsRepository.regionData,
                settingsRepository.hiddenCategories,
                settingsRepository.favoriteChannels
            ) { regionMap, hidden, favorites ->
                FilterSettings(regionMap, hidden, favorites)
            }.collect { filter ->
                applyFilter(rawChannels, filter)
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val mergeTxt = settingsRepository.mergeTxtEnabled.first()
            val mergeTxtUrl = settingsRepository.mergeTxtUrl.first()
            val stateCode = _uiState.value.usStateCode
            val country = _uiState.value.country
            val source = SourceConfig(_uiState.value.apiUrl, country, stateCode)
            // 美国模式下即使开启了 mergeTxt 也不合并 TXT（iptv-org 州源不提供 TXT），避免误拉不存在的地址
            val effectiveMerge = if (country == Country.USA) false else mergeTxt
            val result = channelRepository.fetchChannels(source.sourceUrls, effectiveMerge, if (country == Country.USA) "" else mergeTxtUrl)
            result.onSuccess { list ->
                rawChannels = list
                val regionMap = settingsRepository.regionData.first()
                val hidden = settingsRepository.hiddenCategories.first()
                val favorites = settingsRepository.favoriteChannels.first()
                applyFilter(list, FilterSettings(regionMap, hidden, favorites))
                // 异步加载当前节目并填充到频道卡片
                attachEpgPrograms(list)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "加载失败"
                )
            }
        }
    }

    /**
     * 异步加载所有频道"当前正在播放"的节目，填充到频道的 currentProgram 字段
     * EPG 地址取自频道列表（通常所有频道同源）
     */
    private fun attachEpgPrograms(channels: List<Channel>) {
        val epgUrl = channels.firstNotNullOfOrNull { it.epgUrl.ifBlank { null } } ?: return
        viewModelScope.launch {
            val currentMap = epgRepository.getCurrentProgrammes(epgUrl)
            if (currentMap.isEmpty()) return@launch
            val updated = channels.map { channel ->
                val program = currentMap[EpgParser.normalizeId(channel.tvgId)]
                    ?: currentMap[EpgParser.normalizeId(channel.name)]
                if (program != null) channel.copy(currentProgram = program.title) else channel
            }
            rawChannels = updated
            // 重新应用筛选（地区 + 隐藏分类），并保持当前选中分类不变
            val regionMap = settingsRepository.regionData.first()
            val hidden = settingsRepository.hiddenCategories.first()
            val favorites = settingsRepository.favoriteChannels.first()
            applyFilter(updated, FilterSettings(regionMap, hidden, favorites))
        }
    }

    /**
     * 筛选参数
     */
    private data class FilterSettings(
        val regionMap: Map<String, RegionEntry>,
        val hiddenCategoriesStr: String,
        val favoriteChannels: List<String>
    )

    /**
     * 综合筛选：地区 + 隐藏分类
     */
    private fun applyFilter(
        allChannels: List<Channel>,
        filter: FilterSettings
    ) {
        val hiddenList = filter.hiddenCategoriesStr.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val currentCountry = _uiState.value.country
        val currentUsState = RegionProvider.usStates
            .find { it.code == _uiState.value.usStateCode }

        // 1. 地区筛选：仅中国模式下按省份/城市过滤地方频道；
        //    美国模式的频道来自所选州的 iptv-org 节目单（英文台名），不在此处过滤
        val regionFiltered = if (currentCountry != Country.USA && filter.regionMap.isNotEmpty()) {
            allChannels.filter { channel ->
                if (channel.categories.any { it.contains("地方") }) {
                    // 检查频道是否匹配任一已设置的省份/城市
                    filter.regionMap.entries.any { (province, entry) ->
                        val provinceCities = RegionProvider.provinces
                            .find { it.name == province }
                            ?.cities
                            ?: emptyList()
                        val matchesProvince = entry.showProvince && channel.name.contains(province)
                        val matchesCity = if (entry.showAll) {
                            // 全省模式：匹配该省所有城市
                            provinceCities.any { channel.name.contains(it) }
                        } else {
                            // 城市模式：匹配已选城市
                            entry.cities.any { channel.name.contains(it) }
                        }
                        matchesProvince || matchesCity
                    }
                } else {
                    true
                }
            }
        } else {
            allChannels
        }

        // 2. 隐藏分类筛选
        val allCategories = regionFiltered.flatMap { it.categories }.distinct().filter { it.isNotBlank() }
        val filteredChannels = if (hiddenList.isEmpty()) {
            regionFiltered
        } else {
            regionFiltered.filter { channel -> channel.categories.none { it in hiddenList } }
        }

        // 3. 收藏频道虚拟分类：置顶显示（不受地区/隐藏分类影响）
        val favoriteSet = filter.favoriteChannels.toSet()
        val hasFavorites = allChannels.any { it.name in favoriteSet }
        val categories = filteredChannels.flatMap { it.categories }.distinct().filter { it.isNotBlank() }
        val displayCategories = buildList {
            if (hasFavorites) add(FAVORITE_CATEGORY)
            addAll(categories)
        }
        val currentCategory = _uiState.value.selectedCategory
        val newCategory = if (currentCategory in displayCategories) currentCategory else displayCategories.firstOrNull()

        // 构建地区筛选描述（中国按省份，美国按州）
        val isUs = currentCountry == Country.USA
        val regionDesc = if (isUs) {
            currentUsState?.let { "美国·${it.name}" } ?: "美国"
        } else {
            buildRegionDescription(filter.regionMap)
        }
        val hasRegion = if (isUs) currentUsState != null else filter.regionMap.isNotEmpty()

        // channels 始终为全量已过滤列表（各分类共用），
        // 展示层(HomeScreen)再按选中分类/收藏集合过滤，避免切换分类时丢失或残留频道
        _uiState.value = _uiState.value.copy(
            channels = filteredChannels,
            categories = displayCategories,
            allCategories = allCategories,
            selectedCategory = newCategory,
            isLoading = false,
            errorMessage = null,
            regionFilter = regionDesc,
            hasRegionFilter = hasRegion,
            hiddenCategories = hiddenList,
            favoriteChannels = filter.favoriteChannels
        )
    }

    /**
     * 构建地区筛选描述文字
     * 例如: "广东·广州, 北京·北京" 或 "广东·全省, 湖南·长沙"
     */
    private fun buildRegionDescription(regionMap: Map<String, RegionEntry>): String {
        if (regionMap.isEmpty()) return ""
        return regionMap.entries.joinToString(", ") { (province, entry) ->
            if (entry.showAll) {
                // 全省模式：覆盖全省所有频道，含省级台
                "$province·全省"
            } else {
                // 仅显示所选城市，不显示省级频道状态
                if (entry.cities.isNotEmpty()) "$province·${entry.cities.joinToString("/")}" else "$province·全省"
            }
        }
    }

    fun selectCategory(category: String) {
        // channels 恒为全量已过滤列表，展示层(HomeScreen)负责按分类/收藏过滤，
        // 这里只切换选中分类，避免污染列表导致切回普通分类时丢失频道
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    /**
     * 恢复到默认播放列表地址并重新加载
     */
    fun resetToDefaultUrl() {
        viewModelScope.launch {
            settingsRepository.saveApiUrl(SettingsRepository.DEFAULT_API_URL)
            // apiUrl 变化会触发 init 中的 collect 自动调用 loadChannels()
        }
    }

    class Factory(
        private val channelRepository: ChannelRepository,
        private val settingsRepository: SettingsRepository,
        private val epgRepository: EpgRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(channelRepository, settingsRepository, epgRepository) as T
        }
    }
}