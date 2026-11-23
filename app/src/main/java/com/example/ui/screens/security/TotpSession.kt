package com.example.ui.screens.security

object TotpSession {
    var unlockedAt: Long = 0L
    private const val TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    fun isUnlocked(): Boolean {
        if (unlockedAt == 0L) return false
        if (System.currentTimeMillis() - unlockedAt > TIMEOUT_MS) {
            lock()
            return false
        }
        return true
    }

    fun unlock() {
        unlockedAt = System.currentTimeMillis()
    }

    fun lock() {
        unlockedAt = 0L
    }
}
