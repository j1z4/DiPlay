package com.shilapi.xcertplay.openplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Focus and press ---------- */

/**
 * Every interactive part goes through here, so touch, D-pad and rotary all get the same
 * feedback: focus lifts the part on a spring, a press squeezes it to 96%.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusScale: Float = OP.FOCUS_SCALE,
    enabled: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            focused -> focusScale
            else -> 1f
        },
        animationSpec = if (pressed) OP.pressSpring() else if (focused) OP.focusSpring() else OP.restSpring(),
        label = "pressScale",
    )
    // propagateMinConstraints: a part given a fixed or weighted width fills it (dialog buttons);
    // a free one still wraps its content.
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        propagateMinConstraints = true,
        content = { content(focused) },
    )
}

/* ---------- Buttons ---------- */

enum class ButtonKind { Primary, Secondary, Accent, Ghost, TonalDanger, Danger }

@Composable
fun OpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    height: Dp = OP.hMd,
    icon: PhIcon? = null,
) {
    val c = OP.colors
    val (fill, ink) = when (kind) {
        ButtonKind.Primary -> c.focusFill to c.focusText
        ButtonKind.Secondary -> c.surface2 to c.text
        ButtonKind.Accent -> c.accent to Color(0xFF03140A)
        ButtonKind.Ghost -> Color.Transparent to c.textLabel
        ButtonKind.TonalDanger -> c.danger.copy(alpha = 0.14f) to c.danger
        ButtonKind.Danger -> c.danger to Color.White
    }
    val radius = OP.buttonRadius(height)
    Pressable(onClick, modifier) { focused ->
        val ring by animateFloatAsState(if (focused) 1f else 0f, OP.focusSpring(), label = "ring")
        Row(
            Modifier
                .drawBehind {
                    // The ring sits 5dp outside the button so it never moves the label.
                    if (ring > 0f) {
                        val o = 5.dp.toPx()
                        drawRoundRect(
                            c.focusRing.copy(alpha = ring),
                            topLeft = Offset(-o, -o),
                            size = Size(size.width + o * 2, size.height + o * 2),
                            cornerRadius = CornerRadius((radius + 5.dp).toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()),
                        )
                    }
                }
                .height(height)
                .clip(RoundedCornerShape(radius))
                .background(fill)
                .border(1.dp, if (kind == ButtonKind.Secondary) c.outline else Color.Transparent, RoundedCornerShape(radius))
                .padding(horizontal = height * 0.45f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Ph(icon, (height.value * 0.36f).sp, ink)
            BasicText(text, style = OP.text(if (height >= OP.hLg) 19.sp else if (height <= OP.hSm) 15.sp else 17.sp, 600).copy(color = ink))
        }
    }
}

/* ---------- Rows ---------- */

/**
 * The settings row, modelled on Apple TV: label left, value and chevron right. On focus it
 * turns white and lifts. `trailing` replaces the value (a switch, for instance).
 */
@Composable
fun OpRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    hint: String? = null,
    icon: PhIcon? = null,
    chevron: Boolean = value != null,
    danger: Boolean = false,
    minHeight: Dp = OP.hMd,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
) {
    val c = OP.colors
    val radius = OP.rowRadius(minHeight)
    Pressable(onClick, modifier.fillMaxWidth()) { focused ->
        val bg by animateColorAsState(
            when {
                focused && danger -> c.danger
                focused -> c.focusFill
                else -> c.surface
            },
            tween(160),
            label = "rowBg",
        )
        val ink = if (focused) (if (danger) Color.White else c.focusText) else if (danger) c.danger else c.text
        val sub = if (focused) c.focusValue else c.textSecondary
        val chevronShift by animateDpAsState(if (focused) 3.dp else 0.dp, OP.focusSpring(), label = "chev")
        Row(
            Modifier
                .shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(radius), clip = false)
                .clip(RoundedCornerShape(radius))
                .background(bg)
                .heightIn(min = minHeight)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(if (focused) c.focusText else c.surface2),
                    contentAlignment = Alignment.Center,
                ) { Ph(icon, 18.sp, if (focused) c.focusFill else c.textLabel) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(label, style = OP.text(19.sp, 600, (-0.19).sp).copy(color = ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (hint != null) BasicText(hint, style = OP.text(15.sp, 400).copy(color = sub))
            }
            if (trailing != null) trailing(focused)
            else if (value != null) BasicText(value, style = OP.text(18.sp, 500).copy(color = sub), maxLines = 1)
            if (chevron && trailing == null) Ph(PhIcon.Chevron, 16.sp, if (focused) c.focusText else c.textTertiary, Modifier.offset(x = chevronShift))
        }
    }
}

@Composable
fun OpInfoRow(label: String, value: String) {
    val c = OP.colors
    Row(Modifier.fillMaxWidth().heightIn(min = OP.hMd).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = OP.text(19.sp, 600).copy(color = c.text))
        BasicText(value, style = OP.text(18.sp, 500).copy(color = c.textSecondary))
    }
}

/* ---------- Switch, segmented, slider ---------- */

@Composable
fun OpSwitch(checked: Boolean, focused: Boolean) {
    val c = OP.colors
    val x by animateDpAsState(if (checked) 23.dp else 3.dp, OP.focusSpring(), label = "knob")
    val track by animateColorAsState(
        when {
            checked && focused -> c.brandDeep
            checked -> c.success
            focused -> Color(0xFFC4CAD6)
            else -> c.trackOff
        },
        tween(240),
        label = "track",
    )
    Box(Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(track)) {
        Box(Modifier.offset(x = x, y = 3.dp).size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun OpToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    OpRow(label, onClick = { onChange(!checked) }, hint = hint, trailing = { focused -> OpSwitch(checked, focused) })
}

@Composable
fun OpSegmented(options: List<String>, selected: String, onSelect: (String) -> Unit, height: Dp = 44.dp) {
    val c = OP.colors
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(0) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val segment = with(density) { (width / options.size.coerceAtLeast(1)).toDp() }
    val thumbX by animateDpAsState(segment * index, OP.focusSpring(), label = "thumb")
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(OP.buttonRadius(height)))
            .background(c.surface2)
            .padding(4.dp)
            .onSizeChanged { width = it.width },
    ) {
        if (width > 0) {
            Box(
                Modifier.offset(x = thumbX).width(segment).height(height - 8.dp)
                    .clip(RoundedCornerShape(OP.buttonRadius(height - 8.dp))).background(c.focusFill),
            )
        }
        Row {
            options.forEach { option ->
                val on = option == selected
                Pressable({ onSelect(option) }, Modifier.height(height - 8.dp), focusScale = 1f) { focused ->
                    Box(
                        Modifier.height(height - 8.dp).widthIn(min = 76.dp)
                            .border(if (focused) 2.dp else 0.dp, if (focused) c.focusRing else Color.Transparent, RoundedCornerShape(OP.buttonRadius(height - 8.dp)))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(option, style = OP.text(15.sp, 600).copy(color = if (on) c.focusText else c.textSecondary))
                    }
                }
            }
        }
    }
}

@Composable
fun OpSegmentedRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val c = OP.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(OP.rowRadius(OP.hMd))).background(c.surface)
            .heightIn(min = OP.hMd).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = OP.text(19.sp, 600).copy(color = c.text))
        OpSegmented(options, selected, onSelect)
    }
}

/** A focusable slider row: left / right on the D-pad steps the value. */
@Composable
fun OpSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, format: (Float) -> String, onChange: (Float) -> Unit) {
    val c = OP.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pct = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(pct, OP.focusSpring(), label = "slider")
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(OP.rowRadius(OP.hMd)))
            .background(if (focused) c.focusFill else c.surface)
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionRight -> { onChange((value + step).coerceAtMost(range.endInclusive)); true }
                    Key.DirectionLeft -> { onChange((value - step).coerceAtLeast(range.start)); true }
                    else -> false
                }
            }
            .focusable(interactionSource = source)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            BasicText(label, Modifier.weight(1f), style = OP.text(19.sp, 600).copy(color = if (focused) c.focusText else c.text))
            BasicText(format(value), style = OP.text(18.sp, 500).copy(color = if (focused) c.focusValue else c.textSecondary))
        }
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val y = size.height / 2
            val h = 8.dp.toPx()
            val r = CornerRadius(h / 2)
            drawRoundRect(if (focused) Color(0xFFC4CAD6) else c.trackOff, Offset(0f, y - h / 2), Size(size.width, h), r)
            drawRoundRect(if (focused) c.focusText else c.accent, Offset(0f, y - h / 2), Size(size.width * animated, h), r)
            drawCircle(Color.Black.copy(alpha = 0.35f), 14.dp.toPx(), Offset(size.width * animated, y + 2.dp.toPx()))
            drawCircle(Color.White, 13.dp.toPx(), Offset(size.width * animated, y))
        }
    }
}

/* ---------- Group ---------- */

@Composable
fun OpGroup(header: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = OP.colors
    Column(verticalArrangement = Arrangement.spacedBy(OP.rowGap)) {
        if (header != null) BasicText(header, Modifier.padding(start = 20.dp, bottom = 2.dp), style = OP.text(15.sp, 600).copy(color = c.textSection))
        content()
        if (footer != null) BasicText(footer, Modifier.padding(start = 20.dp, top = 2.dp).widthIn(max = 640.dp), style = OP.text(16.sp, 400, lineHeight = 22.sp).copy(color = c.textTertiary))
    }
}

/* ---------- Status ---------- */

enum class Tone { Waiting, Connecting, Connected, Warning, Failed }

@Composable
fun Tone.color(): Color = when (this) {
    Tone.Waiting -> OP.colors.textSecondary
    Tone.Connecting, Tone.Connected -> OP.colors.success
    Tone.Warning -> OP.colors.warning
    Tone.Failed -> OP.colors.danger
}

/** The dot: breathing when waiting, pulsing while connecting, still otherwise. */
@Composable
fun StatusDot(tone: Tone, size: Dp = 10.dp) {
    val color by animateColorAsState(tone.color(), tween(240), label = "dot")
    val t = rememberInfiniteTransition(label = "dotAnim")
    val breathe by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "breathe")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "pulse")
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        if (tone == Tone.Connecting) drawCircle(color.copy(alpha = 0.6f * (1 - pulse)), r + r * 1.6f * pulse)
        drawCircle(color.copy(alpha = if (tone == Tone.Waiting) breathe else 1f), r)
    }
}

/**
 * The status line: a dot and text in the same color code as the card. Messages crossfade
 * into each other; nothing slides.
 */
@Composable
fun StatusLine(tone: Tone, text: String, size: Int = 18) {
    val c = OP.colors
    val ink by animateColorAsState(if (tone == Tone.Waiting) c.textSecondary else lerp(tone.color(), c.text, 0.3f), tween(240), label = "ink")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusDot(tone)
        AnimatedContent(text, transitionSpec = { fadeIn(tween(240, 160)) togetherWith fadeOut(tween(160)) }, label = "status") { t ->
            BasicText(t, style = OP.text(size.sp, 600).copy(color = ink))
        }
    }
}

/* ---------- Device card ---------- */

class CardItem(val label: String, val value: String, val onClick: () -> Unit)

/** The Figma "Choose this Device" card, with the status as its last, color-coded line. */
@Composable
fun DeviceCard(items: List<CardItem>, tone: Tone, status: String, modifier: Modifier = Modifier) {
    val c = OP.colors
    Column(
        modifier
            .shadow(24.dp, RoundedCornerShape(OP.cardRadius), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(OP.cardRadius))
            .background(c.card)
            .border(1.dp, c.cardEdge, RoundedCornerShape(OP.cardRadius))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) Divider()
            Pressable(item.onClick, Modifier.fillMaxWidth(), focusScale = 1.02f) { focused ->
                val bg by animateColorAsState(if (focused) c.focusFill else Color.Transparent, tween(160), label = "cardItem")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(OP.cardRadius - 6.dp)).background(bg).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(item.label, style = OP.text(22.sp, 500).copy(color = if (focused) c.focusValue else c.textLabel))
                    BasicText(item.value, style = OP.text(24.sp, 500).copy(color = if (focused) c.focusText else c.text))
                }
            }
        }
        Divider()
        val toneColor = tone.color()
        Column(
            // The wash keeps the same gap below the divider as the card's inset below it.
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(OP.cardRadius - 6.dp))
                .background(Brush.horizontalGradient(0f to toneColor.copy(alpha = 0.12f), 0.7f to Color.Transparent))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("Status", style = OP.text(22.sp, 500).copy(color = c.textLabel))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(tone, 12.dp)
                val ink = if (tone == Tone.Waiting) c.text else lerp(toneColor, c.text, 0.3f)
                AnimatedContent(status, transitionSpec = { fadeIn(tween(240, 160)) togetherWith fadeOut(tween(160)) }, label = "cardStatus") { s ->
                    BasicText(s, style = OP.text(24.sp, 500).copy(color = ink))
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.5.dp).background(OP.colors.cardDivider))
}

/** The Figma "Settings >" row under the card. */
@Composable
fun SlateRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OP.colors
    val h = OP.hLg
    Pressable(onClick, modifier.fillMaxWidth()) { focused ->
        Row(
            Modifier.clip(RoundedCornerShape(OP.buttonRadius(h)))
                .background(if (focused) Brush.verticalGradient(listOf(c.focusFill, c.focusFill)) else c.card)
                .border(1.dp, if (focused) Color.Transparent else c.cardEdge, RoundedCornerShape(OP.buttonRadius(h)))
                .height(h).fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(label, Modifier.weight(1f), style = OP.text(21.sp, 600).copy(color = if (focused) c.focusText else c.text))
            Ph(PhIcon.Chevron, 16.sp, if (focused) c.focusText else c.textSecondary)
        }
    }
}

/* ---------- Rail ---------- */

@Composable
fun RailItem(label: String, icon: PhIcon, current: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    val c = OP.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    if (focused) androidx.compose.runtime.LaunchedEffect(Unit) { onFocus() }
    val scale by animateFloatAsState(if (focused) OP.FOCUS_SCALE else 1f, if (focused) OP.focusSpring() else OP.restSpring(), label = "rail")
    val bar by animateDpAsState(if (current && !focused) 22.dp else 0.dp, OP.focusSpring(), label = "bar")
    val bg = when {
        focused -> c.focusFill
        current -> c.surface2
        else -> Color.Transparent
    }
    val ink = if (focused) c.focusText else if (current) c.text else c.textSecondary
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(bar).clip(CircleShape).background(c.brandStrong))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ph(icon, 20.sp, if (current && !focused) c.brandStrong else ink)
            BasicText(label, style = OP.text(17.sp, 600).copy(color = ink))
        }
    }
}

/* ---------- Banner ---------- */

@Composable
fun Banner(text: String, warning: Boolean = false, action: (@Composable RowScope.() -> Unit)? = null) {
    val c = OP.colors
    val tint = if (warning) c.warning else c.accent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(lerp(c.bgRaised, tint, 0.13f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.3f)), RoundedCornerShape(18.dp))
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(text, Modifier.weight(1f), style = OP.text(16.sp, 500, lineHeight = 21.sp).copy(color = c.text))
        if (action != null) action()
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
