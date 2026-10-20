package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.shilapi.xcertplay.host.R

/**
 * OpenPlay's typeface, Outfit (res/font/outfit_variable.ttf, OFL), for chrome built in code.
 * One variable font: the weight is set on its "wght" axis. Falls back to sans-serif when the
 * font cannot load (as under Robolectric).
 */
internal object DiPlayType {
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600

    @Volatile private var outfit: Typeface? = null

    // Unit tests build a fresh app per test; loading the font in each would fill the heap.
    private val underRobolectric = android.os.Build.FINGERPRINT == "robolectric"

    fun typeface(context: Context): Typeface = outfit ?: (
        if (underRobolectric) Typeface.SANS_SERIF
        else runCatching { ResourcesCompat.getFont(context.applicationContext, R.font.outfit_variable) }.getOrNull() ?: Typeface.SANS_SERIF
        ).also { outfit = it }

    /** Sets Outfit on every text view in [root] (dialogs, whose views Android builds). */
    fun applyTree(root: android.view.View?, weight: Int = REGULAR) {
        when (root) {
            is android.widget.Button -> apply(root, MEDIUM)
            is TextView -> apply(root, weight)
            is android.view.ViewGroup -> for (i in 0 until root.childCount) applyTree(root.getChildAt(i), weight)
        }
    }

    /** Sets Outfit at [weight] on this text view. */
    fun apply(view: TextView, weight: Int) {
        view.typeface = typeface(view.context)
        if (Build.VERSION.SDK_INT >= 26) view.fontVariationSettings = "'wght' $weight"
    }
}
