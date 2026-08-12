package com.orangeway.iptv.ui.component

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.orangeway.iptv.R
import com.orangeway.iptv.data.model.Channel

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
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
                    channelName = channel.name,
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
                            "正在播放：${channel.currentProgram}"
                        } else {
                            channel.category.ifBlank { "直播频道" }
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
                    contentDescription = "播放",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 备用地址数量角标
            if (channel.allUrls.size > 1) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = "${channel.allUrls.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
@Composable
private fun ChannelLogo(
    logoUrl: String,
    channelName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 软件定制 logo 作为台标加载失败时的兜底
    // 使用 mipmap 资源（已验证在所有设备上可用）
    val fallbackPainter = painterResource(R.mipmap.ic_launcher_foreground)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl.isNotBlank()) {
            val request = remember(logoUrl) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = "台标",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    // 加载失败：显示定制 logo，放大 50% 补偿自适应图标透明边距
                    Image(
                        painter = fallbackPainter,
                        contentDescription = "频道图标",
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
                contentDescription = "频道图标",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.5f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
