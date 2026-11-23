package com.example.spectests
import com.example.ui.screens.chat.LocalChatScreen
import com.example.ui.screens.chat.LocalChatStorage
import com.example.ui.screens.chat.MeshInfoScreen
import com.example.ui.screens.media.MediaManagerScreen
import com.example.ui.screens.media.ScreenRecorderScreen
import com.example.ui.screens.network.NetworkInfoScreen
import com.example.ui.screens.network.WifiMonitorScreen
import com.example.ui.screens.system.AboutScreen
import com.example.ui.screens.system.DevSettingsScreen
import com.example.ui.screens.system.PowerOffProtectionScreen
import com.example.ui.screens.system.SettingsScreen

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasSpecTest {

    // EXT-R01: guia anti-encerramento por fabricante + atalhos de configuração
    @Test
    fun test_EXT_R01_poweroff_guide() {
        val s = src("com/example/ui/screens/system/PowerOffProtectionScreen.kt")
        assertTrue(s.contains("findPowerOffGuide(manufacturer, brand)") && s.contains("Settings.ACTION"))
    }

    // EXT-R02: chat em janela flutuante (documentLaunchMode=always + allowEmbedded)
    @Test
    fun test_EXT_R02_chat_bubble() {
        val m = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("LocalChatBubbleActivity") && m.contains("documentLaunchMode=\"always\"") && m.contains("allowEmbedded=\"true\""))
        assertTrue(srcContains("com/example/ui/activities/LocalChatBubbleActivity.kt", "LocalChatScreen"))
    }

    // EXT-R03: busca global com filtros por tipo
    @Test
    fun test_EXT_R03_global_search_filters() {
        assertTrue(srcContains("com/example/ui/activities/GlobalSearchActivity.kt", "AppCompatActivity"))
        val l = java.io.File("src/main/res/layout/activity_global_search.xml").readText()
        assertTrue(l.contains("documents") && l.contains("images") && l.contains("videos") && l.contains("audio"))
    }

    // EXT-R04: equalizador global na audioSessionId com bandas persistidas
    @Test
    fun test_EXT_R04_equalizer() {
        val s = src("com/example/utils/EqualizerManager.kt")
        assertTrue(s.contains("audiofx.Equalizer") && s.contains("audioSessionId") && s.contains("EqualizerPrefs"))
    }

    // EXT-R05: gravação de tela via MediaProjection + MediaRecorder
    @Test
    fun test_EXT_R05_screen_recorder() {
        val s = src("com/example/ui/screens/media/ScreenRecorderScreen.kt")
        assertTrue(s.contains("MediaProjectionManager") && s.contains("MediaRecorder"))
    }

    // EXT-R06: monitor Wi-Fi via scan results
    @Test
    fun test_EXT_R06_wifi_monitor_scan() {
        assertTrue(srcContains("com/example/ui/screens/network/WifiMonitorScreen.kt", "SCAN_RESULTS_AVAILABLE_ACTION"))
    }

    // EXT-R07: info de rede via ConnectivityManager
    @Test
    fun test_EXT_R07_network_info() {
        assertTrue(srcContains("com/example/ui/screens/network/NetworkInfoScreen.kt", "CONNECTIVITY_SERVICE"))
    }

    // EXT-R08: About exibe versão e build
    @Test
    fun test_EXT_R08_about_version() {
        val s = src("com/example/ui/screens/system/AboutScreen.kt")
        assertTrue(s.contains("VERSION_NAME") && s.contains("VERSION_CODE"))
    }
}

// EXT-R09..R13: preferências, dev settings, media manager, mesh info e storage de chat
class ExtrasPrefsSpecTest {
    @org.junit.Test
    // EXT-R09
    fun test_EXT_R09_tool_settings_prefs() {
        org.junit.Assert.assertTrue(srcContains("com/example/ui/screens/system/SettingsScreen.kt", "tool_settings"))
    }
    @org.junit.Test
    // EXT-R10
    fun test_EXT_R10_dev_settings_intent() {
        org.junit.Assert.assertTrue(srcContains("com/example/ui/screens/system/DevSettingsScreen.kt", "ACTION_APPLICATION_DEVELOPMENT_SETTINGS"))
    }
    @org.junit.Test
    // EXT-R11
    fun test_EXT_R11_media_manager_filter() {
        val s = src("com/example/ui/screens/media/MediaManagerScreen.kt")
        org.junit.Assert.assertTrue(s.contains("listFiles()") && s.contains("\"mp3\""))
    }
    @org.junit.Test
    // EXT-R12
    fun test_EXT_R12_mesh_info_prefs() {
        org.junit.Assert.assertTrue(srcContains("com/example/ui/screens/chat/MeshInfoScreen.kt", "local_chat_prefs"))
    }
    @org.junit.Test
    // EXT-R13
    fun test_EXT_R13_chat_history_storage() {
        val s = src("com/example/ui/screens/chat/LocalChatStorage.kt")
        org.junit.Assert.assertTrue(s.contains("saveChatHistory") && s.contains("loadChatHistory"))
    }
}

// EXT-R14: renew() renova TTL do token sem trocar a URL
class ExtrasServerRenewSpecTest {
    @org.junit.Test
    fun test_EXT_R14_token_renew() {
        org.junit.Assert.assertTrue(srcContains("com/example/service/InternalHttpFileServer.kt", "fun renew("))
    }
}

// EXT-R15: download atômico — .tmp e rename ao completar
class ExtrasAtomicDownloadSpecTest {
    @org.junit.Test
    fun test_EXT_R15_atomic_download() {
        val s = src("com/example/utils/net/NetworkUtils.kt")
        org.junit.Assert.assertTrue(s.contains(".tmp_"))
        org.junit.Assert.assertTrue(s.contains("renameTo") || s.contains("copyTo"))
    }
}
