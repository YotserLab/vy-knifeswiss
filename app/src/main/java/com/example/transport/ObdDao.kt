package com.example.transport

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "obd_sessions")
data class ObdSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val vehicleName: String,
    val maxRpm: Int,
    val maxSpeedKmh: Int,
    val distanceKm: Float,
    val fuelConsumedL: Float
)

@Entity(tableName = "obd_log_entries")
data class ObdLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val rpm: Int,
    val speedKmh: Int,
    val engineLoadPct: Float,
    val coolantTempC: Int,
    val voltage: Float
)

@Dao
interface ObdDao {
    @Insert
    suspend fun insertSession(session: ObdSessionEntity): Long

    @Query("UPDATE obd_sessions SET endTimeMs = :endTimeMs, maxRpm = :maxRpm, maxSpeedKmh = :maxSpeedKmh, distanceKm = :distanceKm, fuelConsumedL = :fuelConsumedL WHERE id = :sessionId")
    suspend fun updateSession(sessionId: Long, endTimeMs: Long, maxRpm: Int, maxSpeedKmh: Int, distanceKm: Float, fuelConsumedL: Float)

    @Insert
    suspend fun insertLogEntry(entry: ObdLogEntryEntity)

    @Query("SELECT * FROM obd_sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<ObdSessionEntity>>

    @Query("SELECT * FROM obd_log_entries WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getLogsForSession(sessionId: Long): List<ObdLogEntryEntity>
}
