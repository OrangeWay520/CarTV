// ============================================================
// OrangeIPTV 反馈代理 Worker（方案A：把 Token 藏到服务端，App 不存密钥）
// ------------------------------------------------------------
// 端点：
//   POST /feedback   反馈 → GitHub Issues + WxPusher（hCaptcha 人机验证，强制）
// 用途：反馈的敏感 Token（GitHub / WxPusher / hCaptcha Secret）都只存放在
//       Worker 环境变量里，App 端只做 hCaptcha 人机验证后携带 token 提交，
//       由本 Worker 服务端再次校验通过后才代投，杜绝 Token 泄露进 APK。
//
// 部署（免费）：
//   1. 打开 https://dash.cloudflare.com → Workers & Pages → 创建 Worker
//   2. 粘贴本文件全部内容 → 部署
//   3. 在 Worker 的「设置 → 变量和机密」中添加以下机密（Secrets）：
//        GITHUB_TOKEN       = GitHub 个人访问令牌（需 Issues 写权限）
//        WXPUSHER_APPTOKEN  = WxPusher 应用 token
//        WXPUSHER_UID       = 你的 WxPusher UID
//        HCAPTCHA_SECRET    = hCaptcha 的 Secret Key（服务端校验 App 端 token，未配置时反馈直接返回 403）
//   4. 把部署后的地址填到 Android 端 Config.kt 的 feedbackProxyUrl
//      （形如 https://你的子域.workers.dev/feedback）
// ============================================================

const GITHUB_MIRROR = "https://gh.llkk.cc/https://api.github.com/repos/OrangeWay520/OrangeIPTV/issues";
const GITHUB_DIRECT = "https://api.github.com/repos/OrangeWay520/OrangeIPTV/issues";
const WXPUSHER_API = "https://wxpusher.zjiecode.com/api/send/message";
// hCaptcha 服务端校验地址
const HCAPTCHA_VERIFY = "https://api.hcaptcha.com/siteverify";

/** GitHub Issues 通道：镜像优先，官方直连兜底 */
async function submitGithub(env, title, body) {
  const token = env.GITHUB_TOKEN;
  if (!token) return { ok: false, detail: "GitHub 未配置" };

  const payload = JSON.stringify({ title, body });
  const headers = {
    "User-Agent": "OrangeIPTV/1.0",
    Authorization: `token ${token}`,
    "Content-Type": "application/json",
  };

  let lastError = "所有地址均不可用";
  for (const url of [GITHUB_MIRROR, GITHUB_DIRECT]) {
    try {
      const resp = await fetch(url, { method: "POST", headers, body: payload });
      const text = await resp.text();
      if (resp.ok) {
        try {
          const issue = JSON.parse(text);
          return { ok: true, detail: issue.html_url || issue.url || "已提交到 GitHub" };
        } catch {
          return { ok: true, detail: "已提交到 GitHub" };
        }
      }
      lastError = `HTTP ${resp.status}`;
    } catch (e) {
      lastError = e.message || "网络错误";
    }
  }
  return { ok: false, detail: lastError };
}

/** WxPusher 通道（code=1000 成功） */
async function submitWxpusher(env, title, body) {
  const appToken = env.WXPUSHER_APPTOKEN;
  const uid = env.WXPUSHER_UID;
  if (!appToken || !uid) return { ok: false, detail: "WxPusher 未配置" };

  const payload = JSON.stringify({
    appToken,
    content: body,
    summary: `[OrangeIPTV] ${title.slice(0, 12)}`,
    contentType: 1,
    uids: [uid],
  });

  try {
    const resp = await fetch(WXPUSHER_API, {
      method: "POST",
      headers: { "User-Agent": "OrangeIPTV/1.0", "Content-Type": "application/json" },
      body: payload,
    });
    const text = await resp.text();
    if (resp.ok) {
      let data = {};
      try {
        data = JSON.parse(text);
      } catch { /* 非 JSON 按失败处理 */ }
      if (data.code === 1000) return { ok: true, detail: "已提交到 WxPusher" };
      return { ok: false, detail: data.msg || `code=${data.code}` };
    }
    return { ok: false, detail: `HTTP ${resp.status}` };
  } catch (e) {
    return { ok: false, detail: e.message || "网络错误" };
  }
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 用 Secret Key 调用 hCaptcha 官方接口校验 token。返回 true 表示验证通过。 */
async function verifyHCaptcha(env, token) {
  try {
    const resp = await fetch(HCAPTCHA_VERIFY, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        secret: env.HCAPTCHA_SECRET,
        response: token,
        remoteip: "",
      }),
    });
    const data = await resp.json();
    return data.success === true;
  } catch {
    return false;
  }
}

/** Worker 入口：路由分发 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 仅接受 POST /feedback
    if (url.pathname === "/feedback" && request.method === "POST") {
      let payload;
      try {
        payload = await request.json();
      } catch {
        return json({ error: "invalid json" }, 400);
      }

      const title = String(payload.title || "").slice(0, 100);
      const body = String(payload.body || "");
      if (!title || !body) {
        return json({ error: "title/body required" }, 400);
      }

      // 人机验证（强制）：必须携带有效的 hCaptcha token 才放行。
      // 未配置 HCAPTCHA_SECRET 时按项目约束直接返回 403，绝不降级提交。
      // Android 端提交的字段名是 hcaptcha_token（与 FeedbackManager.kt 保持一致）
      const token = String(payload.hcaptcha_token || "");
      if (!env.HCAPTCHA_SECRET) {
        return json({ error: "captcha not configured" }, 403);
      }
      if (!token) return json({ error: "missing captcha token" }, 403);
      const ok = await verifyHCaptcha(env, token);
      if (!ok) return json({ error: "captcha verification failed" }, 403);

      // 先发微信推送（快），再建 GitHub Issue（留档）；任一成功即算提交成功
      const wp = await submitWxpusher(env, title, body);
      const gh = await submitGithub(env, title, body);
      if (wp.ok || gh.ok) {
        return json({
          ok: true,
          wxpusher: wp,
          github: gh,
          detail: [wp.ok && wp.detail, gh.ok && gh.detail].filter(Boolean).join(" / "),
        });
      }
      return json({ ok: false, detail: gh.detail || wp.detail || "发送失败" }, 500);
    }

    return json({ error: "not found" }, 404);
  },
};