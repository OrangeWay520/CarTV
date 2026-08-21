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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orangeway.iptv.data.UpdateCheck
import com.orangeway.iptv.player.PlayerActivity
import com.orangeway.iptv.ui.screen.CheckUpdatePage
import com.orangeway.iptv.ui.screen.HomeScreen
import com.orangeway.iptv.ui.screen.HomeViewModel
import com.orangeway.iptv.ui.screen.SettingsScreen
import com.orangeway.iptv.ui.theme.OrangeIPTVTheme
import com.orangeway.iptv.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = OrangeIPTVApp.instance
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            OrangeIPTVTheme(themeMode = themeMode) {
                OrangeIPTVNavigation()
            }
        }
    }
}

@Composable
fun OrangeIPTVNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = OrangeIPTVApp.instance
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.channelRepository, app.settingsRepository, app.epgRepository)
    )

    // 启动时自动检查一次，仅更新全局红点（首页「设置」图标与关于页「检查更新」入口角标），
    // 不再自动弹更新对话框；用户进入「检查更新」二级页时由该页自行检查。
    LaunchedEffect(context) { UpdateCheck.check(context.applicationContext) }

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
                onCheckUpdateClick = { navController.navigate("update") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings/playlist") {
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                homeViewModel = homeViewModel,
                onCheckUpdateClick = { navController.navigate("update") },
                onNavigateBack = { navController.popBackStack() },
                initialPage = "PLAYLIST"
            )
        }

        composable("update") {
            CheckUpdatePage(
                settingsRepository = app.settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}