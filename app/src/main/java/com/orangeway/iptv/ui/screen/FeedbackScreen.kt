package com.orangeway.iptv.ui.screen

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeway.iptv.BuildConfig
import com.orangeway.iptv.data.ChannelResult
import com.orangeway.iptv.data.FeedbackManager
import com.orangeway.iptv.data.FeedbackOutcome
import kotlinx.coroutines.launch

/** 问题反馈页：填写标题与内容，自动附带设备信息，同时提交到 GitHub Issues 与微信推送 */
@Composable
fun FeedbackPage(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<FeedbackOutcome?>(null) }

    // 自动附带设备信息，便于排查问题
    val deviceInfo = """
        
        ---
        设备型号: ${Build.MANUFACTURER} ${Build.MODEL}
        系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        应用版本: v${BuildConfig.VERSION_NAME}
    """.trimIndent()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "反馈将同时提交到 GitHub Issues 和 WxPusher，便于快速定位和修复问题。请尽量描述清楚问题现象和操作步骤。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("问题标题") },
                    placeholder = { Text("例如：CCTV5 播放黑屏") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    label = { Text("问题描述") },
                    placeholder = { Text("请描述遇到的问题、操作步骤、出现频率等") }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (title.isBlank() || content.isBlank()) {
                            Toast.makeText(context, "请填写问题标题和描述", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        submitting = true
                        scope.launch {
                            val result = FeedbackManager.submit(
                                title = title.trim(),
                                body = content.trim() + deviceInfo
                            )
                            submitting = false
                            outcome = result
                            // 两条路线都成功才清空输入，方便失败时重试
                            if (result.github is ChannelResult.Success &&
                                result.wxpusher is ChannelResult.Success
                            ) {
                                title = ""
                                content = ""
                            }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (submitting) {
                        // 提交中：小加载圈 + 提示文字，保持按钮形状不变
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "提交中…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text("提交反馈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                if (!submitting) {
                    TextButton(
                        onClick = {
                            title = ""
                            content = ""
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("清空输入")
                    }
                }
            }
        }
    }

    // 提交结果感谢窗口：显示两条路线各自的提交状态
    outcome?.let { o ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            title = {
                Text(
                    text = if (o.anySuccess) "感谢你的反馈" else "反馈提交失败",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChannelStatusRow(name = "GitHub Issues", result = o.github)
                    ChannelStatusRow(name = "WxPusher", result = o.wxpusher)
                    Text(
                        text = if (o.anySuccess) {
                            "感谢你的反馈！每条意见都会被认真对待，帮助橙子网络电视变得更好。"
                        } else {
                            "两条提交路线均未成功，请检查网络后重试。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { outcome = null }) { Text("好的") }
            }
        )
    }
}

/** 单条提交路线的状态行（成功/失败） */
@Composable
private fun ChannelStatusRow(name: String, result: ChannelResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (result) {
            is ChannelResult.Success -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "提交成功",
                    tint = Color(0xFF4CAF50)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$name：提交成功",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is ChannelResult.Failure -> {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "提交失败",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$name：提交失败（${result.reason}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
