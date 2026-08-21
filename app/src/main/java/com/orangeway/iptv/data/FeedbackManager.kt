package com.orangeway.iptv.data

import com.orangeway.iptv.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * 问题反馈：同时提交到 GitHub Issues 与 WxPusher 微信推送。
 * 网络请求均在 IO 线程执行（避免 NetworkOnMainThreadException）。
 * Token/UID 由 local.properties 注入 BuildConfig。
 */
object FeedbackManager {

    private const val WXPUSHER_API = "https://wxpusher.zjiecode.com/api/send/message"
    private const val GITHUB_API_MIRROR =
        "https://gh.llkk.cc/https://api.github.com/repos/OrangeWay520/OrangeIPTV/issues"
    private const val GITHUB_API_DIRECT =
        "https://api.github.com/repos/OrangeWay520/OrangeIPTV/issues"
    private const val USER_AGENT = "OrangeIPTV/1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .proxySelector(ProxySelector.getDefault())
        .build()

    /** 同时提交到微信推送与 GitHub Issues，返回两条路线各自结果 */
    suspend fun submit(title: String, body: String): FeedbackOutcome {
        val wxpusher = submitToWxpusher(title, body)
        val github = submitToGithub(title, body)
        return FeedbackOutcome(github = github, wxpusher = wxpusher)
    }

    /** WxPusher 通道（code=1000 成功） */
    private suspend fun submitToWxpusher(title: String, body: String): ChannelResult =
        withContext(Dispatchers.IO) {
            try {
                val appToken = BuildConfig.WXPUSHER_APPTOKEN
                val uid = BuildConfig.WXPUSHER_UID
                if (appToken.isBlank() || uid.isBlank()) {
                    ChannelResult.Failure("WxPusher 未配置")
                } else {
                    val json = JSONObject()
                        .put("appToken", appToken)
                        .put("content", body)
                        // 通知栏摘要，限制 20 字以内
                        .put("summary", "[橙子电视] ${title.take(12)}")
                        .put("contentType", 1) // 1 = 纯文本
                        .put("uids", JSONArray().put(uid))
                    val request = Request.Builder().url(WXPUSHER_API)
                        .header("User-Agent", USER_AGENT)
                        .post(json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(request).execute().use { resp ->
                        val bodyText = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val result = JSONObject(bodyText)
                            if (result.optInt("code", -1) == 1000) {
                                ChannelResult.Success("已提交到 WxPusher")
                            } else {
                                ChannelResult.Failure(result.optString("msg", "code=${result.optInt("code")}"))
                            }
                        } else {
                            ChannelResult.Failure("HTTP ${resp.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                ChannelResult.Failure(e.message ?: e.javaClass.simpleName)
            }
        }

    /** GitHub Issues 通道：镜像优先，官方直连兜底 */
    private suspend fun submitToGithub(title: String, body: String): ChannelResult =
        withContext(Dispatchers.IO) {
            try {
                val token = BuildConfig.GITHUB_TOKEN
                if (token.isBlank()) {
                    ChannelResult.Failure("GitHub 未配置")
                } else {
                    val json = JSONObject().put("title", title).put("body", body)
                    var lastError: ChannelResult = ChannelResult.Failure("所有地址均不可用")
                    for (url in listOf(GITHUB_API_MIRROR, GITHUB_API_DIRECT)) {
                        try {
                            val request = Request.Builder().url(url)
                                .header("User-Agent", USER_AGENT)
                                .header("Authorization", "token $token")
                                .post(json.toString().toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(request).execute().use { resp ->
                                val bodyText = resp.body?.string().orEmpty()
                                if (resp.isSuccessful) {
                                    val issue = JSONObject(bodyText)
                                    lastError = ChannelResult.Success(
                                        issue.optString("html_url", issue.optString("url"))
                                    )
                                } else {
                                    lastError = ChannelResult.Failure("HTTP ${resp.code}")
                                }
                            }
                            if (lastError is ChannelResult.Success) break
                        } catch (e: Exception) {
                            lastError = ChannelResult.Failure(e.message ?: e.javaClass.simpleName)
                        }
                    }
                    lastError
                }
            } catch (e: Exception) {
                ChannelResult.Failure(e.message ?: e.javaClass.simpleName)
            }
        }
}
