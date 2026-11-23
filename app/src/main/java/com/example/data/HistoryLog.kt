package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_logs")
data class HistoryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val toolName: String,
    val resultSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)
