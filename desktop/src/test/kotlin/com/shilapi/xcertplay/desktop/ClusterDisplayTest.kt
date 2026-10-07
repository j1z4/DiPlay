package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay.Content
import com.shilapi.xcertplay.airplay.setupEnabledFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import java.io.File

class ClusterDisplayTest {
    private val settings = DesktopSettings("00:11:22:33:44:55", "", File("offline-mfi"), 1280, 720, 60)

    private val mapStyle = SettingsValues.DEFAULTS.with(SettingsSchema.CLUSTER_STYLE, "map")

    private fun config(settings: DesktopSettings) =
        WirelessReceiver.airPlayConfig(settings, "02:00:00:00:00:01", "AA:BB:CC:DD:EE:FF")

    @Test fun mapStyleClusterIsAnInputLessPanelShowingMapAndTurnCard() {
        val display = ClusterDisplay.config(mapStyle)

        assertEquals(1280, display.widthPixels)
        assertEquals(480, display.heightPixels)
        assertEquals("maps:/car/instrumentcluster", display.initialUrl)
        assertEquals(0, display.primaryInputDevice)
        assertEquals(0, display.features)
        assertEquals(30, display.fps)
        // 8 % virtual margins around a centred marker, in pixels of the 1280x480 panel.
        assertEquals(AirPlayInsets(top = 38, bottom = 38, left = 102, right = 102), display.safeArea)
        assertEquals(true, display.safeAreaDrawOutside)
    }

    @Test fun clusterSettingsPickSizeFrameRateAndStartContent() {
        val values = mapStyle
            .with(SettingsSchema.CLUSTER_WIDTH, 1920)
            .with(SettingsSchema.CLUSTER_HEIGHT, 720)
            .with(SettingsSchema.CLUSTER_FPS, 20)
            .with(SettingsSchema.CLUSTER_CONTENT, "map")

        val display = ClusterDisplay.config(values)

        assertEquals(1920, display.widthPixels)
        assertEquals(720, display.heightPixels)
        assertEquals(20, display.fps)
        assertEquals("maps:/car/instrumentcluster/map", display.initialUrl)
        assertEquals(Dimension(1920, 720), ClusterDisplay.windowSize(values))
    }

    @Test fun windowFollowsTheStreamWhichRoundsToMultiplesOfEight() {
        val values = mapStyle
            .with(SettingsSchema.CLUSTER_WIDTH, 1000)
            .with(SettingsSchema.CLUSTER_HEIGHT, 300)

        val display = ClusterDisplay.config(values)

        assertEquals(304, display.heightPixels)
        assertEquals(1016, display.widthPixels)
        assertEquals(Dimension(1016, 304), ClusterDisplay.windowSize(values))
    }

    @Test fun contentSettingMapsToTheIphonesClusterUrls() {
        assertEquals(Content.INSTRUMENTS, ClusterDisplay.content("instruments"))
        assertEquals(Content.MAP, ClusterDisplay.content("map"))
        assertEquals(Content.TURN_CARD, ClusterDisplay.content("turncard"))
        assertEquals(Content.INSTRUMENTS, ClusterDisplay.content("something-else"))

        assertEquals("maps:/car/instrumentcluster", ClusterDisplay.content("instruments").url)
        assertEquals("maps:/car/instrumentcluster/map", ClusterDisplay.content("map").url)
        assertEquals("maps:/car/instrumentcluster/instructioncard", ClusterDisplay.content("turncard").url)
    }

    @Test fun everyContentChoiceInTheSchemaHasAClusterUrl() {
        val choice = SettingsSchema.CLUSTER_CONTENT.type as SettingType.Choice

        val urls = choice.options.map { (value, _) -> ClusterDisplay.content(value).url }

        assertEquals(3, urls.distinct().size)
        assertTrue(urls.all { it.startsWith("maps:/car/instrumentcluster") })
    }

    @Test fun settingAddsTheConfiguredClusterAndLeavesTheMainDisplayAlone() {
        val advanced = SettingsValues.DEFAULTS.with(SettingsSchema.CLUSTER_CONTENT, "turncard")
        val plain = config(settings.copy(advanced = advanced))
        val withCluster = config(settings.copy(clusterDisplay = true, advanced = advanced))

        assertNull(plain.cluster)
        assertEquals(ClusterDisplay.config(advanced), withCluster.cluster)
        assertEquals("maps:/car/instrumentcluster/instructioncard", withCluster.cluster?.initialUrl)
        assertEquals(plain, withCluster.copy(cluster = null))
    }

    @Test fun clusterIsAdvertisedAsStream111AndSetupEnablesAltScreen() {
        val withCluster = config(settings.copy(clusterDisplay = true))

        val displays = AirPlayInfoPlist.build(withCluster)["displays"] as List<*>
        val cluster = displays[1] as Map<*, *>

        assertEquals(listOf(110, 111), displays.map { (it as Map<*, *>)["type"] })
        assertEquals(AirPlayInfoPlist.ALT_UUID, cluster["uuid"])
        assertEquals("maps:/car/instrumentcluster", cluster["initialURL"])
        assertEquals(0, cluster["primaryInputDevice"])
        assertTrue("altScreen" in setupEnabledFeatures(withCluster, null))
        assertFalse("altScreen" in setupEnabledFeatures(config(settings), null))
    }
}
