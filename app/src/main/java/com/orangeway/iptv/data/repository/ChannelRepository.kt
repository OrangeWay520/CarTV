package com.orangeway.iptv.data.repository

import com.orangeway.iptv.data.model.Channel
import com.orangeway.iptv.data.parser.PlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ChannelRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 获取频道列表
     * @param mergeTxtEnabled 是否启用 M3U+TXT 双源合并
     * @param mergeTxtUrl 自定义 TXT 合并地址（空字符串则自动根据主 URL 推导）
     * - 如果主 URL 以 .m3u/.m3u8 结尾，自动尝试将后缀改为 .txt 获取 TXT 源
     * - 如果主 URL 以 .txt 结尾，自动尝试将后缀改为 .m3u 获取 M3U 源
     * - 如果设置了自定义 mergeTxtUrl，则直接使用该地址
     * - 否则作为 iptv-api 服务的基础地址，尝试 /m3u -> /txt -> / 路径
     */
    suspend fun fetchChannels(
        apiUrl: String,
        mergeTxtEnabled: Boolean = false,
        mergeTxtUrl: String = ""
    ): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val url = apiUrl.trimEnd('/')
            val lower = url.lowercase()

            val channels = when {
                lower.endsWith(".m3u") || lower.endsWith(".m3u8") -> {
                    val m3uResult = tryFetch(url)
                    if (m3uResult.isSuccess) {
                        val m3uChannels = PlaylistParser.parseM3U(m3uResult.getOrThrow())
                        if (mergeTxtEnabled) {
                            // 使用自定义 TXT URL 或自动推导
                            val txtUrl = if (mergeTxtUrl.isNotBlank()) {
                                mergeTxtUrl
                            } else {
                                url.replace(Regex("\\.m3u8?$", RegexOption.IGNORE_CASE), ".txt")
                            }
                            if (txtUrl != url) {
                                val txtResult = tryFetch(txtUrl)
                                if (txtResult.isSuccess) {
                                    val txtChannels = PlaylistParser.parseTXT(txtResult.getOrThrow())
                                    mergeM3UWithTXT(m3uChannels, txtChannels)
                                } else {
                                    m3uChannels
                                }
                            } else {
                                m3uChannels
                            }
                        } else {
                            m3uChannels
                        }
                    } else {
                        return@withContext Result.failure(
                            Exception("无法解析播放列表，请检查地址是否正确")
                        )
                    }
                }
                lower.endsWith(".txt") -> {
                    val txtResult = tryFetch(url)
                    if (txtResult.isSuccess) {
                        val txtChannels = PlaylistParser.parseTXT(txtResult.getOrThrow())
                        if (mergeTxtEnabled) {
                            // TXT 为主时，尝试获取 M3U 源（含台标信息）
                            val m3uUrl = if (mergeTxtUrl.isNotBlank()) {
                                mergeTxtUrl
                            } else {
                                url.replace(Regex("\\.txt$", RegexOption.IGNORE_CASE), ".m3u")
                            }
                            if (m3uUrl != url) {
                                val m3uResult = tryFetch(m3uUrl)
                                if (m3uResult.isSuccess) {
                                    val m3uChannels = PlaylistParser.parseM3U(m3uResult.getOrThrow())
                                    mergeTXTWithM3U(txtChannels, m3uChannels)
                                } else {
                                    txtChannels
                                }
                            } else {
                                txtChannels
                            }
                        } else {
                            txtChannels
                        }
                    } else {
                        return@withContext Result.failure(
                            Exception("无法解析播放列表，请检查地址是否正确")
                        )
                    }
                }
                else -> {
                    val result = fetchFromIptvApi(url)
                    if (result.isSuccess) {
                        result.getOrThrow()
                    } else {
                        return@withContext Result.failure(result.exceptionOrNull()!!)
                    }
                }
            }

            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("播放列表为空"))
            }

            // 按频道名合并，同一频道所有 URL 都保留
            val merged = mergeChannels(channels)
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 按频道名合并多个 URL
     * 例如 CCTV1 在 TXT 文件中有多个地址，合并成一个 Channel 对象
     */
    private fun mergeChannels(channels: List<Channel>): List<Channel> {
        val merged = channels
            .groupBy { it.name }
            .map { (name, list) ->
                val first = list.first()
                // 使用 allUrls 而不是 url，确保 mergeM3UWithTXT 合并的多源信息不被丢失
                val allUrls = list.flatMap { it.allUrls }.distinct()
                first.copy(
                    urls = allUrls,
                    logo = list.firstOrNull { it.logo.isNotBlank() }?.logo ?: first.logo,
                    tvgId = list.firstOrNull { it.tvgId.isNotBlank() }?.tvgId ?: first.tvgId,
                    epgUrl = list.firstOrNull { it.epgUrl.isNotBlank() }?.epgUrl ?: first.epgUrl
                )
            }

        // 统计合并情况，便于排查单源问题
        val multiSourceCount = merged.count { it.allUrls.size > 1 }
        println("ChannelRepository: 原始频道数=${channels.size}, 合并后=${merged.size}, 多源频道数=$multiSourceCount")
        merged
            .filter { it.allUrls.size > 1 }
            .take(10)
            .forEach { println("  ${it.name}: ${it.allUrls.size} 个源") }

        return merged
    }

    /**
     * 合并 M3U 频道（含台标/EPG）和 TXT 频道（含更多源地址）
     * - 保留 M3U 频道的元数据（logo、tvgId、category）
     * - 将 TXT 中同名的频道 URL 追加到源列表
     * - 这样用 M3U 源时既能显示台标，又能有多源切换
     */
    private fun mergeM3UWithTXT(m3u: List<Channel>, txt: List<Channel>): List<Channel> {
        val txtByChannel = txt.groupBy { it.name }

        return m3u.map { m3uChannel ->
            val txtUrls = txtByChannel[m3uChannel.name]?.map { it.url } ?: emptyList()
            val allUrls = listOf(m3uChannel.url) + txtUrls.filter { it != m3uChannel.url }
            m3uChannel.copy(urls = allUrls.distinct())
        }
    }

    /**
     * 合并 TXT 频道（主）和 M3U 频道（含台标/EPG）
     * - 保留 TXT 频道的源地址
     * - 从 M3U 中提取同频道的台标/EPG 信息
     * - 这样用 TXT 源时也能显示台标
     */
    private fun mergeTXTWithM3U(txt: List<Channel>, m3u: List<Channel>): List<Channel> {
        val m3uByChannel = m3u.groupBy { it.name }
        return txt.map { txtChannel ->
            val m3uMatch = m3uByChannel[txtChannel.name]
            if (m3uMatch != null) {
                val m3uFirst = m3uMatch.first()
                // 保留 TXT 的 URL，补充 M3U 的台标信息
                txtChannel.copy(
                    logo = if (txtChannel.logo.isBlank()) m3uFirst.logo else txtChannel.logo,
                    tvgId = if (txtChannel.tvgId.isBlank()) m3uFirst.tvgId else txtChannel.tvgId,
                    category = if (txtChannel.category.isBlank()) m3uFirst.category else txtChannel.category
                )
            } else {
                txtChannel
            }
        }
    }

    /**
     * 从 iptv-api 服务获取
     * 优先尝试 M3U 格式，失败则回退到 TXT 格式
     */
    private suspend fun fetchFromIptvApi(baseUrl: String): Result<List<Channel>> {
        // 先尝试 M3U 格式
        val m3uResult = tryFetch("$baseUrl/m3u")
        if (m3uResult.isSuccess) {
            val channels = PlaylistParser.parseM3U(m3uResult.getOrThrow())
            if (channels.isNotEmpty()) {
                return Result.success(channels)
            }
        }

        // 再尝试 TXT 格式
        val txtResult = tryFetch("$baseUrl/txt")
        if (txtResult.isSuccess) {
            val channels = PlaylistParser.parseTXT(txtResult.getOrThrow())
            if (channels.isNotEmpty()) {
                return Result.success(channels)
            }
        }

        // 最后尝试默认格式
        val defaultResult = tryFetch(baseUrl)
        if (defaultResult.isSuccess) {
            val channels = PlaylistParser.parse(defaultResult.getOrThrow())
            if (channels.isNotEmpty()) {
                return Result.success(channels)
            }
        }

        return Result.failure(Exception("无法连接到 iptv-api 服务，请检查服务器地址是否正确"))
    }

    private fun tryFetch(url: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "OrangeIPTVCar/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Result.success(body)
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}