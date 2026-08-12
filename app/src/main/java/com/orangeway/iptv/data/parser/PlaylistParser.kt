package com.orangeway.iptv.data.parser

import com.orangeway.iptv.data.model.Channel

/**
 * 解析 iptv-api 输出的 M3U/TXT 格式直播源列表
 */
object PlaylistParser {

    /**
     * 解析 M3U 格式
     * #EXTM3U x-tvg-url="http://epg.xxx/e.xml"
     * #EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV-1" tvg-logo="http://..." group-title="央视频道",CCTV-1
     * http://example.com/stream
     */
    fun parseM3U(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0

        // 第一行 #EXTM3U 可能带 x-tvg-url 属性（节目单地址）
        var epgUrl = ""
        while (i < lines.size && lines[i].trim().isBlank()) i++
        if (i < lines.size) {
            val first = lines[i].trim()
            if (first.startsWith("#EXTM3U")) {
                epgUrl = extractAttribute(first, "x-tvg-url")
            }
        }

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                // 解析频道信息
                val tvgId = extractAttribute(line, "tvg-id")
                val tvgName = extractAttribute(line, "tvg-name")
                val tvgLogo = extractAttribute(line, "tvg-logo")
                val groupTitle = extractAttribute(line, "group-title")
                // 频道名在最后一个逗号后面
                val name = line.substringAfterLast(",").trim()
                // 下一行是 URL
                if (i + 1 < lines.size) {
                    val url = lines[i + 1].trim()
                    if (url.isNotBlank() && !url.startsWith("#")) {
                        channels.add(
                            Channel(
                                name = name.ifBlank { tvgName },
                                url = url,
                                category = groupTitle,
                                logo = tvgLogo,
                                tvgId = tvgId,
                                epgUrl = epgUrl
                            )
                        )
                    }
                }
                i += 2
            } else {
                i++
            }
        }
        return channels
    }

    /**
     * 解析 TXT 格式 (iptv-api 默认格式)
     * 📺央视频道,#genre#
     * CCTV-1,http://example.com/stream
     * CCTV-2,http://example.com/stream2
     */
    fun parseTXT(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var currentCategory = "未分类"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // 分类行：以 #genre# 结尾
            if (trimmed.endsWith("#genre#")) {
                currentCategory = trimmed.substringBeforeLast(",", "").trim()
                if (currentCategory.isBlank()) {
                    currentCategory = trimmed.substringBeforeLast(",#genre#").trim()
                }
                continue
            }

            // 数据行：频道名,URL
            val parts = trimmed.split(",", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val url = parts[1].trim()
                if (name.isNotBlank() && url.isNotBlank() && !url.startsWith("#")) {
                    channels.add(
                        Channel(
                            name = name,
                            url = url,
                            category = currentCategory
                        )
                    )
                }
            }
        }
        return channels
    }

    /**
     * 自动检测格式并解析
     */
    fun parse(content: String): List<Channel> {
        val trimmed = content.trim()
        return if (trimmed.startsWith("#EXTM3U")) {
            parseM3U(trimmed)
        } else {
            parseTXT(trimmed)
        }
    }

    private fun extractAttribute(line: String, attr: String): String {
        val regex = """$attr="([^"]*)"""".toRegex()
        return regex.find(line)?.groupValues?.getOrElse(1) { "" } ?: ""
    }
}