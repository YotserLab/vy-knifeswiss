package com.example.utils.crypto

import com.example.BuildConfig
import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.example.R
import com.example.Res

/**
 * Experimental local-chat payload encryption. Uses a single pre-derived key.
 * This provides confidentiality against passive network capture, but because the
 * key is currently baked into the app, it is NOT resistant to a determined
 * attacker who extracts the binary. A future version should use a peer-to-peer
 * key agreement (e.g., X25519) or a user-supplied shared password.
 */
object LocalChatCrypto {

    private const val FIXED_PASSPHRASE = "VYKnIFE-SWISS-CHAT-KEY" // CHAT-R15
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private val key: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance(Res.get(R.string.sha_256)).digest(FIXED_PASSPHRASE.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, ALGORITHM)
    }

    fun encrypt(plainText: String): String? {
        return try {
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteBuffer.allocate(iv.size + cipherText.size).apply {
                put(iv)
                put(cipherText)
            }.array()
            Res.get(R.string.enc) + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
    }

    fun decrypt(cipherText: String): String? {
        if (!cipherText.startsWith(Res.get(R.string.enc))) return cipherText
        return try {
            val bytes = Base64.decode(cipherText.removePrefix(Res.get(R.string.enc)), Base64.NO_WRAP)
            if (bytes.size <= IV_LENGTH) return null
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val payload = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
    }
}
