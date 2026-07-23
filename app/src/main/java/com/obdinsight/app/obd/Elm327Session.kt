package com.obdinsight.app.obd

import com.obdinsight.app.bluetooth.ObdTransport
import com.obdinsight.app.bluetooth.ObdTransportException

/**
 * Drives the standard ELM327 AT-command handshake over whatever [ObdTransport] is connected,
 * then exposes simple request/response helpers. The Carista dongle - like essentially every
 * consumer OBD2 Bluetooth dongle - is built around an ELM327-compatible command set, so this
 * layer works the same whether the underlying link is classic SPP or BLE.
 */
class Elm327Session(private val transport: ObdTransport) {

    var headerFormat: Boolean = false
        private set

    suspend fun initialize() {
        // Reset, then configure a known-good baseline: echo off, linefeeds off, headers off,
        // automatic protocol detection. Spaces are left on (ELM327 default) since they make
        // parsing the response bytes trivial and the overhead is irrelevant at Bluetooth speeds.
        send("ATZ", timeoutMillis = 3000)
        send("ATE0")
        send("ATL0")
        send("ATH0")
        send("ATSP0")
        // Confirm the adapter is actually responding sanely before declaring success.
        val voltage = send("ATRV")
        if (voltage.isBlank()) {
            throw ObdTransportException("Dongle did not respond to AT commands - is this an ELM327-compatible adapter?")
        }
    }

    suspend fun send(command: String, timeoutMillis: Long = 5000): String =
        transport.sendCommand(command, timeoutMillis)

    /** Requests one Mode 01 PID and returns the decoded (or raw/error) result plus the raw text. */
    suspend fun readMode01Pid(pid: Int): Pair<ObdResult, String> {
        val raw = send("01%02X".format(pid))
        return ObdDecoder.decodeMode01(pid, raw) to raw
    }

    /** Discovers which Mode 01 PIDs this ECU actually supports, by querying the four bitmask PIDs. */
    suspend fun discoverSupportedPids(): Set<Int> {
        val supported = mutableSetOf<Int>()
        for (bitmaskPid in StandardPids.SUPPORT_BITMASK_PIDS) {
            val raw = send("01%02X".format(bitmaskPid))
            val found = ObdDecoder.decodeSupportedBitmask(bitmaskPid, raw)
            if (found.isEmpty() && bitmaskPid != 0x00) break
            supported += found
        }
        return supported
    }

    suspend fun readStoredDtcs(): List<String> = DtcDecoder.decode(send("03"))
    suspend fun readPendingDtcs(): List<String> = DtcDecoder.decode(send("07"))
    suspend fun clearDtcs(): String = send("04")

    /** Sends an arbitrary raw command (custom AT command, or a raw OBD mode+PID/DID hex string). */
    suspend fun sendRaw(command: String): String = send(command)
}
