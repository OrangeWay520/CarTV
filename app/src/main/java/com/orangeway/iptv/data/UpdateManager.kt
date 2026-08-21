package com.orangeway.iptv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.orangeway.iptv.BuildConfig
import com.orangeway.iptv.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/**
 * 全局"有新版本可更新"角标状态：启动时调用 [check] 自动检查一次，
 * 首页「设置」图标与关于页「检查更新」入口共用此状态点亮红点角标。
 */
object UpdateCheck {
    /** 是否有可安装的新版本（Compose 可观察，驱动红点显隐） */
    var hasUpdate by mutableStateOf(false)
        private set

    private var started = false

    /** 启动后只自动检查一次；发现可安装新版本则置 true */
    suspend fun check(context: Context) {
        if (started) return
        started = true
        try {
            hasUpdate = when (UpdateManager(context).checkUpdate()) {
                is UpdateResult.Latest -> true
                else -> false
            }
        } catch (_: Exception) {
            hasUpdate = false
        }
    }

    /** 由页面（切换下载源后重新检查）同步红点状态 */
    fun refresh(result: UpdateResult) {
        hasUpdate = result is UpdateResult.Latest
    }

    /** 重置检查标记，供开发者调试用 */
    fun reset() {
        started = false
        hasUpdate = false
    }
}

/** 最新版本信息 */
data class UpdateInfo(
    val versionName: String,     // 例如 "1.0.3"
    val changelog: String,       // 更新内容
    val apkUrl: String,          // APK 下载直链
    val source: DownloadSource = DownloadSource.fromId(null), // 检查到该版本所用的下载源
)

/** 检查结果 */
sealed interface UpdateResult {
    data class Latest(val info: UpdateInfo) : UpdateResult
    data object NoUpdate : UpdateResult
    data object Error : UpdateResult
}

/** 安装 APK 的调用结果 */
sealed interface InstallResult {
    /** 已允许安装未知应用，已调起系统安装界面 */
    data object Granted : InstallResult
    /** 未允许安装未知应用，需要引导用户先开启「安装未知应用」 */
    data object NeedPermission : InstallResult
    /** 调起系统安装界面时发生异常，msg 为可读原因 */
    data class Failed(val msg: String) : InstallResult
}

/** 更新流程状态（Compose 可观察） */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Found(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState
    data class Downloaded(val info: UpdateInfo, val file: File) : UpdateState
    data class DownloadError(val info: UpdateInfo, val msg: String = "") : UpdateState
    data object NoUpdate : UpdateState
    data object Error : UpdateState
}

/**
 * 更新管理器：按用户在检查更新页下拉托盘选择的下载源（GitCode/Gitee/GitHub）查询/下载，
 * 下载 APK 到应用外部目录、调起系统安装界面。不做源之间的自动切换。
 */
class UpdateManager(private val context: Context) {

    companion object {
        private const val USER_AGENT = "OrangeIPTV/1.0"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .proxySelector(ProxySelector.getDefault())
        .build()

    /** 检查是否有新版本（无更新 / 失败分别返回对应结果）。只遍历当前所选下载源 */
    suspend fun checkUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        val source = DownloadSource.fromId(SettingsRepository(context).downloadSource.first())
        for (url in source.checkFallbackUrls()) {
            try {
                val result = fetchLatest(url, source)
                if (result != null) return@withContext result
            } catch (_: Exception) {
                // 当前地址失败，尝试该源下一个回退地址
            }
        }
        UpdateResult.Error
    }

    /** 下载 APK 到应用外部目录，返回文件；全部候选地址失败则抛异常（IO 线程执行）。
     *  只从当前源下载，下载后做 PK 魔数校验，避免误下载到 HTML 错误页。 */
    suspend fun downloadApk(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val source = info.source
        val target = File(context.getExternalFilesDir(null), "update_${info.versionName}.apk")
        for (url in source.downloadFallbackUrls(info.apkUrl)) {
            try {
                downloadTo(url, target, onProgress)
                if (!isValidApk(target)) error("下载文件损坏")
                return@withContext target
            } catch (_: Exception) {
                target.delete()
            }
        }
        error("下载失败")
    }

    /** 调起系统安装界面（Android 8+ 需用户允许"安装未知应用"）。
     *  声明了 REQUEST_INSTALL_PACKAGES 后直接调系统安装，系统自行弹出确认框；
     *  仅当系统拒绝调起时才返回 NeedPermission 兜底引导。 */
    fun installApk(file: File): InstallResult {
        if (!file.exists() || file.length() < 1) {
            return InstallResult.Failed("更新包文件不存在或已损坏，请重新下载")
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.Granted
        } catch (e: SecurityException) {
            InstallResult.NeedPermission
        } catch (e: Exception) {
            InstallResult.Failed("无法调起安装界面（${e.message}）")
        }
    }

    /** 跳转系统设置开启「允许安装未知应用」；个别设备不支持该 Intent 时退回应用详情页 */
    fun openInstallSourceSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 拉取最新 release 并与本地版本比较；无更新返回 NoUpdate */
    private fun fetchLatest(url: String, source: DownloadSource): UpdateResult? {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank()) throw IllegalStateException("empty tag")
            val changelog = json.optString("body").trim()
            val apkUrl = source.resolveApkUrl(json)
                ?: throw IllegalStateException("no apk asset")
            val latest = parseVersion(tag) ?: return null
            val current = parseVersion(BuildConfig.VERSION_NAME) ?: return null
            return if (compareVersions(latest, current) > 0) {
                UpdateResult.Latest(UpdateInfo(tag, changelog, apkUrl, source))
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

    /** 简单校验下载文件确为 APK：以 ZIP 魔数(PK)开头且大于 1MB，兜底误下载到 HTML 错误页的情况 */
    private fun isValidApk(file: File): Boolean {
        if (file.length() < 1_000_000) return false
        return try {
            file.inputStream().use { ins ->
                val magic = ByteArray(2)
                ins.read(magic) == 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
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
            // 同步全局红点（切换下载源后重新检查也要更新角标）
            UpdateCheck.refresh(result)
            state = when (result) {
                is UpdateResult.Latest -> {
                    val info = result.info
                    val dir = appContext.getExternalFilesDir(null)
                    val exact = dir?.let { File(it, "update_${info.versionName}.apk") }
                    if (exact != null && exact.exists() && isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) {
                        pendingFile = exact
                        pendingInfo = info
                        UpdateState.Downloaded(info, exact)
                    } else {
                        UpdateState.Found(info)
                    }
                }
                UpdateResult.NoUpdate -> {
                    // 没有新版本发布，但可能之前已下载过 APK，检查本地文件（只认比当前版本新的）
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
                pendingFile = file
                pendingInfo = info
                state = UpdateState.Downloaded(info, file)
            } catch (e: Exception) {
                state = UpdateState.DownloadError(info, e.message ?: "")
            }
        }
    }

    /** 安装已下载的 APK；返回安装是否已成功调起（未开启安装权限时返回 NeedPermission） */
    fun install(): InstallResult {
        val downloaded = state as? UpdateState.Downloaded
            ?: return InstallResult.Failed("更新包尚未下载完成")
        return UpdateManager(appContext).installApk(downloaded.file)
    }

    fun dismiss() {
        if (state is UpdateState.Found ||
            state is UpdateState.DownloadError ||
            state is UpdateState.Downloaded
        ) {
            state = UpdateState.Idle
        }
    }

    /** 查找已下载且比当前安装版本新的 APK；已安装过的旧 APK 会被清理 */
    private fun findDownloadedApk(): File? {
        val current = BuildConfig.VERSION_NAME
        val check = { file: File ->
            val fileVer = file.name.removePrefix("update_").removeSuffix(".apk")
            // 已下载版本不高于当前安装版本 → 说明已装过，删除并忽略
            if (isNewerVersion(fileVer, current)) {
                file.takeIf { it.exists() }
            } else {
                file.delete()
                null
            }
        }
        pendingFile?.let { return check(it) }
        val dir = appContext.getExternalFilesDir(null) ?: return null
        val files = dir.listFiles { f -> f.name.startsWith("update_") && f.name.endsWith(".apk") }
            ?: return null
        return files.sortedByDescending { it.lastModified() }.firstNotNullOfOrNull(check)
    }

    /** 判断 a 版本是否高于 b 版本 */
    private fun isNewerVersion(a: String, b: String): Boolean {
        val an = a.split('.').mapNotNull { it.toIntOrNull() }
        val bn = b.split('.').mapNotNull { it.toIntOrNull() }
        val len = maxOf(an.size, bn.size)
        for (i in 0 until len) {
            val x = an.getOrElse(i) { 0 }
            val y = bn.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}