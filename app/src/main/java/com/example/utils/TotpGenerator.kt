package com.example.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TotpGenerator — RFC 6238 TOTP implementation (pure Kotlin, no external libs)
 * Supports SHA-1, SHA-256, SHA-512 algorithms.
 */
object TotpGenerator {

    /**
     * Generate a TOTP code for the given parameters.
     * @param secret Base32-encoded shared secret
     * @param digits Number of digits (6 or 8)
     * @param period Time step in seconds (default 30)
     * @param algorithm HMAC algorithm: "SHA1", "SHA256", or "SHA512"
     * @param epochMillis Optional epoch millis for testing (default = now)
     */
    fun generate(
        secret: String,
        digits: Int = 6,
        period: Int = 30,
        algorithm: String = "SHA1", // COF-R06
        epochMillis: Long = System.currentTimeMillis()
    ): String {
        val secretBytes = decodeBase32(secret.uppercase().replace(" ", "").replace("-", ""))
        val counter = epochMillis / 1000 / period
        val code = hotp(secretBytes, counter, digits, algorithm)
        return code.toString().padStart(digits, '0')
    }

    /**
     * Returns the number of seconds remaining in the current time window.
     */
    fun timeRemaining(period: Int = 30, epochMillis: Long = System.currentTimeMillis()): Int {
        val currentStep = (epochMillis / 1000) % period
        return (period - currentStep).toInt() // COF-R12
    }

    /**
     * Returns a 0..1 float representing progress within current window.
     */
    fun timeProgress(period: Int = 30, epochMillis: Long = System.currentTimeMillis()): Float {
        val currentStep = (epochMillis / 1000) % period
        return currentStep.toFloat() / period.toFloat()
    }

    /**
     * Parse an otpauth:// URI into its components.
     * Format: otpauth://totp/issuer:account?secret=XXX&issuer=YYY&algorithm=SHA1&digits=6&period=30
     */
    fun parseOtpAuthUri(uri: String): OtpAuthParams? { // COF-R08
        return try {
            if (!uri.startsWith("otpauth://totp/")) return null
            val withoutScheme = uri.removePrefix("otpauth://totp/")
            val qIndex = withoutScheme.indexOf('?')
            val label = if (qIndex >= 0) withoutScheme.substring(0, qIndex) else withoutScheme
            val queryString = if (qIndex >= 0) withoutScheme.substring(qIndex + 1) else ""
            val params = queryString.split('&').associate { part ->
                val eqIndex = part.indexOf('=')
                if (eqIndex >= 0) part.substring(0, eqIndex) to part.substring(eqIndex + 1)
                else part to ""
            }
            val secret = params["secret"] ?: return null
            val (issuer, account) = parseLabel(label, params["issuer"])
            OtpAuthParams(
                issuer = issuer,
                account = account,
                secret = secret,
                algorithm = params["algorithm"] ?: "SHA1",
                digits = params["digits"]?.toIntOrNull() ?: 6,
                period = params["period"]?.toIntOrNull() ?: 30
            )
        } catch (e: Exception) {
            null
        }
    }

    data class OtpAuthParams(
        val issuer: String,
        val account: String,
        val secret: String,
        val algorithm: String = "SHA1",
        val digits: Int = 6,
        val period: Int = 30
    )

    data class GoogleAuthMigrationPayload(
        val accounts: List<OtpAuthParams>,
        val version: Int = 1,
        val batchSize: Int = 1,
        val batchIndex: Int = 0,
        val batchId: Int = 0
    )

    /**
     * Parses a Google Authenticator migration URI (otpauth-migration://offline?data=...).
     * Decodes the Protobuf payload and returns a list of TOTP accounts.
     */
    fun parseGoogleAuthMigrationUri(uri: String): List<OtpAuthParams> {
        return parseGoogleAuthMigrationUriFull(uri).accounts
    }

    fun parseGoogleAuthMigrationUriFull(uri: String): GoogleAuthMigrationPayload {
        return try {
            if (!uri.startsWith("otpauth-migration://")) return GoogleAuthMigrationPayload(emptyList())
            val query = uri.substringAfter("?")
            val params = query.split('&').associate {
                val eq = it.indexOf('=')
                if (eq >= 0) it.substring(0, eq) to it.substring(eq + 1) else it to ""
            }
            val rawData = params["data"] ?: return GoogleAuthMigrationPayload(emptyList())
            val dataB64 = java.net.URLDecoder.decode(rawData, "UTF-8").replace(" ", "+")
            val bytes = android.util.Base64.decode(dataB64, android.util.Base64.DEFAULT)
            parseMigrationProtobufFull(bytes)
        } catch (e: Exception) {
            GoogleAuthMigrationPayload(emptyList())
        }
    }

    private fun parseMigrationProtobufFull(bytes: ByteArray): GoogleAuthMigrationPayload {
        val result = mutableListOf<OtpAuthParams>()
        var version = 1
        var batchSize = 1
        var batchIndex = 0
        var batchId = 0
        var pos = 0
        while (pos < bytes.size) {
            val tag = readVarint(bytes, pos)
            pos = tag.second
            if (pos > bytes.size) break
            val fieldNumber = (tag.first shr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()
            when (fieldNumber) {
                1 -> {
                    if (wireType == 2) {
                        val len = readVarint(bytes, pos)
                        pos = len.second
                        val length = len.first.toInt()
                        val end = (pos + length).coerceIn(pos, bytes.size)
                        val param = parseOtpParameters(bytes, pos, end)
                        if (param != null) result.add(param)
                        pos = end
                    } else {
                        pos = skipField(bytes, pos, wireType)
                    }
                }
                2 -> {
                    val v = readVarint(bytes, pos)
                    pos = v.second
                    version = v.first.toInt()
                }
                3 -> {
                    val v = readVarint(bytes, pos)
                    pos = v.second
                    batchSize = v.first.toInt()
                }
                4 -> {
                    val v = readVarint(bytes, pos)
                    pos = v.second
                    batchIndex = v.first.toInt()
                }
                5 -> {
                    val v = readVarint(bytes, pos)
                    pos = v.second
                    batchId = v.first.toInt()
                }
                else -> {
                    pos = skipField(bytes, pos, wireType)
                }
            }
        }
        return GoogleAuthMigrationPayload(
            accounts = result,
            version = version,
            batchSize = batchSize,
            batchIndex = batchIndex,
            batchId = batchId
        )
    }

    private fun parseOtpParameters(bytes: ByteArray, start: Int, end: Int): OtpAuthParams? {
        var pos = start
        var secretBytes: ByteArray? = null
        var name = ""
        var issuer = ""
        var algStr = "SHA1"
        var digits = 6
        while (pos < end) {
            val tag = readVarint(bytes, pos)
            pos = tag.second
            if (pos > end) break
            val fieldNumber = (tag.first shr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()
            when (fieldNumber) {
                1 -> {
                    val len = readVarint(bytes, pos)
                    pos = len.second
                    val length = len.first.toInt()
                    if (pos + length <= bytes.size) {
                        secretBytes = bytes.copyOfRange(pos, pos + length)
                        pos += length
                    }
                }
                2 -> {
                    val len = readVarint(bytes, pos)
                    pos = len.second
                    val length = len.first.toInt()
                    if (pos + length <= bytes.size) {
                        name = String(bytes, pos, length, Charsets.UTF_8)
                        pos += length
                    }
                }
                3 -> {
                    val len = readVarint(bytes, pos)
                    pos = len.second
                    val length = len.first.toInt()
                    if (pos + length <= bytes.size) {
                        issuer = String(bytes, pos, length, Charsets.UTF_8)
                        pos += length
                    }
                }
                4 -> {
                    val v = readVarint(bytes, pos)
                    pos = v.second
                    algStr = when (v.first.toInt()) {
                        2 -> "SHA256"
                        3 -> "SHA512"
                        else -> "SHA1"
                    }
                }
                5 -> {
                    val v = readVarint(bytes, pos)
                    pos = v.second
                    digits = if (v.first.toInt() == 2) 8 else 6
                }
                else -> {
                    pos = skipField(bytes, pos, wireType)
                }
            }
        }
        val secret = secretBytes?.let { encodeBase32(it) } ?: return null
        val (finalIssuer, finalAccount) = parseLabel(name, if (issuer.isNotBlank()) issuer else null)
        return OtpAuthParams(
            issuer = if (finalIssuer.isNotBlank()) finalIssuer else "2FA",
            account = finalAccount,
            secret = secret,
            algorithm = algStr,
            digits = digits,
            period = 30
        )
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < bytes.size) {
            val b = bytes[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 64) break
        }
        return result to pos
    }

    private fun skipField(bytes: ByteArray, start: Int, wireType: Int): Int {
        var pos = start
        when (wireType) {
            0 -> pos = readVarint(bytes, pos).second
            1 -> pos += 8
            2 -> {
                val len = readVarint(bytes, pos)
                pos = len.second + len.first.toInt()
            }
            5 -> pos += 4
            else -> pos++
        }
        return pos.coerceAtMost(bytes.size)
    }

    /** Base32 Encoder */
    fun encodeBase32(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var i = 0
        var index = 0
        var digit: Int
        var currByte: Int
        var nextByte: Int
        val base32 = StringBuilder((data.size * 8 + 4) / 5)
        while (i < data.size) {
            currByte = if (data[i] >= 0) data[i].toInt() else data[i].toInt() + 256
            if (index > 3) {
                nextByte = if (i + 1 < data.size) (if (data[i + 1] >= 0) data[i + 1].toInt() else data[i + 1].toInt() + 256) else 0
                digit = currByte and (0xFF shr index)
                index = (index + 5) % 8
                digit = digit shl index
                digit = digit or (nextByte shr (8 - index))
                i++
            } else {
                digit = (currByte shr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            base32.append(alphabet[digit])
        }
        return base32.toString()
    }

    // ── Internal ──────────────────────────────────────────────────────

    private fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: String): Int {
        val macAlgorithm = when (algorithm.uppercase()) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else     -> "HmacSHA1"
        }
        val counterBytes = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (c and 0xFF).toByte()
            c = c shr 8
        }
        val mac = Mac.getInstance(macAlgorithm)
        mac.init(SecretKeySpec(key, macAlgorithm))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                     ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                     ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                     (hash[offset + 3].toInt() and 0xFF)
        return binary % 10.0.pow(digits).toInt()
    }

    /** RFC 4648 Base32 decoder */
    private fun decodeBase32(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.trimEnd('=')
        var buffer = 0L
        var bitsLeft = 0
        val result = mutableListOf<Byte>()
        for (ch in cleaned) {
            val idx = alphabet.indexOf(ch)
            if (idx < 0) continue
            buffer = (buffer shl 5) or idx.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                result.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return result.toByteArray()
    }

    private fun parseLabel(label: String, issuerParam: String?): Pair<String, String> {
        val decoded = java.net.URLDecoder.decode(label, "UTF-8")
        val colonIdx = decoded.indexOf(':')
        return if (colonIdx >= 0) {
            val issuer = decoded.substring(0, colonIdx).trim()
        val account = decoded.substring(colonIdx + 1).trim()
            issuer to account
        } else {
            (issuerParam ?: decoded) to decoded
        }
    }
}
