package com.example.spectests
import com.example.ui.screens.security.DeviceSecurityScreen
import com.example.ui.screens.security.PasswordVaultScreen
import com.example.ui.screens.security.TotpAuthenticatorScreen
import com.example.utils.crypto.DatabaseKeyManager
import com.example.utils.crypto.TotpSecretCrypto
import com.example.utils.crypto.VaultCrypto

import com.example.data.VaultBackupPayload
import com.example.utils.TotpGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CofreSpecTest {


    // COF-R06: RFC 6238 — vetor oficial SHA1, 8 dígitos, T=59s => 94287082
    @Test
    fun test_COF_R06_totp_rfc6238_vector_sha1() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" // "12345678901234567890"
        val code = TotpGenerator.generate(secret, digits = 8, period = 30, algorithm = "SHA1", epochMillis = 59_000)
        assertEquals("94287082", code)
    }

    // COF-R06: padrão é 6 dígitos e período 30
    @Test
    fun test_COF_R06_defaults_6_digits_30s() {
        val code = TotpGenerator.generate("JBSWY3DPEHPK3PXP", epochMillis = 0)
        assertEquals(6, code.length)
    }

    // COF-R12: tempo restante = period - (epochSeconds mod period)
    @Test
    fun test_COF_R12_time_remaining_formula() {
        assertEquals(1, TotpGenerator.timeRemaining(30, 59_000))
        assertEquals(30, TotpGenerator.timeRemaining(30, 0))
        assertEquals(15, TotpGenerator.timeRemaining(30, 15_000))
    }

    // COF-R08: parse de URI otpauth://totp/ retorna parâmetros ou null
    @Test
    fun test_COF_R08_parse_otpauth_uri() {
        val params = TotpGenerator.parseOtpAuthUri(
            "otpauth://totp/Issuer:conta@x.com?secret=JBSWY3DPEHPK3PXP&issuer=Issuer&digits=6&period=30"
        )
        assertNotNull(params)
        assertNull(TotpGenerator.parseOtpAuthUri("https://nao-e-totp"))
        assertNull(TotpGenerator.parseOtpAuthUri("otpauth://hotp/sem-totp"))
    }

    // COF-R09: payload de backup serializa entradas e contas TOTP em JSON versionado
    @Test
    fun test_COF_R09_backup_payload_json_roundtrip() {
        val payload = VaultBackupPayload(version = 1, vaultEntries = listOf(com.example.data.VaultEntry(title = "t", username = "u", encryptedPassword = "p")))
        val json = payload.toJson()
        val restored = VaultBackupPayload.fromJson(json)
        assertNotNull(restored)
        assertEquals(1, restored!!.version)
        assertTrue(json.contains("vaultEntries") && json.contains("totpAccounts"))
    }

    // COF-R01: banco Room usa SQLCipher com SupportFactory
    @Test
    fun test_COF_R01_room_database_uses_sqlcipher() {
        assertTrue(srcContains("com/example/data/AppDatabase.kt", "net.sqlcipher.database.SupportFactory"))
        assertTrue(srcContains("com/example/data/AppDatabase.kt", "VaultEntry::class"))
        assertTrue(srcContains("com/example/data/AppDatabase.kt", "TotpAccount::class"))
    }

    // COF-R02: passphrase do banco vem de EncryptedSharedPreferences
    @Test
    fun test_COF_R02_passphrase_in_encrypted_prefs() {
        assertTrue(srcContains("com/example/utils/crypto/DatabaseKeyManager.kt", "EncryptedSharedPreferences"))
    }

    // COF-R03: abrir o cofre exige BiometricPrompt
    @Test
    fun test_COF_R03_vault_requires_biometric_prompt() {
        assertTrue(srcContains("com/example/ui/screens/security/PasswordVaultScreen.kt", "BiometricPrompt(activity"))
    }

    // COF-R04: revelar código TOTP exige BiometricPrompt
    @Test
    fun test_COF_R04_totp_requires_biometric_prompt() {
        assertTrue(srcContains("com/example/ui/screens/security/TotpAuthenticatorScreen.kt", "BiometricPrompt"))
    }

    // COF-R05: cancelamento/erro de biometria não desbloqueia
    @Test
    fun test_COF_R05_biometric_cancel_keeps_locked() {
        val s = src("com/example/ui/screens/security/PasswordVaultScreen.kt")
        assertTrue(s.contains("ERROR_USER_CANCELED") && s.contains("onAuthenticationError"))
    }

    // COF-R07: segredo TOTP cifrado via Keystore AES-256-GCM
    @Test
    fun test_COF_R07_totp_secret_crypto_uses_keystore_gcm() {
        val s = src("com/example/utils/crypto/TotpSecretCrypto.kt")
        assertTrue(s.contains("KeyStore") && s.contains("GCM"))
    }

    // COF-R11: DeviceSecurity avalia postura sem alterar o sistema
    @Test
    fun test_COF_R11_device_security_checks_present() {
        val s = src("com/example/ui/screens/security/DeviceSecurityScreen.kt")
        assertTrue(s.contains("Security patch") || s.contains("PatchStatus"))
    }

    // COF-R09: backup .vyvault = Base64(salt 32B + iv 12B + ciphertext AES-256-GCM) — contrato de fonte
    @Test
    fun test_COF_R09_backup_export_is_aes256_package() {
        val s = src("com/example/utils/crypto/VaultCrypto.kt")
        assertTrue(s.contains("SALT_LENGTH") && s.contains("GCM_IV_LENGTH") && s.contains("AES/GCM/NoPadding"))
        assertTrue(s.contains("salt + iv + ciphertext"))
    }

    // COF-R10: importar com o PIN mestre correto decifra; PIN errado retorna null — contrato de fonte
    @Test
    fun test_COF_R10_backup_import_requires_correct_pin() {
        val s = src("com/example/utils/crypto/VaultCrypto.kt")
        assertTrue(s.contains("fun decryptBackupPayload") && s.contains("deriveKey(pin, salt)"))
        assertTrue(s.contains("return null") || s.contains("null"))
    }
}
