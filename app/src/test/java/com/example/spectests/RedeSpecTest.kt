package com.example.spectests
import com.example.ui.screens.network.PingScreen
import com.example.ui.screens.network.PortScannerScreen
import com.example.ui.screens.network.SpeedTestScreen
import com.example.ui.screens.network.SubnetScannerScreen
import com.example.ui.screens.network.WifiMonitorScreen
import com.example.utils.net.NetworkUtils
import com.example.utils.net.SpeedTestUtils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedeSpecTest {

    // RED-R01: scan deriva /24 do IPv4 local
    @Test
    fun test_RED_R01_subnet_24_contract() {
        assertTrue(srcContains("com/example/ui/screens/network/SubnetScannerScreen.kt", "substringBeforeLast(\".\")"))
    }

    // RED-R02: descoberta usa ping com 4 pacotes
    @Test
    fun test_RED_R02_ping_count_4() {
        assertTrue(srcContains("com/example/ui/screens/network/SubnetScannerScreen.kt", "NetworkUtils.ping(ip, 4)"))
    }

    // RED-R03: resultados de ping são cacheados com timestamp
    @Test
    fun test_RED_R03_scan_cache() {
        assertTrue(srcContains("com/example/ui/screens/network/SubnetScannerScreen.kt", "ScanCache"))
    }

    // RED-R04: lista de portas comuns tem exatamente 15 portas
    @Test
    fun test_RED_R04_common_ports_count() {
        val s = src("com/example/ui/screens/network/PortScannerScreen.kt")
        val ports = Regex("listOf\\(([^)]*)\\)").find(s.substring(s.indexOf("commonPorts")))!!.groupValues[1]
        assertEquals(15, ports.split(",").size)
    }

    // RED-R05: connect com timeout de 600ms
    @Test
    fun test_RED_R05_port_connect_timeout_600() {
        assertTrue(srcContains("com/example/ui/screens/network/PortScannerScreen.kt", "), 600)"))
    }

    // RED-R06: porta aberta é rotulada por mapa de serviços
    @Test
    fun test_RED_R06_service_name_map() {
        val s = src("com/example/utils/net/NetworkUtils.kt")
        assertTrue(s.contains("443 to \"HTTPS\"") && s.contains("3389 to \"RDP\""))
    }

    // RED-R07: PingScreen executa ping no host informado
    @Test
    fun test_RED_R07_ping_screen() {
        assertTrue(srcContains("com/example/ui/screens/network/PingScreen.kt", "NetworkUtils.ping(host)"))
    }

    // RED-R08/R09/R10: speed test reporta ping/download/upload/jitter, payload 2MB, timeout 3s
    @Test
    fun test_RED_R08_R09_R10_speed_test_contract() {
        val s = src("com/example/utils/net/SpeedTestUtils.kt")
        assertTrue(s.contains("downloadMbps") && s.contains("uploadMbps") && s.contains("jitterMs"))
        assertTrue(s.contains("2 * 1024 * 1024") && s.contains("connectTimeout = 3000"))
    }

    // RED-R11: WifiMonitor exige permissão de localização
    @Test
    fun test_RED_R11_wifi_requires_location() {
        assertTrue(srcContains("com/example/ui/screens/network/WifiMonitorScreen.kt", "ACCESS_FINE_LOCATION"))
    }

    // RED-R12: rotas _with_host pré-preenchem o host
    @Test
    fun test_RED_R12_deep_link_with_host() {
        val s = src("com/example/plugin/registry/NativePluginRegistry.kt")
        assertTrue(s.contains("port_scanner_with_host?host={host}") && s.contains("network_test_with_host?host={host}"))
    }

    // RED-R13: scans são sob demanda — nenhum agendamento em background nas telas de rede
    @Test
    fun test_RED_R13_no_scheduled_scans() {
        val files = listOf("SubnetScannerScreen", "PortScannerScreen", "PingScreen", "SpeedTestScreen")
        files.forEach { f ->
            val s = src("com/example/ui/screens/network/$f.kt")
            assertTrue(!s.contains("WorkManager") && !s.contains("AlarmManager") && !s.contains("schedulePeriodic"))
        }
    }

    // RED-R14: fabricante do adaptador vem da tabela OUI
    @Test
    fun test_RED_R14_oui_vendor_lookup() {
        val s = src("com/example/utils/net/NetworkUtils.kt")
        assertTrue(s.contains("ouiVendors") && s.contains("\"2C:54:2D\" to \"Intel\""))
    }
}

// RED-R09: upload usa payload de 2MB
class RedeUploadPayloadSpecTest {
    @org.junit.Test
    fun test_RED_R09_upload_2mb() {
        org.junit.Assert.assertTrue(srcContains("com/example/utils/net/SpeedTestUtils.kt", "2 * 1024 * 1024"))
    }
}

// RED-R10: conexões do SpeedTest com connectTimeout 3s
class RedeSpeedTimeoutSpecTest {
    @org.junit.Test
    fun test_RED_R10_connect_timeout_3s() {
        org.junit.Assert.assertTrue(srcContains("com/example/utils/net/SpeedTestUtils.kt", "connectTimeout = 3000"))
    }
}
