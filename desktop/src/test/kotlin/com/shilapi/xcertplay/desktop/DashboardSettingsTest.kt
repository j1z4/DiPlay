package com.shilapi.xcertplay.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DashboardSettingsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun parsesPairedDevicesFromRegQueryOutput() {
        val output = """
            HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices\001122334455
                Name    REG_BINARY    4578616D706C652050686F6E6500

            HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices\aabbccddeeff
                Name    REG_BINARY    436F6E74726F6C6C657200

            End of search: 2 match(es) found.
        """.trimIndent()

        val devices = PairedBluetoothDevices.parse(output)

        assertEquals(
            listOf(PairedDevice("AA:BB:CC:DD:EE:FF", "Controller"), PairedDevice("00:11:22:33:44:55", "Example Phone")),
            devices,
        )
    }

    @Test fun incompleteSettingsListWhatIsMissing() {
        val settings = DesktopSettings("", "", File(temp.root, "missing"), 1280, 720, 60)

        val problems = settings.problems()

        assertEquals(2, problems.size)
        assertTrue(problems[0].contains("iPhone"))
        assertTrue(problems[1].contains("identity.pk8"))
    }

    @Test fun completeSettingsHaveNoProblems() {
        val identity = temp.newFolder("offline-mfi").apply {
            File(this, "identity.pk8").writeBytes(byteArrayOf(1))
            File(this, "certificate.p7b").writeBytes(byteArrayOf(1))
        }

        assertTrue(DesktopSettings("00:11:22:33:44:55", "", identity, 1280, 720, 60).problems().isEmpty())
    }

    @Test fun granularSettingsFallBackToDefaultsAndRoundTrip() {
        val store = DesktopStore(temp.newFolder("granular"))
        val custom = SettingsValues.DEFAULTS
            .with(SettingsSchema.AUDIO_BUFFER_MILLIS, 250)
            .with(SettingsSchema.NIGHT_MODE, "night")
            .with(SettingsSchema.CENTER_LATITUDE, 41.88)
            .with(SettingsSchema.DEVICE_NAME, "Test Car")
        val saved = DesktopSettings("00:11:22:33:44:55", "", File(temp.root, "id"), 1280, 720, 60, advanced = custom)

        store.saveSettings(saved)
        val loaded = store.loadSettings().advanced

        assertEquals(250, loaded[SettingsSchema.AUDIO_BUFFER_MILLIS])
        assertEquals("night", loaded[SettingsSchema.NIGHT_MODE])
        assertEquals(41.88, loaded[SettingsSchema.CENTER_LATITUDE], 1e-9)
        assertEquals("Test Car", loaded[SettingsSchema.DEVICE_NAME])
        assertEquals(SettingsSchema.AIRPLAY_PORT.default, loaded[SettingsSchema.AIRPLAY_PORT])
    }

    @Test fun invalidGranularValuesUseTheDefault() {
        val values = SettingsValues(mapOf("audio.bufferMillis" to "5", "carplay.nightMode" to "purple"))

        assertEquals(120, values[SettingsSchema.AUDIO_BUFFER_MILLIS])
        assertEquals("system", values[SettingsSchema.NIGHT_MODE])
    }

    @Test fun settingsSurviveSaveAndLoad() {
        val store = DesktopStore(temp.newFolder("OpenPlay"))
        val saved = DesktopSettings(
            iphoneAddress = "00:11:22:33:44:55",
            wifiPassphrase = "example pass",
            mfiDirectory = File(temp.root, "offline-mfi"),
            width = 1920,
            height = 720,
            fps = 30,
            fullscreen = true,
            autoStart = true,
            clusterDisplay = true,
            vehicleData = true,
        )

        store.saveSettings(saved)

        assertEquals(saved, store.loadSettings())
    }
}
