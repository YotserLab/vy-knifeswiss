package com.example.spectests

import com.example.droidlink.protocol.FrameType
import com.example.droidlink.protocol.SessionCrypto
import com.example.droidlink.protocol.WireFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DroidLinkSpecTest {

    // DLK-R01: header de 10 bytes version(1)+type(1)+requestId(4)+payloadLen(4)
    @Test
    fun test_DLK_R01_frame_header_is_10_bytes() {
        val frame = WireFrame(WireFrame.PROTOCOL_VERSION, FrameType.HEARTBEAT, 7, byteArrayOf(1, 2, 3))
        val encoded = WireFrame.encode(frame)
        assertEquals(10 + 3, encoded.size)
        assertEquals(WireFrame.PROTOCOL_VERSION, encoded[0])
        assertEquals(FrameType.HEARTBEAT.code, encoded[1])
        val buf = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buf.position(2)
        assertEquals(7, buf.int)
        assertEquals(3, buf.int)
    }

    // DLK-R02: versão de protocolo é 1; decode rejeita versão diferente
    @Test
    fun test_DLK_R02_decode_rejects_wrong_version() {
        assertEquals(1.toByte(), WireFrame.PROTOCOL_VERSION)
        val frame = WireFrame(2, FrameType.HEARTBEAT, 1, ByteArray(0))
        val raw = WireFrame.encode(frame)
        assertThrows(IllegalArgumentException::class.java) { WireFrame.decode(raw) }
    }

    // DLK-R03: payloadLen acima de 64MiB é rejeitado no decode
    @Test
    fun test_DLK_R03_decode_rejects_payload_over_64mib() {
        val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
            .put(WireFrame.PROTOCOL_VERSION)
            .put(FrameType.FILE_CHUNK.code)
            .putInt(1)
            .putInt(64 * 1024 * 1024 + 1)
            .array()
        assertThrows(IllegalArgumentException::class.java) { WireFrame.decode(header) }
    }

    // DLK-R04: FrameType mapeado por código byte; código desconhecido falha
    @Test
    fun test_DLK_R04_frame_type_roundtrip_by_code() {
        FrameType.entries.forEach { assertEquals(it, FrameType.fromCode(it.code)) }
        assertThrows(IllegalArgumentException::class.java) { FrameType.fromCode(0x7F.toByte()) }
    }

    // DLK-R05: PBKDF2-HMAC-SHA256, 100 mil iterações, chave AES de 256 bits
    @Test
    fun test_DLK_R05_derived_key_is_aes_256_deterministic() {
        val salt = SessionCrypto.generateSalt()
        val k1 = SessionCrypto.deriveKey("senha", salt)
        val k2 = SessionCrypto.deriveKey("senha", salt)
        assertEquals("AES", k1.algorithm)
        assertEquals(32, k1.encoded.size)
        assertArrayEquals(k1.encoded, k2.encoded)
    }

    // DLK-R06: AES/GCM/NoPadding com IV de 12 bytes prefixado ao ciphertext
    @Test
    fun test_DLK_R06_aes_gcm_roundtrip_with_12_byte_iv() {
        val key = SessionCrypto.deriveKey("senha", SessionCrypto.generateSalt())
        val plain = "payload secreto".toByteArray()
        val encrypted = SessionCrypto.encrypt(key, plain)
        assertTrue(encrypted.size >= 12 + plain.size + 16) // iv + data + tag
        val encrypted2 = SessionCrypto.encrypt(key, plain)
        assertNotEquals(encrypted.toList(), encrypted2.toList()) // IV aleatório
        assertArrayEquals(plain, SessionCrypto.decrypt(key, encrypted))
    }

    // DLK-R07: HMAC-SHA256 sobre nonce valida sessão; hmac adulterado falha
    @Test
    fun test_DLK_R07_verify_hmac_accepts_valid_rejects_tampered() {
        val key = SessionCrypto.deriveKey("senha", SessionCrypto.generateSalt())
        val nonce = SessionCrypto.generateNonce()
        val hmac = SessionCrypto.computeHmac(key, nonce)
        assertTrue(SessionCrypto.verifyHmac(key, nonce, hmac))
        val tampered = hmac.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        assertTrue(!SessionCrypto.verifyHmac(key, nonce, tampered))
    }

    // DLK-R08: nonce e salt têm 16 bytes e são aleatórios
    @Test
    fun test_DLK_R08_nonce_and_salt_are_16_random_bytes() {
        assertEquals(16, SessionCrypto.generateNonce().size)
        assertEquals(16, SessionCrypto.generateSalt().size)
        assertNotEquals(SessionCrypto.generateNonce().toList(), SessionCrypto.generateNonce().toList())
    }

    // DLK-R12: histórico persiste ConnectedClientRecord por conexão
    @Test
    fun test_DLK_R12_connection_record_is_data_class() {
        val record = com.example.droidlink.server.ConnectedClientRecord::class.java
        assertTrue(record.declaredMethods.any { it.name == "component1" })
    }
}

// DLK-R09: porta configurável com padrão 8989
class DroidLinkPortSpecTest {
    @org.junit.Test
    fun test_DLK_R09_default_port_8989() {
        org.junit.Assert.assertTrue(srcContains("com/example/droidlink/server/DroidLinkServerService.kt", "8989"))
    }
}

// DLK-R10: cliente aceita IP local e Tailscale 100.x
class DroidLinkConnectSpecTest {
    @org.junit.Test
    fun test_DLK_R10_ip_local_tailscale() {
        val s = src("com/example/droidlink/ui/DroidLinkConnectScreen.kt")
        org.junit.Assert.assertTrue(s.contains("suporta_ip_local"))
        org.junit.Assert.assertTrue(java.io.File("src/main/res/values/strings.xml").readText().contains("100.x"))
    }
}

// DLK-R11: input remoto injetado via AccessibilityService
class DroidLinkInputSpecTest {
    @org.junit.Test
    fun test_DLK_R11_accessibility_injection() {
        val s = src("com/example/droidlink/server/RemoteInputAccessibilityService.kt")
        org.junit.Assert.assertTrue(s.contains("AccessibilityService"))
        org.junit.Assert.assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("RemoteInputAccessibilityService"))
    }
}

// DLK-R13: áudio capturado e transmitido ao cliente
class DroidLinkAudioSpecTest {
    @org.junit.Test
    fun test_DLK_R13_audio_capture() {
        org.junit.Assert.assertTrue(srcContains("com/example/droidlink/server/DroidLinkAudioCapture.kt", "AudioRecord") ||
            srcContains("com/example/droidlink/server/DroidLinkAudioCapture.kt", "MediaProjection"))
    }
}

// DLK-R14: autenticação dual — efêmera + senha fixa (SecurePrefs)
class DroidLinkDualAuthSpecTest {
    @org.junit.Test
    fun test_DLK_R14_dual_auth() {
        val s = src("com/example/droidlink/server/DroidLinkServerService.kt")
        org.junit.Assert.assertTrue(s.contains("ephemeralKey") && s.contains("fixedKey"))
        org.junit.Assert.assertTrue(s.contains("verifyHmac(eph") && s.contains("verifyHmac(fix"))
        org.junit.Assert.assertTrue(srcContains("com/example/droidlink/server/DroidLinkServerViewModel.kt", "PREF_FIXED_PASSWORD"))
        org.junit.Assert.assertTrue(srcContains("com/example/droidlink/server/DroidLinkServerViewModel.kt", "SecurePrefs"))
    }
}

// DLK-R15: session settings — audio, vídeo e permissões remotas granulares
class DroidLinkSettingsSpecTest {
    @org.junit.Test
    fun test_DLK_R15_session_settings() {
        val s = src("com/example/droidlink/model/DroidLinkSettings.kt")
        org.junit.Assert.assertTrue(s.contains("qualityMode") && s.contains("targetFps"))
        org.junit.Assert.assertTrue(s.contains("allowRemoteInput") && s.contains("allowSystemActions"))
        org.junit.Assert.assertTrue(s.contains("allowFileTransfer") && s.contains("keepScreenAwake"))
        org.junit.Assert.assertTrue(s.contains("transmitAudio") && s.contains("clientVolume"))
    }
}
