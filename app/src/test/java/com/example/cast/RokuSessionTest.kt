package com.example.cast

import com.example.cast.control.RokuSession
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import com.example.cast.media.CastMedia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import kotlin.concurrent.thread

class RokuSessionTest {

    /**
     * Multi-request single-shot TCP server: serves `responses` in order (one
     * per connection) and records raw request bytes. Daemon thread + accept
     * timeout so a regression can't hang the test worker.
     */
    private fun startServer(responses: List<String>): Pair<Int, () -> List<String>> {
        val server = ServerSocket(0)
        server.soTimeout = 10_000
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val t = thread(isDaemon = true) {
            try {
                for (i in responses.indices) {
                    try {
                        server.accept().use { socket ->
                            val input = socket.getInputStream()
                            val buf = ByteArrayOutputStream()
                            val tmp = ByteArray(4096)
                            var headerEnd = -1
                            var contentLength = 0
                            while (true) {
                                val n = input.read(tmp)
                                if (n < 0) break
                                buf.write(tmp, 0, n)
                                val text = buf.toString(Charsets.ISO_8859_1)
                                if (headerEnd < 0) {
                                    headerEnd = text.indexOf("\r\n\r\n")
                                    if (headerEnd >= 0) {
                                        contentLength = Regex("(?i)Content-Length: (\\d+)")
                                            .find(text)?.groupValues?.get(1)?.toInt() ?: 0
                                    }
                                }
                                if (headerEnd >= 0 && buf.size() >= headerEnd + 4 + contentLength) break
                            }
                            requests.add(buf.toString(Charsets.ISO_8859_1))
                            val out = socket.getOutputStream()
                            out.write(responses[i].toByteArray(Charsets.ISO_8859_1))
                            out.flush()
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }
            } finally {
                server.close()
            }
        }
        return server.localPort to { t.join(15_000); requests.toList() }
    }

    private fun device(port: Int) = CastDevice(
        id = "r1", name = "Roku", type = CastDeviceType.ROKU,
        ip = "127.0.0.1", port = port
    )

    private val media = CastMedia("/sdcard/v.mp4", "Filme", "video/mp4")

    @Test
    fun `load posts ecp launch path for media app`() = runBlocking {
        val (port, getRequests) = startServer(
            listOf("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        )
        val session = RokuSession(device(port))
        assertTrue(session.load(media, "http://h/v.mp4"))

        val req = getRequests().single()
        assertTrue(req.startsWith("POST /launch/15985?t=v&u=http%3A%2F%2Fh%2Fv.mp4&videoName=Filme HTTP/1.1\r\n"))
        assertTrue(req.contains("Host: 127.0.0.1:$port\r\n"))
    }

    @Test
    fun `ecp accepts 200 202 and 204`() = runBlocking {
        val (port, _) = startServer(
            listOf(
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n",
                "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n",
                "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n"
            )
        )
        val session = RokuSession(device(port))
        assertTrue(session.play())   // 200
        assertTrue(session.pause())  // 202
        assertTrue(session.stop())   // 204
    }

    @Test
    fun `ecp returns false on 500`() = runBlocking {
        val (port, _) = startServer(
            listOf("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n")
        )
        val session = RokuSession(device(port))
        assertFalse(session.play())
    }

    @Test
    fun `roku session does not support position info`() {
        assertFalse(RokuSession(device(8060)).supportsPositionInfo)
    }

    @Test
    fun `keypress paths are sent for play pause and stop`() = runBlocking {
        val (port, getRequests) = startServer(
            List(3) { "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n" }
        )
        val session = RokuSession(device(port))
        session.play()
        session.pause()
        session.stop()

        val requests = getRequests()
        assertEquals(3, requests.size)
        assertTrue(requests[0].startsWith("POST /keypress/Play "))
        assertTrue(requests[1].startsWith("POST /keypress/Play "))
        assertTrue(requests[2].startsWith("POST /keypress/Back "))
    }
}
