package com.orangeway.iptv.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeway.iptv.BuildConfig
import com.orangeway.iptv.LocaleManager
import com.orangeway.iptv.R
import com.orangeway.iptv.data.UpdateCheck
import com.orangeway.iptv.data.model.Country
import com.orangeway.iptv.data.model.RegionProvider
import com.orangeway.iptv.data.model.USState
import com.orangeway.iptv.data.repository.RegionEntry
import com.orangeway.iptv.data.repository.SettingsRepository
import com.orangeway.iptv.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class SettingsPage {
    MAIN, LANGUAGE, REGION_PROVINCE, REGION_CITY, CATEGORY_FILTER, THEME, DECODER, ABOUT, FEEDBACK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    homeViewModel: HomeViewModel,
    onCheckUpdateClick: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var selectedProvinceName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val savedRegionMap by settingsRepository.regionData.collectAsState(initial = emptyMap())
    val uiState by homeViewModel.uiState.collectAsState()
    val currentCountry by settingsRepository.country.collectAsState(initial = Country.CHINA)
    val currentUsStateCode by settingsRepository.usStateCode.collectAsState(initial = "")
    val currentUsState = RegionProvider.usStates.find { it.code == currentUsStateCode }

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
    // 注意：不能用 pendingRegionMap.isEmpty() 判断是否已初始化，
    // 否则用户清空所有选择（如取消全部城市勾选）返回省份页时，
    // 会被 DataStore 里的旧数据重新覆盖，导致已清除的选择又恢复
    var regionPendingInited by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        when (currentPage) {
            SettingsPage.REGION_PROVINCE -> {
                // 仅在首次进入地区设置时从 DataStore 加载
                if (!regionPendingInited) {
                    pendingRegionMap = settingsRepository.regionData.first()
                    regionPendingInited = true
                }
            }
            SettingsPage.MAIN -> {
                pendingRegionMap = emptyMap()
                regionPendingInited = false
            }
            else -> { /* 其他页面不处理 pendingRegionMap */ }
        }
    }

    val title = when (currentPage) {
        SettingsPage.MAIN -> stringResource(R.string.settings_title)
        SettingsPage.LANGUAGE -> stringResource(R.string.language_title)
        SettingsPage.REGION_PROVINCE -> stringResource(R.string.region_title)
        SettingsPage.REGION_CITY -> stringResource(R.string.city_title)
        SettingsPage.CATEGORY_FILTER -> stringResource(R.string.category_title)
        SettingsPage.THEME -> stringResource(R.string.theme_title)
        SettingsPage.DECODER -> stringResource(R.string.decoder_title)
        SettingsPage.ABOUT -> stringResource(R.string.about_title)
        SettingsPage.FEEDBACK -> stringResource(R.string.feedback_title)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                country = currentCountry,
                usState = currentUsState,
                onRegionClick = { currentPage = SettingsPage.REGION_PROVINCE },
                onCategoryFilterClick = { currentPage = SettingsPage.CATEGORY_FILTER },
                onThemeClick = { currentPage = SettingsPage.THEME },
                onDecoderClick = { currentPage = SettingsPage.DECODER },
                onLanguageClick = { currentPage = SettingsPage.LANGUAGE },
                onAboutClick = { currentPage = SettingsPage.ABOUT },
                onFeedbackClick = { currentPage = SettingsPage.FEEDBACK }
            )
            SettingsPage.LANGUAGE -> LanguageSelectionPage(
                modifier = Modifier.padding(padding)
            )
            SettingsPage.REGION_PROVINCE -> RegionSelectionPage(
                modifier = Modifier.padding(padding),
                regionMap = pendingRegionMap,
                country = currentCountry,
                usStateCode = currentUsStateCode,
                onCountryChange = { country ->
                    scope.launch {
                        settingsRepository.saveCountry(country)
                    }
                },
                onUsStateSelect = { code ->
                    scope.launch {
                        settingsRepository.saveUsStateCode(code)
                    }
                },
                onProvinceSelected = { province ->
                    selectedProvinceName = province
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
            SettingsPage.ABOUT -> AboutPage(
                modifier = Modifier.padding(padding),
                onCheckUpdateClick = onCheckUpdateClick
            )
            SettingsPage.FEEDBACK -> FeedbackPage(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun MainMenuPage(
    modifier: Modifier,
    regionMap: Map<String, RegionEntry>,
    hiddenCategories: List<String>,
    country: Country,
    usState: USState?,
    onRegionClick: () -> Unit,
    onCategoryFilterClick: () -> Unit,
    onThemeClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onAboutClick: () -> Unit,
    onFeedbackClick: () -> Unit
) {
    val entireProvince = stringResource(R.string.entire_province)
    val regionSubtitle = if (country == Country.USA) {
        if (usState != null) stringResource(R.string.menu_current_fmt, "美国·${usState.name}")
        else stringResource(R.string.menu_region_us_nostate)
    } else if (regionMap.isNotEmpty()) {
        stringResource(R.string.menu_current_fmt, regionMap.entries.joinToString(", ") { (province, entry) ->
            if (entry.showAll) "$province·$entireProvince"
            else {
                // 仅显示所选城市，不显示省级频道状态
                if (entry.cities.isNotEmpty()) "$province·${entry.cities.joinToString("/")}" else "$province·$entireProvince"
            }
        })
    } else {
        stringResource(R.string.menu_region_subtitle_none)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MenuCard(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.menu_region_title),
            subtitle = regionSubtitle,
            onClick = onRegionClick
        )
        MenuCard(
            icon = Icons.Default.Category,
            title = stringResource(R.string.menu_category_title),
            subtitle = if (hiddenCategories.isNotEmpty())
                stringResource(R.string.menu_category_subtitle_hidden_fmt, hiddenCategories.size)
            else
                stringResource(R.string.menu_category_subtitle_none),
            onClick = onCategoryFilterClick
        )
        MenuCard(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.menu_theme_title),
            subtitle = stringResource(R.string.menu_theme_subtitle),
            onClick = onThemeClick
        )
        MenuCard(
            icon = Icons.Default.Memory,
            title = stringResource(R.string.menu_decoder_title),
            subtitle = stringResource(R.string.menu_decoder_subtitle),
            onClick = onDecoderClick
        )
        MenuCard(
            icon = Icons.Default.Language,
            title = stringResource(R.string.menu_language_title),
            subtitle = stringResource(R.string.menu_language_subtitle),
            onClick = onLanguageClick
        )
        MenuCard(
            icon = Icons.Default.Feedback,
            title = stringResource(R.string.menu_feedback_title),
            subtitle = stringResource(R.string.menu_feedback_subtitle),
            onClick = onFeedbackClick
        )
        MenuCard(
            icon = Icons.Default.Info,
            title = stringResource(R.string.menu_about_title),
            subtitle = stringResource(R.string.menu_about_subtitle),
            onClick = onAboutClick,
            showBadge = UpdateCheck.hasUpdate
        )
    }
}

@Composable
private fun MenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showBadge: Boolean = false
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
            Box {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 有新版本可更新时，在箭头右上角点亮小红点
                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectionPage(modifier: Modifier) {
    val context = LocalContext.current
    val currentCode = remember { LocaleManager.langCode(context) }
    val explicit = remember { LocaleManager.isExplicit(context) }

    data class LangOption(val key: String?, val label: String, val description: String)
    val options = listOf(
        LangOption(null, stringResource(R.string.lang_follow_system), stringResource(R.string.lang_follow_system_desc)),
        LangOption("zh", stringResource(R.string.lang_simplified_chinese), stringResource(R.string.lang_chinese_desc)),
        LangOption("en", stringResource(R.string.lang_english), stringResource(R.string.lang_english_desc))
    )
    val selectedKey = if (explicit) currentCode else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.language_choose_hint),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        options.forEach { option ->
            Card(
                onClick = {
                    LocaleManager.save(context, option.key ?: "")
                    // 语言写入后重建整个 Activity，使新语言立即生效
                    context.findActivity()?.recreate()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedKey == option.key)
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
                        selected = selectedKey == option.key,
                        onClick = null
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

/** 沿 ContextWrapper 链向上查找 Activity，用于语言切换后触发 recreate() */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun RegionSelectionPage(
    modifier: Modifier,
    regionMap: Map<String, RegionEntry>,
    country: Country,
    usStateCode: String,
    onCountryChange: (Country) -> Unit,
    onUsStateSelect: (String) -> Unit,
    onProvinceSelected: (String) -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val entireProvince = stringResource(R.string.entire_province)
    val cityCountFmt = stringResource(R.string.city_count_fmt)

    Column(modifier = modifier.fillMaxSize()) {
        // 国家/地区选择卡片（顶部）
        CountrySelectorCard(
            country = country,
            onCountryChange = onCountryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (country == Country.USA) {
            // === 美国模式：可选"全国频道"或单选某州 ===
            // 不选州（全国）→ 仅国家频道；选州 → 国家频道 + 州频道（合并去重）
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 全国频道选项（不选州）
                item {
                    val isNational = usStateCode.isBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUsStateSelect("") }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isNational)
                                Icons.Default.Check
                            else
                                Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isNational)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.us_national),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isNational) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isNational)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.us_national_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.us_state_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
                    )
                }
                items(RegionProvider.usStates) { state ->
                    val isSelected = state.code == usStateCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUsStateSelect(state.code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected)
                                Icons.Default.Check
                            else
                                Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = state.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = state.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.us_state_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // 美国模式底部按钮：不需要强制选州，点完成即保存当前选择返回
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save_region))
            }
        } else {
            // === 中国模式：省份列表（原有逻辑） ===
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
                            val desc = if (entry.showAll) entireProvince
                                       else {
                                           // 仅显示所选城市，不显示省级频道状态
                                           if (entry.cities.isNotEmpty()) entry.cities.joinToString("/") else entireProvince
                                       }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.city_count_fmt, province.cities.size),
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
                    Text(stringResource(R.string.clear_filter))
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save_region))
                }
            }
        }
    }
}

/**
 * 国家/地区选择卡片：卡片内嵌下拉托盘（DropDown），用户选择 中国 / 美国。
 * 选择美国后下方列表切换为美国各州。
 */
@Composable
private fun CountrySelectorCard(
    country: Country,
    onCountryChange: (Country) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.country_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (country == Country.USA)
                            stringResource(R.string.country_usa)
                        else
                            stringResource(R.string.country_china),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded)
                            stringResource(R.string.country_collapse)
                        else
                            stringResource(R.string.country_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    // 菜单宽度与显示框一致，左右边缘完全对齐
                    modifier = Modifier.width(maxWidth)
                ) {
                    Country.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (option == Country.USA)
                                        stringResource(R.string.country_usa)
                                    else
                                        stringResource(R.string.country_china),
                                    fontWeight = if (option == country) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                expanded = false
                                if (option != country) onCountryChange(option)
                            },
                            trailingIcon = {
                                if (option == country) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (country == Country.USA)
                    stringResource(R.string.country_subtitle_us)
                else
                    stringResource(R.string.country_subtitle_cn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    text = stringResource(R.string.show_all_channel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.show_all_channel_desc_fmt, provinceName),
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
                    text = stringResource(R.string.show_province_channel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.show_province_channel_desc_fmt, provinceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = showProvince, onCheckedChange = { onStateChange(selectedCities, showAll, it) })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.or_select_cities),
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
            Text(stringResource(R.string.confirm))
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
            text = stringResource(R.string.category_filter_hint),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = stringResource(R.string.category_filter_tip),
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
                    text = stringResource(R.string.category_filter_empty),
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
                                text = stringResource(R.string.hidden_mark),
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
                Text(stringResource(R.string.show_all))
            }
            Button(
                onClick = { onSave(hiddenSet.toList()) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save_settings))
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
            text = stringResource(R.string.theme_choose_hint),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.SYSTEM -> stringResource(R.string.theme_follow_system)
                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                ThemeMode.DARK -> stringResource(R.string.theme_dark)
            }
            val description = when (mode) {
                ThemeMode.SYSTEM -> stringResource(R.string.theme_follow_system_desc)
                ThemeMode.LIGHT -> stringResource(R.string.theme_light_desc)
                ThemeMode.DARK -> stringResource(R.string.theme_dark_desc)
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
        DecoderOption("auto", stringResource(R.string.decoder_hw), stringResource(R.string.decoder_hw_desc)),
        DecoderOption("software", stringResource(R.string.decoder_sw), stringResource(R.string.decoder_sw_desc))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.decoder_choose_hint),
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
private fun AboutPage(modifier: Modifier, onCheckUpdateClick: () -> Unit) {
    var showDonateDialog by remember { mutableStateOf(false) }

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
                contentDescription = stringResource(R.string.app_icon),
                modifier = Modifier.requiredSize(221.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // 检查更新入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCheckUpdateClick() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = stringResource(R.string.check_update),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.check_update),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 有新版本可更新时，在箭头右上角点亮小红点
                    if (UpdateCheck.hasUpdate) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }
        }

        // 与下方卡片保持恰当间距
        Spacer(Modifier.height(12.dp))

        // 投喂作者入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showDonateDialog = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.VolunteerActivism,
                    contentDescription = stringResource(R.string.feed_author),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.feed_author),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 更新对话框已由 MainActivity 全局挂载，此处不再重复创建

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutRow(stringResource(R.string.about_app_name_label), stringResource(R.string.about_app_name))
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_version_label), BuildConfig.VERSION_NAME)
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_type_label), stringResource(R.string.about_app_type))
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_desc_label), stringResource(R.string.about_description))
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_features_label), stringResource(R.string.about_features))
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_tech_label), stringResource(R.string.about_tech))
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_developer_label), stringResource(R.string.about_developer))
                HorizontalDivider()
                AboutRow(stringResource(R.string.about_license_label), stringResource(R.string.about_license))
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "© 2026 Orange Way · MIT License",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
    }

    // 赞赏二维码弹窗（按当前实际主题明暗切换二维码图片）
    if (showDonateDialog) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.donate_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = if (isDark) R.drawable.donate_dark else R.drawable.donate_light
                        ),
                        contentDescription = stringResource(R.string.donate_qr_desc),
                        modifier = Modifier.size(260.dp)
                    )
                    Text(
                        text = stringResource(R.string.donate_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDonateDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
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