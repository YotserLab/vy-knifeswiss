package com.example.cast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.InternalHttpFileServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InternalHttpFileServerTest {

    private data class RawResponse(
        val code: Int,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        InternalHttpFileServer.stopServer()
    }

    private fun newFile(size: Int): File =
        File.createTempFile("cast_srv_test", ".bin").apply {
            writeBytes(ByteArray(size) { (it % 251).toByte() })
            deleteOnExit()
        }

    /** Raw-socket GET so we can send arbitrary headers (e.g. Range). */
    private fun rawGet(
        port: Int,
        path: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): RawResponse {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 5000)
            socket.soTimeout = 10_000
            val out = socket.getOutputStream()
            val sb = StringBuilder(
                "GET $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n"
            )
            extraHeaders.forEach { (k, v) -> sb.append("$k: $v\r\n") }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            out.flush()

            val buf = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            val input = socket.getInputStream()
            while (true) {
                val n = try {
                    input.read(tmp)
                } catch (e: Exception) {
                    break
                }
                if (n < 0) break
                buf.write(tmp, 0, n)
            }
            val bytes = buf.toByteArray()
            var headEnd = -1
            var i = 0
            while (i + 3 < bytes.size) {
                if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte() &&
                    bytes[i + 2] == '\r'.code.toByte() && bytes[i + 3] == '\n'.code.toByte()
                ) {
                    headEnd = i
                    break
                }
                i++
            }
            assertTrue("no header terminator in response", headEnd >= 0)
            val head = String(bytes, 0, headEnd, Charsets.ISO_8859_1)
            val lines = head.split("\r\n")
            val code = lines[0].split(" ").getOrNull(1)?.toIntOrNull() ?: -1
            val headers = lines.drop(1).mapNotNull {
                val idx = it.indexOf(':')
                if (idx < 0) null else
                    it.substring(0, idx).trim().lowercase() to it.substring(idx + 1).trim()
            }.toMap()
            return RawResponse(code, headers, bytes.copyOfRange(headEnd + 4, bytes.size))
        }
    }

    @Test
    fun `full GET returns configured mime type and Accept-Ranges`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(100)
        val token = InternalHttpFileServer.hostFile(file, mimeType = "video/mp4")

        val resp = rawGet(InternalHttpFileServer.port, "/download/$token")

        assertEquals(200, resp.code)
        assertEquals("video/mp4", resp.headers["content-type"])
        assertEquals("bytes", resp.headers["accept-ranges"])
        assertEquals("100", resp.headers["content-length"])
        assertEquals(100, resp.body.size)
        assertTrue(resp.body.contentEquals(file.readBytes()))
    }

    @Test
    fun `full GET without mime falls back to octet-stream`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(32)
        val token = InternalHttpFileServer.hostFile(file)

        val resp = rawGet(InternalHttpFileServer.port, "/download/$token")

        assertEquals(200, resp.code)
        assertEquals("application/octet-stream", resp.headers["content-type"])
        assertEquals("bytes", resp.headers["accept-ranges"])
        assertEquals(32, resp.body.size)
    }

    @Test
    fun `range bytes=10-19 returns 206 partial content`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(100)
        val token = InternalHttpFileServer.hostFile(file, mimeType = "video/mp4")

        val resp = rawGet(
            InternalHttpFileServer.port, "/download/$token",
            mapOf("Range" to "bytes=10-19")
        )

        assertEquals(206, resp.code)
        assertEquals("bytes 10-19/100", resp.headers["content-range"])
        assertEquals("10", resp.headers["content-length"])
        assertEquals("bytes", resp.headers["accept-ranges"])
        assertEquals(10, resp.body.size)
        assertTrue(resp.body.contentEquals(file.readBytes().copyOfRange(10, 20)))
    }

    @Test
    fun `range bytes=90- returns 206 with tail of file`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(100)
        val token = InternalHttpFileServer.hostFile(file, mimeType = "video/mp4")

        val resp = rawGet(
            InternalHttpFileServer.port, "/download/$token",
            mapOf("Range" to "bytes=90-")
        )

        assertEquals(206, resp.code)
        assertEquals("bytes 90-99/100", resp.headers["content-range"])
        assertEquals("10", resp.headers["content-length"])
        assertEquals(10, resp.body.size)
        assertTrue(resp.body.contentEquals(file.readBytes().copyOfRange(90, 100)))
    }

    @Test
    fun `range end is clamped to file length`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(100)
        val token = InternalHttpFileServer.hostFile(file)

        val resp = rawGet(
            InternalHttpFileServer.port, "/download/$token",
            mapOf("Range" to "bytes=95-9999")
        )

        assertEquals(206, resp.code)
        assertEquals("bytes 95-99/100", resp.headers["content-range"])
        assertEquals(5, resp.body.size)
    }

    @Test
    fun `unsatisfiable range returns 416`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(100)
        val token = InternalHttpFileServer.hostFile(file)

        val resp = rawGet(
            InternalHttpFileServer.port, "/download/$token",
            mapOf("Range" to "bytes=200-")
        )

        assertEquals(416, resp.code)
        assertEquals("bytes */100", resp.headers["content-range"])
        assertEquals(0, resp.body.size)
    }

    @Test
    fun `renew extends expiry beyond original ttl`() {
        InternalHttpFileServer.startServer(context)
        val file = newFile(16)
        val token = InternalHttpFileServer.hostFile(file, mimeType = "video/mp4", ttlMs = 1)

        assertTrue(InternalHttpFileServer.renew(token, ttlMs = 60_000))
        Thread.sleep(50) // past the original 1 ms ttl

        val resp = rawGet(InternalHttpFileServer.port, "/download/$token")
        assertEquals(200, resp.code)
        assertEquals(16, resp.body.size)
    }

    @Test
    fun `renew returns false for unknown or expired tokens`() {
        assertFalse(InternalHttpFileServer.renew("no-such-token"))
        val file = newFile(8)
        val token = InternalHttpFileServer.hostFile(file, ttlMs = 1)
        Thread.sleep(50) // token is now past its ttl
        assertFalse(InternalHttpFileServer.renew(token))
    }

    @Test
    fun `parseRange handles valid ranges`() {
        assertEquals(0L..99L, InternalHttpFileServer.parseRange("bytes=0-99", 100))
        assertEquals(10L..19L, InternalHttpFileServer.parseRange("bytes=10-19", 100))
        assertEquals(90L..99L, InternalHttpFileServer.parseRange("bytes=90-", 100))
        assertEquals(90L..99L, InternalHttpFileServer.parseRange("bytes=90-9999", 100))
        assertEquals(95L..99L, InternalHttpFileServer.parseRange("bytes=-5", 100))
        assertEquals(0L..99L, InternalHttpFileServer.parseRange("bytes=0-", 100))
    }

    @Test
    fun `parseRange returns null for malformed input`() {
        assertNull(InternalHttpFileServer.parseRange("bananas", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=abc-def", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=10", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=-", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=20-10", 100))
    }

    @Test
    fun `parseRange returns null when start beyond file length`() {
        assertNull(InternalHttpFileServer.parseRange("bytes=100-", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=200-300", 100))
        assertNull(InternalHttpFileServer.parseRange("bytes=0-10", 0))
    }
}
