package com.shilapi.xcertplay.desktop

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun detailChoicesMapToLogLevels() {
        assertEquals(Log.INFO, Diagnostics.logLevel("info"))
        assertEquals(Log.DEBUG, Diagnostics.logLevel("debug"))
        assertEquals(Log.VERBOSE, Diagnostics.logLevel("verbose"))
        assertEquals(Log.INFO, Diagnostics.logLevel("purple"))
    }

    @Test fun applyingTheSettingChangesWhatTheLogShimWrites() {
        val before = Log.minimumLevel
        try {
            Diagnostics.applyLogLevel(SettingsValues.DEFAULTS.with(SettingsSchema.LOG_LEVEL, "debug"))
            assertTrue(Log.isLoggable("test", Log.DEBUG))
            assertFalse(Log.isLoggable("test", Log.VERBOSE))

            Diagnostics.applyLogLevel(SettingsValues.DEFAULTS)
            assertFalse(Log.isLoggable("test", Log.DEBUG))
            assertTrue(Log.isLoggable("test", Log.INFO))
        } finally {
            Log.minimumLevel = before
        }
    }

    @Test fun pruneKeepsOnlyTheNewestLogFiles() {
        val logs = temp.newFolder("logs")
        (1..5).forEach { day -> File(logs, "openplay-2026100$day-120000.log").writeText("") }
        File(logs, "other.txt").writeText("")

        FileLogging.prune(logs, keepFiles = 2)

        assertEquals(
            listOf("openplay-20261004-120000.log", "openplay-20261005-120000.log", "other.txt"),
            logs.list()!!.sorted(),
        )
    }

    @Test fun pruneAlwaysKeepsTheCurrentLog() {
        val logs = temp.newFolder("logs")
        File(logs, "openplay-20261001-120000.log").writeText("")

        FileLogging.prune(logs, keepFiles = 0)

        assertEquals(listOf("openplay-20261001-120000.log"), logs.list()!!.toList())
    }
}
