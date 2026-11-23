package com.example.utils.crypto

import com.example.BuildConfig
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.example.R
import com.example.Res

/**
 * TotpSecretCrypto — Hardware-backed encryption for TOTP secrets using Android KeyStore (AES-256-GCM).
 * Ensures that even if the raw SQLite database is extracted, secret keys cannot be read without the device's hardware key.
 */
object TotpSecretCrypto { // COF-R07
    private const val KEY_ALIAS = "vy_totp_keystore_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_SIZE = 12 // bytes
    private const val PREFIX = "ENC_KS_v1:"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts a raw Base32 secret using Android KeyStore AES-256-GCM.
     * Returns "ENC_KS_v1:<Base64(IV + Ciphertext)>"
     */
    fun encryptSecret(rawSecret: String): String {
        if (rawSecret.isBlank() || rawSecret.startsWith(PREFIX)) return rawSecret
        return try {
            val key = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(rawSecret.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            throw IllegalStateException(Res.get(R.string.falha_ao_criptografar_o_segredo_totp_via, (e.message)), e)
        }
    }

    /**
     * Decrypts a secret stored in DB.
     * If secret starts with PREFIX, decrypts using KeyStore key.
     * If secret does not start with PREFIX (unencrypted legacy secret), returns rawSecret directly.
     */
    fun decryptSecret(storedSecret: String): String {
        if (!storedSecret.startsWith(PREFIX)) return storedSecret
        return try {
            val encodedData = storedSecret.substring(PREFIX.length)
            val combined = Base64.decode(encodedData, Base64.NO_WRAP)
            if (combined.size <= IV_SIZE) return storedSecret
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val key = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            storedSecret
        }
    }
}
