package com.orangeway.iptv.ui.screen

import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaCompose
import com.hcaptcha.sdk.HCaptchaRenderMode
import com.hcaptcha.sdk.HCaptchaResponse
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import com.orangeway.iptv.BuildConfig
import com.orangeway.iptv.Config
import com.orangeway.iptv.R
import com.orangeway.iptv.data.FeedbackManager
import com.orangeway.iptv.data.FeedbackOutcome
import kotlinx.coroutines.launch

/** 问题反馈页：填写标题与内容，自动附带设备信息。
 *  提交前弹出 hCaptcha 人机验证（原生 Compose SDK），通过后携带 token 提交到 Worker 代理，不泄露底层通道。 */
@Composable
fun FeedbackPage(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<FeedbackOutcome?>(null) }
    // 是否正在显示人机验证弹窗（点击提交但尚未通过验证时弹出）
    val showCaptcha = remember { mutableStateOf(false) }
    // 待提交内容快照：点击提交时保存，验证通过后据此真正提交（避免验证弹窗期间用户再改动输入）
    val pendingTitle = remember { mutableStateOf("") }
    val pendingContent = remember { mutableStateOf("") }

    // 自动附带设备信息，便于排查问题（不含分割线：分割线在拼接处补上，紧贴描述下一行）
    val deviceTitle = stringResource(R.string.feedback_device_title)
    val modelLabel = stringResource(R.string.feedback_device_model_label)
    val osLabel = stringResource(R.string.feedback_device_os_label)
    val appLabel = stringResource(R.string.feedback_device_app_label)
    val deviceInfo = """
        $deviceTitle:
        $modelLabel: ${Build.MANUFACTURER} ${Build.MODEL}
        $osLabel: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        $appLabel: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
    """.trimIndent()

    // 真正提交反馈（hCaptcha 验证通过后携带 token 提交）
    val doSubmit: (String) -> Unit = { token ->
        val titleText = pendingTitle.value
        val bodyText = pendingContent.value
        submitting = true
        scope.launch {
            val result = FeedbackManager.submit(
                title = titleText.trim(),
                body = bodyText.trim(),
                captchaToken = token
            )
            submitting = false
            outcome = result
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.feedback_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feedback_title_colon)) },
                    placeholder = { Text(stringResource(R.string.feedback_title_hint)) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    label = { Text(stringResource(R.string.feedback_desc_colon)) },
                    placeholder = { Text(stringResource(R.string.feedback_content_hint)) }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (title.isBlank() || content.isBlank()) {
                            context.getString(R.string.feedback_empty_toast).let {
                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            }
                            return@Button
                        }
                        // 保存快照并弹出人机验证，通过后自动提交
                        pendingTitle.value = title.trim()
                        // 分割线紧贴问题描述下一行（不空行），再拼设备信息
                        pendingContent.value = content.trim() + "\n---\n" + deviceInfo
                        showCaptcha.value = true
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
                                text = stringResource(R.string.feedback_submitting),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(stringResource(R.string.feedback_submit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.feedback_clear))
                    }
                }
            }
        }
    }

    // 提交结果窗口：只告诉用户提交给作者，不泄露底层通道
    outcome?.let { o ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            title = {
                Text(
                    text = if (o.anySuccess) stringResource(R.string.feedback_thanks_title) else stringResource(R.string.feedback_fail_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (o.anySuccess) {
                        stringResource(R.string.feedback_thanks_body)
                    } else {
                        stringResource(R.string.feedback_fail_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { outcome = null }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // 人机验证：自建弹窗（不调暗屏幕）+ hCaptcha EMBEDDED 模式在弹窗内渲染 checkbox，
    // 弹窗带说明文字与取消按钮，取消后关闭弹窗可继续编辑问题。
    if (showCaptcha.value) {
        FeedbackCaptchaDialog(
            onToken = { token ->
                showCaptcha.value = false
                doSubmit(token)
            },
            onDismiss = { showCaptcha.value = false }
        )
    }
}

/** 人机验证弹窗：不调暗屏幕（dimBehind=false）。
 *  弹窗内说明文字 + hCaptcha 原生 EMBEDDED checkbox + 取消按钮（关闭弹窗继续编辑）。
 *  验证成功拿到 token 后回调提交；失败或取消则关闭弹窗，可重新点提交再试。 */
@Composable
private fun FeedbackCaptchaDialog(
    onToken: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // 是否已结束（取到 token 或失败/取消），防止重复回调导致重复提交
    var done by remember { mutableStateOf(false) }
    val config = remember(dark) {
        HCaptchaConfig.builder()
            .siteKey(Config.hcaptchaSiteKey)
            .theme(if (dark) HCaptchaTheme.DARK else HCaptchaTheme.LIGHT)
            .size(HCaptchaSize.NORMAL)
            .renderMode(HCaptchaRenderMode.EMBEDDED)
            .build()
    }

    Dialog(
        onDismissRequest = { if (!done) { done = true; onDismiss() } },
        // 不限制平台默认宽度，让弹窗充分展开，hCaptcha checkbox 完整显示不被右侧裁切
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 不调暗屏幕：清除对话框窗口默认的半透明黑色变暗背景
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)
                ?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            // 加轮廓描边与阴影，避免与页面底色融为一体
            border = BorderStroke(
                1.dp,
                if (dark) Color(0xFF3A3E4A) else Color(0xFFDFE2EA)
            ),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.captcha_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.captcha_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                // EMBEDDED 模式：SDK 在此容器内渲染 hCaptcha checkbox，不再弹 SDK 对话框（避免叠加遮挡）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HCaptchaCompose(config = config) { result ->
                        when (result) {
                            is HCaptchaResponse.Success -> {
                                if (!done) {
                                    done = true
                                    val token = result.token
                                    if (token.isNotEmpty()) onToken(token)
                                }
                            }
                            is HCaptchaResponse.Failure -> {
                                if (!done) {
                                    done = true
                                    Toast.makeText(context, context.getString(R.string.captcha_fail_toast), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                            is HCaptchaResponse.Event -> { /* Loaded/Opened 等生命周期事件，忽略 */ }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { if (!done) { done = true; onDismiss() } }) {
                        Text(stringResource(R.string.captcha_cancel))
                    }
                }
            }
        }
    }
}