package com.orangeway.iptv.data.model

data class Channel(
    val name: String,
    val url: String,
    val urls: List<String> = emptyList(),
    /** 频道所属分类（M3U 的 group-title 可能用分号分隔多个分类，如 "Animation;Kids"，已拆分为列表） */
    val categories: List<String> = emptyList(),
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