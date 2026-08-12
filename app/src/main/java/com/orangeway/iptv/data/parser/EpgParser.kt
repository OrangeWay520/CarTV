package com.orangeway.iptv.data.parser

import com.orangeway.iptv.data.model.EpgProgramme
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Xml

/**
 * 解析 XMLTV (e.xml) 格式的节目单数据
 *
 * XMLTV 结构：
 * <tv>
 *   <channel id="CCTV-1"><display-name>CCTV-1</display-name>...</channel>
 *   <programme start="20260811083000 +0800" stop="20260811093000 +0800" channel="CCTV-1">
 *     <title lang="zh">节目名称</title>
 *   </programme>
 * </tv>
 */
object EpgParser {

    // XMLTV 时间格式：20260811083000 +0800
    private val timeFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    /**
     * 归一化频道 ID：去空白、转小写、去常见符号
     * 用于容错匹配 tvg-id 与 XMLTV channel id 的差异（如 "CCTV-1" / "CCTV1"）
     */
    fun normalizeId(id: String): String =
        id.trim().lowercase(Locale.ROOT).replace(Regex("[\\s_\\-]+"), "")

    /**
     * 解析 XMLTV 内容，只提取指定候选频道的节目
     * @param content XML 内容
     * @param targetIds 候选频道 ID 集合（已归一化），如 tvg-id 与频道名的归一化结果
     * @return 归一化频道 ID -> 该频道节目列表（按开始时间排序）
     */
    fun parseForChannels(content: String, targetIds: Set<String>): Map<String, List<EpgProgramme>> {
        val result = mutableMapOf<String, MutableList<EpgProgramme>>()
        if (targetIds.isEmpty()) return result

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(content.reader())

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                    val channel = parser.getAttributeValue(null, "channel") ?: ""
                    val normalized = normalizeId(channel)
                    if (normalized in targetIds) {
                        val start = parseTime(parser.getAttributeValue(null, "start"))
                        val stop = parseTime(parser.getAttributeValue(null, "stop"))
                        val title = readProgrammeTitle(parser)
                        if (start != null && stop != null && title.isNotBlank()) {
                            result.getOrPut(normalized) { mutableListOf() }
                                .add(EpgProgramme(channel, start, stop, title))
                        }
                    } else {
                        // 跳过该 programme 节点内容
                        skipElement(parser)
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // 解析失败返回已解析部分
        }

        return result.mapValues { (_, list) -> list.sortedBy { it.startMillis } }
    }

    /**
     * 解析 XMLTV 内容，提取所有频道"当前时刻正在播放"的节目
     * 用于首页频道卡片显示节目预告。只保留当前播放的节目，内存占用小。
     * @param content XML 内容
     * @param nowMillis 当前时间（epoch 毫秒）
     * @return 归一化频道 ID -> 当前正在播放的节目
     */
    fun parseAllCurrent(content: String, nowMillis: Long): Map<String, EpgProgramme> {
        val result = mutableMapOf<String, EpgProgramme>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(content.reader())

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                    val channel = parser.getAttributeValue(null, "channel") ?: ""
                    val start = parseTime(parser.getAttributeValue(null, "start"))
                    val stop = parseTime(parser.getAttributeValue(null, "stop"))
                    val title = readProgrammeTitle(parser)
                    val normalized = normalizeId(channel)
                    if (normalized.isNotBlank() &&
                        start != null && stop != null &&
                        title.isNotBlank() &&
                        nowMillis in start until stop
                    ) {
                        result[normalized] = EpgProgramme(channel, start, stop, title)
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // 解析失败返回已解析部分
        }
        return result
    }

    /** 读取 programme 节点内的 <title> 文本 */
    private fun readProgrammeTitle(parser: XmlPullParser): String {
        var title = ""
        var event = parser.next()
        while (event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "title") {
                title = parser.nextText().trim()
            } else {
                event = parser.next()
                continue
            }
            event = parser.next()
        }
        return title
    }

    /** 跳过当前节点的所有子内容 */
    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** 解析 XMLTV 时间 "20260811083000 +0800" 为 epoch 毫秒 */
    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            timeFormat.parse(value.trim())?.time
        } catch (_: Exception) {
            null
        }
    }
}
