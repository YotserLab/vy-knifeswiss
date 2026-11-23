package com.example.spec

import androidx.test.platform.app.InstrumentationRegistry
import com.example.utils.crypto.TotpSecretCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CofreInstrumentedSpecTest {

    // COF-R07: segredo TOTP cifrado/decifrado via Android Keystore real (AES-256-GCM)
    @Test
    fun test_COF_R07_totp_secret_crypto_roundtrip() {
        val secret = "JBSWY3DPEHPK3PXP"
        val encrypted = TotpSecretCrypto.encryptSecret(secret)
        assertTrue(encrypted != secret)
        assertEquals(secret, TotpSecretCrypto.decryptSecret(encrypted))
    }
}
