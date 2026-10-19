package com.shilapi.xcertplay.openplay

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The CarPlay icon, from design/ui-library/brand/carplay.svg (Wikimedia Commons,
 * Apple_CarPlay_Logo.svg; viewBox 12.59 12.621 145.078 145.078). CarPlay and its icon belong to
 * Apple Inc.
 */
private const val SQUARE_D =
    "M157.668 125.402c0 17.838-14.459 32.297-32.296 32.297H44.886c-17.837 0-32.296-14.459-32.296-32.297" +
        "V44.917c0-17.837 14.459-32.296 32.296-32.296h80.486c17.837 0 32.296 14.459 32.296 32.296v80.485z"

/** The white "C" ring and play triangle, filled. */
private const val GLYPH_D =
    "M85.225 129.963c-24.652 0-44.707-20.055-44.707-44.707s20.055-44.707 44.707-44.707c14.269 0 27.798 6.901 36.195 18.46" +
        "a2.498 2.498 0 0 1-4.041 2.935C109.92 51.674 97.9 45.542 85.225 45.542c-21.898 0-39.713 17.815-39.713 39.713" +
        " 0 21.897 17.815 39.713 39.713 39.713 12.649 0 24.654-6.11 32.117-16.347a2.497 2.497 0 0 1 4.034 2.941" +
        "c-8.397 11.523-21.912 18.401-36.151 18.401zM77.088 66.12c-.384-.283-.824-.533-1.185-.533a1.66 1.66 0 0 0-1.659 1.659" +
        "v35.421a1.66 1.66 0 0 0 2.534 1.407c.311-.196 30.316-17.307 30.607-17.561.35-.304.571-.752.571-1.252" +
        " 0-.43-.163-.821-.431-1.116-.304-.333-29.936-17.656-30.437-18.025z"

/** The centre line of the "C", which the trace follows (unfilled in the source SVG). */
private const val ARC_D =
    "M119.4 60.476c-7.672-10.563-20.121-17.431-34.175-17.431-23.312 0-42.21 18.898-42.21 42.21" +
        "s18.898 42.21 42.21 42.21c14.028 0 26.458-6.843 34.132-17.373"

/** The play triangle alone, for the trace. */
private const val PLAY_D =
    "M77.088 66.12c-.384-.283-.824-.533-1.185-.533a1.66 1.66 0 0 0-1.659 1.659v35.421a1.66 1.66 0 0 0 2.534 1.407" +
        "c.311-.196 30.316-17.307 30.607-17.561.35-.304.571-.752.571-1.252 0-.43-.163-.821-.431-1.116" +
        "-.304-.333-29.936-17.656-30.437-18.025z"

private class Traced(d: String) {
    val path: Path = PathParser.createPathFromPathData(d).asComposePath()
    private val length: Float = PathMeasure().apply { setPath(path, false) }.length

    fun segment(from: Float, to: Float): Path {
        val out = Path()
        PathMeasure().apply { setPath(path, false) }.getSegment(from * length, to * length, out, true)
        return out
    }
}

private val square by lazy { Traced(SQUARE_D) }
private val arc by lazy { Traced(ARC_D) }
private val play by lazy { Traced(PLAY_D) }
private val glyph: Path by lazy { PathParser.createPathFromPathData(GLYPH_D).asComposePath() }

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

// The source viewBox, padded on every side for the glow.
private const val VB_X = 12.59f
private const val VB_Y = 12.621f
private const val VB = 145.078f
private const val PAD = 14f
private const val BOX = VB + PAD * 2

private inline fun DrawScope.inIcon(block: DrawScope.() -> Unit) {
    val s = size.width / BOX
    withTransform({ scale(s, s, Offset.Zero); translate(PAD - VB_X, PAD - VB_Y) }) { block() }
}

private fun DrawScope.glowStroke(path: Path, color: Color, width: Float, blur: Float, alpha: Float) {
    if (alpha <= 0f) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = width
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
        this.color = color.copy(alpha = color.alpha * alpha).toArgb()
        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
}

// Apple's gradient, from the SVG: deep green at the top to light green at the bottom.
private val IconFill = Brush.verticalGradient(
    0f to Color(0xFF0BD319), 0.22f to Color(0xFF18D722), 1f to Color(0xFF86FC6F),
    startY = VB_Y, endY = VB_Y + VB,
)

/**
 * The loading screen's mark: the CarPlay icon. It traces in with the white outline of the
 * OpenPlay splash (square, then the "C" and the play triangle, the glow trailing by 80 ms),
 * fills, and the outline dissolves. While loading, a short light runs round the square.
 * Done: a pop. Failed: a shake. Size it with a width; it is square.
 */
@Composable
fun CarPlayMark(state: MarkState, modifier: Modifier = Modifier) {
    val c = OP.colors
    val traced = state == MarkState.Done
    val trace = remember { Animatable(if (traced) 1f else 0f) }
    val glow = remember { Animatable(if (traced) 1f else 0f) }
    val fill = remember { Animatable(if (traced) 1f else 0f) }
    val strokeAlpha = remember { Animatable(if (traced) 0f else 1f) }
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (traced) return@LaunchedEffect
        launch { trace.animateTo(1f, tween(1600, easing = EaseInOut)) }
        launch { delay(80); glow.animateTo(1f, tween(1600, easing = EaseInOut)) }
        launch { delay(1250); fill.animateTo(1f, tween(800, easing = ExpoOut)) }
        launch { delay(1800); strokeAlpha.animateTo(0f, tween(800)) }
    }
    LaunchedEffect(state) {
        when (state) {
            MarkState.Done -> pop.animateTo(1f, keyframes { durationMillis = 600; 1f at 0; 1.07f at 240; 1f at 600 })
            MarkState.Failed -> shake.animateTo(0f, keyframes {
                durationMillis = 500; 0f at 0; -8f at 100; 7f at 200; -4f at 300; 2f at 400; 0f at 500
            })
            else -> Unit
        }
    }
    val orbit = rememberInfiniteTransition(label = "carplayOrbit")
    val orbitPos by orbit.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "carplayOrbitPos")

    // The square leads; the "C" and the triangle start a little later and finish with it.
    fun lagged(t: Float, start: Float) = ((t - start) / (1f - start)).coerceIn(0f, 1f)
    val outline = Brush.verticalGradient(listOf(Color.White, c.brand), startY = VB_Y, endY = VB_Y + VB)

    Canvas(
        modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            translationX = shake.value * density
        }.aspectRatio(1f),
    ) {
        inIcon {
            val settle = 0.96f + 0.04f * fill.value
            if (fill.value > 0f) {
                scale(settle, settle, Offset(VB_X + VB / 2, VB_Y + VB / 2)) {
                    drawPath(square.path, IconFill, alpha = fill.value)
                    drawPath(glyph, Color.White, alpha = fill.value)
                }
            }
            if (strokeAlpha.value > 0f) {
                val parts = listOf(
                    square to (trace.value to glow.value),
                    arc to (lagged(trace.value, 0.25f) to lagged(glow.value, 0.25f)),
                    play to (lagged(trace.value, 0.45f) to lagged(glow.value, 0.45f)),
                )
                for ((shape, progress) in parts) {
                    val (t, g) = progress
                    if (g > 0f) glowStroke(shape.segment(0f, g), c.brand, 6f, 3f, 0.9f * strokeAlpha.value)
                    if (t > 0f) {
                        drawPath(
                            shape.segment(0f, t), outline, alpha = strokeAlpha.value,
                            style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            if (state == MarkState.Loading && fill.value >= 1f) {
                val head = orbitPos
                val tail = head + 0.12f
                val runs = if (tail <= 1f) listOf(head to tail) else listOf(head to 1f, 0f to tail - 1f)
                for ((a, b) in runs) {
                    val p = square.segment(a, b)
                    glowStroke(p, c.brand, 7f, 3f, 1f)
                    drawPath(p, Color.White, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
