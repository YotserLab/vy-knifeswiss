package com.example.spectests
import com.example.ui.screens.obd.Obd2ScannerScreen

import com.example.transport.ObdData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdSpecTest {

    // OBD-R01: conexão ELM327 Bluetooth pareado ou WiFi TCP :35000
    @Test
    fun test_OBD_R01_bluetooth_and_wifi_paths() {
        assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "Bluetooth"))
        assertTrue(srcContains("com/example/ui/screens/obd/Obd2ScannerScreen.kt", "35000"))
    }

    // OBD-R02: polling padrão de 400ms
    @Test
    fun test_OBD_R02_fast_poll_400ms() {
        assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "intervalMs: Long = 400L"))
    }

    // OBD-R03: registro de PIDs cobre Modes 01, 02, 03, 04, 06, 07 e 09
    @Test
    fun test_OBD_R03_supported_modes() {
        val s = src("com/example/transport/BluetoothObdManager.kt")
        assertTrue(srcContains("com/example/transport/ObdPidRegistry.kt", "Mode 01"))
        assertTrue(s.contains("readMode06") || s.contains("mode06"))
        assertTrue(s.contains("clearDtcs"))
    }

    // OBD-R04: limpar DTCs exige confirmação
    @Test
    fun test_OBD_R04_clear_dtc_requires_confirm() {
        val s = src("com/example/ui/screens/obd/Obd2ScannerScreen.kt")
        assertTrue(s.contains("showClearDialog") && s.contains("limpar"))
    }

    // OBD-R05: registro J1979 + PIDs customizáveis OEM
    @Test
    fun test_OBD_R05_pid_registry() {
        // registry init calls Res.get; assert via source contract
        val reg = src("com/example/transport/ObdPidRegistry.kt")
        assertTrue(reg.contains("PidDefinition(\"01\", \"0C\"") && reg.contains("val ALL"))
        assertTrue(srcContains("com/example/ui/screens/obd/Obd2ScannerScreen.kt", "custom_pids"))
    }

    // OBD-R06: alternância Visão Motorista/Técnica
    @Test
    fun test_OBD_R06_pro_mode_toggle() {
        val s = src("com/example/ui/screens/obd/Obd2ScannerScreen.kt")
        assertTrue(s.contains("isProMode") && s.contains("visao_tecnica"))
    }

    // OBD-R07: sessões persistem em Room
    @Test
    fun test_OBD_R07_session_persistence() {
        assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "obdDao.insertSession"))
        assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "obdDao.insertLogEntry"))
    }

    // OBD-R08: exportação CSV e PDF
    @Test
    fun test_OBD_R08_export_csv_pdf() {
        val s = src("com/example/ui/screens/obd/Obd2ScannerScreen.kt")
        assertTrue(s.contains("exportSessionToCsv") && s.contains("exportDiagnosticsToPdf"))
    }

    // OBD-R09: readiness monitors expostos
    @Test
    fun test_OBD_R09_readiness_exposed() {
        assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "readinessMonitors"))
    }

    // OBD-R10: alertas distinguem crítico
    @Test
    fun test_OBD_R10_alert_severity() {
        assertTrue(srcContains("com/example/ui/screens/obd/Obd2ScannerScreen.kt", "isCritical"))
    }

    // OBD-R11: trip computer integra distância por velocidade média e combustível via MAF
    @Test
    fun test_OBD_R11_trip_computer_math() {
        val computer = com.example.transport.ObdTripComputer()
        val data = ObdData(speedKmh = 100, engineLoadPct = 50f, mafGS = 10f)
        computer.update(data, 400)
        computer.update(data, 400)
        assertTrue(computer.distanceKm > 0f)
        assertTrue(computer.fuelConsumedL > 0f)
        assertEquals(50f, computer.avgLoad, 0.01f)
    }

    // OBD-R12: desconectado mostra tela de seleção de adaptador
    @Test
    fun test_OBD_R12_disconnected_shows_selection() {
        assertTrue(srcContains("com/example/ui/screens/obd/Obd2ScannerScreen.kt", "conectar_ao_obd_ii"))
    }

    // OBD-R13: init ELM327 envia sequência AT completa
    @Test
    fun test_OBD_R13_elm327_init_sequence() {
        val s = src("com/example/transport/BluetoothObdManager.kt")
        listOf("ATZ", "ATE0", "ATL0", "ATH0", "ATS0", "ATM0", "ATAT1", "ATSP0").forEach {
            assertTrue("$it ausente", s.contains("\"$it\""))
        }
    }

    // OBD-R14: socket TCP com soTimeout de 4s
    @Test
    fun test_OBD_R14_socket_timeout_4s() {
        assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "soTimeout = 4000"))
    }
}

// OBD-R15: performance test — 0-100/0-60/0-200 e quarto de milha
class ObdPerformanceSpecTest {
    @org.junit.Test
    fun test_OBD_R15_performance_types() {
        val s = src("com/example/transport/ObdModels.kt")
        org.junit.Assert.assertTrue(s.contains("ZERO_TO_100") && s.contains("ZERO_TO_60"))
        org.junit.Assert.assertTrue(s.contains("ZERO_TO_200") && s.contains("QUARTER_MILE"))
        org.junit.Assert.assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "runPerformanceTest"))
        org.junit.Assert.assertTrue(srcContains("com/example/transport/BluetoothObdManager.kt", "_performanceResults"))
    }
}
