package com.orangeway.iptv.data

import androidx.annotation.DrawableRes
import com.orangeway.iptv.R
import org.json.JSONObject

/**
 * 可选的 APK 下载源。用户可在检查更新页通过下拉托盘选择其一进行下载。
 * GitHub / GitCode / Gitee 三种源的 Release 元数据结构基本一致（tag_name/body/assets），
 * 差异仅在仓库地址与网络环境：GitHub 国内访问不稳定，检查与下载都会优先走 gh-proxy。
 */
enum class DownloadSource(
    val id: String,
    val label: String,
    @DrawableRes val logoRes: Int,
) {
    GITCODE("gitcode", "GitCode", R.drawable.src_gitcode),
    GITEE("gitee", "Gitee", R.drawable.src_gitee),
    GITHUB("github", "GitHub", R.drawable.src_github);

    /** 该源直连的检查更新接口 */
    fun checkUrl(): String = when (this) {
        GITCODE -> "https://api.gitcode.com/api/v5/repos/OrangeWay/OrangeIPTV/releases/latest"
        GITEE -> "https://gitee.com/api/v5/repos/orange-way/OrangeIPTV/releases/latest"
        GITHUB -> "https://api.github.com/repos/OrangeWay520/OrangeIPTV/releases/latest"
    }

    /** 依次尝试的检查更新地址：GitHub 先走 gh-proxy 代理、失败回退官方直连，其余源直接用直连 */
    fun checkFallbackUrls(): List<String> = when (this) {
        GITHUB -> listOf("https://v6.gh-proxy.org/${checkUrl()}", checkUrl())
        else -> listOf(checkUrl())
    }

    /** 下载 APK 时依次尝试的地址（对 GitHub 走代理优先，其余源直接用直链） */
    fun downloadFallbackUrls(apkUrl: String): List<String> = when (this) {
        GITHUB -> listOf("https://v6.gh-proxy.org/$apkUrl", apkUrl)
        else -> listOf(apkUrl)
    }

    /** 从 release JSON 的 assets 数组中找出 .apk 附件的下载直链 */
    fun resolveApkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk")) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    companion object {
        /** 按 id（含持久化存储值）取对应源；未知值回退 GitCode */
        fun fromId(id: String?): DownloadSource = entries.firstOrNull { it.id == id } ?: GITCODE
    }
}