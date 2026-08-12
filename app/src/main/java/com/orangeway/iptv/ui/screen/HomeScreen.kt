package com.orangeway.iptv.ui.screen

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeway.iptv.R
import com.orangeway.iptv.data.model.Channel
import com.orangeway.iptv.ui.component.ChannelCard

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

    // 双击返回键退出
    val backPressedTime = remember { mutableStateOf(0L) }
    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - backPressedTime.value < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedTime.value = now
            Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
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
                },
                actions = {
                    IconButton(onClick = { viewModel.loadChannels() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
                        // 分类导航栏
                        CategoryBar(
                            categories = uiState.categories,
                            selectedCategory = uiState.selectedCategory ?: uiState.categories.firstOrNull() ?: "",
                            onCategorySelected = { viewModel.selectCategory(it) }
                        )

                        // 频道列表
                        val filteredChannels = if (uiState.selectedCategory != null) {
                            uiState.channels.filter { it.category == uiState.selectedCategory }
                        } else {
                            uiState.channels
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredChannels) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    onClick = { onNavigateToPlayer(channel) }
                                )
                            }
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
        val options = BitmapFactory.Options().apply { inSampleSize = inSampleSize }
        BitmapFactory.decodeResource(res, resId, options)?.asImageBitmap()
    } catch (_: Exception) {
        null
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