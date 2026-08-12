package com.orangeway.iptv

import android.app.Application
import com.orangeway.iptv.data.repository.ChannelRepository
import com.orangeway.iptv.data.repository.EpgRepository
import com.orangeway.iptv.data.repository.SettingsRepository

class OrangeIPTVCarApp : Application() {

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

    companion object {
        lateinit var instance: OrangeIPTVCarApp
            private set
    }
}