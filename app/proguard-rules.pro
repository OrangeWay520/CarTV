# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# FFmpeg 软解码扩展（含 native 方法和反射加载的类）
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclassmembers class androidx.media3.decoder.ffmpeg.** {
    native <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**