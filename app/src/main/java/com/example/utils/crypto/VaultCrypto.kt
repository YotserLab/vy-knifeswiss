package com.example.utils.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.example.R
import com.example.Res

/**
 * VaultCrypto — AES-256-GCM encryption with PBKDF2WithHmacSHA256 key derivation
 * Data is stored locally, salt persisted in SharedPreferences (not encrypted).
 */
object VaultCrypto {

    private const val PREFS_NAME = "vault_crypto_prefs"
    private const val KEY_SALT = "vault_salt"
    private const val KEY_PIN_HASH = "vault_pin_hash"
    private const val KEY_HASH_SALT = "vault_hash_salt"
    private const val KEY_IS_SETUP = "vault_is_setup"

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12  // bytes
    private const val SALT_LENGTH = 32    // bytes

    // ── Setup & PIN management ──────────────────────────────────────────

    fun isVaultSetup(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IS_SETUP, false)

    /**
     * Set the master PIN for the first time (or reset).
     * Generates new salts and stores the PIN hash.
     */
    fun setupPin(context: Context, pin: String) {
        val prefs = getPrefs(context)
        val encSalt = generateSalt()
        val hashSalt = generateSalt()
        val pinHash = hashPin(pin, hashSalt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(encSalt, Base64.NO_WRAP))
            .putString(KEY_HASH_SALT, Base64.encodeToString(hashSalt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, pinHash)
            .putBoolean(KEY_IS_SETUP, true)
            .apply()
    }

    /** Returns true if the provided PIN matches the stored hash. */
    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getPrefs(context)
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val hashSalt = Base64.decode(prefs.getString(KEY_HASH_SALT, null) ?: return false, Base64.NO_WRAP)
        return hashPin(pin, hashSalt) == storedHash
    }

    /** Reset all vault crypto state (clears everything). */
    fun resetVault(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    // ── Encryption / Decryption ─────────────────────────────────────────

    /**
     * Encrypts [plaintext] using AES-256-GCM.
     * Returns a Base64-encoded string containing: IV (12 bytes) + ciphertext + GCM tag.
     */
    fun encrypt(plaintext: String, pin: String, context: Context): String {
        val key = deriveKey(pin, getEncSalt(context))
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded string previously produced by [encrypt].
     * Returns null if decryption fails (wrong PIN or corrupted data).
     */
    fun decrypt(encoded: String, pin: String, context: Context): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val key = deriveKey(pin, getEncSalt(context))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // ── Backup Payload Criptografado (.vyvault) ─────────────────────────

    /**
     * Encrypts a full backup payload string (JSON) into a standalone Base64 package containing:
     * Salt (32 bytes) + IV (12 bytes) + Ciphertext.
     * Portable across devices using the Master PIN.
     */
    fun encryptBackupPayload(jsonPayload: String, pin: String): String { // COF-R09
        val salt = generateSalt()
        val key = deriveKey(pin, salt)
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))
        val combined = salt + iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a standalone Base64 backup package produced by [encryptBackupPayload].
     * Returns null if decryption fails (wrong PIN or corrupted payload).
     */
    fun decryptBackupPayload(encodedBackup: String, pin: String): String? { // COF-R10
        return try {
            val combined = Base64.decode(encodedBackup, Base64.NO_WRAP)
            val salt = combined.copyOfRange(0, SALT_LENGTH)
            val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, combined.size)
            val key = deriveKey(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun deriveKey(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    private fun generateSalt(): ByteArray =
        ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }

    private fun getEncSalt(context: Context): ByteArray {
        val str = getPrefs(context).getString(KEY_SALT, null)
            ?: throw IllegalStateException(Res.get(R.string.vault_not_set_up))
        return Base64.decode(str, Base64.NO_WRAP)
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
