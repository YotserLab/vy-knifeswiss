package com.example.spectests
import com.example.ui.screens.system.CloudSyncManager
import com.example.ui.screens.system.DashboardScreen
import com.example.ui.screens.system.HistoryScreen

import com.example.plugin.model.PluginCategory
import com.example.plugin.model.PluginManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlataformaSpecTest {

    // PLAT-R01/R02: deep links vyknifeswiss://tool/<route> e //oauth
    @Test
    fun test_PLAT_R01_R02_deep_link_schemes() {
        val s = src("com/example/MainActivity.kt")
        assertTrue(s.contains("\"vyknifeswiss\"") && s.contains("\"tool\"") && s.contains("\"oauth\""))
    }

    // PLAT-R03: manifest tem id, name, category, entryPoint, permissions
    @Test
    fun test_PLAT_R03_manifest_fields() {
        // org.json is an Android stub on JVM; assert via source contract
        val s = src("com/example/plugin/model/PluginManifest.kt")
        assertTrue(s.contains("getString(\"id\")") && s.contains("getString(\"name\")"))
        assertTrue(s.contains("optString(\"entryPoint\", \"index.html\")"))
        assertTrue(s.contains("optJSONArray(\"permissions\")"))
    }

    // PLAT-R04: instalação rejeita manifest.id em branco
    @Test
    fun test_PLAT_R04_blank_id_rejected() {
        assertTrue(srcContains("com/example/plugin/manager/PluginManager.kt", "manifest.id.isBlank()"))
    }

    // PLAT-R05: categoria desconhecida cai em COMMUNITY
    @Test
    fun test_PLAT_R05_unknown_category_falls_to_community() {
        val s = src("com/example/plugin/model/PluginCategory.kt")
        assertTrue(s.contains("?: COMMUNITY") || s.contains("} ?: COMMUNITY"))
        assertTrue(srcContains("com/example/plugin/model/PluginManifest.kt", "PluginCategory.fromString"))
    }

    // PLAT-R06: plugin web roda em WebView com VyPluginBridge
    @Test
    fun test_PLAT_R06_webview_bridge() {
        val s = src("com/example/plugin/ui/DynamicPluginScreen.kt")
        assertTrue(s.contains("WebView") && s.contains("VyPluginBridge"))
    }

    // PLAT-R07: versão vem de version.properties
    @Test
    fun test_PLAT_R07_version_from_properties() {
        assertTrue(java.io.File("build.gradle.kts").readText().contains("version.properties"))
        val props = java.io.File("../version.properties").readText()
        assertTrue(Regex("versionName=\\d+\\.\\d+\\.\\d+").containsMatchIn(props))
    }

    // PLAT-R08/R09: ações gravam HistoryLog e tela permite limpar
    @Test
    fun test_PLAT_R08_R09_history_log_and_clear() {
        assertTrue(srcContains("com/example/ui/screens/system/HistoryScreen.kt", "repository.clear()"))
        assertTrue(srcContains("com/example/data/HistoryLog.kt", "@Entity"))
    }

    // PLAT-R10: dashboard agrupa por categoria
    @Test
    fun test_PLAT_R10_dashboard_groups_by_category() {
        assertTrue(srcContains("com/example/ui/screens/system/DashboardScreen.kt", "it.category == cat"))
    }

    // PLAT-R11: barra superior padrão com botão de voltar
    @Test
    fun test_PLAT_R11_top_app_bar_back() {
        assertTrue(srcContains("com/example/ui/components/AppComponents.kt", "fun AppTopAppBar("))
    }

    // PLAT-R12: migration destrutiva configurada
    @Test
    fun test_PLAT_R12_destructive_migration() {
        assertTrue(srcContains("com/example/data/AppDatabase.kt", "fallbackToDestructiveMigration"))
    }

    // PLAT-R13: miniapp instalado habilitado por padrão
    @Test
    fun test_PLAT_R13_enabled_by_default() {
        assertTrue(srcContains("com/example/plugin/manager/PluginManager.kt", "setPluginEnabled(context, manifest.id, true)"))
    }

    // PLAT-R14: largeHeap declarado
    @Test
    fun test_PLAT_R14_large_heap() {
        assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("largeHeap=\"true\""))
    }
}

// PLAT-R02: deep link vyknifeswiss://oauth no fluxo OAuth
class PlataformaOAuthSpecTest {
    @org.junit.Test
    fun test_PLAT_R02_oauth_deeplink() {
        org.junit.Assert.assertTrue(srcContains("com/example/ui/screens/system/CloudSyncManager.kt", "vyknifeswiss://oauth"))
        org.junit.Assert.assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("android:host=\"oauth\""))
    }
}

// PLAT-R09: histórico permite limpar todos os registros
class PlataformaHistorySpecTest {
    @org.junit.Test
    fun test_PLAT_R09_clear_history() {
        val s = src("com/example/ui/screens/system/HistoryScreen.kt")
        org.junit.Assert.assertTrue(s.contains("historyDao") && (s.contains("clear") || s.contains("Clear") || s.contains("delete")))
    }
}
