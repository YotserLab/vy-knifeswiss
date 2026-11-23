package com.example.cast

import com.example.cast.control.DlnaSession
import com.example.cast.control.UpnpSoap
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import com.example.cast.media.CastMedia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import kotlin.concurrent.thread

class DlnaSessionTest {

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

    private fun ok() = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"

    private fun device(avtPort: Int, rcsPort: Int = 0) = CastDevice(
        id = "d1", name = "TV", type = CastDeviceType.DLNA, ip = "127.0.0.1",
        avTransportControlUrl = if (avtPort > 0) "http://127.0.0.1:$avtPort/avt" else "",
        renderingControlUrl = if (rcsPort > 0) "http://127.0.0.1:$rcsPort/rcs" else ""
    )

    private val media = CastMedia("/sdcard/v.mp4", "Filme & Cia", "video/mp4", 90_000)

    @Test
    fun `load posts SetAVTransportURI with escaped uri and didl then Play`() = runBlocking {
        val (port, getRequests) = startServer(listOf(ok(), ok()))
        val session = DlnaSession(device(port))
        assertTrue(session.load(media, "http://h/v.mp4?a=1&b=2"))

        val requests = getRequests()
        assertEquals(2, requests.size)

        val setUri = requests[0]
        assertTrue(setUri.startsWith("POST /avt HTTP/1.1\r\n"))
        assertTrue(setUri.contains("SOAPACTION: \"${UpnpSoap.AVT}#SetAVTransportURI\"\r\n"))
        assertTrue(setUri.contains("<u:SetAVTransportURI xmlns:u=\"${UpnpSoap.AVT}\">"))
        assertTrue(setUri.contains("<InstanceID>0</InstanceID>"))
        assertTrue(setUri.contains("<CurrentURI>http://h/v.mp4?a=1&amp;b=2</CurrentURI>"))
        // DIDL is an escaped XML string inside the envelope
        assertTrue(setUri.contains("<CurrentURIMetaData>&lt;DIDL-Lite"))
        assertTrue(setUri.contains("&lt;dc:title&gt;Filme &amp;amp; Cia&lt;/dc:title&gt;"))

        val play = requests[1]
        assertTrue(play.contains("SOAPACTION: \"${UpnpSoap.AVT}#Play\"\r\n"))
        assertTrue(play.contains("<u:Play xmlns:u=\"${UpnpSoap.AVT}\">"))
        assertTrue(play.contains("<Speed>1</Speed>"))
    }

    @Test
    fun `load returns false without avTransportControlUrl`() = runBlocking {
        val session = DlnaSession(device(avtPort = 0))
        assertFalse(session.load(media, "http://h/v.mp4"))
    }

    @Test
    fun `load returns false when SetAVTransportURI is not 200`() = runBlocking {
        val (port, _) = startServer(listOf("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n"))
        val session = DlnaSession(device(port))
        assertFalse(session.load(media, "http://h/v.mp4"))
    }

    @Test
    fun `play pause stop post their avtransport actions`() = runBlocking {
        val (port, getRequests) = startServer(listOf(ok(), ok(), ok()))
        val session = DlnaSession(device(port))
        assertTrue(session.play())
        assertTrue(session.pause())
        assertTrue(session.stop())

        val requests = getRequests()
        assertEquals(3, requests.size)
        assertTrue(requests[0].contains("#Play\""))
        assertTrue(requests[1].contains("#Pause\""))
        assertTrue(requests[1].contains("<u:Pause "))
        assertTrue(requests[2].contains("#Stop\""))
    }

    @Test
    fun `seek posts Seek with REL_TIME target in hms`() = runBlocking {
        val (port, getRequests) = startServer(listOf(ok()))
        val session = DlnaSession(device(port))
        assertTrue(session.seek(65_000))

        val req = getRequests().single()
        assertTrue(req.contains("SOAPACTION: \"${UpnpSoap.AVT}#Seek\"\r\n"))
        assertTrue(req.contains("<Unit>REL_TIME</Unit>"))
        assertTrue(req.contains("<Target>0:01:05</Target>"))
    }

    @Test
    fun `setVolume posts SetVolume to rendering control url and clamps level`() = runBlocking {
        val (port, getRequests) = startServer(listOf(ok()))
        val session = DlnaSession(device(avtPort = 0, rcsPort = port))
        assertTrue(session.setVolume(150))

        val req = getRequests().single()
        assertTrue(req.startsWith("POST /rcs HTTP/1.1\r\n"))
        assertTrue(req.contains("SOAPACTION: \"${UpnpSoap.RCS}#SetVolume\"\r\n"))
        assertTrue(req.contains("<Channel>Master</Channel>"))
        assertTrue(req.contains("<DesiredVolume>100</DesiredVolume>"))
    }

    @Test
    fun `setVolume returns false without renderingControlUrl`() = runBlocking {
        val session = DlnaSession(device(avtPort = 0, rcsPort = 0))
        assertFalse(session.setVolume(30))
    }

    @Test
    fun `getVolume parses CurrentVolume from GetVolume response`() = runBlocking {
        val body = soapResponse(
            """<u:GetVolumeResponse xmlns:u="${UpnpSoap.RCS}">
            <CurrentVolume>37</CurrentVolume>
            </u:GetVolumeResponse>"""
        )
        val (port, getRequests) = startServer(listOf(body))
        val session = DlnaSession(device(avtPort = 0, rcsPort = port))
        assertEquals(37, session.getVolume())

        val req = getRequests().single()
        assertTrue(req.startsWith("POST /rcs HTTP/1.1\r\n"))
        assertTrue(req.contains("SOAPACTION: \"${UpnpSoap.RCS}#GetVolume\"\r\n"))
        assertTrue(req.contains("<Channel>Master</Channel>"))
    }

    @Test
    fun `getVolume returns null without renderingControlUrl`() = runBlocking {
        assertNull(DlnaSession(device(avtPort = 0, rcsPort = 0)).getVolume())
    }

    @Test
    fun `dlna session supports position info by default`() {
        assertTrue(DlnaSession(device(avtPort = 0)).supportsPositionInfo)
    }

    private fun soapResponse(innerXml: String): String {
        val body = """<?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
            $innerXml
            </s:Body></s:Envelope>"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        return "HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${bytes.size}\r\n\r\n" + body
    }

    @Test
    fun `positionInfo posts GetPositionInfo and GetTransportInfo`() = runBlocking {
        val posBody = soapResponse(
            """<u:GetPositionInfoResponse xmlns:u="${UpnpSoap.AVT}">
            <TrackDuration>0:03:00</TrackDuration><RelTime>0:00:42</RelTime>
            </u:GetPositionInfoResponse>"""
        )
        val tiBody = soapResponse(
            """<u:GetTransportInfoResponse xmlns:u="${UpnpSoap.AVT}">
            <CurrentTransportState>PLAYING</CurrentTransportState>
            </u:GetTransportInfoResponse>"""
        )
        val (port, getRequests) = startServer(listOf(posBody, tiBody))
        val session = DlnaSession(device(port))

        val info = session.positionInfo()
        assertNotNull(info)
        assertEquals(42_000L, info!!.relTimeMs)
        assertEquals(180_000L, info.durationMs)
        assertEquals("PLAYING", info.state)

        val requests = getRequests()
        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("#GetPositionInfo\""))
        assertTrue(requests[1].contains("#GetTransportInfo\""))
    }

    @Test
    fun `positionInfo falls back to UNKNOWN state when GetTransportInfo fails`() = runBlocking {
        val posBody = soapResponse(
            """<u:GetPositionInfoResponse xmlns:u="${UpnpSoap.AVT}">
            <TrackDuration>0:03:00</TrackDuration><RelTime>0:00:42</RelTime>
            </u:GetPositionInfoResponse>"""
        )
        val (port, _) = startServer(
            listOf(posBody, "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n")
        )
        val session = DlnaSession(device(port))

        val info = session.positionInfo()
        assertNotNull(info)
        assertEquals(42_000L, info!!.relTimeMs)
        assertEquals("UNKNOWN", info.state)
    }

    @Test
    fun `positionInfo returns null when GetPositionInfo fails`() = runBlocking {
        val (port, _) = startServer(
            listOf("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n")
        )
        val session = DlnaSession(device(port))
        assertNull(session.positionInfo())
    }

    @Test
    fun `positionInfo returns null without avTransportControlUrl`() = runBlocking {
        val session = DlnaSession(device(avtPort = 0))
        assertNull(session.positionInfo())
    }
}
