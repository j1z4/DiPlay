package com.shilapi.xcertplay.openplay

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.doOnAttach
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.host.R
import kotlinx.coroutines.delay

/**
 * The OpenPlay loading screen inside CarPlayHostActivity: the animated mark alone, the live
 * connection stage in the bottom-left status line, Cancel in the bottom-right.
 *
 * It sits over the host's classic waiting panel, which stays laid out but invisible. The
 * host keeps writing its stage, help and button state to those views; this screen mirrors
 * them and presses the same buttons, so retry budgets, Wi-Fi recovery and "back" behave
 * exactly as before.
 */
internal class OpenPlayLoadingViews(
    val panel: View,
    val stage: TextView,
    val instructions: TextView,
    val help: TextView,
    val gestureHint: TextView,
    val retry: View,
    val recovery: View,
    val back: View,
)

internal fun attachOpenPlayLoading(viewport: FrameLayout, views: OpenPlayLoadingViews) {
    // Compose needs the window's lifecycle owner, so wait for the window. A view tree that is
    // never attached (a layout test) keeps the classic panel.
    viewport.doOnAttach {
        views.panel.visibility = View.INVISIBLE
        val compose = ComposeView(viewport.context).apply {
            setContent { HostLoading(views) }
        }
        viewport.addView(compose, FrameLayout.LayoutParams(-1, -1))
    }
}

private data class Snapshot(
    val stage: String,
    val hint: String,
    val help: String?,
    val gestureHint: String,
    val retry: Boolean,
    val recovery: Boolean,
    val active: Boolean,
)

private fun read(v: OpenPlayLoadingViews) = Snapshot(
    stage = v.stage.text?.toString().orEmpty(),
    hint = v.instructions.text?.toString().orEmpty(),
    help = v.help.takeIf { it.visibility == View.VISIBLE }?.text?.toString()?.takeIf { it.isNotBlank() },
    gestureHint = v.gestureHint.text?.toString().orEmpty(),
    retry = v.retry.visibility == View.VISIBLE,
    recovery = v.recovery.visibility == View.VISIBLE,
    active = CarPlayBackgroundSession.active,
)

@Composable
private fun HostLoading(views: OpenPlayLoadingViews) {
    var snap by remember { mutableStateOf(read(views)) }
    // The host updates its views from several places; reading them a few times a second keeps
    // this screen in step without touching any of that logic.
    LaunchedEffect(Unit) {
        while (true) {
            snap = read(views)
            delay(200)
        }
    }
    var widthPx by remember { mutableIntStateOf(0) }
    val colors = OpenPlayColors()
    Box(Modifier.fillMaxSize().background(colors.bg).onSizeChanged { widthPx = it.width }) {
        if (widthPx == 0) return@Box
        CompositionLocalProvider(
            LocalDensity provides Density(widthPx / 1280f, 1f),
            LocalOpenPlayColors provides colors,
        ) {
            LoadingContent(
                stage = snap.stage,
                tone = when {
                    snap.help != null -> Tone.Failed
                    snap.active -> Tone.Connected
                    else -> Tone.Connecting
                },
                mark = when {
                    snap.help != null -> MarkState.Failed
                    snap.active -> MarkState.Done
                    else -> MarkState.Loading
                },
                hint = snap.help ?: snap.hint,
                footnote = snap.gestureHint,
                onCancel = { views.back.performClick() },
                onRetry = if (snap.retry) ({ views.retry.performClick() }) else null,
                onReset = if (snap.recovery) ({ views.recovery.performClick() }) else null,
            )
        }
    }
}

/** The loading layout, shared by the host and by previews. */
@Composable
internal fun LoadingContent(
    stage: String,
    tone: Tone,
    mark: MarkState,
    hint: String?,
    footnote: String?,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    markModifier: Modifier = Modifier,
) {
    val c = OP.colors
    Box(Modifier.fillMaxSize()) {
        // The loading screen shows the CarPlay icon; the splash keeps the OpenPlay mark.
        CarPlayMark(mark, markModifier.align(Alignment.Center).width(200.dp))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusLine(tone, stage)
            if (!hint.isNullOrBlank()) {
                BasicText(hint, style = OP.text(16.sp, 400, lineHeight = 22.sp).copy(color = if (tone == Tone.Failed) c.textSecondary else c.textTertiary))
            }
            if (!footnote.isNullOrBlank()) {
                BasicText(footnote, style = OP.text(13.sp, 500).copy(color = c.textTertiary))
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The host's own translated labels.
            if (onReset != null) OpButton(stringResource(R.string.reset_carplay_wi_fi), onReset)
            if (onRetry != null) OpButton(stringResource(R.string.retry_carplay_connection), onRetry, kind = ButtonKind.Primary)
            OpButton(stringResource(R.string.cancel), onCancel, kind = ButtonKind.Ghost)
        }
    }
}
