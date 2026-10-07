package com.shilapi.xcertplay.desktop

import android.util.Log

/** The Diagnostics tab applied to the process: how much DiPlay's protocol code logs. */
object Diagnostics {
    /** The android.util.Log level for a Detail choice; anything unknown logs at INFO. */
    fun logLevel(detail: String): Int = when (detail) {
        "verbose" -> Log.VERBOSE
        "debug" -> Log.DEBUG
        else -> Log.INFO
    }

    /** Applies the Detail setting; called at startup and again whenever CarPlay starts. */
    fun applyLogLevel(advanced: SettingsValues) {
        Log.minimumLevel = logLevel(advanced[SettingsSchema.LOG_LEVEL])
    }
}
