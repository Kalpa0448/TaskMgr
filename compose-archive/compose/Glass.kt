package io.github.kalpa0448.taskmgr.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 毛玻璃卡片。
 *
 * 卡片裁圆角后，在 drawBehind 里把**整屏模糊图**按「负的窗口坐标」画进去 ——
 * 于是卡片露出的正好是屏幕上该位置的模糊画面。
 * 用绘制而非 Image 组件，避免背景图参与布局把卡片撑大。
 *
 * Compose 滚动时会重新绘制，位置永远最新，不会错位或延迟。
 */
@Composable
fun GlassCard(
    blur: ImageBitmap?,
    tint: Color,
    corner: Dp = 20.dp,
    modifier: Modifier = Modifier,
    dstW: Int = 0,
    dstH: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .drawBehind {
                if (blur != null) {
                    // 显式指定像素尺寸，避免按 bitmap 密度缩放
                    val tw = if (dstW > 0) dstW else blur.width
                    val th = if (dstH > 0) dstH else blur.height
                    drawImage(
                        image = blur,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(blur.width, blur.height),
                        dstOffset = IntOffset(-pos.x, -pos.y),
                        dstSize = IntSize(tw, th),
                        filterQuality = FilterQuality.Low
                    )
                }
                drawRect(tint)
            },
        content = content
    )
}

/** 一行玻璃条（用于列表项、设置项） */
@Composable
fun GlassRow(
    blur: ImageBitmap?,
    tint: Color,
    corner: Dp = 14.dp,
    modifier: Modifier = Modifier,
    dstW: Int = 0,
    dstH: Int = 0,
    content: @Composable RowScope.() -> Unit
) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .drawBehind {
                if (blur != null) {
                    // 显式指定像素尺寸，避免按 bitmap 密度缩放
                    val tw = if (dstW > 0) dstW else blur.width
                    val th = if (dstH > 0) dstH else blur.height
                    drawImage(
                        image = blur,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(blur.width, blur.height),
                        dstOffset = IntOffset(-pos.x, -pos.y),
                        dstSize = IntSize(tw, th),
                        filterQuality = FilterQuality.Low
                    )
                }
                drawRect(tint)
            },
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
