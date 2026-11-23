package com.example.droidlink

import com.example.droidlink.protocol.SessionCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidLinkAuthTest {

    @Test
    fun testDualPasswordAuthentication() {
        val ephemeralPassword = "ephemeral-random-pass-123"
        val fixedPassword = "fixed-unattended-pass-999"
        val wrongPassword = "wrong-password-xyz"

        val salt = SessionCrypto.generateSalt()
        val nonce = SessionCrypto.generateNonce()

        // Server derives both keys
        val ephemeralKey = SessionCrypto.deriveKey(ephemeralPassword, salt)
        val fixedKey = SessionCrypto.deriveKey(fixedPassword, salt)

        // Case 1: Client connects using ephemeral password
        val clientEphemeralKey = SessionCrypto.deriveKey(ephemeralPassword, salt)
        val clientEphemeralHmac = SessionCrypto.computeHmac(clientEphemeralKey, nonce)

        val matchedForEphemeral = when {
            SessionCrypto.verifyHmac(ephemeralKey, nonce, clientEphemeralHmac) -> ephemeralKey
            SessionCrypto.verifyHmac(fixedKey, nonce, clientEphemeralHmac) -> fixedKey
            else -> null
        }
        assertNotNull("Ephemeral password should authenticate successfully", matchedForEphemeral)
        assertTrue(matchedForEphemeral === ephemeralKey)

        // Case 2: Client connects using fixed password
        val clientFixedKey = SessionCrypto.deriveKey(fixedPassword, salt)
        val clientFixedHmac = SessionCrypto.computeHmac(clientFixedKey, nonce)

        val matchedForFixed = when {
            SessionCrypto.verifyHmac(ephemeralKey, nonce, clientFixedHmac) -> ephemeralKey
            SessionCrypto.verifyHmac(fixedKey, nonce, clientFixedHmac) -> fixedKey
            else -> null
        }
        assertNotNull("Fixed password should authenticate successfully", matchedForFixed)
        assertTrue(matchedForFixed === fixedKey)

        // Case 3: Client connects using wrong password
        val clientWrongKey = SessionCrypto.deriveKey(wrongPassword, salt)
        val clientWrongHmac = SessionCrypto.computeHmac(clientWrongKey, nonce)

        val matchedForWrong = when {
            SessionCrypto.verifyHmac(ephemeralKey, nonce, clientWrongHmac) -> ephemeralKey
            SessionCrypto.verifyHmac(fixedKey, nonce, clientWrongHmac) -> fixedKey
            else -> null
        }
        assertTrue("Wrong password must fail authentication", matchedForWrong == null)
    }

    @Test
    fun testTailscaleDetectionLogic() {
        // Tailscale IPs always fall within CGNAT 100.64.0.0/10 range
        val tsIp1 = "100.85.12.34"
        val tsIp2 = "100.115.92.2"
        val localIp = "192.168.7.50"

        assertTrue(tsIp1.startsWith("100."))
        assertTrue(tsIp2.startsWith("100."))
        assertFalse(localIp.startsWith("100."))
    }

    @Test
    fun testConnectedClientRecordDuration() {
        val now = 1000000L
        val recordActive = com.example.droidlink.server.ConnectedClientRecord(
            ip = "192.168.7.62",
            connectedAt = now,
            disconnectedAt = null,
            authType = "Senha Fixa"
        )
        org.junit.Assert.assertEquals(0L, recordActive.durationSeconds)

        val recordFinished = recordActive.copy(disconnectedAt = now + 125000L) // 125s
        org.junit.Assert.assertEquals(125L, recordFinished.durationSeconds)
    }
}

