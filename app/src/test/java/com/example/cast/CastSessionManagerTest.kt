package com.example.cast

import com.example.cast.control.CastSessionManager
import com.example.cast.discovery.CastDeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastSessionManagerTest {

    @Test
    fun `deviceErrorFor returns limitado for DIAL`() {
        // i18n: deviceErrorFor(DIAL) delegates to Res.get(R.string.dispositivo_limitado)
        val src = java.io.File("src/main/java/com/example/cast/control/CastSessionManager.kt").readText()
        org.junit.Assert.assertTrue(src.contains("CastDeviceType.DIAL -> Res.get(R.string.dispositivo_limitado)"))
        val strings = java.io.File("src/main/res/values/strings.xml").readText()
        org.junit.Assert.assertTrue(strings.contains("name=\"dispositivo_limitado\""))
    }

    @Test
    fun `deviceErrorFor returns null for castable types`() {
        assertNull(CastSessionManager.deviceErrorFor(CastDeviceType.DLNA))
        assertNull(CastSessionManager.deviceErrorFor(CastDeviceType.ROKU))
        assertNull(CastSessionManager.deviceErrorFor(CastDeviceType.CHROMECAST))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `consumeError clears lastError`() {
        // Inject an error directly — the public suspend ops need
        // Dispatchers.Main, unavailable in plain JVM tests.
        val field = CastSessionManager::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val flow = field.get(CastSessionManager)
            as MutableStateFlow<CastSessionManager.CastState>
        val previous = flow.value
        try {
            flow.value = previous.copy(lastError = "boom")
            CastSessionManager.consumeError()
            assertNull(CastSessionManager.state.value.lastError)
        } finally {
            flow.value = previous
        }
    }
}
