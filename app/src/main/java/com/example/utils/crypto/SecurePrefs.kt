package com.example.utils.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure preference storage backed by Android Keystore (AES-256-GCM).
 * Values are encrypted at rest and never stored as plain text.
 */
object SecurePrefs {

    private const val PREFS_FILE = "vyknifeswiss_secure"
    private const val KEY_ALIAS = "vyknifeswiss_secure_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun get(context: Context): SharedPreferences {
        return prefs ?: synchronized(this) {
            prefs ?: context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).also { prefs = it }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteBuffer.allocate(iv.size + cipherText.size).apply {
            put(iv)
            put(cipherText)
        }.array()
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(cipherText: String): String? {
        return try {
            val key = getOrCreateKey()
            val bytes = Base64.decode(cipherText, Base64.NO_WRAP)
            if (bytes.size <= IV_LENGTH) return null
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val payload = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun getString(context: Context, key: String, default: String? = null): String? {
        val raw = get(context).getString(key, null) ?: return default
        return decrypt(raw)
    }

    fun putString(context: Context, key: String, value: String?) {
        if (value == null) {
            get(context).edit().remove(key).apply()
            return
        }
        get(context).edit().putString(key, encrypt(value)).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean {
        return getString(context, key, default.toString())?.toBoolean() ?: default
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        putString(context, key, value.toString())
    }

    fun getLong(context: Context, key: String, default: Long = 0L): Long {
        return getString(context, key, default.toString())?.toLongOrNull() ?: default
    }

    fun putLong(context: Context, key: String, value: Long) {
        putString(context, key, value.toString())
    }

    fun remove(context: Context, key: String) {
        get(context).edit().remove(key).apply()
    }

    /**
     * Legacy migration helper: move a plain SharedPreferences value into
     * encrypted storage and optionally remove it from the source.
     */
    fun migrateFrom(
        context: Context,
        source: SharedPreferences,
        key: String,
        removeAfter: Boolean = true
    ) {
        val value = source.getString(key, null) ?: return
        putString(context, key, value)
        if (removeAfter) source.edit().remove(key).apply()
    }
}
