package com.orangeway.iptv

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

/**
 * 应用内语言管理（简体中文 / English）：
 * - 未设置时跟随系统语言（系统中文 -> 简体中文，其它 -> English）
 * - 设置后强制使用所选语言，并通过 context 包装在运行时切换生效
 */
object LocaleManager {

    private const val PREFS = "app_locale"
    private const val KEY_LANG = "language"
    private const val ZH = "zh"
    private const val EN = "en"

    /** 当前生效语言码，用于设置页高亮 */
    fun langCode(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, null)
        if (saved == ZH || saved == EN) return saved
        // 跟随系统：直接读取系统真实语言。
        // 注意：不能依赖 Locale.getDefault()，因为 wrap() 里会把它改写成应用所选语言，
        // 否则从英文切回"跟随系统"时会误判成英文。
        return systemLanguage()
    }

    /** 读取系统真实语言。Resources.getSystem() 返回的系统全局资源不受本应用 Context 覆写影响 */
    private fun systemLanguage(): String {
        return try {
            val sysResource = Resources.getSystem()
            val sysLocales = sysResource.configuration.locales
            if (sysLocales.isEmpty) localeLanguage(Locale.getDefault())
            else localeLanguage(sysLocales[0])
        } catch (_: Exception) {
            localeLanguage(Locale.getDefault())
        }
    }

    private fun localeLanguage(locale: Locale): String =
        if (locale.language == ZH || locale.language.startsWith("zh")) ZH else EN

    /** 是否用户手动设置过语言 */
    fun isExplicit(context: Context): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, null)
        return saved == ZH || saved == EN
    }

    /** 保存并应用所选语言 */
    fun save(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, language).apply()
    }

    fun currentLocale(context: Context): Locale =
        if (langCode(context) == ZH) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
}

/** 把 locale 包装进 context，使 stringResource/getString 按所选语言解析 */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val locale = LocaleManager.currentLocale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return base.createConfigurationContext(config)
        }
        @Suppress("DEPRECATION")
        base.resources.updateConfiguration(config, base.resources.displayMetrics)
        return base
    }

    /** 重新按默认 locale 刷新 resources 配置（用于 Activity.getResources 覆盖） */
    fun refreshResources(res: Resources): Resources {
        val config = Configuration(res.configuration)
        config.setLocale(Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(Locale.getDefault()))
        }
        res.updateConfiguration(config, res.displayMetrics)
        return res
    }
}