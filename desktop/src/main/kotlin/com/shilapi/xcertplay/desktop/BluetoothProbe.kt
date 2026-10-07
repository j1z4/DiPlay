package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import java.io.File
import java.util.UUID
import kotlin.system.exitProcess

/** The iPhone's iAP2 RFCOMM service, as used by DiPlay's Android receiver. */
val IAP2_IPHONE_UUID: UUID = UUID.fromString("00000000-deca-fade-deca-deafdecacafe")

private const val STEP_TIMEOUT_MILLIS = 15_000L

/**
 * Go/no-go check for the Windows port: open the paired iPhone's iAP2 service over Windows RFCOMM,
 * then run DiPlay's own identification and MFi authentication.
 *
 * Usage: BluetoothProbe <iPhone BT address> <offline-mfi directory> [ssid]
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: BluetoothProbe <iPhone BT address> <offline-mfi directory> [ssid]")
        exitProcess(2)
    }
    val (address, mfiDirectory) = args
    val ssid = args.getOrElse(2) { "DiPlay-Windows" }

    log("connecting RFCOMM to $address service=$IAP2_IPHONE_UUID")
    val socket = WindowsRfcommSocket.connect(address, IAP2_IPHONE_UUID)
    val localAddress = socket.localAddress
    log("RFCOMM connected; local adapter=$localAddress")

    val session = Iap2Session.openWireless(socket.duplexStream(), onTrace = { log("trace $it") })
    try {
        val identification = Iap2IdentificationConfig(
            name = "DiPlay",
            modelIdentifier = "DiPlay",
            manufacturer = "DiPlay",
            serialNumber = "DIPLAY-" + localAddress.replace(":", ""),
            firmwareVersion = "0.1.0",
            hardwareVersion = "1.0",
            wireless = Iap2WirelessIdentification(localAddress, ssid),
        )
        Iap2IdentificationClient(session).identify(identification, STEP_TIMEOUT_MILLIS)
        log("RESULT iap2 identification accepted")

        val mfi = LocalMfiAuthenticationClient.load(File(mfiDirectory))
        Iap2MfiAuthenticationClient(mfi).run(session, STEP_TIMEOUT_MILLIS) { log("mfi $it") }
        log("RESULT iap2 MFi authentication accepted: GO")
    } finally {
        session.close()
    }
}

private fun log(message: String) = println("${System.currentTimeMillis() % 100_000} $message")
