package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<HistoryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HistoryLog)

    @androidx.room.Delete
    suspend fun deleteLog(log: HistoryLog)

    @Query("DELETE FROM history_logs")
    suspend fun clearHistory()
}
