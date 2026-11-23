package com.example.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allLogs: Flow<List<HistoryLog>> = historyDao.getAllLogs()

    suspend fun insert(log: HistoryLog) = historyDao.insertLog(log)

    suspend fun clear() = historyDao.clearHistory()
}
