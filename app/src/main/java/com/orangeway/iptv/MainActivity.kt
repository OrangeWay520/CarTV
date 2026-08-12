package com.orangeway.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orangeway.iptv.data.Updater
import com.orangeway.iptv.player.PlayerActivity
import com.orangeway.iptv.ui.component.UpdateDialog
import com.orangeway.iptv.ui.screen.HomeScreen
import com.orangeway.iptv.ui.screen.HomeViewModel
import com.orangeway.iptv.ui.screen.SettingsScreen
import com.orangeway.iptv.ui.theme.OrangeIPTVCarTheme
import com.orangeway.iptv.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = OrangeIPTVCarApp.instance
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            OrangeIPTVCarTheme(themeMode = themeMode) {
                OrangeIPTVCarNavigation()
            }
        }
    }
}

@Composable
fun OrangeIPTVCarNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = OrangeIPTVCarApp.instance
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.channelRepository, app.settingsRepository, app.epgRepository)
    )

    // 全局更新管理器：启动时自动检查一次；About 页手动检查复用同一实例
    val scope = rememberCoroutineScope()
    val updater = remember(context.applicationContext) {
        Updater(context.applicationContext, scope)
    }
    LaunchedEffect(Unit) { updater.check() }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToPlaylistSettings = {
                    navController.navigate("settings/playlist")
                },
                onNavigateToPlayer = { channel ->
                    val urlsJson = channel.allUrls.joinToString(
                        separator = ",",
                        prefix = "[",
                        postfix = "]"
                    ) { "\"${it}\"" }
                    val mode = kotlinx.coroutines.runBlocking {
                        app.settingsRepository.decoderMode.first()
                    }
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        putExtra("channel_name", channel.name)
                        putExtra("channel_url", channel.url)
                        putExtra("channel_urls", urlsJson)
                        putExtra("decoder_mode", mode)
                        putExtra("tvg_id", channel.tvgId)
                        putExtra("epg_url", channel.epgUrl)
                    }
                    context.startActivity(intent)
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                homeViewModel = homeViewModel,
                updater = updater,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings/playlist") {
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                homeViewModel = homeViewModel,
                updater = updater,
                onNavigateBack = { navController.popBackStack() },
                initialPage = "PLAYLIST"
            )
        }
    }

    // 全局更新对话框（启动自动检查发现新版本时也会弹出）
    UpdateDialog(updater)
}