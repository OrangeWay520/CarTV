package com.orangeway.iptv.ui.screen

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.orangeway.iptv.R
import com.orangeway.iptv.data.model.Channel
import com.orangeway.iptv.ui.component.ChannelCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlaylistSettings: () -> Unit,
    onNavigateToPlayer: (Channel) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 频道列表滚动状态（右侧滑动条 + 返回顶部按钮）
    val listState = rememberLazyListState()

    // 搜索状态
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 安全加载定制 logo：优先用 drawable/ic_logo.png（用户新提供的 logo），
    // 解码失败则回退到 mipmap 的橙子 logo（About 页面已验证可用）
    val logoBitmap = remember(context) {
        loadLogoBitmap(context, R.drawable.ic_logo)
            ?: loadLogoBitmap(context, R.mipmap.ic_launcher_foreground)
    }

    // 安全兜底：首次进入时确保加载
    LaunchedEffect(Unit) {
        if (uiState.channels.isEmpty() && !uiState.isLoading && uiState.errorMessage == null) {
            viewModel.loadChannels()
        }
    }

    // 频道加载完成后，预热央视/卫视等高频分类的台标到缓存，
    // 避免用户切换分类时每次都能看到"台标在加载"
    val loadedChannels = uiState.channels
    LaunchedEffect(loadedChannels.size, uiState.isLoading) {
        if (!uiState.isLoading && loadedChannels.isNotEmpty()) {
            loadedChannels
                .filter { it.category == "央视频道" || it.category == "卫视频道" }
                .mapNotNull { it.logo.ifBlank { null } }
                .distinct()
                .take(120)
                .forEach { logoUrl ->
                    context.imageLoader
                        .enqueue(ImageRequest.Builder(context)
                            .data(logoUrl)
                            .build())
                }
        }
    }

    // 切换分类或搜索内容变化时，列表回到顶部
    LaunchedEffect(uiState.selectedCategory, isSearching, searchQuery) {
        listState.scrollToItem(0)
    }

    // 双击返回键退出；搜索状态下优先关闭搜索
    var backPressedTime by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - backPressedTime < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                backPressedTime = now
                Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        // 搜索模式：显示频道搜索输入框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索频道名称...") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap,
                                    contentDescription = "应用图标",
                                    modifier = Modifier
                                        .size(54.dp)
                                        .scale(1.3f)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("橙子网络电视", fontWeight = FontWeight.Bold)
                                if (uiState.hasRegionFilter) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = uiState.regionFilter,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索频道")
                        }
                        IconButton(onClick = { viewModel.loadChannels() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "正在加载频道列表...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "无法加载播放列表",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "未知错误",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "当前播放列表: ${uiState.apiUrl}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadChannels() }) {
                            Text("重试")
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onNavigateToPlaylistSettings) {
                            Text("设置播放列表地址")
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.resetToDefaultUrl() }) {
                            Text("恢复默认播放列表")
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 搜索模式下隐藏分类导航栏
                        if (!isSearching) {
                            CategoryBar(
                                categories = uiState.categories,
                                selectedCategory = uiState.selectedCategory ?: uiState.categories.firstOrNull() ?: "",
                                onCategorySelected = { viewModel.selectCategory(it) }
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // 频道列表（支持分类筛选和搜索过滤）
                            val filteredChannels = when {
                                isSearching -> {
                                    if (searchQuery.isBlank()) {
                                        uiState.channels
                                    } else {
                                        uiState.channels.filter {
                                            it.name.contains(searchQuery.trim(), ignoreCase = true)
                                        }
                                    }
                                }
                                // "收藏频道"是虚拟分类：ViewModel 已返回收藏列表，无需按真实分类过滤
                                uiState.selectedCategory != null &&
                                    uiState.selectedCategory != HomeViewModel.FAVORITE_CATEGORY -> {
                                    uiState.channels.filter { it.category == uiState.selectedCategory }
                                }
                                else -> uiState.channels
                            }

                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // key 用频道名保证稳定：分类切换/滚动复用 item 时，
                                // rememberSaveable 的台标加载状态按频道正确保存恢复，不会串位
                                items(filteredChannels, key = { it.name }) { channel ->
                                    ChannelCard(
                                        channel = channel,
                                        isFavorite = channel.name in uiState.favoriteChannels,
                                        onClick = { onNavigateToPlayer(channel) }
                                    )
                                }
                            }

                            // 右侧细滑动条（可拖拽快速定位，不遮挡内容）
                            SimpleVerticalScrollbar(
                                listState = listState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                            )

                            // 搜索无结果提示
                            if (isSearching && searchQuery.isNotBlank() && filteredChannels.isEmpty()) {
                                Text(
                                    text = "未找到相关频道",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            // 返回顶部按钮（现代风格：下滑后淡入，圆形半透明，点击回到顶部）
                            BackToTopButton(
                                listState = listState,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 24.dp, bottom = 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 安全加载 logo 位图：
 * 1. 先用 inJustDecodeBounds 读取图片尺寸，不占内存
 * 2. 按目标尺寸计算采样率，压缩解码，避免大图 OOM 崩溃
 * 3. 解码失败返回 null（调用方回退到下一个可用图标）
 */
private fun loadLogoBitmap(context: android.content.Context, resId: Int): ImageBitmap? {
    return try {
        val res = context.resources
        // 第一步：只读取边界信息
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 目标显示尺寸（54.dp，按 4x 冗余采样即可）
        var inSampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / (inSampleSize * 2) >= 432) {
            inSampleSize *= 2
        }

        // 第二步：按采样率解码
        val options = BitmapFactory.Options().apply { }
        BitmapFactory.decodeResource(res, resId, options)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * 滑块几何信息（比例值，供绘制与手势共用）
 */
private data class ThumbRatio(val topRatio: Float, val sizeRatio: Float, val valid: Boolean)

/**
 * 轻量级垂直滑块（foundation 1.9 已移除内置 VerticalScrollbar，此处自定义实现）：
 * - 视觉为 6dp 细条半透明样式，不遮挡频道内容
 * - 外层 20dp 触摸热区，点击/拖动更易操作
 * - 拇指高度反映可视比例，位置反映浏览进度
 * - 仅当手指按在滑块上时才接管拖动（消费事件，滑块跟手）；
 * - 未按在滑块上时完全不消费任何事件，列表滚动畅通无阻；
 * - 轨道区域原地点击（位移小于触摸阈值）→ 平滑跳转到对应区域。
 * 全程只在接管滑块拖动时消费事件，从根本上避免热区拦截导致列表滑动卡顿。
 */
@Composable
private fun SimpleVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { 36.dp.toPx() }
    val thumbColor = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    // 滑块几何（用 derivedStateOf 聚合，避免直接订阅 layoutInfo 每帧重绘）
    val thumbRatio = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible: List<LazyListItemInfo> = info.visibleItemsInfo
            val total = info.totalItemsCount
            if (total > 0 && visible.isNotEmpty()) {
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
                val avgItem = visible.sumOf { it.size.toLong() }.toFloat() / visible.size
                val content = total * avgItem
                val scrollable = (content - viewport).coerceAtLeast(1f)
                // 用 firstVisibleItemIndex + firstVisibleItemScrollOffset 计算滚动偏移：
                // 两者都是单调连续递增的值（scrollOffset 为可见偏移量，永不为负），
                // 避免直接用 item.offset（部分滚出视口时为负）导致滑块位置来回跳动
                val scrollOffset =
                    (listState.firstVisibleItemIndex * avgItem + listState.firstVisibleItemScrollOffset).coerceAtLeast(0f)
                val sizeRatio = (viewport / content).coerceIn(0.05f, 1f)
                val topRatio = if (scrollable > 0f) (scrollOffset / scrollable).coerceIn(0f, 1f) else 0f
                ThumbRatio(topRatio, sizeRatio, true)
            } else {
                ThumbRatio(0f, 0f, false)
            }
        }
    }

    Box(
        modifier = modifier
            .width(20.dp)
            .pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val ratio = thumbRatio.value
                    val viewportPx = size.height.toFloat().coerceAtLeast(1f)
                    val thumbHeightPx = ratio.sizeRatio * viewportPx
                    val thumbTopPx = ratio.topRatio * (viewportPx - thumbHeightPx)
                    val onThumb = ratio.valid && down.position.y in thumbTopPx..(thumbTopPx + thumbHeightPx)

                    if (!onThumb) {
                        // 未按在滑块上：完全不消费任何事件，列表滚动畅通；
                        // 仅当最终是"原地点击"（累计位移小于触摸阈值）时执行轨道跳转，
                        // 一旦列表开始滚动或手指发生位移即放弃，不干扰任何手势
                        val startPos = down.position
                        var isTap = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.isConsumed) { isTap = false; break }
                            val delta = change.position - startPos
                            if (delta.getDistance() > viewConfiguration.touchSlop) { isTap = false; break }
                        }
                        if (isTap) {
                            val total = listState.layoutInfo.totalItemsCount
                            if (ratio.valid && total > 0) {
                                val fraction = (down.position.y / viewportPx).coerceIn(0f, 1f)
                                val target = (fraction * total).toInt().coerceIn(0, total - 1)
                                scope.launch { listState.animateScrollToItem(target) }
                            }
                        }
                        return@awaitEachGesture
                    }

                    // 按在滑块上：消费按下事件（阻止列表滚动），进入滑块拖动
                    down.consume()
                    var lastFraction = (down.position.y / viewportPx).coerceIn(0f, 1f)
                    var dragJob: Job? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val fraction = (change.position.y / viewportPx).coerceIn(0f, 1f)
                        if (fraction != lastFraction) {
                            lastFraction = fraction
                            change.consume()
                            val total = listState.layoutInfo.totalItemsCount
                            if (total > 0) {
                                val target = (fraction * total).toInt().coerceIn(0, total - 1)
                                // 取消上一次滚动任务，避免协程叠加导致滑块跳动
                                dragJob?.cancel()
                                dragJob = scope.launch { listState.scrollToItem(target) }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(6.dp)
        ) {
            val ratio = thumbRatio.value
            if (ratio.valid && size.height > 0f) {
                val thumbHeight = (ratio.sizeRatio * size.height).coerceIn(minThumbHeightPx, size.height)
                val maxTop = (size.height - thumbHeight).coerceAtLeast(0f)
                val thumbTop = ratio.topRatio * maxTop

                // 轨道（细圆角条）
                drawRoundRect(
                    color = thumbColor.copy(alpha = 0.15f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(size.width / 2f)
                )
                // 拇指（可拖动，更醒目）
                drawRoundRect(
                    color = thumbColor.copy(alpha = 0.75f),
                    topLeft = Offset(0f, thumbTop),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = CornerRadius(size.width / 2f)
                )
            }
        }
    }
}

/**
 * 返回顶部按钮（现代风格）：
 * - 向下滚动后淡入显示，回到顶部后自动淡出
 * - 圆形半透明背景 + 向上箭头，点击平滑回到列表顶部
 */
@Composable
private fun BackToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // 通过 derivedStateOf 订阅滚动位置，避免每次滚动都触发重组
    val showButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    AnimatedVisibility(
        visible = showButton,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f)
    ) {
        Surface(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 6.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "返回顶部",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryBar(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Button(
                onClick = { onCategorySelected(category) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}