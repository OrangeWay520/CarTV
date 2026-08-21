package com.orangeway.iptv

/**
 * 网络与服务配置。
 *
 * 后端反馈代理（Cloudflare Worker，worker/worker.js 即其源码）。
 * 反馈内容先在本机完成 hCaptcha 人机验证，再携带 token 提交到 Worker，
 * 由 Worker 服务端校验后转投给作者 —— 因此 APK 内不再打包任何推送/GitHub Token。
 */
object Config {
    /**
     * 后端代理基础地址（Cloudflare Worker 部署后域名）。发布前请替换为实际部署地址。
     */
    const val workProxyBase = "https://orangeiptv.orangeway.workers.dev"

    /** 问题反馈代理：Worker 在服务端校验 hCaptcha token 后才代投反馈 */
    const val feedbackProxyUrl = "$workProxyBase/feedback"

    /** hCaptcha Site Key（公开，客户端校验用；Secret Key 只存服务端 Worker 环境变量 HCAPTCHA_SECRET） */
    const val hcaptchaSiteKey = "f13c506e-c8fe-423e-872a-442a935ad2a2"
}