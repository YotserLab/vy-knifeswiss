package com.example.cast

import com.example.cast.control.LanHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LanHttpClientTest {

    /**
     * Runs a single-shot TCP server (daemon thread + accept timeout so a
     * regression can't hang the test worker); returns port and a holder for
     * the raw request bytes.
     */
    private fun startServer(responseBytes: ByteArray): Pair<Int, () -> String> {
        val server = ServerSocket(0)
        server.soTimeout = 10_000
        val requestHolder = arrayOf("")
        val t = thread(isDaemon = true) {
            try {
                server.accept().use { socket: Socket ->
                    val input = socket.getInputStream()
                    val buf = ByteArrayOutputStream()
                    val tmp = ByteArray(4096)
                    // Read until end of headers, then keep reading per Content-Length.
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
                    requestHolder[0] = buf.toString(Charsets.ISO_8859_1)
                    val out = socket.getOutputStream()
                    out.write(responseBytes)
                    out.flush()
                }
            } finally {
                server.close()
            }
        }
        return server.localPort to { t.join(5000); requestHolder[0] }
    }

    private fun startServer(response: String): Pair<Int, () -> String> =
        startServer(response.toByteArray(Charsets.ISO_8859_1))

    @Test
    fun `get parses status code, headers and body`() {
        val (port, getRequest) = startServer(
            "HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nX-Custom: abc\r\n\r\n<root>hello</root>"
        )
        val resp = LanHttpClient.get("http://127.0.0.1:$port/upnp/desc.xml?x=1")
        assertNotNull(resp)
        assertEquals(200, resp!!.code)
        assertEquals("text/xml", resp.headers["content-type"])
        assertEquals("abc", resp.headers["x-custom"])
        assertEquals("<root>hello</root>", resp.body)

        val req = getRequest()
        assertTrue(req.startsWith("GET /upnp/desc.xml?x=1 HTTP/1.1\r\n"))
        assertTrue(req.contains("Host: 127.0.0.1:$port\r\n"))
        assertTrue(req.contains("Connection: close\r\n"))
    }

    @Test
    fun `post sends body and extra headers verbatim`() {
        val soapBody = """<?xml version="1.0"?><s:Envelope><s:Body><u:Play/></s:Body></s:Envelope>"""
        val (port, getRequest) = startServer("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        val resp = LanHttpClient.post(
            "http://127.0.0.1:$port/ctl",
            body = soapBody,
            contentType = "text/xml",
            extraHeaders = mapOf("SOAPACTION" to "\"urn:upnp-org:serviceId:AVTransport#Play\"")
        )
        assertNotNull(resp)
        assertEquals(200, resp!!.code)

        val req = getRequest()
        assertTrue(req.startsWith("POST /ctl HTTP/1.1\r\n"))
        assertTrue(req.contains("Content-Type: text/xml; charset=\"utf-8\"\r\n"))
        assertTrue(req.contains("SOAPACTION: \"urn:upnp-org:serviceId:AVTransport#Play\"\r\n"))
        assertTrue(req.endsWith(soapBody))
    }

    @Test
    fun `response body is decoded as utf-8`() {
        val body = "<friendlyName>São Paulo TV — </friendlyName>"
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=\"utf-8\"\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n\r\n"
        val (port, _) = startServer(
            head.toByteArray(Charsets.ISO_8859_1) + bodyBytes
        )
        val resp = LanHttpClient.get("http://127.0.0.1:$port/desc.xml")
        assertNotNull(resp)
        assertEquals(body, resp!!.body)
    }

    @Test
    fun `reads exactly content-length bytes and ignores trailing junk`() {
        val (port, _) = startServer(
            "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nHELLOWORLD"
        )
        val resp = LanHttpClient.get("http://127.0.0.1:$port/x")
        assertNotNull(resp)
        assertEquals("HELLO", resp!!.body)
    }

    @Test
    fun `tolerates lf-only headers and double spaces in status line`() {
        val (port, _) = startServer("HTTP/1.1  200  OK\nContent-Type: text/plain\n\nhi")
        val resp = LanHttpClient.get("http://127.0.0.1:$port/x")
        assertNotNull(resp)
        assertEquals(200, resp!!.code)
        assertEquals("hi", resp.body)
    }

    @Test
    fun `chunked transfer-encoding body is de-framed`() {
        // desc.xml-shaped payload with a non-ASCII friendly name — the
        // chunk boundary lands mid-string and reassembly must restore the
        // exact UTF-8 bytes before charset decoding.
        val xml = "<?xml version=\"1.0\"?><root><friendlyName>São Paulo TV</friendlyName>" +
            "<UDN>uuid:1234</UDN></root>"
        val xmlBytes = xml.toByteArray(Charsets.UTF_8)
        val part1 = xmlBytes.copyOfRange(0, 40)
        val part2 = xmlBytes.copyOfRange(40, xmlBytes.size)
        val frame = ByteArrayOutputStream().apply {
            fun writeChunk(data: ByteArray, sizeSuffix: String = "") {
                write((data.size.toString(16) + sizeSuffix).toByteArray(Charsets.ISO_8859_1))
                write("\r\n".toByteArray(Charsets.ISO_8859_1))
                write(data)
                write("\r\n".toByteArray(Charsets.ISO_8859_1))
            }
            writeChunk(part1, ";ext=ignored")
            writeChunk(part2)
            write("0\r\nX-Trailer: done\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=\"utf-8\"\r\n" +
            "Transfer-Encoding: chunked\r\n\r\n"
        val (port, _) = startServer(
            head.toByteArray(Charsets.ISO_8859_1) + frame
        )

        val resp = LanHttpClient.get("http://127.0.0.1:$port/upnp/desc.xml")

        assertNotNull(resp)
        assertEquals(200, resp!!.code)
        assertEquals("chunked", resp.headers["transfer-encoding"])
        assertEquals(xml, resp.body)
    }

    @Test
    fun `decodeChunked keeps complete data on truncated tail`() {
        val framed = "5\r\nhello\r\n3\r\nbye\r\n9\r\ntrunc"
        val decoded = LanHttpClient.decodeChunked(framed.toByteArray(Charsets.ISO_8859_1))
        assertEquals("hellobyetrunc", String(decoded, Charsets.ISO_8859_1))
    }

    @Test
    fun `returns null when connection refused`() {
        val server = ServerSocket(0)
        val port = server.localPort
        server.close()
        assertNull(LanHttpClient.get("http://127.0.0.1:$port/", timeoutMs = 500))
    }
}
