package com.example.cast

import com.example.cast.control.UpnpSoap
import com.example.cast.media.CastMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpSoapTest {

    @Test
    fun `envelope wraps action in soap body with service namespace`() {
        val xml = UpnpSoap.envelope(
            "SetAVTransportURI", UpnpSoap.AVT,
            mapOf("InstanceID" to "0", "CurrentURI" to "http://x/v.mp4")
        )
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(xml.contains("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""))
        assertTrue(xml.contains("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\""))
        assertTrue(xml.contains("<u:SetAVTransportURI xmlns:u=\"${UpnpSoap.AVT}\">"))
        assertTrue(xml.contains("<InstanceID>0</InstanceID>"))
        assertTrue(xml.contains("<CurrentURI>http://x/v.mp4</CurrentURI>"))
        assertTrue(xml.contains("</u:SetAVTransportURI></s:Body></s:Envelope>"))
    }

    @Test
    fun `envelope does not escape arg values - callers pass pre-escaped values`() {
        val xml = UpnpSoap.envelope("A", UpnpSoap.AVT, mapOf("K" to "a&b"))
        assertTrue(xml.contains("<K>a&b</K>"))
    }

    @Test
    fun `escape converts all five xml entities`() {
        assertEquals(
            "a&lt;b&gt;&amp;&quot;q&quot;&apos;p&apos;",
            UpnpSoap.escape("a<b>&\"q\"'p'")
        )
    }

    @Test
    fun `escape does not double escape - amp first`() {
        // '&' must become &amp; before < becomes &lt;, else "&lt;" would double-escape
        assertEquals("&amp;&lt;", UpnpSoap.escape("&<"))
    }

    @Test
    fun `didlLite escapes title and url and uses video class`() {
        val didl = UpnpSoap.didlLite(
            "A & B <Movie>", "http://h/v.mp4?x=1&y=2", "video/mp4",
            CastMedia.Kind.VIDEO, 65_000
        )
        assertTrue(didl.startsWith("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\""))
        assertTrue(didl.contains("xmlns:dc=\"http://purl.org/dc/elements/1.1/\""))
        assertTrue(didl.contains("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\""))
        assertTrue(didl.contains("<dc:title>A &amp; B &lt;Movie&gt;</dc:title>"))
        assertTrue(didl.contains("<upnp:class>object.item.videoItem</upnp:class>"))
        assertTrue(didl.contains("protocolInfo=\"http-get:*:video/mp4:*\""))
        assertTrue(didl.contains("duration=\"0:01:05\""))
        assertTrue(didl.contains(">http://h/v.mp4?x=1&amp;y=2</res>"))
        assertTrue(didl.contains("<item id=\"0\" parentID=\"-1\" restricted=\"1\">"))
        assertTrue(didl.endsWith("</item></DIDL-Lite>"))
    }

    @Test
    fun `didlLite uses per-kind upnp class`() {
        assertTrue(
            UpnpSoap.didlLite("t", "u", "audio/mpeg", CastMedia.Kind.AUDIO, 0)
                .contains("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
        )
        assertTrue(
            UpnpSoap.didlLite("t", "u", "image/jpeg", CastMedia.Kind.IMAGE, 0)
                .contains("<upnp:class>object.item.imageItem.photo</upnp:class>")
        )
    }

    @Test
    fun `didlLite escapes mime inside protocolInfo attribute`() {
        // A hostile/malformed mimeType must not break out of the attribute
        val didl = UpnpSoap.didlLite("t", "u", "video/mp4\" x=\"y", CastMedia.Kind.VIDEO, 0)
        assertTrue(didl.contains("protocolInfo=\"http-get:*:video/mp4&quot; x=&quot;y:*\""))
    }

    @Test
    fun `didlLite omits duration attribute when zero`() {
        val didl = UpnpSoap.didlLite("t", "u", "video/mp4", CastMedia.Kind.VIDEO, 0)
        assertTrue(!didl.contains("duration="))
        assertTrue(didl.contains("<res protocolInfo=\"http-get:*:video/mp4:*\">"))
    }

    @Test
    fun `parseHmsToMs handles h colon mm colon ss`() {
        assertEquals(0L, UpnpSoap.parseHmsToMs("0:00:00"))
        assertEquals(5_000L, UpnpSoap.parseHmsToMs("0:00:05"))
        assertEquals(3_723_000L, UpnpSoap.parseHmsToMs("1:02:03"))
    }

    @Test
    fun `parseHmsToMs handles mm colon ss`() {
        assertEquals(330_000L, UpnpSoap.parseHmsToMs("05:30"))
        assertEquals(5_000L, UpnpSoap.parseHmsToMs("00:05"))
    }

    @Test
    fun `parseHmsToMs tolerates fractional seconds`() {
        assertEquals(5_500L, UpnpSoap.parseHmsToMs("0:00:05.500"))
        assertEquals(5_050L, UpnpSoap.parseHmsToMs("0:00:05.05"))
    }

    @Test
    fun `parseHmsToMs returns zero for invalid input`() {
        assertEquals(0L, UpnpSoap.parseHmsToMs(""))
        assertEquals(0L, UpnpSoap.parseHmsToMs("NOT_IMPLEMENTED"))
        assertEquals(0L, UpnpSoap.parseHmsToMs("abc"))
        assertEquals(0L, UpnpSoap.parseHmsToMs("1:2:3:4"))
        assertEquals(0L, UpnpSoap.parseHmsToMs("123"))
    }

    @Test
    fun `msToHms formats as h colon mm colon ss`() {
        assertEquals("0:00:00", UpnpSoap.msToHms(0))
        assertEquals("0:00:05", UpnpSoap.msToHms(5_000))
        assertEquals("1:02:03", UpnpSoap.msToHms(3_723_000))
        assertEquals("0:00:00", UpnpSoap.msToHms(-5_000))
    }

    @Test
    fun `hms and ms round trip`() {
        listOf(0L, 999L, 5_000L, 65_000L, 3_723_000L, 36_000_000L).forEach { ms ->
            assertEquals(ms / 1000 * 1000, UpnpSoap.parseHmsToMs(UpnpSoap.msToHms(ms)))
        }
    }

    @Test
    fun `parsePositionInfo extracts fields from realistic soap response`() {
        // Real GetPositionInfoResponse per AVTransport:1 — no CurrentTransportState
        // (that field lives in GetTransportInfoResponse).
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
            <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <Track>1</Track>
            <TrackDuration>0:03:45</TrackDuration>
            <TrackMetaData>NOT_IMPLEMENTED</TrackMetaData>
            <TrackURI>http://tv.local/media.mp4</TrackURI>
            <RelTime>0:01:12</RelTime>
            <AbsTime>0:01:12</AbsTime>
            <RelCount>2147483647</RelCount>
            <AbsCount>2147483647</AbsCount>
            </u:GetPositionInfoResponse>
            </s:Body>
            </s:Envelope>"""
        val info = UpnpSoap.parsePositionInfo(body)
        assertEquals(72_000L, info.relTimeMs)
        assertEquals(225_000L, info.durationMs)
        assertEquals("UNKNOWN", info.state)
    }

    @Test
    fun `parsePositionInfo reads state from gettransportinfo response`() {
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
            <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <CurrentTransportState>PLAYING</CurrentTransportState>
            <CurrentTransportStatus>OK</CurrentTransportStatus>
            <CurrentSpeed>1</CurrentSpeed>
            </u:GetTransportInfoResponse>
            </s:Body>
            </s:Envelope>"""
        val info = UpnpSoap.parsePositionInfo(body)
        assertEquals("PLAYING", info.state)
        assertEquals(0L, info.relTimeMs)
        assertEquals(0L, info.durationMs)
    }

    @Test
    fun `parsePositionInfo matches leaf tags carrying namespaced attributes`() {
        // xmlns declarations on the leaf tag itself must not hide the tag name
        val body = """<RelTime xmlns:u="urn:x" u:attr="1">0:00:10</RelTime>"""
        assertEquals(10_000L, UpnpSoap.parsePositionInfo(body).relTimeMs)
    }

    @Test
    fun `parsePositionInfo tolerates namespaced and attributed tags`() {
        val body = """<u:X xmlns:u="y"><u:RelTime>00:02</u:RelTime>
            <TrackDuration attr="1">0:10:00</TrackDuration>
            <CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState></u:X>"""
        val info = UpnpSoap.parsePositionInfo(body)
        assertEquals(2_000L, info.relTimeMs)
        assertEquals(600_000L, info.durationMs)
        assertEquals("PAUSED_PLAYBACK", info.state)
    }

    @Test
    fun `parsePositionInfo returns defaults for missing fields`() {
        val info = UpnpSoap.parsePositionInfo("<s:Envelope><s:Body/></s:Envelope>")
        assertEquals(0L, info.relTimeMs)
        assertEquals(0L, info.durationMs)
        assertEquals("UNKNOWN", info.state)
    }

    @Test
    fun `parsePositionInfo maps non hms values to zero`() {
        val body = "<RelTime>NOT_IMPLEMENTED</RelTime><TrackDuration>00:30</TrackDuration>"
        val info = UpnpSoap.parsePositionInfo(body)
        assertEquals(0L, info.relTimeMs)
        assertEquals(30_000L, info.durationMs)
    }

    @Test
    fun `parseVolume extracts current volume`() {
        val body = """<u:GetVolumeResponse xmlns:u="${UpnpSoap.RCS}">
            <CurrentVolume>42</CurrentVolume></u:GetVolumeResponse>"""
        assertEquals(42, UpnpSoap.parseVolume(body))
    }

    @Test
    fun `parseVolume returns null when missing or invalid`() {
        assertNull(UpnpSoap.parseVolume("<Envelope/>"))
        assertNull(UpnpSoap.parseVolume("<CurrentVolume>loud</CurrentVolume>"))
    }
}
