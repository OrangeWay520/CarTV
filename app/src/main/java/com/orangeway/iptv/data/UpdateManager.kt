package com.orangeway.iptv.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.orangeway.iptv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/** 最新版本信息 */
data class UpdateInfo(
    val versionName: String, // 例如 "1.0.1"
    val changelog: String,   // 更新内容
    val apkUrl: String       // APK 下载直链
)

/** 检查结果 */
sealed interface UpdateResult {
    data class Latest(val info: UpdateInfo) : UpdateResult
    data object NoUpdate : UpdateResult
    data object Error : UpdateResult
}

/** 更新流程状态（Compose 可观察） */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Found(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState
    data class Downloaded(val info: UpdateInfo, val file: java.io.File) : UpdateState
    data class DownloadError(val info: UpdateInfo) : UpdateState
    data object NoUpdate : UpdateState
    data object Error : UpdateState
}

/**
 * 更新管理器：检查 GitHub Releases（tag 格式 vX.Y.Z，APK 作为 Release 附件）、
 * 下载 APK 到应用外部目录、调起系统安装界面。
 * 所有 GitHub 请求均优先走 gh-proxy 加速，失败回退官方直连。
 */
class UpdateManager(private val context: Context) {

    companion object {
        private const val API_URL =
            "https://v6.gh-proxy.org/https://api.github.com/repos/OrangeWay520/CarTV/releases/latest"
        private const val API_URL_DIRECT =
            "https://api.github.com/repos/OrangeWay520/CarTV/releases/latest"
        private const val PROXY_PREFIX = "https://v6.gh-proxy.org/"
        private const val USER_AGENT = "OrangeIPTVCar/1.0"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .proxySelector(ProxySelector.getDefault())
        .build()

    /** 检查是否有新版本（无更新 / 失败分别返回对应结果） */
    suspend fun checkUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        for (url in listOf(API_URL, API_URL_DIRECT)) {
            try {
                val result = fetchLatest(url)
                if (result != null) return@withContext result
            } catch (_: Exception) {
                // 当前地址失败，尝试下一个
            }
        }
        UpdateResult.Error
    }

    /** 下载 APK 到应用外部目录，返回文件；全部地址失败则抛异常（IO 线程执行） */
    suspend fun downloadApk(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(context.getExternalFilesDir(null), "update_${info.versionName}.apk")
        for (url in listOf(PROXY_PREFIX + info.apkUrl, info.apkUrl)) {
            try {
                downloadTo(url, target, onProgress)
                return@withContext target
            } catch (_: Exception) {
                target.delete()
            }
        }
        error("下载失败")
    }

    /** 调起系统安装界面（Android 8+ 需用户允许"安装未知应用"） */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 拉取 latest release 并与本地版本比较；无更新返回 NoUpdate */
    private fun fetchLatest(url: String): UpdateResult? {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank()) throw IllegalStateException("empty tag")
            val changelog = json.optString("body").trim()
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            if (apkUrl.isNullOrBlank()) throw IllegalStateException("no apk asset")

            val latest = parseVersion(tag) ?: throw IllegalStateException("bad tag")
            val current = parseVersion(BuildConfig.VERSION_NAME) ?: return null
            return if (compareVersions(latest, current) > 0) {
                UpdateResult.Latest(UpdateInfo(tag, changelog, apkUrl))
            } else {
                UpdateResult.NoUpdate
            }
        }
    }

    private fun downloadTo(url: String, file: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("empty body")
            val total = body.contentLength()
            FileOutputStream(file).use { fos ->
                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        fos.write(buffer, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseVersion(v: String): List<Int>? {
        val nums = v.trim().split('.').mapNotNull { it.toIntOrNull() }
        return if (nums.isNotEmpty()) nums else null
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

/** 更新流程控制器（Compose 状态机），由页面创建并持有 */
class Updater(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** 已下载但未安装的 APK 文件（跨会话保留，用于"稍后安装"） */
    private var pendingFile: File? = null
    private var pendingInfo: UpdateInfo? = null

    fun check() {
        // 检查中/下载中忽略重复触发
        if (state == UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            val result = UpdateManager(appContext).checkUpdate()
            state = when (result) {
                is UpdateResult.Latest -> UpdateState.Found(result.info)
                UpdateResult.NoUpdate -> {
                    // 没有新版本发布，但可能之前已下载过 APK，检查本地文件
                    val file = findDownloadedApk()
                    if (file != null) {
                        val info = pendingInfo ?: UpdateInfo(
                            versionName = file.name.removePrefix("update_").removeSuffix(".apk"),
                            changelog = "",
                            apkUrl = ""
                        )
                        UpdateState.Downloaded(info, file)
                    } else {
                        UpdateState.NoUpdate
                    }
                }
                UpdateResult.Error -> UpdateState.Error
            }
        }
    }

    fun download() {
        val info = (state as? UpdateState.Found)?.info
            ?: (state as? UpdateState.DownloadError)?.info
            ?: return
        state = UpdateState.Downloading(info, 0)
        scope.launch {
            try {
                val file = UpdateManager(appContext).downloadApk(info) { progress ->
                    state = UpdateState.Downloading(info, progress)
                }
                // 下载完成，保存到 pending 供稍后安装
                pendingFile = file
                pendingInfo = info
                state = UpdateState.Downloaded(info, file)
            } catch (_: Exception) {
                state = UpdateState.DownloadError(info)
            }
        }
    }

    /** 安装已下载的 APK */
    fun install() {
        val downloaded = state as? UpdateState.Downloaded ?: return
        UpdateManager(appContext).installApk(downloaded.file)
    }

    fun dismiss() {
        if (state is UpdateState.Found ||
            state is UpdateState.DownloadError ||
            state is UpdateState.Downloaded
        ) {
            state = UpdateState.Idle
        }
    }

    /** 查找已下载的 APK 文件：优先 pendingFile，其次扫描外部目录 */
    private fun findDownloadedApk(): File? {
        pendingFile?.let { if (it.exists()) return it }
        val dir = appContext.getExternalFilesDir(null) ?: return null
        val files = dir.listFiles { f -> f.name.startsWith("update_") && f.name.endsWith(".apk") }
            ?: return null
        // 返回最新的 APK 文件
        return files.maxByOrNull { it.lastModified() }?.takeIf { it.exists() }
    }
}
