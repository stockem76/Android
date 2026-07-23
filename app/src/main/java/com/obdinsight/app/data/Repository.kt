package com.obdinsight.app.data

import com.obdinsight.app.bluetooth.RawIoEvent
import com.obdinsight.app.obd.ScanSink
import kotlinx.coroutines.flow.Flow

/**
 * Single point of persistence for a diagnostic session: the raw byte stream, decoded/unknown
 * PID exchanges, and AI-generated hypotheses about the unknown ones.
 */
class Repository(private val db: AppDatabase) {

    suspend fun startSession(deviceName: String, deviceAddress: String, transportKind: String, vehicleLabel: String): Long =
        db.sessionDao().insert(
            DiagSession(
                startedAtMillis = System.currentTimeMillis(),
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                transportKind = transportKind,
                vehicleLabel = vehicleLabel,
            )
        )

    suspend fun endSession(sessionId: Long) {
        db.sessionDao().get(sessionId)?.let {
            db.sessionDao().update(it.copy(endedAtMillis = System.currentTimeMillis()))
        }
    }

    fun observeSessions(): Flow<List<DiagSession>> = db.sessionDao().observeAll()

    suspend fun recordRawFrame(sessionId: Long, event: RawIoEvent) {
        db.rawFrameDao().insert(
            RawFrame(
                sessionId = sessionId,
                timestampMillis = event.timestampMillis,
                direction = event.direction.name,
                hex = event.hex,
                ascii = event.ascii,
            )
        )
    }

    fun observeRawFrames(sessionId: Long, limit: Int = 500): Flow<List<RawFrame>> =
        db.rawFrameDao().observeRecent(sessionId, limit)

    fun observeExchanges(sessionId: Long): Flow<List<PidExchange>> =
        db.pidExchangeDao().observeForSession(sessionId)

    fun observeUnknownExchanges(): Flow<List<PidExchange>> =
        db.pidExchangeDao().observeUnknown()

    suspend fun getExchange(id: Long): PidExchange? = db.pidExchangeDao().get(id)

    suspend fun getAllRawFrames(sessionId: Long): List<RawFrame> = db.rawFrameDao().getAllForSession(sessionId)

    suspend fun getAllExchanges(sessionId: Long): List<PidExchange> = db.pidExchangeDao().getAllForSession(sessionId)

    suspend fun saveAiAnalysis(analysis: AiAnalysis): Long = db.aiAnalysisDao().insert(analysis)

    fun observeAiAnalyses(): Flow<List<AiAnalysis>> = db.aiAnalysisDao().observeAll()

    suspend fun latestAnalysisFor(pidExchangeId: Long): AiAnalysis? = db.aiAnalysisDao().latestFor(pidExchangeId)

    /** A [ScanSink] that persists every exchange the scan engine reports for the given session. */
    fun scanSinkFor(sessionId: Long): ScanSink = object : ScanSink {
        override suspend fun onExchange(
            mode: Int?,
            pid: Int?,
            requestHex: String,
            responseRaw: String,
            decodedName: String?,
            decodedValue: Double?,
            decodedUnit: String?,
            isKnownStandardPid: Boolean,
        ) {
            db.pidExchangeDao().insert(
                PidExchange(
                    sessionId = sessionId,
                    timestampMillis = System.currentTimeMillis(),
                    mode = mode,
                    pid = pid,
                    requestHex = requestHex,
                    responseRaw = responseRaw,
                    decodedName = decodedName,
                    decodedValue = decodedValue,
                    decodedUnit = decodedUnit,
                    isKnownStandardPid = isKnownStandardPid,
                )
            )
        }
    }
}
