package com.obdinsight.app.obd

/** Result of asking the ELM327 for one PID/DID. */
sealed class ObdResult {
    data class Decoded(val value: Double, val unit: String, val name: String) : ObdResult()
    data class RawUnknown(val bytes: List<Int>) : ObdResult()
    data class Error(val message: String) : ObdResult()
}

object ObdDecoder {

    private val ERROR_TOKENS = setOf(
        "NO DATA", "STOPPED", "UNABLE TO CONNECT", "?", "SEARCHING...",
        "CAN ERROR", "BUS INIT: ERROR", "BUFFER FULL", "DATA ERROR",
        "ERR", "OK", // "OK" is a valid AT-command ack, not data, but never a PID response
    )

    /**
     * Parses an ELM327 response into a flat list of byte values. Takes the first data-looking
     * line; with headers off (ATH0) and CAN auto-formatting on (ATCAF1, the ELM327 default)
     * multi-frame responses already arrive reassembled as one logical line of hex byte pairs.
     */
    fun parseBytes(raw: String): List<Int>? {
        val line = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.uppercase() !in ERROR_TOKENS }
            ?: return null

        val tokens = line.split(Regex("\\s+"))
        val bytes = tokens.mapNotNull { tok -> tok.toIntOrNull(16) }
        return bytes.ifEmpty { null }
    }

    /** Decodes a Mode 01 response (request "01XX") given the raw ELM327 reply text. */
    fun decodeMode01(pid: Int, raw: String): ObdResult {
        val bytes = parseBytes(raw) ?: return ObdResult.Error(raw.trim().ifEmpty { "no response" })
        // Expect response mode byte 0x41 and the echoed PID, then the data bytes.
        val dataStart = when {
            bytes.size >= 2 && bytes[0] == 0x41 && bytes[1] == pid -> 2
            else -> 0 // some adapters/ECUs omit the echo when in a terse mode - use raw bytes as-is
        }
        val data = bytes.drop(dataStart)
        val known = StandardPids.BY_PID[pid]
        return if (known != null && data.size >= known.minBytes) {
            ObdResult.Decoded(known.decode(data), known.unit, known.name)
        } else {
            ObdResult.RawUnknown(bytes)
        }
    }

    /** Parses one of the four "PIDs supported" bitmask responses (01 00 / 01 20 / 01 40 / 01 60). */
    fun decodeSupportedBitmask(requestPid: Int, raw: String): Set<Int> {
        val bytes = parseBytes(raw) ?: return emptySet()
        val dataStart = if (bytes.size >= 2 && bytes[0] == 0x41 && bytes[1] == requestPid) 2 else 0
        val data = bytes.drop(dataStart)
        if (data.size < 4) return emptySet()
        val supported = mutableSetOf<Int>()
        var bitIndex = 1
        for (byteIdx in 0 until 4) {
            val byteVal = data[byteIdx]
            for (bit in 7 downTo 0) {
                if ((byteVal shr bit) and 0x1 == 1) {
                    supported += requestPid + bitIndex
                }
                bitIndex++
            }
        }
        return supported
    }
}
