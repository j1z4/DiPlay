package com.shilapi.xcertplay

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.ContextThemeWrapper
import com.shilapi.xcertplay.host.R

internal interface AppAppearanceOwner {
    val currentAppNight: Boolean
}

internal fun Context.appDialogTheme(): Int = if (
    (this as? AppAppearanceOwner)?.currentAppNight ?: resolveAppNightNow()
) {
    R.style.Theme_Xcertplay_Dialog_Dark
} else {
    R.style.Theme_Xcertplay_Dialog_Light
}

internal fun Context.appDialogContext(): Context = ContextThemeWrapper(this, appDialogTheme())

/** Dialogs in OpenPlay's style: its palette from the theme, and Outfit set on their text when shown. */
internal fun Context.appDialogBuilder(): AlertDialog.Builder = object : AlertDialog.Builder(this, appDialogTheme()) {
    override fun create(): AlertDialog = super.create().also { dialog ->
        dialog.setOnShowListener { DiPlayType.applyTree(dialog.window?.decorView) }
    }
}

internal fun Context.appDialog(): Dialog = Dialog(this, appDialogTheme())
