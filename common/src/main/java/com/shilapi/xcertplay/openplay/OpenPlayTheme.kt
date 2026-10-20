package com.shilapi.xcertplay.openplay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.shilapi.xcertplay.host.R

/*
 * OpenPlay design tokens, ported one for one from design/ui-library/tokens.css. The classic
 * screens' DiPlayPalette uses the same values.
 */

@Immutable
data class OpenPlayColors(
    val brand: Color = Color(0xFF3DFF6E),
    val brandStrong: Color = Color(0xFF2FE46A),
    val brandDeep: Color = Color(0xFF0E9E3E),
    val brandInk: Color = Color(0xFF021A0C),
    val brandGlow: Color = Color(0x473DFF6E),
    val bg: Color = Color(0xFF000000),
    val bgRaised: Color = Color(0xFF0C0E12),
    val surface: Color = Color(0xFF14161B),
    val surface2: Color = Color(0xFF1D2027),
    val surface3: Color = Color(0xFF282C35),
    val outline: Color = Color(0x0FFFFFFF),
    val cardTop: Color = Color(0xFF2A303B),
    val cardBottom: Color = Color(0xFF1E222A),
    val cardEdge: Color = Color(0x12FFFFFF),
    val cardDivider: Color = Color(0x14FFFFFF),
    val text: Color = Color(0xFFF5F7FA),
    val textLabel: Color = Color(0xFFCFDCFF),
    val textSecondary: Color = Color(0xFF8D95A5),
    val textTertiary: Color = Color(0xFF5F6676),
    val textSection: Color = Color(0xFF6F7787),
    val focusFill: Color = Color(0xFFF5F7FA),
    val focusText: Color = Color(0xFF0B0D11),
    val focusValue: Color = Color(0xFF4D5564),
    val focusRing: Color = Color(0xFF2FE46A),
    val trackOff: Color = Color(0xFF343944),
    val warning: Color = Color(0xFFFFBD5C),
    val danger: Color = Color(0xFFFF5A5F),
) {
    val accent get() = brandStrong
    val success get() = brandStrong
    val card get() = Brush.verticalGradient(listOf(cardTop, cardBottom))
    val markFill get() = Brush.verticalGradient(0f to brand, 0.55f to brandDeep, 1f to brandInk)
}

/** Day palette, as `[data-dp-theme="day"]`. */
val DayColors = OpenPlayColors(
    brandStrong = Color(0xFF0B7A33),
    brandGlow = Color(0x2E0E9E3E),
    bg = Color(0xFFEEF1F5),
    bgRaised = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE7EBF0),
    surface3 = Color(0xFFDBE0E7),
    outline = Color(0x140F141E),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFF5F7FA),
    cardEdge = Color(0x140F141E),
    cardDivider = Color(0x140F141E),
    text = Color(0xFF111418),
    textLabel = Color(0xFF4B5567),
    textSecondary = Color(0xFF5D6676),
    textTertiary = Color(0xFF8A92A0),
    textSection = Color(0xFF6B7383),
    focusFill = Color(0xFF111418),
    focusText = Color(0xFFFFFFFF),
    focusValue = Color(0xFFAAB2BF),
    focusRing = Color(0xFF0B7A33),
    trackOff = Color(0xFFC3CAD4),
    warning = Color(0xFF8A4B00),
    danger = Color(0xFFC62F35),
)

val LocalOpenPlayColors = staticCompositionLocalOf { OpenPlayColors() }

object OP {
    val colors: OpenPlayColors @Composable get() = LocalOpenPlayColors.current

    /** Outfit, the geometric sans of the wordmark, as one variable font. */
    @OptIn(ExperimentalTextApi::class)
    val outfit = FontFamily(
        listOf(400, 500, 600, 700).map { w ->
            Font(
                R.font.outfit_variable,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        },
    )
    val phosphor = FontFamily(Font(R.font.phosphor_bold))

    fun text(size: TextUnit, weight: Int = 500, tracking: TextUnit = 0.sp, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = outfit, fontSize = size, fontWeight = FontWeight(weight), letterSpacing = tracking, lineHeight = lineHeight)

    // Control heights and named gaps (AGENTS.md: gaps come from named constants).
    val hSm = 40.dp
    val hMd = 56.dp
    val hLg = 64.dp
    val rowGap = 10.dp
    val blockGap = 28.dp
    val cardRadius = 20.dp

    /** Corner radius follows control height, as in DiPlayButton: min(24, h * 20 / 56). */
    fun buttonRadius(h: Dp) = min(24.dp, h * 20f / 56f)
    fun rowRadius(h: Dp) = min(24.dp, h * 14f / 56f)

    // Motion: the springs Motion uses on the web (stiffness / damping ratio equivalents).
    const val FOCUS_SCALE = 1.035f
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.62f, stiffness = 420f)
    fun <T> restSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> pressSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)
}

/* ---------- Phosphor icons ---------- */

/** App icon name -> Phosphor Bold code point (same map as PHOSPHOR in components.js). */
enum class PhIcon(val codePoint: Int) {
    About(0xe2ce), Advanced(0xe272), Audio(0xe44a), Automation(0xe2de), Back(0xe058),
    Car(0xe112), Chevron(0xe13a), Connection(0xe4ea), Controls(0xe434), Dashboard(0xe628),
    Day(0xe472), Diagnostics(0xe23a), Display(0xe32e), Home(0xe2c2), Language(0xe288),
    Navigation(0xeade), Night(0xe330), Overview(0xe464), Check(0xe184), Search(0xe30c),
    Vehicle(0xe8cc), Warning(0xe4e0),
}

@Composable
fun Ph(icon: PhIcon, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = String(Character.toChars(icon.codePoint)),
        modifier = modifier,
        style = TextStyle(fontFamily = OP.phosphor, fontSize = size, color = color, lineHeight = size),
    )
}
