package com.example.cast

import com.example.cast.discovery.RokuProbe
import org.junit.Assert.assertEquals
import org.junit.Test

class RokuProbeTest {

    private val deviceInfo = """<?xml version="1.0" encoding="UTF-8"?>
<device-info>
  <udn>12345</udn>
  <serial-number>ABCD</serial-number>
  <device-id>X1</device-id>
  <vendor-name>Roku</vendor-name>
  <model-number>3941X</model-number>
  <model-name>Roku Express 4K</model-name>
  <friendly-device-name>Quarto Roku</friendly-device-name>
  <friendly-model-name>Roku Express 4K</friendly-model-name>
</device-info>
"""

    @Test
    fun `extractTag returns tag contents`() {
        assertEquals("Quarto Roku", RokuProbe.extractTag(deviceInfo, "friendly-device-name"))
        assertEquals("Roku Express 4K", RokuProbe.extractTag(deviceInfo, "model-name"))
    }

    @Test
    fun `extractTag returns empty for absent tag`() {
        assertEquals("", RokuProbe.extractTag(deviceInfo, "power-mode"))
        assertEquals("", RokuProbe.extractTag("", "model-name"))
    }

    @Test
    fun `extractTag with suffix-like tag names does not overmatch`() {
        // <model-name> must not match inside <friendly-model-name>
        val xml = "<friendly-model-name>Fancy</friendly-model-name><model-name>Plain</model-name>"
        assertEquals("Plain", RokuProbe.extractTag(xml, "model-name"))
    }
}
