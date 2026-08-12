package com.orangeway.iptv.data.model

/**
 * 单个电视节目（EPG 节目单条目）
 */
data class EpgProgramme(
    /** 频道 ID（XMLTV 中的 channel id） */
    val channelId: String,
    /** 节目开始时间（epoch 毫秒） */
    val startMillis: Long,
    /** 节目结束时间（epoch 毫秒） */
    val endMillis: Long,
    /** 节目名称 */
    val title: String
) {
    /** 判断某时刻是否处于该节目的播放时间范围内 */
    fun isAtTime(timeMillis: Long): Boolean =
        timeMillis in startMillis until endMillis

    /** 格式化开始时间，如 "20:00" */
    val startTimeText: String
        get() = formatTime(startMillis)

    /** 格式化结束时间，如 "21:00" */
    val endTimeText: String
        get() = formatTime(endMillis)

    private fun formatTime(millis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        return String.format(
            java.util.Locale.CHINA, "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }
}
