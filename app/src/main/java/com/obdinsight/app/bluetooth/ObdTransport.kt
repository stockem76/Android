package com.obdinsight.app.bluetooth

import kotlinx.coroutines.flow.SharedFlow

/**
 * One raw byte-level event captured on the wire, before any OBD/ELM327 interpretation.
 * This is the "record the pids, hex, messages" requirement: every byte sent and received
 * is captured here regardless of whether we know how to decode it yet.
 */
data class RawIoEvent(
    val timestampMillis: Long,
    val direction: Direction,
    val hex: String,
    val ascii: String,
) {
    enum class Direction { TX, RX }
}

class ObdTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A connected byte pipe to an ELM327-compatible dongle (Carista or otherwise),
 * regardless of whether the underlying link is classic Bluetooth SPP or BLE.
 */
interface ObdTransport {
    val isConnected: Boolean
    val rawEvents: SharedFlow<RawIoEvent>

    suspend fun connect()
    suspend fun disconnect()

    /**
     * Sends a single ELM327/AT/OBD command line and suspends until the full response
     * (up to the '>' prompt) has been received, or [timeoutMillis] elapses.
     */
    suspend fun sendCommand(command: String, timeoutMillis: Long = 5000L): String
}
