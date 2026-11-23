package com.example.droidlink.protocol

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic primitives for DroidLink Remote sessions.
 *
 * Security model:
 *  - Keys are derived with PBKDF2-HMAC-SHA256 (100 000 iterations, 256-bit output).
 *  - Session payload encryption uses AES-256/GCM/NoPadding with a random 12-byte IV.
 *  - Handshake authentication uses HMAC-SHA256 over the server nonce.
 *  - All random material is sourced from [SecureRandom].
 *
 * IMPORTANT: This object never logs, stores, or exposes raw passwords or keys.
 */
object SessionCrypto {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000 // DLK-R05
    private const val KEY_LENGTH_BITS = 256

    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12 // DLK-R06
    private const val GCM_TAG_LENGTH_BITS = 128 // DLK-R06

    private const val HMAC_ALGORITHM = "HmacSHA256"

    private const val NONCE_LENGTH_BYTES = 16 // DLK-R08
    private const val SALT_LENGTH_BYTES = 16 // DLK-R08

    private val secureRandom = SecureRandom()

    // -------------------------------------------------------------------------
    // Key Derivation
    // -------------------------------------------------------------------------

    /**
     * Derives a 256-bit AES key from [password] and [salt] using PBKDF2-HMAC-SHA256
     * with 100 000 iterations.
     *
     * The returned [SecretKey] holds only the derived key bytes — the password char
     * array is zeroed before the function returns.
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        require(salt.isNotEmpty()) { "Salt must not be empty" }
        val chars = password.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            spec.clearPassword()
            SecretKeySpec(keyBytes, AES_ALGORITHM)
        } finally {
            chars.fill('\u0000')
        }
    }

    // -------------------------------------------------------------------------
    // HMAC (Handshake authentication)
    // -------------------------------------------------------------------------

    /**
     * Computes HMAC-SHA256 of [nonce] using [key].
     * Used by the client to prove knowledge of the shared key during handshake.
     */
    fun computeHmac(key: SecretKey, nonce: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(nonce)
    }

    /**
     * Verifies that [hmac] equals the expected HMAC-SHA256 of [nonce] under [key].
     * Uses a constant-time comparison to prevent timing attacks.
     */
    fun verifyHmac(key: SecretKey, nonce: ByteArray, hmac: ByteArray): Boolean {
        val expected = computeHmac(key, nonce)
        return constantTimeEquals(expected, hmac) // DLK-R07
    }

    // -------------------------------------------------------------------------
    // AES-GCM Encryption / Decryption
    // -------------------------------------------------------------------------

    /**
     * Encrypts [plaintext] with AES-256/GCM/NoPadding using [key].
     *
     * Returns a concatenated byte array: **IV (12 bytes) || ciphertext+tag**.
     */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // Prepend IV so the receiver can re-create the GCMParameterSpec
        return iv + ciphertext
    }

    /**
     * Decrypts a payload produced by [encrypt].
     *
     * @param ciphertext Must be at least 12 bytes: **IV (12 bytes) || ciphertext+tag**.
     * @throws javax.crypto.AEADBadTagException if authentication fails (tampered data).
     * @throws IllegalArgumentException if [ciphertext] is too short.
     */
    fun decrypt(key: SecretKey, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > GCM_IV_LENGTH_BYTES) {
            "Ciphertext too short: ${ciphertext.size} bytes (minimum ${GCM_IV_LENGTH_BYTES + 1})"
        }
        val iv = ciphertext.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encryptedData = ciphertext.copyOfRange(GCM_IV_LENGTH_BYTES, ciphertext.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encryptedData)
    }

    // -------------------------------------------------------------------------
    // Random Material Generation
    // -------------------------------------------------------------------------

    /**
     * Generates a cryptographically secure random nonce of 16 bytes.
     * Used by the server for the AUTH_CHALLENGE step.
     */
    fun generateNonce(): ByteArray = ByteArray(NONCE_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    /**
     * Generates a cryptographically secure random salt of 16 bytes.
     * Must be stored alongside derived-key metadata; never alongside the password.
     */
    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Constant-time byte array comparison to prevent timing side-channels.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
