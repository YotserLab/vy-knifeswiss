package com.example.cast

import com.example.cast.control.ChromecastSession
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [ChromecastSession.matchesDevice]. Only the pure
 * companion helper is exercised — everything touching GMS/MediaRouter
 * requires a device.
 */
class ChromecastSessionTest {

    @Test
    fun `matchesDevice matches identical friendly names`() {
        assertTrue(ChromecastSession.matchesDevice("Living Room TV", "Living Room TV"))
    }

    @Test
    fun `matchesDevice is case insensitive`() {
        assertTrue(ChromecastSession.matchesDevice("LIVING ROOM TV", "living room tv"))
    }

    @Test
    fun `matchesDevice normalizes whitespace and punctuation`() {
        assertTrue(ChromecastSession.matchesDevice("Living-Room TV", "Living Room TV"))
        assertTrue(ChromecastSession.matchesDevice("Chromecast Ultra!", "Chromecast Ultra"))
    }

    @Test
    fun `matchesDevice trims surrounding whitespace`() {
        assertTrue(ChromecastSession.matchesDevice("  Kitchen Hub  ", "Kitchen Hub"))
    }

    @Test
    fun `matchesDevice rejects different names`() {
        assertFalse(ChromecastSession.matchesDevice("Bedroom TV", "Living Room TV"))
    }

    @Test
    fun `matchesDevice rejects partial names`() {
        assertFalse(ChromecastSession.matchesDevice("TV", "Living Room TV"))
        assertFalse(ChromecastSession.matchesDevice("Living Room TV", "TV"))
    }

    @Test
    fun `matchesDevice rejects null and blank names`() {
        assertFalse(ChromecastSession.matchesDevice(null, "Living Room TV"))
        assertFalse(ChromecastSession.matchesDevice("Living Room TV", ""))
        assertFalse(ChromecastSession.matchesDevice("", "Living Room TV"))
        assertFalse(ChromecastSession.matchesDevice("   ", "Living Room TV"))
    }

    @Test
    fun `matchesDevice rejects names that normalize to empty`() {
        // "..." strips to "" so only exact equality could match — it doesn't.
        assertFalse(ChromecastSession.matchesDevice("...", "Chromecast"))
        // Identical punctuation-only names still match via exact equality.
        assertTrue(ChromecastSession.matchesDevice("---", "---"))
    }

    private fun castTarget(
        name: String = "Chromecast-987a865ddd69412703352280edf250d2",
        ip: String = "192.168.7.58",
        uuid: String = "987a865ddd69412703352280edf250d2"
    ) = CastDevice(
        id = "chromecast-$ip", name = name, type = CastDeviceType.CHROMECAST,
        ip = ip, deviceUuid = uuid
    )

    @Test
    fun `targetMatches prefers uuid equality over friendly name`() {
        // GMS deviceId carries dashes — hex-normalized comparison must ignore them.
        val d = castTarget()
        assertTrue(
            ChromecastSession.targetMatches(
                "192.168.7.58", "987a865d-dd69-4127-0335-2280edf250d2",
                "Sala de TV", d
            )
        )
    }

    @Test
    fun `targetMatches rejects a cast group sharing the device IP`() {
        // The group advertises the leader's IP but a different UUID — same
        // IP alone must NOT be treated as the same target.
        val d = castTarget()
        assertFalse(
            ChromecastSession.targetMatches(
                "192.168.7.58", "b18e432b190144a3a8fc5364d9834cd9",
                "Grupo Sala", d
            )
        )
    }

    @Test
    fun `targetMatches falls back to ip when route uuid is unknown`() {
        val d = castTarget()
        assertTrue(ChromecastSession.targetMatches("192.168.7.58", null, "whatever", d))
        assertFalse(ChromecastSession.targetMatches("192.168.7.99", null, "whatever", d))
    }

    @Test
    fun `targetMatches falls back to name when no ip or uuid`() {
        val d = castTarget(uuid = "")
        assertTrue(ChromecastSession.targetMatches(null, null, "Sala de TV", d.copy(name = "Sala de TV")))
        assertFalse(ChromecastSession.targetMatches(null, null, "Outra TV", d.copy(name = "Sala de TV")))
    }
}
