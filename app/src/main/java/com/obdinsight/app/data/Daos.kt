package com.obdinsight.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: DiagSession): Long

    @Update
    suspend fun update(session: DiagSession)

    @Query("SELECT * FROM sessions ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<DiagSession>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): DiagSession?
}

@Dao
interface RawFrameDao {
    @Insert
    suspend fun insert(frame: RawFrame): Long

    @Query("SELECT * FROM raw_frames WHERE sessionId = :sessionId ORDER BY id DESC LIMIT :limit")
    fun observeRecent(sessionId: Long, limit: Int = 500): Flow<List<RawFrame>>

    @Query("SELECT * FROM raw_frames WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getAllForSession(sessionId: Long): List<RawFrame>

    @Query("SELECT * FROM raw_frames ORDER BY id ASC")
    suspend fun getAll(): List<RawFrame>
}

@Dao
interface PidExchangeDao {
    @Insert
    suspend fun insert(exchange: PidExchange): Long

    @Query("SELECT * FROM pid_exchanges WHERE sessionId = :sessionId ORDER BY id DESC")
    fun observeForSession(sessionId: Long): Flow<List<PidExchange>>

    @Query("SELECT * FROM pid_exchanges WHERE isKnownStandardPid = 0 ORDER BY id DESC")
    fun observeUnknown(): Flow<List<PidExchange>>

    @Query("SELECT * FROM pid_exchanges WHERE id = :id")
    suspend fun get(id: Long): PidExchange?

    @Query("SELECT * FROM pid_exchanges WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getAllForSession(sessionId: Long): List<PidExchange>
}

@Dao
interface AiAnalysisDao {
    @Insert
    suspend fun insert(analysis: AiAnalysis): Long

    @Query("SELECT * FROM ai_analyses ORDER BY id DESC")
    fun observeAll(): Flow<List<AiAnalysis>>

    @Query("SELECT * FROM ai_analyses WHERE pidExchangeId = :pidExchangeId ORDER BY id DESC LIMIT 1")
    suspend fun latestFor(pidExchangeId: Long): AiAnalysis?
}
