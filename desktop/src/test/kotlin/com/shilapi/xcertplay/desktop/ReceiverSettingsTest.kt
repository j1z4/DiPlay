package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReceiverSettingsTest {
    private val base = DesktopSettings("00:11:22:33:44:55", "", File("offline-mfi"), 1281, 721, 30)
    private val identity = SettingsValues.DEFAULTS
        .with(SettingsSchema.DEVICE_NAME, "Test Car")
        .with(SettingsSchema.MANUFACTURER, "Example Motors")
        .with(SettingsSchema.MODEL, "Roadster")

    private fun config(advanced: SettingsValues) =
        WirelessReceiver.airPlayConfig(base.copy(advanced = advanced), "02:00:00:00:00:01", "AA:BB:CC:DD:EE:FF")

    @Test fun defaultsAdvertiseOpenPlayWithAudioOnPort7000() {
        val config = config(SettingsValues.DEFAULTS)

        assertEquals(listOf(APP_NAME, APP_NAME, APP_NAME, APP_NAME), listOf(config.deviceName, config.manufacturer, config.model, config.oemLabel))
        assertEquals(7000, config.port)
        assertFalse(config.rightHandDrive)
        assertFalse(config.disableAudioOutput)
        assertTrue(config.microphone)
        // Odd stream sizes round down to even; the physical size comes from the defaults.
        assertEquals(1280, config.main.widthPixels)
        assertEquals(720, config.main.heightPixels)
        assertEquals(30, config.main.fps)
        assertEquals(200, config.main.widthPhysicalMm)
        assertEquals(113, config.main.heightPhysicalMm)
        assertNull(config.main.safeArea)
    }

    @Test fun identityLayoutAndScreenSettingsReachTheAirPlayConfig() {
        val advanced = identity
            .with(SettingsSchema.RIGHT_HAND_DRIVE, true)
            .with(SettingsSchema.AIRPLAY_PORT, 7100)
            .with(SettingsSchema.SCREEN_WIDTH_MM, 310)
            .with(SettingsSchema.SCREEN_HEIGHT_MM, 175)

        val config = config(advanced)

        assertEquals("Test Car", config.deviceName)
        assertEquals("Test Car", config.oemLabel)
        assertEquals("Example Motors", config.manufacturer)
        assertEquals("Roadster", config.model)
        assertTrue(config.rightHandDrive)
        assertEquals(7100, config.port)
        assertEquals(310, config.main.widthPhysicalMm)
        assertEquals(175, config.main.heightPhysicalMm)
    }

    @Test fun identityAndLayoutAreWhatInfoTellsTheIphone() {
        val info = AirPlayInfoPlist.build(config(identity.with(SettingsSchema.RIGHT_HAND_DRIVE, true)))

        assertEquals("Test Car", info["name"])
        assertEquals("Example Motors", info["manufacturer"])
        assertEquals("Roadster", info["model"])
        assertEquals(true, info["rightHandDrive"])
    }

    @Test fun safeAreaAppearsOnceAnyInsetIsSet() {
        val advanced = SettingsValues.DEFAULTS.with(SettingsSchema.SAFE_TOP, 24).with(SettingsSchema.SAFE_LEFT, 40)

        assertNull(WirelessReceiver.safeArea(SettingsValues.DEFAULTS))
        assertEquals(AirPlayInsets(top = 24, left = 40), WirelessReceiver.safeArea(advanced))
        assertEquals(AirPlayInsets(top = 24, left = 40), config(advanced).main.safeArea)
    }

    @Test fun audioOffDisablesOutputButKeepsTheMicrophoneAdvertised() {
        val config = config(SettingsValues.DEFAULTS.with(SettingsSchema.AUDIO_ENABLED, false))

        assertTrue(config.disableAudioOutput)
        assertTrue(config.microphone)
        assertFalse(AirPlayInfoPlist.build(config).containsKey("audioFormats"))
        assertTrue(AirPlayInfoPlist.build(config(SettingsValues.DEFAULTS)).containsKey("audioFormats"))
    }

    @Test fun identificationUsesTheIdentitySettingsAndKeepsTheSerial() {
        val identification = WirelessReceiver.identification("02:11:22:33:44:55", identity)

        assertEquals("Test Car", identification.name)
        assertEquals("Example Motors", identification.manufacturer)
        assertEquals("Roadster", identification.modelIdentifier)
        assertEquals("OPENPLAY-021122334455", identification.serialNumber)
    }

    @Test fun appearanceForcesDayOrNightAndOtherwiseFollowsWindows() {
        assertTrue(WirelessReceiver.nightMode("night") { false })
        assertFalse(WirelessReceiver.nightMode("day") { true })
        assertTrue(WirelessReceiver.nightMode("system") { true })
        assertFalse(WirelessReceiver.nightMode("system") { false })
    }
}
