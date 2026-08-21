package com.orangeway.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.orangeway.iptv.data.repository.ChannelRepository
import com.orangeway.iptv.data.repository.EpgRepository
import com.orangeway.iptv.data.repository.SettingsRepository

class OrangeIPTVApp : Application(), ImageLoaderFactory {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var channelRepository: ChannelRepository
        private set

    lateinit var epgRepository: EpgRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        channelRepository = ChannelRepository()
        epgRepository = EpgRepository()
    }

    /**
     * 全局 Coil 图片加载配置（用于频道台标）：
     * - 磁盘缓存：台标下载一次后持久化，下次启动直接读缓存，无需重新加载
     * - 内存缓存：加大到 30% 内存，保证切换频道时台标不会频繁被逐出
     * - 台标都是小图，此配置足以容纳全部央视/卫视台标
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: OrangeIPTVApp
            private set
    }
}