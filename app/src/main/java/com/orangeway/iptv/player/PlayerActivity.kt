package com.orangeway.iptv.player

import com.orangeway.iptv.R
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.orangeway.iptv.data.model.EpgProgramme
import com.orangeway.iptv.data.repository.EpgRepository
import com.orangeway.iptv.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        private const val HIDE_DELAY = 5000L
    }

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    // 画面比例容器：通过控制其宽高尺寸强制 16:9 / 4:3 显示
    private var aspectRatioContainer: FrameLayout? = null
    private var currentUrlIndex = 0
    private var urls: List<String> = emptyList()
    private var channelName: String = ""

    // 收藏功能
    private var isFavorite = false
    private var favoriteBtn: ImageView? = null
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    // 播放卡顿监控：缓冲过久自动切换下一个源
    private var stallJob: Job? = null
    private var bufferingStartMs = 0L
    private var hasBeenReady = false

    // 顶部渐变条
    private var topBar: View? = null
    // 底部控制栏
    private var bottomBar: View? = null
    private var sourceButtonsContainer: LinearLayout? = null
    private var overlayVisible = false

    // 长宽比模式：-1=自适应(按源比例), 0=16:9, 1=4:3, 2=铺满
    private var currentAspectRatio = -1

    // 节目预告(EPG)
    private val epgRepository = EpgRepository()
    private var epgProgrammes: List<EpgProgramme> = emptyList()
    private var epgText: TextView? = null
    private var epgRefreshStarted = false

    private val hideRunnable = Runnable { hideOverlay() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 沉浸式全屏（隐藏状态栏/导航栏），兼容 API 23+（替代废弃的 systemUiVisibility）
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        channelName = intent.getStringExtra("channel_name") ?: "未知频道"
        val urlsJson = intent.getStringExtra("channel_urls") ?: ""
        val singleUrl = intent.getStringExtra("channel_url") ?: ""

        urls = parseUrls(urlsJson, singleUrl)
        currentUrlIndex = 0

        Log.d(TAG, "频道: $channelName, 播放源数量: ${urls.size}")

        // 画面比例容器：通过控制其宽高尺寸实现强制 16:9 / 4:3 显示
        aspectRatioContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = false
            // 视频内容填满容器（配合容器宽高比实现强制比例）
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setFocusable(false)
            isFocusableInTouchMode = false
        }
        aspectRatioContainer?.addView(playerView)

        val rootLayout = FrameLayout(this).apply {
            addView(aspectRatioContainer)
            addView(createTopBar())       // 顶部渐变条
            addView(createBottomBar())    // 底部控制栏
        }
        setContentView(rootLayout)

        // 应用默认画面比例（自适应）
        applyAspectRatio(currentAspectRatio)

        // 播放当前频道（默认从源 1 开始，失败/卡顿自动逐个切换）
        startPlayback(currentUrlIndex)

        // 加载节目预告
        loadEpg()

        // 加载收藏状态
        lifecycleScope.launch {
            isFavorite = settingsRepository.isChannelFavorite(channelName)
            favoriteBtn?.setImageResource(
                if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
    }

    /** 切换当前频道的收藏状态 */
    private fun toggleFavorite() {
        isFavorite = !isFavorite
        favoriteBtn?.setImageResource(
            if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        Toast.makeText(
            this,
            if (isFavorite) "已收藏：$channelName" else "已取消收藏：$channelName",
            Toast.LENGTH_SHORT
        ).show()
        lifecycleScope.launch {
            settingsRepository.saveFavoriteChannel(channelName, isFavorite)
        }
    }

    // ========== 顶部渐变条 ==========

    private fun createTopBar(): View {
        // 渐变背景
        val gradientBg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT)
        )

        // 返回键 - 使用现代矢量图标
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_back_arrow)
            setPadding(20, 0, 20, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { finish() }
        }
        val backLayout = LinearLayout.LayoutParams(
            48.toPx(), 48.toPx()
        ).apply {
            gravity = Gravity.CENTER
        }

        // 频道名
        val titleText = TextView(this).apply {
            text = channelName
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
        }

        // 当前节目预告
        epgText = TextView(this).apply {
            text = ""
            setTextColor(Color.argb(220, 255, 255, 255))
            textSize = 13f
            maxLines = 1
        }

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleText)
            addView(epgText)
        }

        // 收藏键（星星，右上角）
        favoriteBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_star_outline)
            setPadding(20, 0, 20, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleFavorite() }
        }
        val favoriteLayout = LinearLayout.LayoutParams(
            48.toPx(), 48.toPx()
        ).apply {
            gravity = Gravity.CENTER
            marginStart = 8.toPx()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 24, 12, 0)
            addView(backBtn, backLayout)
            addView(titleColumn, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ))
            addView(favoriteBtn, favoriteLayout)
        }

        topBar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            background = gradientBg
            setPadding(0, 0, 0, 64)
            addView(topRow)
            visibility = View.GONE
        }
        return topBar!!
    }

    /** dip 转 px 辅助 */
    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    // ========== 底部控制栏 ==========

    private fun createBottomBar(): View {
        // --- 选择播放源 ---
        val sourceLabel = TextView(this).apply {
            text = "选择播放源"
            setTextColor("#FF9800".toColorInt())
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }

        sourceButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val sourceScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(sourceButtonsContainer)
        }

        // --- 画面比例 ---
        val ratioLabel = TextView(this).apply {
            text = "画面比例"
            setTextColor("#FF9800".toColorInt())
            textSize = 13f
            setPadding(0, 24, 0, 12)
        }

        val ratioContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val ratioOptions = listOf(
            Triple("自适应", -1, AspectRatioFrameLayout.RESIZE_MODE_FIT),
            Triple("16:9", 0, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
            Triple("4:3", 1, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
            Triple("铺满", 2, AspectRatioFrameLayout.RESIZE_MODE_FILL)
        )

        ratioOptions.forEach { (label, mode, _) ->
            val btn = TextView(this).apply {
                text = label
                textSize = 14f
                setPadding(40, 24, 40, 24)
                setTextColor(Color.WHITE)
                background = if (mode == currentAspectRatio) {
                    GradientDrawable().apply {
                        setColor("#FF9800".toColorInt()); cornerRadius = 24f
                    }
                } else {
                    GradientDrawable().apply {
                        setColor(Color.argb(60, 255, 255, 255)); cornerRadius = 24f
                    }
                }
                setOnClickListener {
                    currentAspectRatio = mode
                    applyAspectRatio(mode)
                    updateRatioButtons(ratioContainer, ratioOptions)
                    bottomBar?.removeCallbacks(hideRunnable)
                    bottomBar?.postDelayed(hideRunnable, 1500)
                }
            }
            ratioContainer.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 16 })
        }

        val ratioScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(ratioContainer)
        }

        // 渐变背景
        val gradientBg = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT)
        )

        // --- 节目预告按钮（右下角，与"画面比例"按钮同排同高） ---
        val epgBtn = TextView(this).apply {
            text = "节目预告"
            textSize = 14f
            setPadding(28, 24, 28, 24)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 255, 255, 255)); cornerRadius = 24f
            }
            setOnClickListener { showEpgDialog() }
        }

        // 画面比例按钮行 + 节目预告按钮同排，按钮固定在右侧
        val ratioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ratioScrollView, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ))
            addView(epgBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 12 })
        }

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            background = gradientBg
            visibility = View.GONE
            addView(sourceLabel)
            addView(sourceScrollView)
            addView(ratioLabel)
            addView(ratioRow)
        }
        return bottomBar!!
    }

    // ========== 覆盖层显隐 ==========

    private fun showOverlay() {
        topBar?.visibility = View.VISIBLE
        bottomBar?.visibility = View.VISIBLE
        overlayVisible = true
        updateSourceButtons()
        topBar?.removeCallbacks(hideRunnable)
        topBar?.postDelayed(hideRunnable, HIDE_DELAY)
    }

    private fun hideOverlay() {
        topBar?.visibility = View.GONE
        bottomBar?.visibility = View.GONE
        overlayVisible = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (overlayVisible) {
                hideOverlay()
            } else {
                showOverlay()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    // ========== 长宽比 ==========

    /**
     * 应用画面比例
     * - 自适应：容器全屏，视频按源画面比例完整显示（不裁切，黑边补齐）
     * - 16:9：宽填满屏幕，高按 16:9 计算（画面居中，超高时全屏）
     * - 4:3：高填满屏幕，宽按 4:3 计算（画面居中，两侧留黑边）
     * - 铺满：拉伸填满整个屏幕
     */
    private fun applyAspectRatio(mode: Int) {
        val container = aspectRatioContainer ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val lp = container.layoutParams as? FrameLayout.LayoutParams ?: return
        when (mode) {
            -1 -> { // 自适应：自动识别源画面比例，完整显示不裁切
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = FrameLayout.LayoutParams.MATCH_PARENT
                lp.gravity = Gravity.CENTER
                playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            0 -> { // 16:9
                val h = (screenW * 9f / 16f).toInt().coerceAtMost(screenH)
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = if (h >= screenH) FrameLayout.LayoutParams.MATCH_PARENT else h
                lp.gravity = Gravity.CENTER
                playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            1 -> { // 4:3
                val w = (screenH * 4f / 3f).toInt().coerceAtMost(screenW)
                lp.height = FrameLayout.LayoutParams.MATCH_PARENT
                lp.width = w
                lp.gravity = Gravity.CENTER
                playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            2 -> { // 铺满：拉伸填满
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = FrameLayout.LayoutParams.MATCH_PARENT
                playerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        }
        container.layoutParams = lp
    }

    private fun updateRatioButtons(container: LinearLayout, options: List<Triple<String, Int, Int>>) {
        for (i in 0 until container.childCount) {
            val btn = container.getChildAt(i) as? TextView ?: continue
            val mode = options.getOrNull(i)?.second ?: continue
            btn.background = if (mode == currentAspectRatio) {
                GradientDrawable().apply { setColor("#FF9800".toColorInt()); cornerRadius = 24f }
            } else {
                GradientDrawable().apply { setColor(Color.argb(60, 255, 255, 255)); cornerRadius = 24f }
            }
        }
    }

    // ========== 播放源按钮 ==========

    @Suppress("SetTextI18n")
    private fun updateSourceButtons() {
        sourceButtonsContainer?.removeAllViews()
        urls.forEachIndexed { index, _ ->
            val btn = TextView(this).apply {
                text = "源 ${index + 1}"
                textSize = 14f
                setPadding(40, 24, 40, 24)
                setTextColor(Color.WHITE)
                background = if (index == currentUrlIndex) {
                    GradientDrawable().apply { setColor("#FF9800".toColorInt()); cornerRadius = 24f }
                } else {
                    GradientDrawable().apply { setColor(Color.argb(60, 255, 255, 255)); cornerRadius = 24f }
                }
                setOnClickListener {
                    if (index != currentUrlIndex) startPlayback(index)
                    bottomBar?.removeCallbacks(hideRunnable)
                    bottomBar?.postDelayed(hideRunnable, 1500)
                }
            }
            sourceButtonsContainer?.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 16 })
        }
    }

    // ========== 节目预告(EPG) ==========

    /**
     * 加载当前频道的节目预告
     * 数据源来自 M3U 的 x-tvg-url，匹配频道 tvg-id 或频道名
     */
    private fun loadEpg() {
        val epgUrl = intent.getStringExtra("epg_url") ?: ""
        val tvgId = intent.getStringExtra("tvg_id") ?: ""
        if (epgUrl.isBlank()) return

        lifecycleScope.launch {
            val programmes = epgRepository.getProgrammes(epgUrl, tvgId, channelName)
            if (programmes.isEmpty()) return@launch
            epgProgrammes = programmes
            runOnUiThread { updateEpgText() }

            // 每分钟检查一次节目是否切换
            if (!epgRefreshStarted) {
                epgRefreshStarted = true
                lifecycleScope.launch {
                    while (isActive) {
                        delay(60_000.milliseconds)
                        runOnUiThread { updateEpgText() }
                    }
                }
            }
        }
    }

    /** 更新顶部栏的节目预告文本 */
    private fun updateEpgText() {
        val now = System.currentTimeMillis()
        val idx = epgProgrammes.indexOfFirst { it.isAtTime(now) }
        val current = if (idx >= 0) epgProgrammes[idx] else null
        val next = if (idx >= 0 && idx + 1 < epgProgrammes.size) epgProgrammes[idx + 1] else null

        val text = when {
            current != null -> {
                val base = "正在播放：${current.title}  ${current.startTimeText}-${current.endTimeText}"
                if (next != null) "$base   即将播放：${next.title} ${next.startTimeText}" else base
            }
            // 当前无节目，显示下一个即将开始的节目
            else -> epgProgrammes.firstOrNull { it.startMillis >= now }?.let {
                "今日节目：${it.title} ${it.startTimeText}-${it.endTimeText}"
            } ?: ""
        }
        epgText?.text = text
    }

    /**
     * 弹出节目预告窗口（半透明）
     * 显示当前频道从此刻开始的后续节目列表
     */
    @Suppress("SetTextI18n", "UseKtx")
    private fun showEpgDialog() {
        if (epgProgrammes.isEmpty()) {
            Toast.makeText(this, "暂无节目预告数据，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val now = System.currentTimeMillis()
        // 从现在开始的节目（含正在播放的），最多 12 条
        val upcoming = epgProgrammes.filter { it.endMillis > now }.take(12)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.3f)  // 轻微暗化，保留半透明感
        }

        // 节目列表
        val listColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        upcoming.forEach { p ->
            val isCurrent = p.isAtTime(now)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10.toPx(), 0, 10.toPx())
            }
            row.addView(TextView(this@PlayerActivity).apply {
                text = "${p.startTimeText}-${p.endTimeText}"
                setTextColor(if (isCurrent) "#FF9800".toColorInt() else "#CCCCCC".toColorInt())
                textSize = 14f
                width = 130.toPx()
            })
            row.addView(TextView(this@PlayerActivity).apply {
                text = p.title
                setTextColor(if (isCurrent) "#FF9800".toColorInt() else Color.WHITE)
                textSize = 15f
                maxLines = 1
            })
            if (isCurrent) {
                row.addView(TextView(this@PlayerActivity).apply {
                    text = "（正在播放）"
                    setTextColor("#FF9800".toColorInt())
                    textSize = 12f
                    setPadding(8.toPx(), 0, 0, 0)
                })
            }
            listColumn.addView(row)
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(listColumn)
        }

        // 窗口内容（深色半透明圆角背景）
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 18, 18, 18))
                cornerRadius = 16.toPx().toFloat()
            }
            setPadding(28.toPx(), 28.toPx(), 28.toPx(), 20.toPx())
            addView(TextView(this@PlayerActivity).apply {
                text = "$channelName · 节目预告"
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 10.toPx())
            })
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = 8.toPx() })
            addView(TextView(this@PlayerActivity).apply {
                text = "点击窗口外部关闭"
                setTextColor("#888888".toColorInt())
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 16.toPx(), 0, 0)
            })
        }

        dialog.setContentView(
            content,
            FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.82f).toInt(),
                (resources.displayMetrics.heightPixels * 0.62f).toInt(),
                Gravity.CENTER
            )
        )
        dialog.show()
    }

    // ========== 播放控制 ==========

    /**
     * 播放卡顿监控：缓冲状态持续过久视为卡顿，自动切换下一个源。
     * - 已出过画面(READY 过)：缓冲 > 5 秒即切换
     * - 首次加载(从未 READY)：缓冲 > 6 秒即切换
     */
    private fun startStallMonitor() {
        stallJob?.cancel()
        stallJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (!isActive) break
                val start = bufferingStartMs
                if (start > 0 && urls.size > 1) {
                    val threshold = if (hasBeenReady) 5000L else 6000L
                    if (SystemClock.elapsedRealtime() - start > threshold) {
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "播放卡顿，自动切换源…", Toast.LENGTH_SHORT).show()
                            tryNextUrl()
                        }
                        break
                    }
                }
            }
        }
    }

    private fun startPlayback(index: Int) {
        if (index >= urls.size) {
            runOnUiThread { Toast.makeText(this, "所有播放地址均无法播放", Toast.LENGTH_LONG).show() }
            return
        }
        currentUrlIndex = index
        // 重置卡顿监控状态
        bufferingStartMs = 0L
        hasBeenReady = false
        val url = urls[index]
        Log.d(TAG, "开始播放源 ${index + 1}/${urls.size}: $url")
        player?.release()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("OrangeIPTVCar/1.0")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 渲染器：默认硬解优先（主流格式流畅省电）；
        // 音频遇到硬解不支持的编码（如 AC3/EAC3/DTS/TrueHD）时，
        // 自动回退 FFmpeg 扩展软解（已内置全音频解码器），不浪费播放源。
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().build())
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exoPlayer ->
                playerView?.player = exoPlayer
                val mediaItem = MediaItem.Builder()
                    .setUri(url.toUri())
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(channelName).build())
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.volume = 1.0f
                exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true
                )

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                hasBeenReady = true
                                bufferingStartMs = 0L
                                Log.d(TAG, "播放就绪，源 ${currentUrlIndex + 1}/${urls.size}")
                                // 音轨诊断
                                try {
                                    val tracks = exoPlayer.currentTracks
                                    var audioTrackCount = 0
                                    var audioSupportedCount = 0
                                    tracks.groups.forEach { group ->
                                        val type = group.type
                                        for (i in 0 until group.length) {
                                            val supported = group.isTrackSupported(i)
                                            if (type == C.TRACK_TYPE_AUDIO) {
                                                audioTrackCount++
                                                if (supported) audioSupportedCount++
                                            }
                                        }
                                    }
                                    if (audioTrackCount == 0) {
                                        runOnUiThread {
                                            Toast.makeText(this@PlayerActivity, "该源无音频，请尝试切换", Toast.LENGTH_LONG).show()
                                        }
                                    } else if (audioSupportedCount == 0) {
                                        Log.w(TAG, "音轨均不被支持，${audioTrackCount}条，自动尝试下一个源")
                                        // 有音轨但设备不支持解码，自动尝试下一个源
                                        runOnUiThread {
                                            Toast.makeText(this@PlayerActivity, "音频编码不支持，自动切换源...", Toast.LENGTH_LONG).show()
                                        }
                                        exoPlayer.stop()
                                        tryNextUrl()
                                    }
                                } catch (_: Throwable) {}
                                if (overlayVisible) updateSourceButtons()
                            }
                            Player.STATE_BUFFERING -> {
                                if (bufferingStartMs == 0L) {
                                    bufferingStartMs = SystemClock.elapsedRealtime()
                                }
                            }
                            else -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "播放错误: ${error.message}", error)
                        runOnUiThread {
                            Toast.makeText(this@PlayerActivity, "源 ${currentUrlIndex + 1} 不可用，尝试下一个...", Toast.LENGTH_SHORT).show()
                        }
                        tryNextUrl()
                    }
                })
            }
        if (overlayVisible) updateSourceButtons()
        // 启动卡顿监控（缓冲过久自动切换下一个源）
        startStallMonitor()
    }

    /** 按顺序尝试下一个播放源（原始逐个切换方式） */
    private fun tryNextUrl() {
        val nextIndex = currentUrlIndex + 1
        if (nextIndex < urls.size) startPlayback(nextIndex)
        else runOnUiThread { Toast.makeText(this, "所有播放地址均不可用", Toast.LENGTH_LONG).show() }
    }

    private fun parseUrls(urlsJson: String, singleUrl: String): List<String> {
        if (urlsJson.isNotBlank()) {
            try {
                val trimmed = urlsJson.trim()
                if (trimmed.startsWith("[")) {
                    val json = trimmed.substring(1, trimmed.length - 1)
                    return json.split(",").map { it.trim().trim('"').trim('\'') }.filter { it.isNotBlank() }
                }
            } catch (_: Exception) {}
        }
        return if (singleUrl.isNotBlank()) listOf(singleUrl) else emptyList()
    }

    override fun onPause() { super.onPause(); player?.pause() }
    override fun onResume() { super.onResume(); player?.playWhenReady = true }
    override fun onDestroy() {
        super.onDestroy()
        stallJob?.cancel()
        topBar?.removeCallbacks(hideRunnable)
        bottomBar?.removeCallbacks(hideRunnable)
        player?.release()
        player = null
    }
}