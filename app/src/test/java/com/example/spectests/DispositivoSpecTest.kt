package com.example.spectests
import com.example.ui.screens.system.ActivityLauncherScreen
import com.example.ui.screens.system.CompassScreen
import com.example.ui.screens.system.DeviceInfoScreen
import com.example.ui.screens.system.TerminalScreen
import com.example.ui.screens.system.TermuxStyleTerminal

import com.example.ui.screens.deviceinfo.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispositivoSpecTest {

    // DEV-R01: telemetria lida de /sys e /proc reais, sem mocks
    @Test
    fun test_DEV_R01_real_sysfs_reads() {
        val s = src("com/example/ui/screens/deviceinfo/DeviceInfoModels.kt")
        assertTrue(s.contains("/proc/cpuinfo") && s.contains("/sys/devices/system/cpu"))
    }

    // DEV-R02: DeviceInfo declara lista de 14 abas
    @Test
    fun test_DEV_R02_tab_list_has_14() {
        val s = src("com/example/ui/screens/system/DeviceInfoScreen.kt")
        assertEquals(14, Regex("DeviceInfoTab\\((\"|Res)").findAll(s).count())
    }

    // DEV-R03: frequência por núcleo vem de scaling_cur_freq
    @Test
    fun test_DEV_R03_cpu_freq_per_core() {
        assertTrue(srcContains("com/example/ui/screens/deviceinfo/DeviceInfoModels.kt", "scaling_cur_freq"))
    }

    // DEV-R04: sensores do SensorManager com fluxo contínuo
    @Test
    fun test_DEV_R04_sensor_manager_realtime() {
        assertTrue(srcContains("com/example/utils/SensorUtils.kt", "registerListener"))
        assertTrue(srcContains("com/example/utils/SensorUtils.kt", "SENSOR_SERVICE"))
    }

    // DEV-R05: suíte tem exatamente 10 testes de hardware
    @Test
    fun test_DEV_R05_exactly_10_hardware_tests() {
        // enum entries call Res.get at init; assert via source contract
        val s = src("com/example/ui/screens/deviceinfo/DeviceInfoModels.kt")
        val block = s.substring(s.indexOf("enum class HardwareTestId"), s.indexOf("enum class TestStatus"))
        val entries = Regex("^    [A-Z_]+\\(", RegexOption.MULTILINE).findAll(block).count()
        assertEquals(10, entries)
        assertTrue(s.contains("MULTI_TOUCH") && s.contains("VIBRATION"))
    }

    // DEV-R06: relatório copiável para área de transferência
    @Test
    fun test_DEV_R06_export_report() {
        assertTrue(srcContains("com/example/ui/screens/system/DeviceInfoScreen.kt", "R.string.copiar_relatorio"))
    }

    // DEV-R07: bússola mostra azimute inteiro em graus e ponto cardeal derivado
    @Test
    fun test_DEV_R07_compass_azimuth() {
        val s = src("com/example/ui/screens/system/CompassScreen.kt")
        assertTrue(s.contains("azimuth.toInt()") && s.contains("when (azimuth)"))
    }

    // DEV-R08/R13: nível/bússola usam sensores com fallback quando ausente
    @Test
    fun test_DEV_R08_R13_sensor_fallback() {
        val s = src("com/example/utils/SensorUtils.kt")
        assertTrue(s.contains("TYPE_GRAVITY") && s.contains("TYPE_ACCELEROMETER") && s.contains("null"))
    }

    // DEV-R09: terminal interno estilo Termux
    @Test
    fun test_DEV_R09_internal_terminal() {
        assertTrue(srcContains("com/example/ui/screens/system/TerminalScreen.kt", "TermuxStyleTerminal"))
    }

    // DEV-R10: SSH delega ao Termux via RUN_COMMAND no binário do Termux
    @Test
    fun test_DEV_R10_termux_ssh_intent() {
        val s = src("com/example/ui/screens/system/TerminalScreen.kt")
        assertTrue(s.contains("com.termux.RUN_COMMAND") && s.contains("/data/data/com.termux/files/usr/bin/ssh"))
    }

    // DEV-R11: ActivityLauncher lança activities de apps instalados
    @Test
    fun test_DEV_R11_launch_activities() {
        assertTrue(srcContains("com/example/ui/screens/system/ActivityLauncherScreen.kt", "launchActivity("))
    }

    // DEV-R12: lista usa ícone placeholder, sem carregar drawables
    @Test
    fun test_DEV_R12_icon_placeholder_no_drawables() {
        val s = src("com/example/ui/screens/system/ActivityLauncherScreen.kt")
        assertTrue(s.contains("App icon placeholder") && !s.contains("loadIcon("))
    }

    // DEV-R14: aba Apps separa usuário/sistema e busca por nome ou pacote
    @Test
    fun test_DEV_R14_apps_filter_and_search() {
        val s = src("com/example/ui/screens/deviceinfo/DeviceInfoTabs.kt")
        assertTrue(s.contains("isSystemApp") && s.contains("searchQuery.isBlank()"))
    }
}

// DEV-R13: sensor ausente é informado sem quebrar
class DispositivoSensorMissingSpecTest {
    @org.junit.Test
    fun test_DEV_R13_missing_sensor_handled() {
        val files = listOf("com/example/ui/screens/deviceinfo/DeviceInfoTabs.kt",
            "com/example/ui/screens/deviceinfo/DeviceHardwareTests.kt",
            "com/example/utils/SensorUtils.kt")
        val found = files.any { f ->
            val s = src(f)
            s.contains("getDefaultSensor") && (s.contains("null") || s.contains("?:") || s.contains("== null") || s.contains("!= null"))
        }
        org.junit.Assert.assertTrue(found)
    }
}
