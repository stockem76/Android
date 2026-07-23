package com.obdinsight.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One connect-to-disconnect session, so logs and AI analyses can be grouped and exported per drive. */
@Entity(tableName = "sessions")
data class DiagSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    var endedAtMillis: Long? = null,
    val deviceName: String,
    val deviceAddress: String,
    val transportKind: String, // "BLE" or "CLASSIC"
    val vehicleLabel: String,
)

/**
 * Every single byte-level chunk sent or received on the wire, exactly as captured by the
 * transport layer - this is the unconditional "record the hex, messages" requirement, kept
 * independent of whether we understood what any of it meant.
 */
@Entity(tableName = "raw_frames")
data class RawFrame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val direction: String, // "TX" or "RX"
    val hex: String,
    val ascii: String,
)

/**
 * One logical request/response exchange (e.g. "01 0C" -> "41 0C 1A F8"), decoded if we
 * recognized the PID, raw otherwise. This is the "record the PIDs" requirement.
 */
@Entity(tableName = "pid_exchanges")
data class PidExchange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val mode: Int?,
    val pid: Int?,
    val requestHex: String,
    val responseRaw: String,
    val decodedName: String?,
    val decodedValue: Double?,
    val decodedUnit: String?,
    val isKnownStandardPid: Boolean,
)

/** Persisted result of asking Claude (with web search) to identify an unrecognized PID/DID. */
@Entity(tableName = "ai_analyses")
data class AiAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pidExchangeId: Long,
    val createdAtMillis: Long,
    val vehicleContext: String,
    val requestHex: String,
    val responseRaw: String,
    val hypothesisName: String?,
    val hypothesisUnit: String?,
    val hypothesisFormula: String?,
    val confidence: String?, // "high" / "medium" / "low"
    val reasoning: String,
    val sourcesJson: String, // JSON array of {title, url}
    val error: String? = null,
)
