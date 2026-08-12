package com.orangeway.iptv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeway.iptv.R
import com.orangeway.iptv.data.model.RegionProvider
import com.orangeway.iptv.data.repository.RegionEntry
import com.orangeway.iptv.data.repository.SettingsRepository
import com.orangeway.iptv.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class SettingsPage {
    MAIN, PLAYLIST, REGION_PROVINCE, REGION_CITY, CATEGORY_FILTER, THEME, DECODER, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    initialPage: String = "MAIN"
) {
    val startPage = when (initialPage) {
        "PLAYLIST" -> SettingsPage.PLAYLIST
        "REGION_PROVINCE" -> SettingsPage.REGION_PROVINCE
        "CATEGORY_FILTER" -> SettingsPage.CATEGORY_FILTER
        "THEME" -> SettingsPage.THEME
        "DECODER" -> SettingsPage.DECODER
        "ABOUT" -> SettingsPage.ABOUT
        else -> SettingsPage.MAIN
    }
    var currentPage by remember { mutableStateOf(startPage) }
    var selectedProvinceName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val apiUrl by settingsRepository.apiUrl.collectAsState(initial = SettingsRepository.DEFAULT_API_URL)
    val refreshInterval by settingsRepository.refreshInterval.collectAsState(initial = SettingsRepository.DEFAULT_REFRESH_INTERVAL)
    val savedRegionMap by settingsRepository.regionData.collectAsState(initial = emptyMap())
    val uiState by homeViewModel.uiState.collectAsState()
    val mergeTxtEnabled by settingsRepository.mergeTxtEnabled.collectAsState(initial = false)
    val mergeTxtUrl by settingsRepository.mergeTxtUrl.collectAsState(initial = "")

    // 暂存区：进入地区设置时从 savedRegionMap 初始化，修改先保存在这里
    var pendingRegionMap by remember { mutableStateOf<Map<String, RegionEntry>>(emptyMap()) }

    // 城市选择页的实时状态（用于返回时也能保存修改）
    var citySelected by remember { mutableStateOf<List<String>>(emptyList()) }
    var cityShowAll by remember { mutableStateOf(false) }
    var cityShowProvince by remember { mutableStateOf(true) }

    /** 保存当前城市页状态到暂存区，返回省份页 */
    fun saveCitySelection() {
        if (citySelected.isEmpty() && !cityShowAll) {
            pendingRegionMap = pendingRegionMap - selectedProvinceName
        } else {
            pendingRegionMap = pendingRegionMap + (selectedProvinceName to RegionEntry(citySelected, cityShowAll, cityShowProvince))
        }
    }

    // 处理系统返回键（手势/硬件），与工具栏返回按钮行为一致
    BackHandler {
        when (currentPage) {
            SettingsPage.MAIN -> onNavigateBack()
            SettingsPage.REGION_CITY -> {
                saveCitySelection()
                currentPage = SettingsPage.REGION_PROVINCE
            }
            else -> currentPage = SettingsPage.MAIN
        }
    }

    // 进入地区设置页时初始化 pendingRegionMap，离开时清空
    LaunchedEffect(currentPage) {
        when (currentPage) {
            SettingsPage.REGION_PROVINCE, SettingsPage.REGION_CITY -> {
                if (pendingRegionMap.isEmpty()) {
                    pendingRegionMap = settingsRepository.regionData.first()
                }
            }
            SettingsPage.MAIN -> {
                pendingRegionMap = emptyMap()
            }
            else -> { /* 其他页面不处理 pendingRegionMap */ }
        }
    }

    var urlInput by remember(apiUrl) { mutableStateOf(apiUrl) }
    var intervalInput by remember(refreshInterval) { mutableStateOf(refreshInterval) }
    var mergeTxtUrlInput by remember(mergeTxtUrl) { mutableStateOf(mergeTxtUrl) }

    val title = when (currentPage) {
        SettingsPage.MAIN -> "设置"
        SettingsPage.PLAYLIST -> "播放列表设置"
        SettingsPage.REGION_PROVINCE -> "选择省份"
        SettingsPage.REGION_CITY -> "选择城市"
        SettingsPage.CATEGORY_FILTER -> "频道分类设置"
        SettingsPage.THEME -> "主题设置"
        SettingsPage.DECODER -> "解码模式"
        SettingsPage.ABOUT -> "关于"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentPage) {
                            SettingsPage.MAIN -> onNavigateBack()
                            SettingsPage.REGION_CITY -> {
                                saveCitySelection()
                                currentPage = SettingsPage.REGION_PROVINCE
                            }
                            else -> currentPage = SettingsPage.MAIN
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (currentPage) {
            SettingsPage.MAIN -> MainMenuPage(
                modifier = Modifier.padding(padding),
                regionMap = savedRegionMap,
                hiddenCategories = uiState.hiddenCategories,
                onPlaylistClick = { currentPage = SettingsPage.PLAYLIST },
                onRegionClick = { currentPage = SettingsPage.REGION_PROVINCE },
                onCategoryFilterClick = { currentPage = SettingsPage.CATEGORY_FILTER },
                onThemeClick = { currentPage = SettingsPage.THEME },
                onDecoderClick = { currentPage = SettingsPage.DECODER },
                onAboutClick = { currentPage = SettingsPage.ABOUT }
            )
            SettingsPage.PLAYLIST -> PlaylistSettingsPage(
                modifier = Modifier.padding(padding),
                urlInput = urlInput,
                intervalInput = intervalInput,
                mergeTxtEnabled = mergeTxtEnabled,
                mergeTxtUrlInput = mergeTxtUrlInput,
                onUrlChange = { urlInput = it },
                onIntervalChange = { intervalInput = it },
                onMergeTxtChange = { enabled ->
                    scope.launch {
                        settingsRepository.saveMergeTxtEnabled(enabled)
                    }
                },
                onMergeTxtUrlChange = { mergeTxtUrlInput = it },
                onSave = {
                    scope.launch {
                        settingsRepository.saveApiUrl(urlInput.trim())
                        settingsRepository.saveRefreshInterval(intervalInput.trim())
                        settingsRepository.saveMergeTxtUrl(mergeTxtUrlInput.trim())
                    }
                    currentPage = SettingsPage.MAIN
                },
                onResetToDefault = {
                    urlInput = SettingsRepository.DEFAULT_API_URL
                    intervalInput = SettingsRepository.DEFAULT_REFRESH_INTERVAL
                    mergeTxtUrlInput = ""
                    scope.launch {
                        settingsRepository.saveApiUrl(SettingsRepository.DEFAULT_API_URL)
                        settingsRepository.saveRefreshInterval(SettingsRepository.DEFAULT_REFRESH_INTERVAL)
                        settingsRepository.saveMergeTxtUrl("")
                    }
                    currentPage = SettingsPage.MAIN
                }
            )
            SettingsPage.REGION_PROVINCE -> ProvinceSelectionPage(
                modifier = Modifier.padding(padding),
                regionMap = pendingRegionMap,
                onProvinceSelected = { province ->
                    selectedProvinceName = province
                    // 初始化城市页状态
                    val entry = pendingRegionMap[province]
                    citySelected = entry?.cities?.filter { it.isNotBlank() } ?: emptyList()
                    cityShowAll = entry?.showAll ?: false
                    cityShowProvince = entry?.showProvince ?: true
                    currentPage = SettingsPage.REGION_CITY
                },
                onClearAll = {
                    pendingRegionMap = emptyMap()
                },
                onDone = {
                    scope.launch {
                        settingsRepository.saveAllRegions(pendingRegionMap)
                    }
                    currentPage = SettingsPage.MAIN
                }
            )
            SettingsPage.REGION_CITY -> CitySelectionPage(
                modifier = Modifier.padding(padding),
                provinceName = selectedProvinceName,
                selectedCities = citySelected,
                showAll = cityShowAll,
                showProvince = cityShowProvince,
                onStateChange = { cities, all, provinceChannels ->
                    citySelected = cities
                    cityShowAll = all
                    cityShowProvince = provinceChannels
                },
                onConfirm = {
                    saveCitySelection()
                    currentPage = SettingsPage.REGION_PROVINCE
                }
            )
            SettingsPage.CATEGORY_FILTER -> CategoryFilterPage(
                modifier = Modifier.padding(padding),
                allCategories = uiState.allCategories,
                savedHiddenCategories = uiState.hiddenCategories,
                onSave = { hiddenList ->
                    scope.launch {
                        settingsRepository.saveHiddenCategories(hiddenList)
                    }
                    currentPage = SettingsPage.MAIN
                }
            )
            SettingsPage.THEME -> ThemeSelectionPage(
                modifier = Modifier.padding(padding),
                settingsRepository = settingsRepository,
                onNavigateBack = { currentPage = SettingsPage.MAIN }
            )
            SettingsPage.DECODER -> DecoderSelectionPage(
                modifier = Modifier.padding(padding),
                settingsRepository = settingsRepository
            )
            SettingsPage.ABOUT -> AboutPage(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun MainMenuPage(
    modifier: Modifier,
    regionMap: Map<String, RegionEntry>,
    hiddenCategories: List<String>,
    onPlaylistClick: () -> Unit,
    onRegionClick: () -> Unit,
    onCategoryFilterClick: () -> Unit,
    onThemeClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val regionSubtitle = if (regionMap.isNotEmpty()) {
        "当前: " + regionMap.entries.joinToString(", ") { (province, entry) ->
            if (entry.showAll) "$province·全省"
            else {
                val parts = mutableListOf<String>()
                if (entry.showProvince) parts.add("$province·省级")
                parts.add("$province·${entry.cities.joinToString("/")}")
                parts.joinToString(", ")
            }
        }
    } else {
        "筛选地方频道，只看本地节目"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MenuCard(
            icon = Icons.Default.List,
            title = "播放列表设置",
            subtitle = "配置直播源地址和刷新间隔",
            onClick = onPlaylistClick
        )
        MenuCard(
            icon = Icons.Default.LocationOn,
            title = "地区设置",
            subtitle = regionSubtitle,
            onClick = onRegionClick
        )
        MenuCard(
            icon = Icons.Default.Category,
            title = "频道分类设置",
            subtitle = if (hiddenCategories.isNotEmpty()) "已隐藏 ${hiddenCategories.size} 个分类" else "隐藏不感兴趣的频道分类",
            onClick = onCategoryFilterClick
        )
        MenuCard(
            icon = Icons.Default.Palette,
            title = "主题设置",
            subtitle = "切换深浅色主题模式",
            onClick = onThemeClick
        )
        MenuCard(
            icon = Icons.Default.Memory,
            title = "解码模式",
            subtitle = "切换硬件/软件解码方式",
            onClick = onDecoderClick
        )
        MenuCard(
            icon = Icons.Default.Info,
            title = "关于",
            subtitle = "版本信息、数据来源等",
            onClick = onAboutClick
        )
    }
}

@Composable
private fun MenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistSettingsPage(
    modifier: Modifier,
    urlInput: String,
    intervalInput: String,
    mergeTxtEnabled: Boolean,
    mergeTxtUrlInput: String,
    onUrlChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onMergeTxtChange: (Boolean) -> Unit,
    onMergeTxtUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onResetToDefault: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    // 确认恢复对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "恢复默认播放列表",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("确定要恢复为默认播放列表吗？\n\n当前自定义的播放列表地址、刷新间隔和双源合并设置将被清除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetToDefault()
                }) {
                    Text("确定恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "播放列表地址",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("M3U/TXT 播放列表地址或 iptv-api 地址") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            text = "支持以下格式：\n" +
                    "1. M3U 播放列表 (https://.../xxx.m3u)\n" +
                    "2. TXT 播放列表 (https://.../xxx.txt)\n" +
                    "3. iptv-api 服务地址 (http://ip:端口)\n" +
                    "\n" +
                    "默认已内置 M3U 格式直播源，\n" +
                    "含央视、卫视等频道，支持台标显示。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "刷新间隔（分钟）",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        OutlinedTextField(
            value = intervalInput,
            onValueChange = onIntervalChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如: 30") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(16.dp))

        // === M3U+TXT 双源合并开关 ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mergeTxtEnabled)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "M3U+TXT 双源合并",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (mergeTxtEnabled)
                            "已开启：使用 M3U 的台标信息 + TXT 的更多源地址"
                        else
                            "关闭：仅使用当前播放列表的源地址",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = mergeTxtEnabled,
                    onCheckedChange = onMergeTxtChange
                )
            }
        }

        // === 自定义 TXT 合并地址（仅在开关开启时显示） ===
        if (mergeTxtEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "自定义合并地址（可选）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "留空时自动根据主地址后缀推导（.m3u → .txt，.txt → .m3u）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = mergeTxtUrlInput,
                        onValueChange = onMergeTxtUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/iptv.txt") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("恢复默认播放列表", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ProvinceSelectionPage(
    modifier: Modifier,
    regionMap: Map<String, RegionEntry>,
    onProvinceSelected: (String) -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(RegionProvider.provinces) { province ->
                val isSelected = province.name in regionMap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProvinceSelected(province.name) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 已选省份标记
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = province.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        // 显示已选城市的简要描述
                        val entry = regionMap[province.name]!!
                        val desc = if (entry.showAll) "全省"
                                   else {
                                       val parts = mutableListOf<String>()
                                       if (entry.showProvince) parts.add("省级")
                                       parts.addAll(entry.cities)
                                       parts.joinToString("/")
                                   }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        Text(
                            text = "${province.cities.size}个城市",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }

        // 底部操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onClearAll,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("清除所有地区筛选")
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存地区")
            }
        }
    }
}

@Composable
private fun CitySelectionPage(
    modifier: Modifier,
    provinceName: String,
    selectedCities: List<String>,
    showAll: Boolean,
    showProvince: Boolean,
    onStateChange: (List<String>, Boolean, Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    val province = RegionProvider.provinces.find { it.name == provinceName }
    val allCities = province?.cities ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 全省切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "显示全省频道",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "开启后显示 $provinceName 全省所有频道",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = showAll, onCheckedChange = { onStateChange(selectedCities, it, showProvince) })
        }

        // 省级频道切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "显示省级频道",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "开启后同时显示 $provinceName 省级频道",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = showProvince, onCheckedChange = { onStateChange(selectedCities, showAll, it) })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "或选择具体城市（可多选）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(allCities) { city ->
                val isSelected = city in selectedCities
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newCities = if (isSelected) selectedCities - city
                                           else selectedCities + city
                            onStateChange(newCities, showAll, showProvince)
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = city,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 确认按钮 - 保存当前状态并返回省份页
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("确认")
        }
    }
}

@Composable
private fun CategoryFilterPage(
    modifier: Modifier,
    allCategories: List<String>,
    savedHiddenCategories: List<String>,
    onSave: (List<String>) -> Unit
) {
    val hiddenSet = remember { mutableStateListOf<String>().apply { addAll(savedHiddenCategories) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "选择要在首页展示的频道分类",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "勾选 = 展示，取消勾选 = 隐藏",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (allCategories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请先返回首页加载频道列表后再来设置",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(allCategories) { category ->
                    val isHidden = category in hiddenSet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isHidden) hiddenSet.remove(category)
                                else hiddenSet.add(category)
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isHidden) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (isHidden) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "（已隐藏）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    hiddenSet.clear()
                    onSave(emptyList())
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("全部显示")
            }
            Button(
                onClick = { onSave(hiddenSet.toList()) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存设置")
            }
        }
    }
}

@Composable
private fun ThemeSelectionPage(
    modifier: Modifier,
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val currentTheme by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "选择主题模式",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.SYSTEM -> "跟随系统"
                ThemeMode.LIGHT -> "浅色"
                ThemeMode.DARK -> "深色"
            }
            val description = when (mode) {
                ThemeMode.SYSTEM -> "自动跟随系统主题设置"
                ThemeMode.LIGHT -> "始终使用浅色主题"
                ThemeMode.DARK -> "始终使用深色主题"
            }
            Card(
                onClick = {
                    scope.launch {
                        settingsRepository.saveThemeMode(mode)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentTheme == mode)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTheme == mode,
                        onClick = {
                            scope.launch {
                                settingsRepository.saveThemeMode(mode)
                            }
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecoderSelectionPage(
    modifier: Modifier,
    settingsRepository: SettingsRepository
) {
    val currentMode by settingsRepository.decoderMode.collectAsState(initial = "auto")
    val scope = rememberCoroutineScope()

    data class DecoderOption(val key: String, val label: String, val description: String)
    val options = listOf(
        DecoderOption("auto", "优先硬件解码", "使用GPU硬件解码，性能最佳。遇到不支持编码时自动回退到软件解码（推荐）"),
        DecoderOption("software", "优先软件解码", "使用FFmpeg软解码，兼容性最强。适合硬件解码器不支持的频道")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "选择解码模式",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        options.forEach { option ->
            Card(
                onClick = {
                    scope.launch {
                        settingsRepository.saveDecoderMode(option.key)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentMode == option.key)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == option.key,
                        onClick = {
                            scope.launch {
                                settingsRepository.saveDecoderMode(option.key)
                            }
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // 应用图标 - foreground有安全区留白，用requiredSize放大超出Box后裁剪
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "应用图标",
                modifier = Modifier.requiredSize(221.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "橙子网络电视",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "v1.0.0",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutRow("应用名称", "橙子网络电视")
                HorizontalDivider()
                AboutRow("版本号", "1.0.0")
                HorizontalDivider()
                AboutRow("应用描述", "车机网络电视播放器\n支持央视、卫视、地方频道等直播源播放")
                HorizontalDivider()
                AboutRow("数据来源", "live.zbds.top\nM3U 格式直播源，含台标信息")
                HorizontalDivider()
                AboutRow("技术栈", "Jetpack Compose + ExoPlayer + OkHttp")
                HorizontalDivider()
                AboutRow("开源协议", "MIT License")
                HorizontalDivider()
                AboutRow("开发者", "Orange Way")
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "© 2026 Orange Way. All rights reserved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}