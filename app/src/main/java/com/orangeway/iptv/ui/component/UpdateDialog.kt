package com.orangeway.iptv.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.orangeway.iptv.BuildConfig
import com.orangeway.iptv.data.Updater
import com.orangeway.iptv.data.UpdateState

/** 更新对话框：发现新版本 / 下载进度 / 下载完成 / 下载失败 */
@Composable
fun UpdateDialog(updater: Updater) {
    when (val s = updater.state) {
        is UpdateState.Found -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("发现新版本 ${s.info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "当前版本：${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (s.info.changelog.isNotBlank()) {
                        Text(
                            text = s.info.changelog,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { updater.download() }) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { updater.dismiss() }) { Text("以后再说") }
            }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("正在下载更新…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // drawStopIndicator = {} 去掉进度条末端的圆点
                    LinearProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {}
                    )
                    Text(
                        text = "${s.progress}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = { },
            dismissButton = { }
        )

        is UpdateState.Downloaded -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("新版本 ${s.info.versionName} 下载完成") },
            text = {
                Text(
                    text = "更新包已下载完成，可立即安装或稍后安装。\n如果安装未弹出，请在系统设置中允许「安装未知应用」。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { updater.install() }) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = { updater.dismiss() }) { Text("稍后再说") }
            }
        )

        is UpdateState.DownloadError -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("下载失败") },
            text = { Text("请检查网络后重试。") },
            confirmButton = {
                Button(onClick = { updater.download() }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { updater.dismiss() }) { Text("取消") }
            }
        )

        else -> Unit
    }
}
