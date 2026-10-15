package com.tandemmoto.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/**
 * The connect wave's progress, 0 → 1 over about 900 ms, played once each time [connected] turns
 * true. It doesn't play when the screen opens already connected, or when system animations are
 * off. 1 means no wave is showing. Draw it with [connectWave].
 */
@Composable
fun rememberConnectWave(connected: Boolean): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(1f) }
    val context = LocalContext.current
    val wasConnected = remember { booleanArrayOf(connected) }
    LaunchedEffect(connected) {
        val becameConnected = connected && !wasConnected[0]
        wasConnected[0] = connected
        if (becameConnected && animationsOn(context)) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(WAVE_MS, easing = LinearOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    return progress
}

/**
 * Draws one ring in [color] over the content, centred horizontally at [centerY] from the top,
 * growing with [progress] until it reaches the farthest corner and fading from 0.35 to 0. It only
 * draws, so touches pass through to the content.
 */
fun Modifier.connectWave(progress: () -> Float, color: Color, centerY: Dp): Modifier =
    drawWithContent {
        drawContent()
        val p = progress()
        if (p >= 1f) return@drawWithContent
        val center = Offset(size.width / 2, centerY.toPx())
        val farthest = hypot(center.x, max(center.y, size.height - center.y))
        drawCircle(
            color = color.copy(alpha = WAVE_ALPHA * (1f - p)),
            radius = farthest * p,
            center = center,
            style = Stroke(width = WaveWidth.toPx())
        )
    }

private fun animationsOn(context: Context): Boolean = Settings.Global.getFloat(
    context.contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f
) != 0f

private const val WAVE_MS = 900
private const val WAVE_ALPHA = 0.35f
private val WaveWidth = 24.dp
