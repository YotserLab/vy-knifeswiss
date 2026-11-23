package com.example.droidlink.server

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.example.R
import com.example.Res

/**
 * Record representing a client device that connected to this DroidLink server.
 */
data class ConnectedClientRecord( // DLK-R12
    val id: String = UUID.randomUUID().toString(),
    val ip: String,
    val connectedAt: Long = System.currentTimeMillis(),
    val disconnectedAt: Long? = null,
    val authType: String = Res.get(R.string.senha) // "Fixed Password" or "Temporary Password"
) {
    val durationSeconds: Long
        get() = if (disconnectedAt != null && disconnectedAt >= connectedAt) {
            (disconnectedAt - connectedAt) / 1000
        } else {
            0L
        }
}

/**
 * Persistent storage for client connection history on the DroidLink server.
 * Uses private SharedPreferences with JSON serialization.
 */
object DroidLinkConnectionHistory {
    private const val TAG = "DroidLinkHistory"
    private const val PREFS_NAME = "droidlink_server_prefs"
    private const val KEY_HISTORY = "connected_clients_history"
    private const val MAX_ENTRIES = 50

    private val lock = Any()

    fun getHistory(context: Context): List<ConnectedClientRecord> = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                ConnectedClientRecord(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    ip = obj.getString("ip"),
                    connectedAt = obj.getLong("connectedAt"),
                    disconnectedAt = if (obj.has("disconnectedAt") && !obj.isNull("disconnectedAt")) {
                        obj.getLong("disconnectedAt")
                    } else null,
                    authType = obj.optString("authType", Res.get(R.string.senha))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse connection history", e)
            emptyList()
        }
    }

    /**
     * Records a new client connection and returns the record ID.
     */
    fun recordConnection(context: Context, ip: String, authType: String): String = synchronized(lock) {
        val current = getHistory(context).toMutableList()
        val record = ConnectedClientRecord(
            ip = ip,
            connectedAt = System.currentTimeMillis(),
            authType = authType
        )
        current.add(0, record)
        val trimmed = current.take(MAX_ENTRIES)
        saveHistory(context, trimmed)
        return record.id
    }

    /**
     * Records disconnection time for an active connection record.
     */
    fun recordDisconnection(context: Context, recordId: String) = synchronized(lock) {
        val current = getHistory(context).map { record ->
            if (record.id == recordId && record.disconnectedAt == null) {
                record.copy(disconnectedAt = System.currentTimeMillis())
            } else {
                record
            }
        }
        saveHistory(context, current)
    }

    /**
     * Clears all connection history records.
     */
    fun clearHistory(context: Context) = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(context: Context, list: List<ConnectedClientRecord>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("ip", item.ip)
                    put("connectedAt", item.connectedAt)
                    if (item.disconnectedAt != null) {
                        put("disconnectedAt", item.disconnectedAt)
                    } else {
                        put("disconnectedAt", JSONObject.NULL)
                    }
                    put("authType", item.authType)
                }
                array.put(obj)
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save connection history", e)
        }
    }
}

