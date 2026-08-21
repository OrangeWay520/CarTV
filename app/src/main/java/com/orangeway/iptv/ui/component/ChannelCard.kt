package com.orangeway.iptv.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import coil.request.ImageRequest
import com.orangeway.iptv.R
import com.orangeway.iptv.data.model.Channel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .height(90.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 台标
                ChannelLogo(
                    logoUrl = channel.logo,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(Modifier.width(12.dp))

                // 频道名 + 当前节目/分类信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (channel.currentProgram.isNotBlank()) {
                            stringResource(R.string.now_playing_fmt, channel.currentProgram)
                        } else {
                            channel.category.ifBlank { stringResource(R.string.live_channel) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (channel.currentProgram.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // 播放按钮
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 已收藏的频道：右上角显示星星角标（无背景无阴影）
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.favorited),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp),
                    tint = Color(0xFFFFC107)
                )
            }
        }
    }
}

/**
 * 频道台标组件：
 * - 有 logo URL 时用 Coil 异步加载
 * - 加载失败或无 logo URL 时，显示软件定制 logo 作为兜底
 *
 * 注意：painterResource 必须在 @Composable 上下文中直接调用，
 * 不能放在 remember {} 的 lambda 中（非 Composable 上下文）。
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun ChannelLogo(
    logoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    // 软件定制 logo 作为台标加载失败时的兜底
    // 使用 mipmap 资源（已验证在所有设备上可用）
    val fallbackPainter = painterResource(R.mipmap.ic_launcher_foreground)

    // 台标请求（复用同一实例，便于查询缓存）
    val request = remember(logoUrl) {
        ImageRequest.Builder(context)
            .data(logoUrl)
            .crossfade(true)
            .build()
    }
    // 该台标是否已在内存缓存中（曾加载成功过）：命中则重新回到屏幕时不再转圈
    val cachedInitially = remember(logoUrl) {
        try {
            request.memoryCacheKey?.let { imageLoader.memoryCache?.get(it) != null } ?: false
        } catch (_: Exception) {
            false
        }
    }
    // 兜底超时状态：用 rememberSaveable 持久化，
    // 滚动移出视口再回来时状态不重置、不会重新转圈，台标默认在后台继续加载，
    // 加载成功后由 Coil 的 crossfade 从橙子 logo 淡出替换成台标
    var loadingTimedOut by rememberSaveable(logoUrl) { mutableStateOf(cachedInitially) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = request,
                contentDescription = stringResource(R.string.logo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    LaunchedEffect(logoUrl) {
                        // 若磁盘缓存中已有该台标（曾加载成功过）：直接切到橙子logo兜底，不转圈；
                        // 否则转圈 5 秒后淡入橙子logo。内存缓存可能被淘汰，磁盘缓存才是可靠兜底
                        val fromDisk = withContext(Dispatchers.IO) {
                            try {
                                request.diskCacheKey?.let { key ->
                                    imageLoader.diskCache?.openSnapshot(key)?.use { true }
                                } ?: false
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (fromDisk) {
                            loadingTimedOut = true
                        } else {
                            delay(5_000.milliseconds)
                            loadingTimedOut = true
                        }
                    }
                    Crossfade(
                        targetState = loadingTimedOut,
                        animationSpec = tween(durationMillis = 600),
                        label = "台标加载兜底"
                    ) { timedOut ->
                        if (timedOut) {
                            Image(
                                painter = fallbackPainter,
                                contentDescription = stringResource(R.string.channel_icon),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(1.5f),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                },
                error = {
                    // 加载失败：显示定制 logo，放大 50% 补偿自适应图标透明边距
                    Image(
                        painter = fallbackPainter,
                        contentDescription = stringResource(R.string.channel_icon),
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.5f),
                        contentScale = ContentScale.Fit
                    )
                }
            )
        } else {
            // 无 logo URL：直接显示定制 logo，放大 50% 补偿自适应图标透明边距
            Image(
                painter = fallbackPainter,
                contentDescription = stringResource(R.string.channel_icon),
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.5f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
