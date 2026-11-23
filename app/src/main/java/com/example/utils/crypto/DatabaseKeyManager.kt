package com.example.utils.crypto

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.security.SecureRandom

/**
 * Generates and retrieves a SQLCipher passphrase stored in EncryptedSharedPreferences. COF-R02
 * The passphrase never leaves the device in plain text; it is encrypted at rest.
 */
object DatabaseKeyManager {

    private const val PREFS_KEY = "sqlcipher_passphrase"

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val existing = SecurePrefs.getString(context, PREFS_KEY, null)
        if (existing != null) {
            return SQLiteDatabase.getBytes(existing.toCharArray())
        }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        val random = SecureRandom()
        val password = CharArray(32)
        for (i in password.indices) {
            password[i] = chars[random.nextInt(chars.length)]
        }
        val str = String(password)
        password.fill('\u0000')
        SecurePrefs.putString(context, PREFS_KEY, str)
        return SQLiteDatabase.getBytes(str.toCharArray())
    }
}
