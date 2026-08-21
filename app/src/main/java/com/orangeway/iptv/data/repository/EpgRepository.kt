package com.orangeway.iptv.data.repository

import com.orangeway.iptv.data.model.EpgProgramme
import com.orangeway.iptv.data.parser.EpgParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 节目单(EPG)数据仓库
 * - 下载 XMLTV 格式节目单
 * - 按频道提取节目列表
 * - 内存缓存（同一 EPG 地址只下载解析一次）
 */
class EpgRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 缓存：epgUrl -> (归一化频道ID -> 节目列表)
    private val cache = mutableMapOf<String, Map<String, List<EpgProgramme>>>()

    // 每个 epgUrl 一把锁，避免并发重复下载
    private val mutexByUrl = mutableMapOf<String, Mutex>()

    // 首页当前节目缓存（含 EPG 地址、下载时间戳）
    private data class CurrentCache(val epgUrl: String, val time: Long, val programmes: Map<String, EpgProgramme>)
    private var currentCache: CurrentCache? = null
    private val currentMutex = Mutex()

    companion object {
        /** 首页当前节目缓存有效期（30 分钟），节目切换后重新下载 */
        private const val CURRENT_CACHE_TTL_MS = 30 * 60 * 1000L
    }

    /**
     * 获取所有频道"当前正在播放"的节目（用于首页频道卡片）
     * @param epgUrl 节目单地址
     * @return 归一化频道 ID -> 当前节目，获取失败返回空 Map
     */
    suspend fun getCurrentProgrammes(epgUrl: String): Map<String, EpgProgramme> =
        withContext(Dispatchers.IO) {
            if (epgUrl.isBlank()) return@withContext emptyMap()
            val now = System.currentTimeMillis()
            currentMutex.withLock {
                val cached = currentCache
                if (cached != null &&
                    cached.epgUrl == epgUrl &&
                    now - cached.time < CURRENT_CACHE_TTL_MS
                ) {
                    return@withLock cached.programmes
                }
                val content = download(epgUrl) ?: return@withLock emptyMap()
                val result = EpgParser.parseAllCurrent(content, now)
                currentCache = CurrentCache(epgUrl, now, result)
                result
            }
        }

    /**
     * 获取指定频道的节目列表
     * @param epgUrl 节目单地址（M3U 的 x-tvg-url）
     * @param channelId 频道 tvg-id
     * @param channelName 频道名（tvg-id 匹配失败时用频道名兜底）
     * @return 节目列表（按开始时间排序），获取失败返回空列表
     */
    suspend fun getProgrammes(
        epgUrl: String,
        channelId: String,
        channelName: String
    ): List<EpgProgramme> = withContext(Dispatchers.IO) {
        if (epgUrl.isBlank()) return@withContext emptyList()

        // 候选频道 ID：tvg-id 归一化 + 频道名归一化
        val candidates = buildSet {
            if (channelId.isNotBlank()) add(EpgParser.normalizeId(channelId))
            if (channelName.isNotBlank()) add(EpgParser.normalizeId(channelName))
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        val mutex = synchronized(mutexByUrl) { mutexByUrl.getOrPut(epgUrl) { Mutex() } }
        mutex.withLock {
            val parsed = cache[epgUrl] ?: run {
                val content = download(epgUrl) ?: return@withLock emptyList()
                val result = EpgParser.parseForChannels(content, candidates)
                cache[epgUrl] = result
                result
            }
            candidates.firstNotNullOfOrNull { parsed[it] } ?: emptyList()
        }
    }

    private fun download(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "OrangeIPTV/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
