package com.example.cast

import com.example.cast.discovery.DeviceDescParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceDescParserTest {

    private val baseUrl = "http://192.168.1.40:5200/desc.xml"

    private val sample = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>Samsung TV</friendlyName>
    <manufacturer>Samsung Electronics</manufacturer>
    <modelName>UE55NU7100</modelName>
    <UDN>uuid:abc-123</UDN>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
        <controlURL>/upnp/control/AVTransport1</controlURL>
        <eventSubURL>/upnp/event/AVTransport1</eventSubURL>
        <SCPDURL>/upnp/scpd/AVTransport1</SCPDURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
        <controlURL>/upnp/control/RenderingControl1</controlURL>
        <eventSubURL>/upnp/event/RenderingControl1</eventSubURL>
        <SCPDURL>/upnp/scpd/RenderingControl1</SCPDURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <controlURL>/upnp/control/ConnectionManager1</controlURL>
        <eventSubURL>/upnp/event/ConnectionManager1</eventSubURL>
        <SCPDURL>/upnp/scpd/ConnectionManager1</SCPDURL>
      </service>
    </serviceList>
  </device>
</root>"""

    @Test
    fun `parses device fields and resolves relative control urls`() {
        val d = DeviceDescParser.parse(sample, baseUrl)!!
        assertEquals("Samsung TV", d.friendlyName)
        assertEquals("Samsung Electronics", d.manufacturer)
        assertEquals("UE55NU7100", d.modelName)
        assertEquals("uuid:abc-123", d.udn)
        assertEquals("http://192.168.1.40:5200/upnp/control/AVTransport1", d.avTransportControlUrl)
        assertEquals("http://192.168.1.40:5200/upnp/control/RenderingControl1", d.renderingControlUrl)
    }

    @Test
    fun `absolute control urls pass through`() {
        val xml = sample.replace(
            "/upnp/control/AVTransport1",
            "http://192.168.1.40:9999/abs/AVT"
        )
        val d = DeviceDescParser.parse(xml, baseUrl)!!
        assertEquals("http://192.168.1.40:9999/abs/AVT", d.avTransportControlUrl)
    }

    @Test
    fun `non-root-relative control url resolves against document path`() {
        val xml = sample.replace(
            "<controlURL>/upnp/control/AVTransport1</controlURL>",
            "<controlURL>upnp/control/AVTransport1</controlURL>"
        )
        val d = DeviceDescParser.parse(xml, "http://192.168.1.40:5200/a/b/desc.xml")!!
        assertEquals("http://192.168.1.40:5200/a/b/upnp/control/AVTransport1", d.avTransportControlUrl)
    }

    @Test
    fun `urlbase element overrides location for resolution`() {
        val xml = sample.replace(
            "<specVersion><major>1</major><minor>0</minor></specVersion>",
            "<specVersion><major>1</major><minor>0</minor></specVersion><URLBase>http://192.168.1.40:6000/base/</URLBase>"
        ).replace(
            "<controlURL>/upnp/control/AVTransport1</controlURL>",
            "<controlURL>ctrl/avt</controlURL>"
        )
        val d = DeviceDescParser.parse(xml, baseUrl)!!
        assertEquals("http://192.168.1.40:6000/base/ctrl/avt", d.avTransportControlUrl)
    }

    @Test
    fun `nested devicelist children do not corrupt root device fields`() {
        val xml = sample.replace(
            "</serviceList>",
            """</serviceList>
    <deviceList>
      <device>
        <friendlyName>Inner Baby Monitor</friendlyName>
        <manufacturer>OtherCorp</manufacturer>
        <modelName>BM-1</modelName>
        <UDN>uuid:inner-999</UDN>
        <serviceList>
          <service>
            <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
            <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
            <controlURL>/inner/control/AVT</controlURL>
          </service>
        </serviceList>
      </device>
    </deviceList>"""
        )
        val d = DeviceDescParser.parse(xml, baseUrl)!!
        assertEquals("Samsung TV", d.friendlyName)
        assertEquals("Samsung Electronics", d.manufacturer)
        assertEquals("uuid:abc-123", d.udn)
        assertEquals("http://192.168.1.40:5200/upnp/control/AVTransport1", d.avTransportControlUrl)
    }

    @Test
    fun `returns null when no device element`() {
        assertNull(DeviceDescParser.parse("<root><specVersion/></root>", baseUrl))
    }

    @Test
    fun `returns null on malformed xml`() {
        assertNull(DeviceDescParser.parse("<device><friendlyName>oops</wrong>", baseUrl))
    }
}
