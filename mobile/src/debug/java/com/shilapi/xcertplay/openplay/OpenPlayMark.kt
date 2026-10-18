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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The OpenPlay mark, from design/ui-library/brand/diplay-mark.svg (viewBox 0 0 150 148). */
private const val MARK_D =
    "M58.0615 0C54.0815 0 50.8035 3.28714 50.8035 7.2785V38.1089C50.8035 38.9159 50.406 39.6654 49.7407 40.1181" +
        "C49.0746 40.5708 48.2344 40.6627 47.4867 40.3641L22.9912 30.186C22.0721 29.8037 21.113 29.6314 20.1702 29.6412" +
        "C17.3425 29.6704 14.65 31.3633 13.5073 34.1287L0.54667 65.5079C-0.976196 69.1951 0.794 73.4982 4.47372 75.0189" +
        "L34.1829 87.3013C34.8644 87.5843 35.3763 88.1591 35.5815 88.87C35.7867 89.5804 35.6571 90.3365 35.2316 90.9406" +
        "L15.7831 115.624C13.3605 118.79 13.9632 123.402 17.1202 125.831L43.9929 146.511C47.1496 148.94 51.7489 148.331 54.1711 145.165" +
        "L73.0819 121.159C73.5407 120.559 74.2461 120.211 75.0002 120.211C75.7538 120.211 76.4592 120.559 76.9179 121.159" +
        "L95.8288 145.165C98.2514 148.331 102.849 148.94 106.007 146.511L132.88 125.831C136.037 123.402 136.64 118.791 134.217 115.624" +
        "L114.768 90.9406C114.343 90.3374 114.214 89.5804 114.418 88.87C114.624 88.1591 115.136 87.5843 115.817 87.3013" +
        "L145.526 75.0189C149.206 73.4982 150.976 69.1963 149.453 65.5079L136.491 34.1287C135.349 31.3635 132.656 29.6706 129.829 29.6412" +
        "C128.886 29.6315 127.927 29.8048 127.008 30.186L102.512 40.3641C101.765 40.6631 100.924 40.5708 100.258 40.1181" +
        "C99.5892 39.6654 99.1917 38.9159 99.1917 38.1089V7.2785C99.1917 3.28714 95.9141 0 91.9341 0H58.0615Z"

private val markPath: Path by lazy { PathParser.createPathFromPathData(MARK_D).asComposePath() }
private val markLength: Float by lazy { PathMeasure().apply { setPath(markPath, false) }.length }

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

// The splash viewBox pads the mark by 15 units on every side for the glow.
private const val PAD = 15f
private const val BOX_W = 150f + PAD * 2

/** Draws inside the padded splash box, in mark units. */
private inline fun DrawScope.inMark(block: DrawScope.() -> Unit) {
    val s = size.width / BOX_W
    withTransform({ scale(s, s, Offset.Zero); translate(PAD, PAD) }) { block() }
}

private fun segment(from: Float, to: Float): Path {
    val m = PathMeasure().apply { setPath(markPath, false) }
    val out = Path()
    m.getSegment(from * markLength, to * markLength, out, true)
    return out
}

private fun DrawScope.blurredStroke(path: Path, color: Color, width: Float, blur: Float, alpha: Float) {
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

enum class MarkState { Tracing, Filled, Loading, Done, Failed }

/**
 * The splash / loading mark. Size it with a width; height follows the 180 x 178 box.
 *  - Tracing: the outline draws (1.6 s), the glow trails it by 80 ms, the fill settles in,
 *    the outline dissolves. Same timeline as the web splash.
 *  - Loading: the filled mark with a short light running round the outline.
 *  - Done: a pop. Failed: a shake.
 */
@Composable
fun OpenPlayMark(state: MarkState, modifier: Modifier = Modifier) {
    val c = OP.colors
    val trace = remember { Animatable(if (state == MarkState.Tracing) 0f else 1f) }
    val glow = remember { Animatable(if (state == MarkState.Tracing) 0f else 1f) }
    val fill = remember { Animatable(if (state == MarkState.Tracing) 0f else 1f) }
    val strokeAlpha = remember { Animatable(if (state == MarkState.Tracing) 1f else 0f) }
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (state != MarkState.Tracing) return@LaunchedEffect
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
    val orbit = rememberInfiniteTransition(label = "orbit")
    val orbitPos by orbit.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "orbitPos")

    Canvas(
        modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            translationX = shake.value * density
        }.aspectBox(),
    ) {
        inMark {
            val settle = 0.96f + 0.04f * fill.value
            if (fill.value > 0f) {
                scale(settle, settle, Offset(75f, 74f)) {
                    drawPath(markPath, c.markFill, alpha = fill.value)
                }
            }
            if (strokeAlpha.value > 0f && glow.value > 0f) {
                blurredStroke(segment(0f, glow.value), c.brand, 6f, 3f, 0.9f * strokeAlpha.value)
            }
            if (strokeAlpha.value > 0f && trace.value > 0f) {
                drawPath(
                    segment(0f, trace.value),
                    Brush.verticalGradient(listOf(Color.White, c.brand), startY = 0f, endY = 148f),
                    alpha = strokeAlpha.value,
                    style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (state == MarkState.Loading) {
                val head = orbitPos
                val tail = head + 0.14f
                val parts = if (tail <= 1f) listOf(head to tail) else listOf(head to 1f, 0f to tail - 1f)
                for ((a, b) in parts) {
                    val p = segment(a, b)
                    blurredStroke(p, c.brand, 7f, 3f, 1f)
                    drawPath(p, Color.White, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** The still mark (Home, Settings), with the soft brand glow under it. */
@Composable
fun OpenPlayMarkStatic(modifier: Modifier = Modifier, glow: Boolean = true) {
    val c = OP.colors
    Canvas(modifier.aspectBox()) {
        inMark {
            if (glow) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = c.brandGlow.toArgb()
                    maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                }
                translate(0f, 8f) { drawContext.canvas.nativeCanvas.drawPath(markPath.asAndroidPath(), paint) }
            }
            drawPath(markPath, c.markFill)
        }
    }
}

private fun Modifier.aspectBox() = aspectRatio(BOX_W / (148f + PAD * 2))
