package com.example.cast

import com.example.cast.discovery.SsdpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SsdpParserTest {

    private val sample = """
        HTTP/1.1 200 OK
        CACHE-CONTROL: max-age=1800
        LOCATION: http://192.168.1.40:5200/desc.xml
        ST: urn:schemas-upnp-org:device:MediaRenderer:1
        USN: uuid:abc-123::urn:schemas-upnp-org:device:MediaRenderer:1
        SERVER: Linux/3.14 UPnP/1.0 Samsung/1.0
        EXT:
    """.trimIndent()

    @Test
    fun `parses headers, host, port and udn`() {
        val r = SsdpParser.parse(sample)!!
        assertEquals("http://192.168.1.40:5200/desc.xml", r.location)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", r.st)
        assertEquals("uuid:abc-123::urn:schemas-upnp-org:device:MediaRenderer:1", r.usn)
        assertEquals("Linux/3.14 UPnP/1.0 Samsung/1.0", r.server)
        assertEquals("192.168.1.40", r.host)
        assertEquals(5200, r.port)
        assertEquals("abc-123", r.udnUuid)
    }

    @Test
    fun `returns null when location header is missing`() {
        assertNull(SsdpParser.parse("NOTIFY * HTTP/1.1\r\nST: upnp:rootdevice\r\n"))
    }

    @Test
    fun `nt header is used as fallback for st`() {
        val r = SsdpParser.parse(
            "HTTP/1.1 200 OK\r\nLOCATION: http://10.0.0.5/d\r\nNT: upnp:rootdevice\r\nUSN: uuid:x::upnp:rootdevice\r\n"
        )!!
        assertEquals("upnp:rootdevice", r.st)
        assertEquals(80, r.port)
    }
}
