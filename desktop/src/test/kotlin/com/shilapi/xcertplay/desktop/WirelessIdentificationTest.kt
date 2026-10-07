package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import com.shilapi.xcertplay.transport.Iap2WirelessLinkRole
import com.shilapi.xcertplay.transport.forWirelessLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessIdentificationTest {
    private val wireless = Iap2WirelessIdentification("AA:BB:CC:DD:EE:FF", "ExampleNet")
    private val base = WirelessReceiver.identification("02:11:22:33:44:55")

    @Test fun withoutVehicleDataBothLinksCarryThePlainIdentity() {
        for (role in Iap2WirelessLinkRole.entries) {
            val identification = WirelessReceiver.linkIdentification(base, role, wireless, vehicleData = false)

            assertEquals(base.forWirelessLink(role, wireless), identification)
            assertFalse(identification.vehicleStatusEnabled)
            assertFalse(identification.locationInformationEnabled)
            assertFalse(identification.vehicleSpeedEnabled)
            assertNull(parameters(identification).first(VEHICLE_INFORMATION))
            assertNull(parameters(identification).first(LOCATION_INFORMATION))
        }
    }

    @Test fun vehicleDataIsDeclaredOnlyInsideTheWifiTunnel() {
        val tunnel = WirelessReceiver.linkIdentification(base, Iap2WirelessLinkRole.RUNTIME_TUNNEL, wireless, vehicleData = true)
        val bootstrap = WirelessReceiver.linkIdentification(base, Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP, wireless, vehicleData = true)

        assertTrue(tunnel.vehicleStatusEnabled)
        assertTrue(tunnel.locationInformationEnabled)
        assertTrue(tunnel.vehicleSpeedEnabled)
        val tunnelParameters = parameters(tunnel)
        assertNotNull(tunnelParameters.first(VEHICLE_INFORMATION))
        assertNotNull(tunnelParameters.first(VEHICLE_STATUS))
        val location = Iap2ParameterList.parse(tunnelParameters.first(LOCATION_INFORMATION)!!.payload)
        assertNotNull(location.first(VEHICLE_SPEED_FLAG))

        assertEquals(base.forWirelessLink(Iap2WirelessLinkRole.BLUETOOTH_BOOTSTRAP, wireless), bootstrap)
        val bootstrapParameters = parameters(bootstrap)
        assertNull(bootstrapParameters.first(VEHICLE_INFORMATION))
        assertNull(bootstrapParameters.first(VEHICLE_STATUS))
        assertNull(bootstrapParameters.first(LOCATION_INFORMATION))
    }

    @Test fun identityNamesOpenPlayWithTheDeviceIdSerial() {
        assertEquals(APP_NAME, base.name)
        assertEquals("OPENPLAY-021122334455", base.serialNumber)
    }

    private fun parameters(config: Iap2IdentificationConfig) =
        Iap2ParameterList.parse(Iap2IdentificationClient.identificationInformation(config).payload)

    private companion object {
        // IdentificationInformation group ids and the wheel-speed flag inside the location group.
        const val VEHICLE_INFORMATION = 20
        const val VEHICLE_STATUS = 21
        const val LOCATION_INFORMATION = 22
        const val VEHICLE_SPEED_FLAG = 20
    }
}