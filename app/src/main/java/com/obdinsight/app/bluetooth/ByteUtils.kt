package com.obdinsight.app.bluetooth

/** Shared helpers for turning raw bytes into the hex/ASCII pair we log everywhere. */
object ByteUtils {

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }

    fun toPrintableAscii(bytes: ByteArray): String =
        bytes.map { b -> if (b in 32..126) b.toInt().toChar() else '.' }.joinToString("")

    /** ELM327-style dongles terminate every response with a '>' prompt character. */
    const val PROMPT_CHAR = '>'

    fun stripPromptAndWhitespace(raw: String): String =
        raw.replace(PROMPT_CHAR.toString(), "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
