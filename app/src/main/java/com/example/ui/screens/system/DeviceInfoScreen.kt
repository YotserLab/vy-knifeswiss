package com.example.ui.screens.system

import android.content.Context
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.deviceinfo.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showCopiedToast by remember { mutableStateOf(false) }

    // State for interactive hardware test results across session
    val testResults = remember { mutableStateMapOf<HardwareTestId, TestStatus>() }

    val tabs = listOf( // DEV-R02 14 abas
        DeviceInfoTab("DASHBOARD", Icons.Default.Dashboard, AccentCyan),
        DeviceInfoTab("DISPOSITIVO", Icons.Default.PhoneAndroid, AccentPurple),
        DeviceInfoTab("SISTEMA", Icons.Default.Settings, AccentBlue),
        DeviceInfoTab("CPU", Icons.Default.Memory, AccentGreen),
        DeviceInfoTab("BATERIA", Icons.Default.BatteryChargingFull, AccentOrange),
        DeviceInfoTab("REDE", Icons.Default.Wifi, AccentBlue),
        DeviceInfoTab("CONECTIVIDADE", Icons.Default.Bluetooth, AccentCyan),
        DeviceInfoTab("TELA", Icons.Default.Smartphone, AccentPink),
        DeviceInfoTab(Res.get(R.string.memoria), Icons.Default.SdCard, AccentPurple),
        DeviceInfoTab(Res.get(R.string.camera), Icons.Default.CameraAlt, AccentOrange),
        DeviceInfoTab(Res.get(R.string.termico), Icons.Default.Thermostat, Color(0xFFEF476F)),
        DeviceInfoTab("SENSORES", Icons.Default.Sensors, AccentCyan),
        DeviceInfoTab("APPS", Icons.Default.Apps, AccentGreen),
        DeviceInfoTab("TESTES", Icons.Default.CheckCircle, AccentGreen)
    )

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Res.get(R.string.device_info),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceLight
                        )
                        Text(
                            Res.get(R.string.sistema_hardware_testes),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = AccentCyan)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val report = generateComprehensiveSystemReport(context, testResults)
                        clipboardManager.setText(AnnotatedString(report))
                        showCopiedToast = true
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = Res.get(R.string.copiar_relatorio), tint = AccentCyan) // DEV-R06
                    }
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = AccentCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
        ) {
            // Scrollable High-Tech Tab Row (14 Tabs)
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkNavy,
                contentColor = AccentCyan,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = tabs[selectedTab].color,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selectedTab == index) tab.color else OnSurfaceMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    tab.title,
                                    color = if (selectedTab == index) tab.color else OnSurfaceMuted,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(DeepDark)
            ) {
                key(refreshTrigger) {
                    when (selectedTab) {
                        0 -> DashboardTabContent(context, testResults, onNavigateToTests = { selectedTab = 13 })
                        1 -> DeviceTabContent(context)
                        2 -> SystemTabContent(context)
                        3 -> CpuTabContent(context)
                        4 -> BatteryTabContent(context)
                        5 -> NetworkTabContent(context)
                        6 -> ConnectivityTabContent(context)
                        7 -> DisplayTabContent(context)
                        8 -> MemoryTabContent(context)
                        9 -> CameraTabContent(context)
                        10 -> ThermalTabContent(context)
                        11 -> SensorsTabContent(context)
                        12 -> AppsTabContent(context)
                        13 -> HardwareTestsContent(testResults, onUpdateResult = { test, status -> testResults[test] = status })
                    }
                }

                if (showCopiedToast) {
                    LaunchedEffect(showCopiedToast) {
                        delay(2500)
                    }
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        containerColor = AccentCyan,
                        contentColor = Color.Black
                    ) {
                        Text(Res.get(R.string.relatorio_completo_do_dispositivo_copiad), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class DeviceInfoTab(val title: String, val icon: ImageVector, val color: Color)

private fun generateComprehensiveSystemReport(
    context: Context,
    testResults: Map<HardwareTestId, TestStatus>
): String {
    val soc = DeviceInfoExtractor.detectSoc(context)
    val battery = DeviceInfoExtractor.getBatteryTelemetry(context)
    val net = DeviceInfoExtractor.getNetworkTelemetry(context)
    val conn = DeviceInfoExtractor.getConnectivityInfo(context)
    val display = DeviceInfoExtractor.getDisplayInfo(context)
    val mem = DeviceInfoExtractor.getMemoryTelemetry(context)
    val sys = DeviceInfoExtractor.getSystemDetails()
    val passedTests = testResults.values.count { it == TestStatus.PASSED }

    return """
        =====================================================
                    DEVICE INFO COMPLETE REPORT
        =====================================================
        ${Res.get(R.string.data_do_relatorio)}: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date())}
        ${Res.get(R.string.dispositivo)}: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
        ${Res.get(R.string.marca_placa)}: ${Build.BRAND} / ${Build.BOARD}
        
        [${Res.get(R.string.sistema_operacional).uppercase()}]
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        ${Res.get(R.string.patch_de_seguranca_2)}: ${Build.VERSION.SECURITY_PATCH ?: Res.get(R.string.n_a)}
        Kernel: ${sys["Kernel"]}
        SELinux: ${sys[Res.get(R.string.selinux)]} | Root: ${sys[Res.get(R.string.acesso_root)]}
        Uptime: ${sys["Uptime"]}
        
        [PROCESSADOR & GPU]
        SoC: ${soc.vendorName} ${soc.modelName}
        ${Res.get(R.string.arquitetura_do_so)}: ${soc.architecture} (${soc.abiList})
        ${Res.get(R.string.nucleos_ativos)}: ${soc.coresCount} Cores | ${Res.get(R.string.governador_de_frequencia)}: ${soc.governor}
        GPU: ${soc.gpuRenderer} (${soc.gpuVendor})
        OpenGL ES: ${soc.openGlVersion} | Vulkan: ${if (soc.vulkanSupported) Res.get(R.string.sim) else Res.get(R.string.nao)}
        
        ${Res.get(R.string.sec_memoria_armazenamento)}
        ${Res.get(R.string.ram_total)}: ${String.format(Locale.US, "%.2f", mem.ramTotalGb)} GB (${Res.get(R.string.usada)}: ${mem.ramUsedPercent}%)
        ${Res.get(R.string.armazenamento_interno)}: ${String.format(Locale.US, "%.2f", mem.internalTotalGb)} GB (${Res.get(R.string.usado)}: ${mem.internalUsedPercent}%)
        
        [${Res.get(R.string.bateria).uppercase()}]
        ${Res.get(R.string.nivel)}: ${battery.level}% | Status: ${battery.status} | ${Res.get(R.string.saude)}: ${battery.health}
        ${Res.get(R.string.tensao)}: ${battery.voltageMv} mV | Temp: ${String.format(Locale.US, "%.1f", battery.temperatureC)} °C
        ${Res.get(R.string.capacidade_estimada)}: ${if (battery.capacityMah > 0) Res.get(R.string.x_mah, (battery.capacityMah)) else Res.get(R.string.n_a)}
        
        ${Res.get(R.string.sec_tela)}
        ${Res.get(R.string.resolucao)}: ${display.widthPixels} x ${display.heightPixels} px (${display.densityDpi} dpi)
        ${Res.get(R.string.taxa_de_atualizacao)}: ${display.refreshRateHz} Hz | ${Res.get(R.string.proporcao_aspect_ratio)}: ${display.aspectRatio}
        ${Res.get(R.string.suporte_a_hdr)}: ${if (display.isHdrSupported) display.hdrCapabilities else "SDR"}
        
        ${Res.get(R.string.sec_rede_conectividade)}
        ${Res.get(R.string.conexao_label)}: ${net.connectionType} (${if (net.isConnected) "Online" else "Offline"})
        IPv4: ${net.ipv4} | Gateway: ${net.gateway}
        Wi-Fi: ${net.ssid} (${net.signalDbm} dBm, ${net.linkSpeedMbps} Mbps)
        Bluetooth BLE: ${if (conn.bleSupported) Res.get(R.string.sim) else Res.get(R.string.nao)} | NFC: ${if (conn.nfcSupported) Res.get(R.string.sim) else Res.get(R.string.nao)}
        USB Host (OTG): ${if (conn.usbHostSupported) Res.get(R.string.sim) else Res.get(R.string.nao)}
        
        [${Res.get(R.string.testes_de_hardware).uppercase()}]
        ${Res.get(R.string.sucesso_x_de_x_testes_aprovados, passedTests, HardwareTestId.values().size)}
        =====================================================
    """.trimIndent()
}
