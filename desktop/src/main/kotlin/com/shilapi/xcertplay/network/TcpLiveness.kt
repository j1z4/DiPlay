package com.shilapi.xcertplay.network

import java.net.Socket

/**
 * Desktop counterpart of the Android TcpLiveness: Windows applies its own keep-alive timing,
 * so only SO_KEEPALIVE is enabled here.
 */
internal object TcpLiveness {
    fun configure(socket: Socket, diagnostic: (String) -> Unit) {
        socket.keepAlive = true
        diagnostic("tcp liveness keepAlive=true (desktop defaults)")
    }
}
