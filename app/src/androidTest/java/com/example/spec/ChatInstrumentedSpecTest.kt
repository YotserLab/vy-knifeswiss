package com.example.spec

import androidx.test.platform.app.InstrumentationRegistry
import com.example.service.InternalHttpFileServer
import com.example.utils.crypto.LocalChatCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

class ChatInstrumentedSpecTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    // CHAT-R15: texto é cifrado AES-256-GCM com prefixo ENC: e decifra de volta
    @Test
    fun test_CHAT_R15_local_chat_crypto_roundtrip() {
        val plain = "mensagem secreta"
        val enc = LocalChatCrypto.encrypt(plain)
        assertNotNull(enc)
        assertTrue(enc!!.startsWith("ENC:"))
        assertEquals(plain, LocalChatCrypto.decrypt(enc))
    }

    // CHAT-R08/R09: arquivo servido via HTTP com Content-Length exato
    @Test
    fun test_CHAT_R08_R09_served_file_content_length() {
        InternalHttpFileServer.startServer(ctx)
        try {
            val file = File(ctx.cacheDir, "spec_chat_file.txt").apply { writeText("12345") }
            val token = InternalHttpFileServer.hostFile(file, mimeType = "text/plain", ttlMs = 60_000)
            val conn = URL("http://127.0.0.1:${InternalHttpFileServer.port}/download/$token").openConnection()
            assertEquals(5, conn.contentLength)
        } finally {
            InternalHttpFileServer.stopServer()
        }
    }
}
