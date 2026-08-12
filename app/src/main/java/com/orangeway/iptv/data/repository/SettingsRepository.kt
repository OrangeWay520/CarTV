package com.orangeway.iptv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orangeway.iptv.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 一个省份的地区设置
 */
data class RegionEntry(
    val cities: List<String>,
    val showAll: Boolean,
    /** 是否同时显示该省的省级频道（默认开启） */
    val showProvince: Boolean = true
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val API_URL_KEY = stringPreferencesKey("api_url")
        private val REFRESH_INTERVAL_KEY = stringPreferencesKey("refresh_interval")
        private val REGION_DATA_KEY = stringPreferencesKey("region_data")
        private val HIDDEN_CATEGORIES_KEY = stringPreferencesKey("hidden_categories")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val DECODER_MODE_KEY = stringPreferencesKey("decoder_mode")
        private val MERGE_TXT_KEY = booleanPreferencesKey("merge_txt_enabled")
        private val MERGE_TXT_URL_KEY = stringPreferencesKey("merge_txt_url")

        // 默认使用 M3U 格式直播源（含台标、EPG 等元数据）
        // 也可部署 iptv-api (https://github.com/Guovin/iptv-api) 后填自己的地址
        const val DEFAULT_API_URL = "https://live.zbds.top/tv/iptv4.m3u"
        const val DEFAULT_REFRESH_INTERVAL = "30"
    }

    val apiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        // 用户首次安装时使用默认 M3U 源；用户手动修改后完全尊重其选择，不再自动迁移
        preferences[API_URL_KEY] ?: DEFAULT_API_URL
    }

    val refreshInterval: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_INTERVAL_KEY] ?: DEFAULT_REFRESH_INTERVAL
    }

    /** 所有地区设置，key=省份名，value=该省份的配置 */
    val regionData: Flow<Map<String, RegionEntry>> = context.dataStore.data.map { preferences ->
        parseRegionData(preferences[REGION_DATA_KEY] ?: "")
    }

    /** 用户隐藏的频道分类（逗号分隔） */
    val hiddenCategories: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[HIDDEN_CATEGORIES_KEY] ?: ""
    }

    /** 主题模式（默认跟随系统） */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun saveApiUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL_KEY] = url
        }
    }

    suspend fun saveRefreshInterval(interval: String) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_KEY] = interval
        }
    }

    /**
     * 保存一个省份的地区设置（合并到已有数据中）
     */
    suspend fun saveRegion(province: String, cities: List<String>, showAll: Boolean) {
        context.dataStore.edit { preferences ->
            val existing = parseRegionData(preferences[REGION_DATA_KEY] ?: "")
            val updated = existing.toMutableMap()
            updated[province] = RegionEntry(cities, showAll)
            preferences[REGION_DATA_KEY] = serializeRegionData(updated)
        }
    }

    /**
     * 保存整个地区设置 Map（覆盖写入）
     */
    suspend fun saveAllRegions(map: Map<String, RegionEntry>) {
        context.dataStore.edit { preferences ->
            preferences[REGION_DATA_KEY] = serializeRegionData(map)
        }
    }

    /**
     * 清除所有地区设置
     */
    suspend fun clearRegion() {
        context.dataStore.edit { preferences ->
            preferences.remove(REGION_DATA_KEY)
        }
    }

    suspend fun saveHiddenCategories(categories: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[HIDDEN_CATEGORIES_KEY] = categories.joinToString(",")
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    /** 解码模式：auto=优先硬件解码（默认），software=优先软解码 */
    val decoderMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DECODER_MODE_KEY] ?: "auto"
    }

    suspend fun saveDecoderMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DECODER_MODE_KEY] = mode
        }
    }

    /** M3U+TXT 双源合并开关（默认关闭） */
    val mergeTxtEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MERGE_TXT_KEY] ?: false
    }

    suspend fun saveMergeTxtEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MERGE_TXT_KEY] = enabled
        }
    }

    /** 自定义 TXT 合并地址（空字符串=自动推导） */
    val mergeTxtUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MERGE_TXT_URL_KEY] ?: ""
    }

    suspend fun saveMergeTxtUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[MERGE_TXT_URL_KEY] = url
        }
    }

    /**
     * 解析地区数据字符串
     * 格式: province:city1,city2:showAllFlag:showProvinceFlag|province2:city1:showAllFlag:showProvinceFlag
     * 示例: 广东:广州:0:1|北京:北京:0:1|湖南:长沙,株洲:1:0
     */
    private fun parseRegionData(data: String): Map<String, RegionEntry> {
        if (data.isBlank()) return emptyMap()
        return data.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 2) {
                val province = parts[0]
                val cities = if (parts[1].isNotBlank()) parts[1].split(",") else emptyList()
                val showAll = parts.size >= 3 && parts[2] == "1"
                // 向后兼容：旧数据没有 showProvince 字段时默认开启
                val showProvince = parts.size < 4 || parts[3] == "1"
                province to RegionEntry(cities, showAll, showProvince)
            } else null
        }.toMap()
    }

    private fun serializeRegionData(map: Map<String, RegionEntry>): String {
        return map.entries.joinToString("|") { (province, entry) ->
            // 即使 showAll=true 也保留城市列表，方便用户关闭"显示全省频道"后恢复城市选择
            val cities = entry.cities.joinToString(",")
            val showAllFlag = if (entry.showAll) "1" else "0"
            val showProvinceFlag = if (entry.showProvince) "1" else "0"
            "$province:$cities:$showAllFlag:$showProvinceFlag"
        }
    }
}