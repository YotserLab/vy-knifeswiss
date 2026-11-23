package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.R
import com.example.Res

@Entity(tableName = "smart_alarms")
data class SmartAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val label: String = "Alarm",
    val soundName: String = Res.get(R.string.default_buzzer), // "Default Buzzer", "Ocean Breeze", "Gentle Rain", "Classic Bell"
    val isGradualVolume: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozeCount: Int = 3
)

@Dao
interface SmartAlarmDao {
    @Query("SELECT * FROM smart_alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<SmartAlarm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: SmartAlarm): Long

    @Update
    suspend fun updateAlarm(alarm: SmartAlarm)

    @Query("DELETE FROM smart_alarms WHERE id = :alarmId")
    suspend fun deleteAlarm(alarmId: Long)
}
