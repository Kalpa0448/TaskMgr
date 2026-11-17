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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
    content: @Composable ColumnScope.() -> Unit
) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .drawBehind {
                if (blur != null) {
                    drawImage(
                        image = blur,
                        topLeft = Offset(-pos.x.toFloat(), -pos.y.toFloat())
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
    content: @Composable RowScope.() -> Unit
) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .drawBehind {
                if (blur != null) {
                    drawImage(
                        image = blur,
                        topLeft = Offset(-pos.x.toFloat(), -pos.y.toFloat())
                    )
                }
                drawRect(tint)
            },
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
