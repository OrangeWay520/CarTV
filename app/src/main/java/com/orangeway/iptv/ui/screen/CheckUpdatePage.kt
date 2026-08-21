package com.orangeway.iptv.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.orangeway.iptv.BuildConfig
import com.orangeway.iptv.data.DownloadSource
import com.orangeway.iptv.data.InstallResult
import com.orangeway.iptv.data.UpdateManager
import com.orangeway.iptv.data.UpdateState
import com.orangeway.iptv.data.Updater
import com.orangeway.iptv.data.repository.SettingsRepository
import kotlinx.coroutines.launch

/**
 * 检查更新二级页面：仿首页地图顶部用户选择框做「下载源」下拉托盘（GitCode/Gitee/GitHub），
 * 自动检查更新，展示当前版本、检查结果与更新日志，支持下载与安装。
 */
@Composable
fun CheckUpdatePage(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { Updater(context.applicationContext, scope) }

    // 持久化的下载源选择（值变化即在 UI 上体现）
    val currentSourceId by settingsRepository.downloadSource.collectAsState(initial = DownloadSource.GITCODE.id)
    var currentSource by remember { mutableStateOf(DownloadSource.fromId(currentSourceId)) }
    var sourceMenu by remember { mutableStateOf(false) }

    // 安装未知应用权限未开启时弹窗引导
    var showInstallDialog by remember { mutableStateOf(false) }

    // 下载源持久化值变化（如其它入口切换过）时同步 UI 状态
    LaunchedEffect(currentSourceId) {
        currentSource = DownloadSource.fromId(currentSourceId)
    }

    // 进入页面自动检查更新
    LaunchedEffect(Unit) {
        updater.check()
    }

    // 当前版本号
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ========== 顶栏 ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "检查更新",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // 应用图标
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = "检查更新",
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "橙子网络电视",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "当前版本 v$versionName",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // ========== 下载源选择托盘（logo + 名字 + 下拉箭头）==========
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                // 固定宽度容器：让显示框与下拉托盘以同一中心对齐，避免托盘贴向最左
                Box(
                    modifier = Modifier.width(220.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // 显示框
                    Surface(
                        onClick = { sourceMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SourceLogo(logoRes = currentSource.logoRes, size = 26.dp)
                            Text(
                                currentSource.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 浮动 DropdownMenu：悬浮在页面内容上方，不把下方更新日志向下顶
                    DropdownMenu(
                        expanded = sourceMenu,
                        onDismissRequest = { sourceMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp,
                        modifier = Modifier.width(220.dp)
                    ) {
                    DownloadSource.entries.forEach { src ->
                        SourceSelectRow(
                            source = src,
                            checked = src == currentSource,
                            onClick = {
                                if (src != currentSource) {
                                    // 切换下载源并持久化，随后按新源重新检查
                                    currentSource = src
                                    scope.launch {
                                        settingsRepository.saveDownloadSource(src.id)
                                    }
                                    updater.check()
                                }
                                sourceMenu = false
                            }
                        )
                    }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 状态卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val state = updater.state) {
                        is UpdateState.Idle -> {
                            Text("准备检查更新…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        is UpdateState.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("正在检查更新…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        is UpdateState.Found -> {
                            Text(
                                "发现新版本",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "v${state.info.versionName}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "当前版本 v$versionName",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (state.info.changelog.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "更新日志",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    state.info.changelog,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp
                                )
                            }

                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { updater.download() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("立即更新", fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onBack) {
                                Text("稍后再说")
                            }
                        }

                        is UpdateState.NoUpdate -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "已是最新",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "当前已是最新版本",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "v$versionName",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is UpdateState.Error -> {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = "检查失败",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "检查更新失败",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "请检查网络后重试",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { updater.check() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("重试")
                            }
                        }

                        is UpdateState.Downloading -> {
                            CircularProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "正在下载更新…",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface,
                                strokeCap = StrokeCap.Round,
                                drawStopIndicator = {}
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${state.progress}%",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is UpdateState.Downloaded -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "下载完成",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "新版本 v${state.info.versionName} 下载完成",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "更新包已下载完成，可立即安装或稍后安装。\n" +
                                        "如果安装未弹出，请在系统设置中允许「安装未知应用」。",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    when (val r = updater.install()) {
                                        is InstallResult.NeedPermission -> showInstallDialog = true
                                        is InstallResult.Failed ->
                                            Toast.makeText(context, r.msg, Toast.LENGTH_LONG).show()
                                        is InstallResult.Granted -> {}
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("安装", fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onBack) {
                                Text("稍后再说")
                            }
                        }

                        is UpdateState.DownloadError -> {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = "下载失败",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "下载失败",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.msg.ifBlank { "请检查网络后重试" },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { updater.download() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("重试", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // 未开启「安装未知应用」权限时，引导用户去系统设置开启
        if (showInstallDialog) {
            AlertDialog(
                onDismissRequest = { showInstallDialog = false },
                title = { Text("需要开启安装权限", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "系统默认禁止本应用安装其他应用。\n" +
                            "点击「去开启」后，请在系统设置中打开「允许来自此来源的应用」，再返回点击「安装」。",
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showInstallDialog = false
                            UpdateManager(context.applicationContext).openInstallSourceSettings()
                        }
                    ) { Text("去开启") }
                },
                dismissButton = {
                    TextButton(onClick = { showInstallDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

/** 下载源 logo（官方 logo 自带品牌颜色与形状，直接展示不额外加底色/裁切） */
@Composable
private fun SourceLogo(logoRes: Int, size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(logoRes),
        contentDescription = null,
        modifier = Modifier.size(size)
    )
}

/** 下拉托盘中的单个下载源：logo + 源名 + 选中对勾 */
@Composable
private fun SourceSelectRow(
    source: DownloadSource,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceLogo(logoRes = source.logoRes, size = 26.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            source.label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (checked) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}