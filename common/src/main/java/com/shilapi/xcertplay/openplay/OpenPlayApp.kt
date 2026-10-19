@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.shilapi.xcertplay.openplay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/* ---------- Navigation model ---------- */

sealed class Screen(val depth: Int) {
    object Splash : Screen(0)
    object Home : Screen(1)
    object Settings : Screen(2)
    class Picker(val key: String) : Screen(3)
    class SubPage(val id: String) : Screen(3)
    object Phones : Screen(3)
    object Transport : Screen(3)
}

private val Morph = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

internal fun iconFor(name: String?): PhIcon = when (name) {
    "about" -> PhIcon.About; "advanced" -> PhIcon.Advanced; "audio" -> PhIcon.Audio
    "automation" -> PhIcon.Automation; "car" -> PhIcon.Car; "connection" -> PhIcon.Connection
    "controls" -> PhIcon.Controls; "dashboard" -> PhIcon.Dashboard; "day" -> PhIcon.Day
    "diagnostics" -> PhIcon.Diagnostics; "display" -> PhIcon.Display; "home" -> PhIcon.Home
    "language" -> PhIcon.Language; "navigation" -> PhIcon.Navigation; "night" -> PhIcon.Night
    "overview" -> PhIcon.Overview; "permissions" -> PhIcon.Check; else -> PhIcon.Advanced
}

/** What the screens need from OpenPlayActivity beyond settings: the session and the iPhone list. */
internal interface OpenPlayHost : SettingsHost {
    /** Connect phone / Open CarPlay: starts the real session (the host shows the loading screen). */
    fun connect()
    fun disconnect()
    /** Paired iPhones, or null when DiPlay's Bluetooth dialogs must take over. */
    fun phones(): List<OpenPlaySession.Phone>?
}

internal class AppState(val catalog: Catalog, val settings: RealSettings, val host: OpenPlayHost, start: Screen) {
    var screen by mutableStateOf(start)
    val back = mutableStateListOf<Screen>()
    var category by mutableStateOf("overview")
    var status by mutableStateOf(OpenPlaySession.Status(OpenPlaySession.State.Ready, null))
    var wireless by mutableStateOf(true)
    var toast by mutableStateOf<String?>(null)
    var toastId by mutableIntStateOf(0)
    var confirm by mutableStateOf<Pair<String, () -> Unit>?>(null)
    var afterPhone: (() -> Unit)? = null
    var splashDone: () -> Unit = {}
    /** Bumped by [refresh] so rows re-read values that live outside the catalog (phone name). */
    var revision by mutableIntStateOf(0)

    fun go(to: Screen) { back.add(screen); screen = to }
    fun pop() { screen = back.removeLastOrNull() ?: Screen.Home }
    fun home() { back.clear(); screen = Screen.Home }
    fun say(text: String) { toast = text; toastId++ }
    fun change(key: String, value: Any?) = settings.set(host, key, value)
}

/* ---------- App ---------- */

/**
 * OpenPlay laid out on a 1280-wide canvas, as on a head unit at 1280 x 720: the density is set
 * so 1280 dp fills the screen width.
 */
@Composable
internal fun OpenPlayApp(state: AppState) {
    var widthPx by remember { mutableIntStateOf(0) }
    val colors = if (state.settings.string("appearance") == "Light") DayColors else OpenPlayColors()
    // Live status, polled like DiPlayActivity.refreshStatus() (there is no session broadcast).
    LaunchedEffect(Unit) {
        val context = state.host.activity
        // Loading the CarPlay authentication can take a moment; keep it off the main thread.
        val setupFailed = withContext(Dispatchers.IO) { OpenPlaySession.setupFailed(context) }
        while (true) {
            state.status = OpenPlaySession.status(context, setupFailed)
            state.wireless = OpenPlaySession.wireless(context)
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize().background(colors.bg).onSizeChanged { widthPx = it.width }) {
        if (widthPx == 0) return@Box
        CompositionLocalProvider(
            LocalDensity provides Density(widthPx / 1280f, 1f),
            LocalOpenPlayColors provides colors,
        ) {
            Screens(state)
            ToastHost(state)
            ConfirmHost(state)
        }
    }
}

@Composable
private fun Screens(state: AppState) {
    BackHandler(enabled = state.screen != Screen.Splash && state.screen != Screen.Home) {
        if (state.screen == Screen.Settings) state.home() else state.pop()
    }
    SharedTransitionLayout {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                val a = initialState
                val b = targetState
                when {
                    // Splash to Home: the shared mark and wordmark carry the motion.
                    a.depth <= 1 && b.depth <= 1 -> fadeIn(tween(500, 250)) togetherWith fadeOut(tween(300))
                    b.depth > a.depth -> (slideInHorizontally(tween(420, easing = Morph)) { it / 35 } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 35 } + fadeOut(tween(240)))
                    else -> (slideInHorizontally(tween(420, easing = Morph)) { -it / 35 } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(240)) { it / 35 } + fadeOut(tween(240)))
                }
            },
            contentKey = { s -> when (s) { is Screen.Picker -> "picker:${s.key}"; is Screen.SubPage -> "page:${s.id}"; else -> s::class.simpleName } },
            label = "screens",
        ) { screen ->
            when (screen) {
                Screen.Splash -> SplashScreen(state, this@SharedTransitionLayout, this)
                Screen.Home -> HomeScreen(state, this@SharedTransitionLayout, this)
                Screen.Settings -> SettingsScreen(state)
                is Screen.Picker -> PickerScreen(state, screen.key)
                is Screen.SubPage -> SubPageScreen(state, screen.id)
                Screen.Phones -> PhonesScreen(state)
                Screen.Transport -> TransportScreen(state)
            }
        }
    }
}

/* ---------- Shared brand pieces ---------- */

@Composable
private fun Modifier.shared(key: String, t: SharedTransitionScope, v: AnimatedVisibilityScope): Modifier = with(t) {
    this@shared.sharedBounds(
        rememberSharedContentState(key),
        animatedVisibilityScope = v,
        boundsTransform = { _, _ -> tween(850, easing = Morph) },
        enter = fadeIn(tween(1)),
        exit = fadeOut(tween(1)),
    )
}

@Composable
private fun Wordmark(t: SharedTransitionScope, v: AnimatedVisibilityScope, nameSize: Int, taglineSize: Int) {
    val c = OP.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((nameSize * 0.17f).dp)) {
        Column(horizontalAlignment = Alignment.End) {
            BasicText("OpenPlay", Modifier.shared("name", t, v), style = OP.text(nameSize.sp, 700, (-nameSize * 0.02f).sp, lineHeight = nameSize.sp).copy(color = c.text))
            BasicText(
                "(a fork of DiPlay)",
                // Tucked under the name, ending where the "y" starts (as in components.css).
                Modifier.shared("fork", t, v).offset(y = (-nameSize * 0.12f).dp).padding(end = (nameSize * 0.63f).dp),
                style = OP.text((nameSize * 0.19f).sp, 700).copy(color = c.text),
            )
        }
        BasicText("CarPlay, that you own.", Modifier.shared("tagline", t, v), style = OP.text(taglineSize.sp, 700).copy(color = c.text))
    }
}

/* ---------- Splash ---------- */

/**
 * The mark traces with the wordmark already under it (it never fades in on its own), then the
 * splash morphs into Home. The loading screen comes only from Connect phone / Open CarPlay.
 */
@Composable
private fun SplashScreen(state: AppState, t: SharedTransitionScope, v: AnimatedVisibilityScope) {
    var status by remember { mutableStateOf("Starting OpenPlay…") }
    LaunchedEffect(Unit) {
        delay(2800)
        status = "Ready to connect"
        delay(600)
        state.home()
        state.splashDone()
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp)) {
            OpenPlayMark(MarkState.Tracing, Modifier.width(220.dp).shared("mark", t, v))
            Wordmark(t, v, nameSize = 72, taglineSize = 22)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp)) { StatusLine(Tone.Waiting, status) }
    }
}

/* ---------- Home ---------- */

private fun statusOf(s: OpenPlaySession.Status): Pair<Tone, String> = when (s.state) {
    OpenPlaySession.State.SetupError -> Tone.Warning to "Setup needs attention"
    OpenPlaySession.State.Connected -> Tone.Connected to "CarPlay connected"
    OpenPlaySession.State.ReconnectNeeded -> Tone.Warning to "Reconnect to apply changes"
    OpenPlaySession.State.Connecting -> Tone.Connecting to "Connecting to your iPhone…"
    OpenPlaySession.State.Failed -> Tone.Failed to "Connection interrupted"
    OpenPlaySession.State.Ready -> Tone.Waiting to "Ready to connect"
    OpenPlaySession.State.NoPhone -> Tone.Warning to "Choose your iPhone"
}

@Composable
private fun HomeScreen(state: AppState, t: SharedTransitionScope, v: AnimatedVisibilityScope) {
    val c = OP.colors
    val (tone, status) = statusOf(state.status)
    val running = state.status.state in setOf(OpenPlaySession.State.Connected, OpenPlaySession.State.ReconnectNeeded, OpenPlaySession.State.Connecting)
    val connect = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { connect.requestFocus() } }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = 104.dp, end = 72.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            DeviceCard(
                listOf(
                    CardItem("Choose this Device", state.status.phone ?: "Choose your iPhone") { state.go(Screen.Phones) },
                    CardItem("Connection", if (state.wireless) "Wi-Fi" else "USB") { state.go(Screen.Transport) },
                ),
                tone,
                status,
            )
            SlateRow("Settings", { state.go(Screen.Settings) })
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 120.dp).background(Brush.verticalGradient(listOf(Color.Transparent, c.outline, c.outline, Color.Transparent))))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.align(Alignment.Center).size(520.dp).background(Brush.radialGradient(listOf(c.brandGlow.copy(alpha = 0.16f), Color.Transparent))))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(56.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OpenPlayMarkStatic(Modifier.width(72.dp).shared("mark", t, v))
                    Wordmark(t, v, nameSize = 96, taglineSize = 26)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OpButton(
                        if (running) "Open CarPlay" else "Connect phone",
                        { state.host.connect() },
                        Modifier.widthIn(min = 280.dp).focusRequester(connect),
                        kind = ButtonKind.Primary,
                        height = OP.hLg,
                    )
                    if (running) OpButton("Disconnect", { state.host.disconnect() }, kind = ButtonKind.TonalDanger, height = OP.hSm)
                }
            }
        }
    }
}

/* ---------- Settings ---------- */

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val c = OP.colors
    Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        OpButton("", onBack, Modifier.width(OP.hSm), height = OP.hSm, icon = PhIcon.Back)
        BasicText(title, Modifier.weight(1f), style = OP.text(30.sp, 600).copy(color = c.text.copy(alpha = 0.72f), textAlign = TextAlign.Center))
        Spacer(Modifier.width(OP.hSm))
    }
}

@Composable
private fun SettingsScreen(state: AppState) {
    val cat = state.catalog.categories.first { it.id == state.category }
    val list = rememberLazyListState()
    LaunchedEffect(state.category) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        Header("Settings") { state.home() }
        Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.width(300.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.catalog.categories.forEach { c ->
                    RailItem(c.title, iconFor(c.icon), c.id == state.category, onClick = { state.category = c.id }, onFocus = { state.category = c.id })
                }
            }
            LazyColumn(
                Modifier.weight(1f).widthIn(max = 760.dp),
                state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(OP.blockGap),
            ) {
                item(key = "head:${cat.id}") {
                    Column(Modifier.padding(start = 20.dp)) {
                        BasicText(cat.title, style = OP.text(28.sp, 600).copy(color = OP.colors.text))
                        BasicText(cat.subtitle, style = OP.text(16.sp, 400).copy(color = OP.colors.textSecondary))
                    }
                }
                if (state.status.state == OpenPlaySession.State.ReconnectNeeded) item(key = "reconnect") {
                    Banner("Saved. Some changes apply when CarPlay reconnects.") {
                        OpButton("Reconnect now", { state.host.reconnectIfRunning() }, kind = ButtonKind.Accent, height = OP.hSm)
                    }
                }
                items(cat.cards.size, key = { "${cat.id}:$it" }) { i -> CardView(state, cat, cat.cards[i]) }
            }
        }
    }
}

@Composable
private fun CardView(state: AppState, cat: Category, card: Card) {
    val c = OP.colors
    when (card.kind) {
        "banner" -> Banner(card.text ?: "", warning = true)
        "status" -> OpGroup("Status") {
            val (tone, text) = statusOf(state.status)
            OpRow("CarPlay", { state.host.connect() }, hint = text, icon = PhIcon.Connection, minHeight = 76.dp, trailing = { StatusDot(tone, 10.dp) })
        }
        "categories" -> OpGroup(card.title) {
            state.catalog.categories.filter { it.id != cat.id }.forEach { other ->
                OpRow(other.title, { state.category = other.id }, hint = other.subtitle, icon = iconFor(other.icon), chevron = true, minHeight = 72.dp)
            }
        }
        else -> {
            val rows = card.rows.filter { state.settings.visible(it) }
            if (rows.isEmpty()) return
            OpGroup(null) {
                Row(Modifier.padding(start = 20.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.icon != null) Ph(iconFor(card.icon), 14.sp, c.textTertiary)
                    BasicText(card.title ?: "", style = OP.text(15.sp, 600).copy(color = c.textSection))
                    if (card.requires != null) Requires(card.requires)
                }
                rows.forEach { RowView(state, it) }
            }
        }
    }
}

@Composable
private fun Requires(text: String) {
    val c = OP.colors
    BasicText(
        text,
        Modifier.clip(RoundedCornerShape(6.dp)).background(c.warning.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = OP.text(12.sp, 600).copy(color = lerp(c.warning, c.text, 0.2f)),
    )
}

/** One catalog row, by kind, bound to the real setting. */
@Composable
private fun RowView(state: AppState, r: Row) {
    val s = state.settings
    val c = OP.colors
    val hint = listOfNotNull(r.hint, r.requires?.let { "Needs: $it" }).joinToString("\n").ifEmpty { null }
    when (r.kind) {
        "toggle" -> OpToggleRow(r.label, s.bool(r.key!!), { state.change(r.key, it) }, hint)
        "picker" -> OpRow(
            r.label,
            { if (r.key == "iphone") state.go(Screen.Phones) else state.go(Screen.Picker(r.key!!)) },
            value = if (r.key == "iphone") state.status.phone ?: "Choose" else s.string(r.key!!),
            hint = r.requires?.let { "Needs: $it" },
        )
        "segment" -> OpSegmentedRow(r.label, r.options, s.string(r.key!!)) { state.change(r.key, it) }
        "slider", "number" -> {
            val max = if (r.kind == "number") minOf(r.max, r.min + r.step * 400) else r.max
            OpSliderRow(r.label, s.number(r.key!!).toFloat(), r.min.toFloat()..max.toFloat(), r.step.toFloat(), { "${it.toInt()} ${r.unit}".trim() }) {
                state.change(r.key, it.toDouble())
            }
        }
        "text" -> OpRow(r.label, { state.change(r.key!!, null) }, value = s.string(r.key!!))
        "link" -> OpRow(
            r.label,
            {
                when {
                    // Pages OpenPlay draws itself; others (the setup guide) are DiPlay's.
                    r.page != null && r.page in state.catalog.pages -> state.go(Screen.SubPage(r.page))
                    r.category != null -> state.category = r.category
                    else -> OpenPlayBindings.action(state.host, r)
                }
            },
            hint = if (r.valueFrom == "ambientSummary") ambientSummary(s) else hint,
            icon = r.icon?.let { iconFor(it) },
            chevron = true,
        )
        "button" -> OpRow(r.label, { OpenPlayBindings.action(state.host, r) }, hint = r.requires?.let { "Needs: $it" }, chevron = false)
        "note" -> BasicText(
            r.text,
            Modifier.padding(horizontal = 20.dp).widthIn(max = 640.dp),
            style = OP.text(16.sp, 400, lineHeight = 22.sp).copy(color = if (r.tone == "warning") c.warning else c.textTertiary),
        )
        "info" -> OpInfoRow(r.label, r.value ?: "")
        "swatches" -> Swatches(state, r)
    }
}

private fun ambientSummary(s: RealSettings): String {
    val mode = if (s.bool("ambientFollow")) s.string("ambientMode") else "Fixed"
    val colors = (s["ambientColors"] as? List<*>)?.size ?: 1
    return "${if (s.bool("ambientOn")) "On" else "Off"} · $mode · ${s.string("ambientSpeed")} · $colors colors · brightness ${s.number("ambientBrightness").toInt()}"
}

@Composable
private fun Swatches(state: AppState, r: Row) {
    val picked = (state.settings[r.key!!] as? List<*>)?.map { (it as Number).toInt() }?.toSet() ?: emptySet()
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..r.count).chunked(11).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { n ->
                    val color = when (n) {
                        30 -> Color(0xFFFFF6E8)
                        31 -> Color(0xFFE8F3FF)
                        else -> Color.hsl(((n - 1) / 29f) * 340f, 0.88f, 0.58f)
                    }
                    Pressable({
                        val next = picked.toMutableSet()
                        // Several colours while following music, one in fixed mode; never none.
                        if (!state.settings.bool("ambientFollow")) next.clear()
                        if (n in next && next.size > 1) next.remove(n) else if (n in next) state.say("Select at least one color") else next.add(n)
                        state.change(r.key, next.sorted())
                    }, Modifier.size(40.dp), focusScale = 1.15f) { _ ->
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color)) {
                            if (n in picked) Box(Modifier.align(Alignment.Center).size(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)))
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Pickers and sub-pages ---------- */

@Composable
private fun SplitPage(title: String, icon: PhIcon, note: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val c = OP.colors
    Column(Modifier.fillMaxSize()) {
        Header(title, onBack)
        Row(Modifier.fillMaxSize().padding(horizontal = 80.dp), horizontalArrangement = Arrangement.spacedBy(72.dp)) {
            Column(Modifier.weight(0.85f).fillMaxHeight().padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                Box(
                    Modifier.size(168.dp).clip(RoundedCornerShape(48.dp)).background(Brush.radialGradient(listOf(c.surface2, c.surface))),
                    contentAlignment = Alignment.Center,
                ) { Ph(icon, 64.sp, c.text) }
                BasicText(note, Modifier.widthIn(max = 420.dp), style = OP.text(18.sp, 400, lineHeight = 25.sp).copy(color = c.textSecondary, textAlign = TextAlign.Center))
            }
            LazyColumn(Modifier.weight(1.3f), contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(OP.rowGap), content = content)
        }
    }
}

@Composable
private fun Choice(label: String, chosen: Boolean, hint: String? = null, onClick: () -> Unit) {
    OpRow(label, onClick, hint = hint, chevron = false, trailing = if (chosen) { focused -> Ph(PhIcon.Check, 20.sp, if (focused) OP.colors.focusText else OP.colors.success) } else null)
}

@Composable
private fun PickerScreen(state: AppState, key: String) {
    val (row, icon) = state.catalog.rows.getValue(key)
    val current = state.settings.string(key)
    SplitPage(row.label, iconFor(icon), row.hint ?: "", { state.pop() }) {
        items(row.options) { option -> Choice(option, option == current) { state.pop(); state.change(key, option) } }
    }
}

/** The paired iPhones, as DiPlay lists them; a choice is saved as the selected iPhone. */
@Composable
private fun PhonesScreen(state: AppState) {
    val phones = remember(state.revision) { state.host.phones() }
    val current = state.status.phone
    SplitPage("Choose iPhone", PhIcon.Connection, "OpenPlay connects to this iPhone. Pair it with the car in Bluetooth settings first.", { state.pop() }) {
        if (phones == null) {
            item {
                OpRow("Bluetooth is off or nothing is paired", { state.host.choosePhone(state.afterPhone ?: {}) }, hint = "Opens the Bluetooth help", chevron = true)
            }
        } else {
            items(phones, key = { it.address }) { phone ->
                Choice(phone.name, phone.name.substringBefore(" · ") == current) {
                    OpenPlaySession.savePhone(state.host.activity, phone)
                    state.pop()
                    state.revision++
                    state.afterPhone?.invoke()
                    state.afterPhone = null
                }
            }
        }
        item {
            OpRow("Pair another iPhone", { state.host.activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, chevron = true)
        }
    }
}

/** How Connect phone reaches the iPhone: Wi-Fi (wireless CarPlay) or a USB cable. */
@Composable
private fun TransportScreen(state: AppState) {
    SplitPage("Connection", PhIcon.Connection, "Wireless CarPlay uses the car's hotspot. USB uses a data cable and charges the iPhone.", { state.pop() }) {
        item { Choice("Wi-Fi", state.wireless, "Wireless CarPlay") { OpenPlaySession.setWireless(state.host.activity, true); state.wireless = true; state.pop() } }
        item { Choice("USB", !state.wireless, "Cable") { OpenPlaySession.setWireless(state.host.activity, false); state.wireless = false; state.pop() } }
    }
}

@Composable
private fun SubPageScreen(state: AppState, id: String) {
    val page = state.catalog.pages.getValue(id)
    SplitPage(page.title, iconFor(page.icon), page.note, { state.pop() }) {
        items(page.rows.filter { state.settings.visible(it) }, key = { it.key ?: it.label }) { RowView(state, it) }
    }
}

/* ---------- Toast and confirm ---------- */

@Composable
private fun ToastHost(state: AppState) {
    val c = OP.colors
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.toastId) {
        val t = state.toast ?: return@LaunchedEffect
        shown = t
        delay(2600)
        shown = null
    }
    Box(Modifier.fillMaxSize().padding(bottom = 32.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(shown != null, enter = fadeIn(tween(300)) + scaleIn(tween(420, easing = Morph), 0.94f), exit = fadeOut(tween(240)) + scaleOut(tween(240), 0.96f)) {
            Row(
                Modifier.widthIn(max = 900.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xF62A2F3A))
                    .border(1.dp, c.outline, RoundedCornerShape(28.dp))
                    .padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ph(PhIcon.Check, 18.sp, c.success)
                BasicText(shown ?: "", style = OP.text(15.sp, 600, lineHeight = 20.sp).copy(color = c.text))
            }
        }
    }
}

@Composable
private fun ConfirmHost(state: AppState) {
    val c = OP.colors
    val ask = state.confirm ?: return
    Box(Modifier.fillMaxSize().background(Color(0x9E000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(28.dp)).background(c.bgRaised).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BasicText(ask.first, style = OP.text(32.sp, 600).copy(color = c.text))
            BasicText("This change reconnects CarPlay. Music and navigation pause for a few seconds.", style = OP.text(18.sp, 400, lineHeight = 25.sp).copy(color = c.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OpButton("Cancel", { state.confirm = null; state.settings.reload() }, Modifier.weight(1f))
                OpButton("Apply and reconnect", { state.confirm = null; ask.second() }, Modifier.weight(1f), kind = ButtonKind.Primary)
            }
        }
    }
}
