package com.orangeway.iptv.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orangeway.iptv.data.model.Channel
import com.orangeway.iptv.data.model.RegionProvider
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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 原始未筛选的频道列表 */
    private var rawChannels: List<Channel> = emptyList()

    init {
        // 监听 API URL 变化
        viewModelScope.launch {
            settingsRepository.apiUrl.collect { url ->
                _uiState.value = _uiState.value.copy(apiUrl = url)
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
            val result = channelRepository.fetchChannels(_uiState.value.apiUrl, mergeTxt, mergeTxtUrl)
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

        // 1. 地区筛选：遍历所有设置了地区的省份，匹配地方频道
        val regionFiltered = if (filter.regionMap.isEmpty()) {
            allChannels
        } else {
            allChannels.filter { channel ->
                if (channel.category.contains("地方")) {
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
        }

        // 2. 隐藏分类筛选
        val allCategories = regionFiltered.map { it.category }.distinct().filter { it.isNotBlank() }
        val filteredChannels = if (hiddenList.isEmpty()) {
            regionFiltered
        } else {
            regionFiltered.filter { it.category !in hiddenList }
        }

        // 3. 收藏频道虚拟分类：置顶显示（不受地区/隐藏分类影响）
        val favoriteSet = filter.favoriteChannels.toSet()
        val hasFavorites = allChannels.any { it.name in favoriteSet }
        val categories = filteredChannels.map { it.category }.distinct().filter { it.isNotBlank() }
        val displayCategories = buildList {
            if (hasFavorites) add(FAVORITE_CATEGORY)
            addAll(categories)
        }
        val currentCategory = _uiState.value.selectedCategory
        val newCategory = if (currentCategory in displayCategories) currentCategory else displayCategories.firstOrNull()

        // 选中"收藏频道"时：仅显示当前播放列表中已收藏的频道
        val displayChannels = if (newCategory == FAVORITE_CATEGORY) {
            allChannels.filter { it.name in favoriteSet }
        } else {
            filteredChannels
        }

        // 构建地区筛选描述
        val regionDesc = buildRegionDescription(filter.regionMap)

        _uiState.value = _uiState.value.copy(
            channels = displayChannels,
            categories = displayCategories,
            allCategories = allCategories,
            selectedCategory = newCategory,
            isLoading = false,
            errorMessage = null,
            regionFilter = regionDesc,
            hasRegionFilter = filter.regionMap.isNotEmpty(),
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