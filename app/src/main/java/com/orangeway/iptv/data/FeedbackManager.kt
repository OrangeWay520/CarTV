package com.orangeway.iptv.data

import com.orangeway.iptv.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/** 单个通道的提交结果 */
sealed interface ChannelResult {
    data class Success(val detail: String) : ChannelResult
    data class Failure(val reason: String) : ChannelResult
}

/** 反馈提交总结果：两条路线（GitHub Issues / 微信推送）各自状态 */
data class FeedbackOutcome(
    val github: ChannelResult,
    val wxpusher: ChannelResult
) {
    /** 至少一条路线提交成功 */
    val anySuccess: Boolean
        get() = github is ChannelResult.Success || wxpusher is ChannelResult.Success
}

/**
 * 问题反馈：把反馈内容提交到自建 Cloudflare Worker 代理。
 * 网络请求在 IO 线程执行（避免 NetworkOnMainThreadException）。
 * GitHub Token / WxPusher / hCaptcha Secret 都只存在于 Worker 环境变量中，不再打进 APK。
 * hCaptcha 人机验证在客户端完成（FeedbackScreen 弹出），通过后携带 token 一并交给 Worker 服务端校验。
 */
object FeedbackManager {

    private const val USER_AGENT = "OrangeIPTV/1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .proxySelector(ProxySelector.getDefault())
        .build()

    /** 同时提交到微信推送与 GitHub Issues（由 Worker 代发），返回两条路线各自结果。 */
    suspend fun submit(title: String, body: String, captchaToken: String): FeedbackOutcome =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                    .put("title", title)
                    .put("body", body)
                    .put("hcaptcha_token", captchaToken)
                val request = Request.Builder().url(Config.feedbackProxyUrl)
                    .header("User-Agent", USER_AGENT)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // 403=人机验证未通过/未配置；HTTP 错误统一按提交失败处理
                        return@withContext FeedbackOutcome(
                            github = ChannelResult.Failure("代理服务 HTTP ${resp.code}"),
                            wxpusher = ChannelResult.Failure("代理服务 HTTP ${resp.code}")
                        )
                    }
                    val result = JSONObject(resp.body?.string().orEmpty())
                    FeedbackOutcome(
                        github = parseChannel(result.optJSONObject("github")),
                        wxpusher = parseChannel(result.optJSONObject("wxpusher"))
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                FeedbackOutcome(
                    github = ChannelResult.Failure(msg),
                    wxpusher = ChannelResult.Failure(msg)
                )
            }
        }

    /** 解析 Worker 返回的单通道结果 {ok, detail} */
    private fun parseChannel(obj: JSONObject?): ChannelResult {
        if (obj == null) return ChannelResult.Failure("缺少通道结果")
        val ok = obj.optBoolean("ok", false)
        val detail = obj.optString("detail", if (ok) "已提交" else "提交失败")
        return if (ok) ChannelResult.Success(detail) else ChannelResult.Failure(detail)
    }
}