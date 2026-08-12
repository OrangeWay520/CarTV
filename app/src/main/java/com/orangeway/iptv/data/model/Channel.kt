package com.orangeway.iptv.data.model

data class Channel(
    val name: String,
    val url: String,
    val urls: List<String> = emptyList(),
    val category: String = "",
    val logo: String = "",
    val tvgId: String = "",
    /** 节目单(EPG)数据源地址，从 M3U 的 x-tvg-url 属性解析 */
    val epgUrl: String = "",
    /** 当前正在播放的节目名称（首页显示用，由 EPG 填充） */
    val currentProgram: String = ""
) {
    /** 获取所有可用的播放地址 */
    val allUrls: List<String>
        get() = if (urls.isNotEmpty()) urls else listOf(url)
}