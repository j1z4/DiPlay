package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothSocket

/** A bounded [BlockingDuplexByteStream] over an already-open Android RFCOMM socket. */
class BluetoothRfcommDuplexStream(
    socket: BluetoothSocket,
) : BlockingDuplexByteStream by RfcommDuplexStream(socket.inputStream, socket.outputStream, socket::close)
