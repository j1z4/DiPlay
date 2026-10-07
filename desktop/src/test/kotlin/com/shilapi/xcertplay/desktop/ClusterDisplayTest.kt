package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.setupEnabledFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClusterDisplayTest {
    private val settings = DesktopSettings("00:11:22:33:44:55", "", File("offline-mfi"), 1280, 720, 60)

    private fun config(settings: DesktopSettings) =
        WirelessReceiver.airPlayConfig(settings, "02:00:00:00:00:01", "AA:BB:CC:DD:EE:FF")

    @Test fun clusterIsAnInputLessPanelShowingMapAndTurnCard() {
        val display = ClusterDisplay.config()

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

    @Test fun settingAddsTheClusterAndLeavesTheMainDisplayAlone() {
        val plain = config(settings)
        val withCluster = config(settings.copy(clusterDisplay = true))

        assertNull(plain.cluster)
        assertEquals(ClusterDisplay.config(), withCluster.cluster)
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
