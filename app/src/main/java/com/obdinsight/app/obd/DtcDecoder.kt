package com.obdinsight.app.obd

/** Decodes Mode 03 (stored) / Mode 07 (pending) diagnostic trouble code responses. */
object DtcDecoder {

    private val LETTERS = charArrayOf('P', 'C', 'B', 'U')

    /** Turns the raw byte stream of a Mode 03/07 response into standard "P0301"-style codes. */
    fun decode(raw: String): List<String> {
        val bytes = ObdDecoder.parseBytes(raw) ?: return emptyList()
        // Response starts with 0x43 (mode 03) or 0x47 (mode 07) then a count byte, then 2-byte
        // codes. Be lenient about which adapters include/omit the mode+count prefix.
        val start = when {
            bytes.size >= 2 && (bytes[0] == 0x43 || bytes[0] == 0x47) -> 2
            else -> 0
        }
        val codes = mutableListOf<String>()
        var i = start
        while (i + 1 < bytes.size) {
            val a = bytes[i]
            val b = bytes[i + 1]
            i += 2
            if (a == 0 && b == 0) continue
            val value = (a shl 8) or b
            val letter = LETTERS[(value shr 14) and 0x03]
            val firstDigit = (value shr 12) and 0x03
            val rest = "%03X".format(value and 0x0FFF)
            codes += "$letter$firstDigit$rest"
        }
        return codes
    }
}
