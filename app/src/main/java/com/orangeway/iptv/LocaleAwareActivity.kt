package com.orangeway.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.fragment.app.FragmentActivity
import java.util.Locale

/**
 * 支持运行时切换语言的基类 Activity。
 * 在 attachBaseContext 包装应用所选语言的 context，并覆盖 getResources
 * 确保 stringResource / getString 按目标语言解析。切换语言后调用 recreate()。
 * 继承 FragmentActivity：hCaptcha 人机验证 SDK 内部依赖 Fragment 管理，
 * 必须由 FragmentActivity（或其子类）承载。
 */
@SuppressLint("Registered")
open class LocaleAwareActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun getResources(): Resources {
        val res = super.getResources()
        return try {
            if (res.configuration.locales[0] != Locale.getDefault()) {
                LocaleHelper.refreshResources(res)
            } else res
        } catch (_: Exception) {
            res
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}