package com.shilapi.xcertplay.desktop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WindowsPlatformParsingTest {
    @Test fun parsesTheConnectedWifiInterface() {
        val output = """

            There is 1 interface on the system:

                Name                   : Wi-Fi
                Description            : Example Wi-Fi 6E Adapter
                State                  : connected
                SSID                   : ExampleNet
                AP BSSID               : 00:11:22:33:44:55
                Band                   : 5 GHz
                Channel                : 36
                Authentication         : WPA3-Personal  (H2E)
                Cipher                 : CCMP

                Hosted network status  : Not available
        """.trimIndent().prependIndent("    ")

        val info = WindowsWlanInfo.parse(output).single()

        assertEquals("Wi-Fi", info.interfaceName)
        assertEquals("ExampleNet", info.ssid)
        assertArrayEquals(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55), info.bssid)
        assertEquals(36, info.channel)
        assertEquals("WPA3-Personal", info.authentication)
    }

    @Test fun ignoresDisconnectedInterfaces() {
        val output = """
                Name                   : Wi-Fi
                State                  : disconnected
        """.trimIndent().prependIndent("    ")

        assertTrue(WindowsWlanInfo.parse(output).isEmpty())
    }

    @Test fun sockaddrBthIsPackedLittleEndianWithWindowsGuidLayout() {
        val address = WindowsRfcommSocket.parseAddress("CC:27:46:5E:3F:78")
        val bytes = WindowsRfcommSocket.sockaddrBth(address, UUID.fromString("00000000-deca-fade-deca-deafdecacafe"), port = 0)

        assertEquals(30, bytes.size)
        // addressFamily AF_BTH = 32
        assertArrayEquals(byteArrayOf(32, 0), bytes.copyOfRange(0, 2))
        // btAddr 0xCC27465E3F78 as a little-endian ULONGLONG
        assertArrayEquals(
            byteArrayOf(0x78, 0x3F, 0x5E, 0x46, 0x27, 0xCC.toByte(), 0, 0),
            bytes.copyOfRange(2, 10),
        )
        // GUID: Data1 LE, Data2 LE (0xdeca), Data3 LE (0xfade), Data4 as stored
        assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 0,
                0xCA.toByte(), 0xDE.toByte(),
                0xDE.toByte(), 0xFA.toByte(),
                0xDE.toByte(), 0xCA.toByte(), 0xDE.toByte(), 0xAF.toByte(),
                0xDE.toByte(), 0xCA.toByte(), 0xCA.toByte(), 0xFE.toByte(),
            ),
            bytes.copyOfRange(10, 26),
        )
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes.copyOfRange(26, 30))
    }

    @Test fun formatsAddressesRoundTrip() {
        assertEquals("CC:27:46:5E:3F:78", WindowsRfcommSocket.formatAddress(WindowsRfcommSocket.parseAddress("cc:27:46:5e:3f:78")))
    }
}
