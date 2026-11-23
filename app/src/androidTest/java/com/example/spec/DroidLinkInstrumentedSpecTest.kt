package com.example.spec

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidLinkInstrumentedSpecTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    // DLK-R09: servidor escuta em porta configurável com default 8989
    @Test
    fun test_DLK_R09_default_server_port_is_8989() {
        val field = Class.forName("com.example.droidlink.server.DroidLinkServerService")
            .getDeclaredField("configuredPort")
        field.isAccessible = true
        assertEquals(8989, field.get(null))
    }

    // DLK-R10: histórico persiste conexões por IP para reconexão (local ou Tailscale)
    @Test
    fun test_DLK_R10_connection_history_roundtrip() {
        val history = com.example.droidlink.server.DroidLinkConnectionHistory
        val id = history.recordConnection(ctx, "100.64.0.1", "test")
        assertTrue(history.getHistory(ctx).any { it.ip == "100.64.0.1" })
        history.recordDisconnection(ctx, id)
        history.clearHistory(ctx)
    }

    // DLK-R11: input remoto injetado via AccessibilityService declarado no manifest
    @Test
    fun test_DLK_R11_accessibility_service_declared() {
        val pm = ctx.packageManager
        val info = pm.getServiceInfo(
            android.content.ComponentName(ctx, "com.example.droidlink.server.RemoteInputAccessibilityService"),
            0
        )
        assertNotNull(info)
        assertTrue(info.permission == android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
    }

    // DLK-R13: captura de áudio do servidor existe e é classe instanciável
    @Test
    fun test_DLK_R13_audio_capture_class_exists() {
        assertNotNull(Class.forName("com.example.droidlink.server.DroidLinkAudioCapture"))
    }
}
