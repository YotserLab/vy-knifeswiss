package com.example.cast

import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceMerger
import com.example.cast.discovery.CastDeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastDeviceMergerTest {

    private fun device(
        name: String,
        ip: String,
        type: CastDeviceType,
        avtUrl: String = ""
    ) = CastDevice(
        id = "$type-$ip-$name",
        name = name,
        type = type,
        ip = ip,
        avTransportControlUrl = avtUrl
    )

    @Test
    fun `same ip as chromecast and dial keeps chromecast`() {
        val merged = CastDeviceMerger.merge(
            existing = listOf(device("Living Room", "10.0.0.7", CastDeviceType.DIAL)),
            incoming = listOf(device("Living Room CC", "10.0.0.7", CastDeviceType.CHROMECAST))
        )
        assertEquals(1, merged.size)
        assertEquals(CastDeviceType.CHROMECAST, merged[0].type)
    }

    @Test
    fun `dlna wins over dial on same ip`() {
        val merged = CastDeviceMerger.merge(
            existing = emptyList(),
            incoming = listOf(
                device("TV DIAL", "10.0.0.8", CastDeviceType.DIAL),
                device("TV DLNA", "10.0.0.8", CastDeviceType.DLNA, avtUrl = "http://10.0.0.8/avt")
            )
        )
        assertEquals(1, merged.size)
        assertEquals(CastDeviceType.DLNA, merged[0].type)
    }

    @Test
    fun `two dial-only devices on same ip collapse to one`() {
        val merged = CastDeviceMerger.merge(
            existing = listOf(device("A", "10.0.0.9", CastDeviceType.DIAL)),
            incoming = listOf(device("B", "10.0.0.9", CastDeviceType.DIAL))
        )
        assertEquals(1, merged.size)
        assertEquals(CastDeviceType.DIAL, merged[0].type)
    }

    @Test
    fun `different ips are all kept and sorted by name`() {
        val merged = CastDeviceMerger.merge(
            existing = emptyList(),
            incoming = listOf(
                device("Zulu", "10.0.0.3", CastDeviceType.CHROMECAST),
                device("alpha", "10.0.0.1", CastDeviceType.DIAL),
                device("Mike", "10.0.0.2", CastDeviceType.DLNA, avtUrl = "u")
            )
        )
        assertEquals(3, merged.size)
        assertEquals(listOf("alpha", "Mike", "Zulu"), merged.map { it.name })
    }

    @Test
    fun `dlna without avtransport url falls back when only candidate`() {
        val merged = CastDeviceMerger.merge(
            existing = emptyList(),
            incoming = listOf(device("Bare DLNA", "10.0.0.4", CastDeviceType.DLNA))
        )
        assertEquals(1, merged.size)
        assertEquals(CastDeviceType.DLNA, merged[0].type)
        assertTrue(merged[0].canCastLocalMedia.not())
    }

    @Test
    fun `physical chromecast wins over a cast group at the same ip`() {
        val group = device("Grupo principal", "10.0.0.8", CastDeviceType.CHROMECAST)
            .copy(isGroup = true, deviceUuid = "b18e432b190144a3a8fc5364d9834cd9")
        val physical = device("Sala de TV", "10.0.0.8", CastDeviceType.CHROMECAST)
            .copy(deviceUuid = "987a865ddd69412703352280edf250d2")
        // Order matters in the wild — groups often resolve first over mDNS.
        val merged = CastDeviceMerger.merge(
            existing = emptyList(),
            incoming = listOf(group, physical)
        )
        assertEquals(1, merged.size)
        assertEquals("Sala de TV", merged[0].name)
        assertFalse(merged[0].isGroup)
    }

    @Test
    fun `cast group survives when it is the only castable target at the ip`() {
        val group = device("Grupo principal", "10.0.0.9", CastDeviceType.CHROMECAST)
            .copy(isGroup = true)
        val merged = CastDeviceMerger.merge(
            existing = emptyList(),
            incoming = listOf(group, device("TV", "10.0.0.9", CastDeviceType.DIAL))
        )
        assertEquals(1, merged.size)
        assertEquals("Grupo principal", merged[0].name)
    }
}
