package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayListenerIdentity
import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.AirPlayTcpAccepted
import com.shilapi.xcertplay.airplay.PairingStore
import com.shilapi.xcertplay.airplay.isInternalAirPlayPeer
import com.shilapi.xcertplay.mfi.MfiAuthenticator
import com.shilapi.xcertplay.network.AirPlayPortSelector
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The AirPlay control listener for the Windows receiver: the desktop counterpart of
 * CarPlayVpnService.attachWireless. Each accepted connection becomes a DiPlay [AirPlaySession].
 */
class DesktopAirPlayServer(
    private val bindAddress: InetAddress,
    private val config: AirPlayConfig,
    private val identity: AirPlayIdentity,
    private val pairings: PairingStore,
    private val mfi: MfiAuthenticator?,
    private val listener: AirPlaySessionListener,
    private val media: AirPlayMediaHandler,
    private val listenerIdentity: AirPlayListenerIdentity,
    private val log: (String) -> Unit,
) : Closeable {
    private val active = AtomicBoolean(false)
    private val sessions = CopyOnWriteArrayList<AirPlaySession>()
    private var server: ServerSocket? = null

    /** The config as advertised, with the port actually bound. */
    lateinit var boundConfig: AirPlayConfig
        private set

    /** Binds (falling back from the preferred port when busy) and starts accepting; returns the port. */
    fun start(): Int {
        check(active.compareAndSet(false, true)) { "AirPlay server already started" }
        val socket = AirPlayPortSelector.bind(bindAddress, config.port) { busy, bound ->
            log("AirPlay port $busy busy; listening on $bound")
        }
        server = socket
        boundConfig = config.copy(port = socket.localPort)
        Thread({ acceptLoop(socket) }, "airplay-accept").apply { isDaemon = true }.start()
        log("AirPlay listener ready ${bindAddress.hostAddress}:${socket.localPort}")
        return socket.localPort
    }

    override fun close() {
        if (!active.compareAndSet(true, false)) return
        runCatching { server?.close() }
        sessions.forEach { session -> runCatching { session.close() } }
        sessions.clear()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (active.get()) {
            val client = try {
                socket.accept()
            } catch (error: IOException) {
                if (active.get()) {
                    log("AirPlay accept failed: ${error.message}")
                    close()
                    listener.onTransportError("AirPlay listener failed: ${error.message}")
                }
                return
            }
            val acceptedAt = System.nanoTime()
            client.tcpNoDelay = true
            client.keepAlive = true
            client.setSoLinger(true, 0)
            val internalPeer = isInternalAirPlayPeer(client.inetAddress, client.localAddress)
            log("AirPlay TCP accepted from ${client.inetAddress.hostAddress} internal=$internalPeer")
            listener.onTcpAccepted(AirPlayTcpAccepted(listenerIdentity, internalPeer, acceptedAt))
            val session = AirPlaySession(
                socket = client,
                config = boundConfig,
                identity = identity,
                pairings = pairings,
                mfi = mfi,
                listener = object : AirPlaySessionListener by listener {
                    override fun onSessionActive(session: AirPlaySession) {
                        if (!internalPeer) listener.onSessionActive(session)
                    }

                    override fun onRemoteControlMessage(session: AirPlaySession, streamId: Long, message: Map<String, Any?>) =
                        listener.onRemoteControlMessage(session, streamId, message)

                    override fun onVideoPlaybackUiRequested(session: AirPlaySession) =
                        listener.onVideoPlaybackUiRequested(session)

                    override fun onSessionEnded(session: AirPlaySession) {
                        sessions.remove(session)
                        if (!internalPeer) listener.onSessionEnded(session)
                    }
                },
                media = media,
            )
            sessions.add(session)
            session.start()
        }
    }
}
