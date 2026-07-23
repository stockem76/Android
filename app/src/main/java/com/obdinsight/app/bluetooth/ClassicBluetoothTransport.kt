package com.obdinsight.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Classic Bluetooth (RFCOMM/SPP) transport. Most ELM327-compatible dongles that support
 * classic Bluetooth expose the standard Serial Port Profile UUID below; the Carista dongle
 * itself is BLE-first (see [com.obdinsight.app.bluetooth.BleTransport]), but many other
 * OBD2 clones - and Carista's own classic-mode fallback on some hardware revisions - use SPP.
 */
class ClassicBluetoothTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
) : ObdTransport {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val commandMutex = Mutex()

    private val _rawEvents = MutableSharedFlow<RawIoEvent>(extraBufferCapacity = 256)
    override val rawEvents = _rawEvents.asSharedFlow()

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            adapter.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            input = s.inputStream
            output = s.outputStream
        } catch (e: IOException) {
            throw ObdTransportException("Classic Bluetooth connect failed: ${e.message}", e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    override suspend fun sendCommand(command: String, timeoutMillis: Long): String =
        commandMutex.withLock {
            withContext(Dispatchers.IO) {
                withTimeout(timeoutMillis) {
                    writeLine(command)
                    readUntilPrompt()
                }
            }
        }

    private fun writeLine(command: String) {
        val out = output ?: throw ObdTransportException("Not connected")
        val bytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
        out.write(bytes)
        out.flush()
        emitRaw(RawIoEvent.Direction.TX, bytes)
    }

    private fun readUntilPrompt(): String {
        val ins = input ?: throw ObdTransportException("Not connected")
        val buffer = StringBuilder()
        val chunk = ByteArray(256)
        while (true) {
            val n = ins.read(chunk)
            if (n <= 0) break
            val slice = chunk.copyOf(n)
            emitRaw(RawIoEvent.Direction.RX, slice)
            buffer.append(String(slice, Charsets.US_ASCII))
            if (buffer.contains(ByteUtils.PROMPT_CHAR)) break
        }
        return ByteUtils.stripPromptAndWhitespace(buffer.toString())
    }

    private fun emitRaw(direction: RawIoEvent.Direction, bytes: ByteArray) {
        _rawEvents.tryEmit(
            RawIoEvent(
                timestampMillis = System.currentTimeMillis(),
                direction = direction,
                hex = ByteUtils.toHex(bytes),
                ascii = ByteUtils.toPrintableAscii(bytes),
            )
        )
    }
}

/** Small helper so callers don't need a raw CoroutineScope just to fire-and-forget disconnect(). */
fun ObdTransport.disconnectAsync(scope: CoroutineScope) {
    scope.async(Dispatchers.IO) { disconnect() }
}
