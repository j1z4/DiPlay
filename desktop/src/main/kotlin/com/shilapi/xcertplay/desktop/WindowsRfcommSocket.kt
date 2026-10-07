package com.shilapi.xcertplay.desktop

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.RfcommDuplexStream
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A client RFCOMM socket on the Windows Bluetooth stack (Winsock AF_BTH).
 *
 * Connecting with a service class UUID and port 0 makes Windows resolve the RFCOMM channel
 * through SDP, as Android's createRfcommSocketToServiceRecord does.
 */
class WindowsRfcommSocket private constructor(private val handle: Long) : Closeable {
    private val closed = AtomicBoolean(false)

    /** This PC's Bluetooth adapter address, as reported for the connected socket. */
    val localAddress: String
        get() {
            val buffer = Memory(SOCKADDR_BTH_SIZE.toLong())
            val length = IntByReference(SOCKADDR_BTH_SIZE)
            check(Ws2.INSTANCE.getsockname(handle, buffer, length) == 0) { "getsockname failed: ${lastError()}" }
            val raw = ByteBuffer.wrap(buffer.getByteArray(0, SOCKADDR_BTH_SIZE)).order(ByteOrder.LITTLE_ENDIAN)
            return formatAddress(raw.getLong(BT_ADDR_OFFSET))
        }

    val inputStream: InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val chunk = ByteArray(len)
            val count = Ws2.INSTANCE.recv(handle, chunk, len, 0)
            return when {
                count > 0 -> chunk.copyInto(b, off, 0, count).let { count }
                count == 0 -> -1
                else -> throw IOException("RFCOMM recv failed: ${lastError()}")
            }
        }
    }

    val outputStream: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var sent = 0
            while (sent < len) {
                val part = b.copyOfRange(off + sent, off + len)
                val count = Ws2.INSTANCE.send(handle, part, part.size, 0)
                if (count <= 0) throw IOException("RFCOMM send failed: ${lastError()}")
                sent += count
            }
        }
    }

    /** Wraps this socket in DiPlay's iAP2 byte-stream contract; closing the stream closes the socket. */
    fun duplexStream(): BlockingDuplexByteStream = RfcommDuplexStream(inputStream, outputStream, ::close)

    override fun close() {
        if (closed.compareAndSet(false, true)) Ws2.INSTANCE.closesocket(handle)
    }

    @Suppress("FunctionName")
    private interface Ws2 : Library {
        fun WSAStartup(version: Short, data: Memory): Int
        fun socket(af: Int, type: Int, protocol: Int): Long
        fun connect(socket: Long, name: ByteArray, length: Int): Int
        fun getsockname(socket: Long, name: Memory, length: IntByReference): Int
        fun send(socket: Long, buffer: ByteArray, length: Int, flags: Int): Int
        fun recv(socket: Long, buffer: ByteArray, length: Int, flags: Int): Int
        fun closesocket(socket: Long): Int

        companion object {
            val INSTANCE: Ws2 = Native.load("Ws2_32", Ws2::class.java)
        }
    }

    companion object {
        private const val AF_BTH = 32
        private const val SOCK_STREAM = 1
        private const val BTHPROTO_RFCOMM = 3
        private const val INVALID_SOCKET = -1L
        private const val WSADATA_SIZE = 512L
        private const val WINSOCK_2_2: Short = 0x0202

        /** Packed SOCKADDR_BTH: family(2) btAddr(8) serviceClassId(16) port(4). */
        private const val SOCKADDR_BTH_SIZE = 30
        private const val BT_ADDR_OFFSET = 2

        private val started = AtomicBoolean(false)

        /** Connects to [deviceAddress] ("AA:BB:CC:DD:EE:FF"), resolving [serviceUuid] through SDP. */
        fun connect(deviceAddress: String, serviceUuid: UUID): WindowsRfcommSocket {
            startWinsock()
            val handle = Ws2.INSTANCE.socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
            if (handle == INVALID_SOCKET) throw IOException("RFCOMM socket failed: ${lastError()}")
            val address = sockaddrBth(parseAddress(deviceAddress), serviceUuid, port = 0)
            if (Ws2.INSTANCE.connect(handle, address, address.size) != 0) {
                val error = lastError()
                Ws2.INSTANCE.closesocket(handle)
                throw IOException("RFCOMM connect to $deviceAddress failed: $error")
            }
            return WindowsRfcommSocket(handle)
        }

        internal fun sockaddrBth(address: Long, serviceUuid: UUID, port: Int): ByteArray =
            ByteBuffer.allocate(SOCKADDR_BTH_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(AF_BTH.toShort())
                putLong(address)
                putGuid(serviceUuid)
                putInt(port)
            }.array()

        /** Windows GUID layout: Data1/Data2/Data3 little-endian, Data4 as stored. */
        private fun ByteBuffer.putGuid(uuid: UUID) {
            val msb = uuid.mostSignificantBits
            putInt((msb ushr 32).toInt())
            putShort((msb ushr 16).toShort())
            putShort(msb.toShort())
            order(ByteOrder.BIG_ENDIAN)
            putLong(uuid.leastSignificantBits)
            order(ByteOrder.LITTLE_ENDIAN)
        }

        internal fun parseAddress(text: String): Long {
            val parts = text.split(':', '-')
            require(parts.size == 6 && parts.all { it.length == 2 }) { "Bluetooth address must be AA:BB:CC:DD:EE:FF" }
            return parts.fold(0L) { acc, part -> (acc shl 8) or part.toLong(16) }
        }

        internal fun formatAddress(address: Long): String =
            (5 downTo 0).joinToString(":") { "%02X".format((address ushr (it * 8)) and 0xff) }

        private fun startWinsock() {
            if (!started.compareAndSet(false, true)) return
            val result = Ws2.INSTANCE.WSAStartup(WINSOCK_2_2, Memory(WSADATA_SIZE))
            if (result != 0) {
                started.set(false)
                throw IOException("WSAStartup failed: $result")
            }
        }

        /** JNA saves the thread's last error after each native call; WSA errors share that slot. */
        private fun lastError(): String = "WSA error ${Native.getLastError()}"
    }
}
