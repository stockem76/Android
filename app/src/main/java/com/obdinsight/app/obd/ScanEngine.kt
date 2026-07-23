package com.obdinsight.app.obd

/** Receives every request/response exchange the scan engine makes, decoded or not. */
interface ScanSink {
    suspend fun onExchange(
        mode: Int?,
        pid: Int?,
        requestHex: String,
        responseRaw: String,
        decodedName: String?,
        decodedValue: Double?,
        decodedUnit: String?,
        isKnownStandardPid: Boolean,
    )
}

/**
 * Drives the actual scanning: discovering which standard PIDs this ECU supports, polling them,
 * and probing arbitrary manufacturer-specific PIDs/DIDs a user (or the AI analyzer) wants to
 * investigate. Every exchange - decoded or not - is reported to [ScanSink], which is what
 * satisfies "scan the outputs, record the PIDs, hex, messages": nothing is discarded just
 * because we don't yet know what it means.
 */
class ScanEngine(private val session: Elm327Session, private val sink: ScanSink) {

    /** Finds every Mode 01 PID this ECU reports as supported, then reads each one once. */
    suspend fun quickScan(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val supported = session.discoverSupportedPids()
        val known = supported.filter { StandardPids.BY_PID.containsKey(it) }.sorted()
        known.forEachIndexed { index, pid ->
            reportMode01(pid)
            onProgress(index + 1, known.size)
        }
    }

    /** Re-reads a fixed set of PIDs once (used to drive a live-updating dashboard). */
    suspend fun pollOnce(pids: List<Int>) {
        for (pid in pids) reportMode01(pid)
    }

    /**
     * Probes one arbitrary mode+PID/DID combination - e.g. a manufacturer-specific Mode 22 DID
     * a user found in a forum post, or one the AI analyzer suggested trying. Deliberately not
     * pre-seeded with a guessed list of "likely" VAG DIDs: any such list would be unverified for
     * this exact ECU and better surfaced through the AI + web-search lookup than hardcoded here.
     */
    suspend fun probeCustom(mode: Int, pidOrDidHex: String) {
        val cmd = "%02X".format(mode) + pidOrDidHex
        val raw = session.sendRaw(cmd)
        sink.onExchange(
            mode = mode,
            pid = pidOrDidHex.toIntOrNull(16),
            requestHex = cmd,
            responseRaw = raw,
            decodedName = null,
            decodedValue = null,
            decodedUnit = null,
            isKnownStandardPid = false,
        )
    }

    private suspend fun reportMode01(pid: Int) {
        val (result, raw) = session.readMode01Pid(pid)
        val requestHex = "01%02X".format(pid)
        when (result) {
            is ObdResult.Decoded -> sink.onExchange(
                1, pid, requestHex, raw, result.name, result.value, result.unit, true,
            )
            is ObdResult.RawUnknown -> sink.onExchange(
                1, pid, requestHex, raw, null, null, null, false,
            )
            is ObdResult.Error -> sink.onExchange(
                1, pid, requestHex, raw, null, null, null, false,
            )
        }
    }
}
