package com.example.utils

import android.content.Context
import android.content.SharedPreferences

object UsageTracker {
    private const val PREFS_NAME = "vy_usage_tracker"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun trackUsage(context: Context, route: String) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(route, 0)
        prefs.edit().putInt(route, currentCount + 1).apply()
    }

    fun getUsageCount(context: Context, route: String): Int {
        return getPrefs(context).getInt(route, 0)
    }
}
