package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.network.CarPlayBonjourEndpoint
import com.shilapi.xcertplay.network.CarPlayBonjourProtocol
import java.io.Closeable
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * Advertises DiPlay's `_airplay._tcp` service with JmDNS and, as the Android JmDNS path does,
 * resolves the iPhone's `_carplay-ctrl._tcp` service and sends the connect probe that prompts
 * the iPhone to open the AirPlay control connection.
 */
class DesktopBonjour(
    private val address: InetAddress,
    private val config: AirPlayConfig,
    private val identity: AirPlayIdentity,
    private val log: (String) -> Unit,
) : Closeable {
    private val probes = LinkedBlockingQueue<CarPlayBonjourEndpoint>()
    private val seen = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var dns: JmDNS? = null
    @Volatile private var running = false

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            log("bonjour: carplay-ctrl added ${event.name}")
            event.dns.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) = Unit

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            val host = info.inetAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: run {
                log("bonjour: ${event.name} resolved without an IPv4 address")
                return
            }
            if (info.port !in 1..65535 || !seen.add("${event.name}|$host")) return
            probes.offer(CarPlayBonjourEndpoint(event.name, host, info.port, info.getPropertyString("id")))
        }
    }

    fun start() {
        val hostName = "carplay-${config.deviceId.replace(":", "")}"
        val instance = JmDNS.create(address, hostName)
        dns = instance
        running = true
        instance.addServiceListener(CARPLAY_CONTROL_TYPE, listener)
        instance.registerService(
            ServiceInfo.create(
                AIRPLAY_TYPE,
                config.deviceName,
                config.port,
                0,
                0,
                CarPlayBonjourProtocol.airPlayTxtRecords(config, identity),
            ),
        )
        log("bonjour: advertising ${config.deviceName} on ${address.hostAddress}:${config.port}")
        Thread(::probeLoop, "carplay-bonjour").apply { isDaemon = true }.start()
    }

    override fun close() {
        running = false
        dns?.let { instance ->
            runCatching { instance.unregisterAllServices() }
            runCatching { instance.close() }
        }
        dns = null
    }

    private fun probeLoop() {
        while (running) {
            val endpoint = try {
                probes.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) ?: continue
            } catch (_: InterruptedException) {
                return
            }
            probe(endpoint)
        }
    }

    /** Asks the iPhone's control service to connect, retrying while it starts listening. */
    private fun probe(endpoint: CarPlayBonjourEndpoint) {
        val request = CarPlayBonjourProtocol.connectProbeRequest(
            endpoint.host, endpoint.port, config.sourceVersion, config.deviceId,
        ).toByteArray(StandardCharsets.US_ASCII)
        repeat(PROBE_ATTEMPTS) { attempt ->
            if (!running) return
            try {
                Socket().use { socket ->
                    socket.bind(InetSocketAddress(address, 0))
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port), PROBE_TIMEOUT_MILLIS)
                    socket.soTimeout = PROBE_TIMEOUT_MILLIS
                    socket.getOutputStream().apply { write(request); flush() }
                    val status = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII).readLine()
                    log("bonjour: connect probe ${endpoint.host}:${endpoint.port} -> $status (attempt ${attempt + 1})")
                }
                return
            } catch (error: IOException) {
                log("bonjour: connect probe attempt ${attempt + 1} failed: ${error.javaClass.simpleName}")
                Thread.sleep(PROBE_RETRY_MILLIS)
            }
        }
    }

    private companion object {
        const val AIRPLAY_TYPE = "_airplay._tcp.local."
        const val CARPLAY_CONTROL_TYPE = "_carplay-ctrl._tcp.local."
        const val PROBE_ATTEMPTS = 7
        const val PROBE_TIMEOUT_MILLIS = 3_000
        const val PROBE_RETRY_MILLIS = 1_500L
        const val POLL_MILLIS = 500L
    }
}
