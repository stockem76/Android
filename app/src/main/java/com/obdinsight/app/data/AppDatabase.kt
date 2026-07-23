package com.obdinsight.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DiagSession::class, RawFrame::class, PidExchange::class, AiAnalysis::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun rawFrameDao(): RawFrameDao
    abstract fun pidExchangeDao(): PidExchangeDao
    abstract fun aiAnalysisDao(): AiAnalysisDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "obd_insight.db",
                ).build().also { instance = it }
            }
    }
}
