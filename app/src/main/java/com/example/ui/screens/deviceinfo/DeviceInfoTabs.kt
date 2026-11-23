package com.example.ui.screens.deviceinfo

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import com.example.R
import com.example.Res

// High-tech Theme Colors matching Device Info (com.ytheekshana.deviceinfo)
val CardBackground = Color(0xFF161922)
val CardBorder = Color(0xFF262B3A)
val AccentPurple = Color(0xFF9D4EDD)
val AccentBlue = Color(0xFF3A86FF)
val AccentGreen = Color(0xFF06D6A0)
val AccentOrange = Color(0xFFFFB703)
val AccentPink = Color(0xFFFF006E)
val AccentCyan = Color(0xFF00F5D4)

// ==========================================
// 1. DASHBOARD TAB
// ==========================================
@Composable
fun DashboardTabContent(
    context: Context,
    testResults: Map<HardwareTestId, TestStatus>,
    onNavigateToTests: () -> Unit
) {
    var memTelemetry by remember { mutableStateOf(DeviceInfoExtractor.getMemoryTelemetry(context)) }
    var batteryTelemetry by remember { mutableStateOf(DeviceInfoExtractor.getBatteryTelemetry(context)) }
    val displayInfo = remember(context) { DeviceInfoExtractor.getDisplayInfo(context) }
    val appsList = remember(context) { DeviceInfoExtractor.getInstalledApps(context) }
    val userAppsCount = remember(appsList) { appsList.count { !it.isSystemApp } } // DEV-R14
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensorsCount = remember(sensorManager) { sensorManager.getSensorList(Sensor.TYPE_ALL).size }

    var coresInfo by remember { mutableStateOf(DeviceInfoExtractor.getCoresInfo()) }

    LaunchedEffect(Unit) {
        while (true) {
            coresInfo = DeviceInfoExtractor.getCoresInfo()
            memTelemetry = DeviceInfoExtractor.getMemoryTelemetry(context)
            batteryTelemetry = DeviceInfoExtractor.getBatteryTelemetry(context)
            delay(1500)
        }
    }

    val passedTests = testResults.values.count { it == TestStatus.PASSED }
    val totalTests = HardwareTestId.values().size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Circular Gauges Row (RAM & Storage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularGaugeCard(
                    modifier = Modifier.weight(1f),
                    title = "RAM",
                    subtitle = "${String.format(Locale.US, "%.1f", memTelemetry.ramUsedGb)} / ${String.format(Locale.US, "%.1f", memTelemetry.ramTotalGb)} GB",
                    percentage = memTelemetry.ramUsedPercent,
                    color = AccentPurple,
                    icon = Icons.Default.Memory
                )

                CircularGaugeCard(
                    modifier = Modifier.weight(1f),
                    title = "ARMAZENAMENTO",
                    subtitle = "${String.format(Locale.US, "%.1f", memTelemetry.internalUsedGb)} / ${String.format(Locale.US, "%.1f", memTelemetry.internalTotalGb)} GB",
                    percentage = memTelemetry.internalUsedPercent,
                    color = AccentBlue,
                    icon = Icons.Default.SdCard
                )
            }
        }

        item {
            // Live Meters (Battery & Cores Overview)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "BATERIA",
                    value = Res.get(R.string.x_37, (batteryTelemetry.level)),
                    progress = batteryTelemetry.level / 100f,
                    color = AccentOrange,
                    subtext = "${batteryTelemetry.status} • ${String.format(Locale.US, "%.1f", batteryTelemetry.temperatureC)}°C",
                    icon = Icons.Default.BatteryChargingFull
                )

                val onlineCores = coresInfo.count { it.isOnline }
                LinearMetricCard(
                    modifier = Modifier.weight(1f),
                    title = Res.get(R.string.cpu_cores),
                    value = Res.get(R.string.x_x_35, onlineCores, (coresInfo.size)),
                    progress = if (coresInfo.isNotEmpty()) onlineCores / coresInfo.size.toFloat() else 1f,
                    color = AccentGreen,
                    subtext = Res.get(R.string.nucleos_ativos),
                    icon = Icons.Default.DeveloperBoard
                )
            }
        }

        item {
            // Quick Grid Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBadgeCard(
                    modifier = Modifier.weight(1f),
                    label = "Sensores",
                    value = Res.get(R.string.x_38, sensorsCount),
                    icon = Icons.Default.Sensors,
                    color = AccentCyan
                )
                StatBadgeCard(
                    modifier = Modifier.weight(1f),
                    label = Res.get(R.string.apps_usuario),
                    value = Res.get(R.string.x_39, userAppsCount),
                    icon = Icons.Default.Apps,
                    color = AccentPink
                )
                StatBadgeCard(
                    modifier = Modifier.weight(1f),
                    label = Res.get(R.string.taxa_tela),
                    value = Res.get(R.string.x_hz, (displayInfo.refreshRateHz)),
                    icon = Icons.Default.Screenshot,
                    color = AccentPurple
                )
            }
        }

        item {
            // Test Suite Status Tile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBackground)
                    .border(1.dp, if (passedTests > 0) AccentGreen.copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(12.dp))
                    .clickable { onNavigateToTests() }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AccentGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(Res.get(R.string.testes_de_hardware), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(Res.get(R.string.x_de_x_testes_concluidos_com_sucesso, passedTests, totalTests), color = Color(0xFF8E99B0), fontSize = 11.sp)
                        }
                    }
                    Text(Res.get(R.string.abrir_testes), color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            SectionHeader(Res.get(R.string.especificacoes_rapidas))
            CardContainer {
                InfoRow("Dispositivo", Res.get(R.string.x_x_36, (Build.MANUFACTURER.capitalize(Locale.ROOT)), (Build.MODEL)))
                InfoRow(Res.get(R.string.placa_soc), Res.get(R.string.x_x_37, (Build.BOARD.uppercase()), (Build.HARDWARE)))
                InfoRow(Res.get(R.string.versao_do_android), Res.get(R.string.android_x_sdk_x, (Build.VERSION.RELEASE), (Build.VERSION.SDK_INT)))
                InfoRow("Kernel", System.getProperty("os.version") ?: Res.get(R.string.linux_kernel))
            }
        }
    }
}

// ==========================================
// 2. DISPOSITIVO TAB
// ==========================================
@Composable
fun DeviceTabContent(context: Context) {
    val displayInfo = remember(context) { DeviceInfoExtractor.getDisplayInfo(context) }
    val connInfo = remember(context) { DeviceInfoExtractor.getConnectivityInfo(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.identificacao_do_hardware))
            CardContainer {
                InfoRow(Res.get(R.string.nome_do_dispositivo), Build.MODEL)
                InfoRow("Modelo", Build.MODEL)
                InfoRow("Fabricante", Build.MANUFACTURER)
                InfoRow("Dispositivo", Build.DEVICE)
                InfoRow(Res.get(R.string.placa_board), Build.BOARD)
                InfoRow("Hardware", Build.HARDWARE ?: Res.get(R.string.indisponivel))
                InfoRow("Marca", Build.BRAND)
                InfoRow("Produto", Build.PRODUCT)
            }
        }

        item {
            SectionHeader(Res.get(R.string.construcao_recursos_fisicos))
            CardContainer {
                InfoRow(Res.get(R.string.resolucao_de_tela), Res.get(R.string.x_x_x_px_2, (displayInfo.widthPixels), (displayInfo.heightPixels)))
                InfoRow("Densidade", Res.get(R.string.x_dpi_xx, (displayInfo.densityDpi), (displayInfo.densityScale)))
                InfoRow(Res.get(R.string.diagonal_estimada), String.format(Locale.US, Res.get(R.string.n_1f_polegadas), displayInfo.physicalSizeInches))
                InfoRow(Res.get(R.string.suporte_a_usb_host), if (connInfo.usbHostSupported) Res.get(R.string.suportado_otg_ativo) else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.suporte_a_usb_accessory), if (connInfo.usbAccessorySupported) "Suportado" else Res.get(R.string.nao_suportado))
            }
        }

        item {
            SectionHeader(Res.get(R.string.impressao_digital_build))
            CardContainer {
                InfoRow(Res.get(R.string.build_fingerprint), Build.FINGERPRINT)
                InfoRow(Res.get(R.string.host_da_compilacao), Build.HOST)
                InfoRow(Res.get(R.string.usuario_da_build), Build.USER)
                InfoRow("Tags", Build.TAGS)
            }
        }
    }
}

// ==========================================
// 3. SISTEMA TAB
// ==========================================
@Composable
fun SystemTabContent(context: Context) {
    val sysDetails = remember { DeviceInfoExtractor.getSystemDetails() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.sistema_operacional))
            CardContainer {
                InfoRow(Res.get(R.string.versao_do_android), sysDetails[Res.get(R.string.versao_do_android)] ?: "Android")
                InfoRow(Res.get(R.string.nivel_de_api_sdk), sysDetails[Res.get(R.string.nivel_de_api_sdk)] ?: "34")
                InfoRow(Res.get(R.string.patch_de_seguranca_2), sysDetails[Res.get(R.string.patch_de_seguranca_2)] ?: Res.get(R.string.indisponivel))
                InfoRow(Res.get(R.string.id_de_build_display), sysDetails[Res.get(R.string.id_de_build_display)] ?: "Build")
                InfoRow("Bootloader", sysDetails["Bootloader"] ?: Res.get(R.string.desconhecido))
                InfoRow(Res.get(R.string.baseband_radio), sysDetails[Res.get(R.string.baseband_radio)] ?: Res.get(R.string.indisponivel))
            }
        }

        item {
            SectionHeader(Res.get(R.string.seguranca_treble))
            CardContainer {
                InfoRow(Res.get(R.string.selinux), sysDetails[Res.get(R.string.selinux)] ?: "Enforcing")
                InfoRow(Res.get(R.string.project_treble), if (sysDetails[Res.get(R.string.treble_suportado)] == "true") Res.get(R.string.habilitado) else Res.get(R.string.desabilitado))
                InfoRow(Res.get(R.string.atualizacoes_a_b_seamless), if (sysDetails[Res.get(R.string.a_b_seamless_updates)] == "true") Res.get(R.string.suportado) else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.acesso_root), sysDetails[Res.get(R.string.acesso_root)] ?: Res.get(R.string.nao_detectado_2))
            }
        }

        item {
            SectionHeader(Res.get(R.string.kernel_runtime))
            CardContainer {
                InfoRow(Res.get(R.string.versao_do_kernel), sysDetails["Kernel"] ?: "Linux")
                InfoRow(Res.get(R.string.arquitetura_do_so), System.getProperty("os.arch") ?: "aarch64")
                InfoRow(Res.get(R.string.java_vm), sysDetails[Res.get(R.string.java_vm)] ?: "ART")
                InfoRow(Res.get(R.string.tempo_de_atividade_uptime), sysDetails["Uptime"] ?: Res.get(R.string.n_0h_0m))
            }
        }
    }
}

// ==========================================
// 4. CPU TAB
// ==========================================
@Composable
fun CpuTabContent(context: Context) {
    val socInfo = remember(context) { DeviceInfoExtractor.detectSoc(context) }
    var coresInfo by remember { mutableStateOf(DeviceInfoExtractor.getCoresInfo()) }

    LaunchedEffect(Unit) {
        while (true) {
            coresInfo = DeviceInfoExtractor.getCoresInfo()
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // SoC Hero Banner with Official Logo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (socInfo.logoRes != null) {
                        Image(
                            painter = painterResource(id = socInfo.logoRes),
                            contentDescription = socInfo.vendorName,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(36.dp))
                        }
                    }

                    Column {
                        Text(socInfo.vendorName, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(socInfo.modelName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(Res.get(R.string.x_nucleos_x, (socInfo.coresCount), (socInfo.architecture)), color = Color(0xFF8E99B0), fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            SectionHeader(Res.get(R.string.arquitetura_governador))
            CardContainer {
                InfoRow(Res.get(R.string.processador_soc), socInfo.modelName)
                InfoRow("Arquitetura", socInfo.architecture)
                InfoRow(Res.get(R.string.conjunto_de_instrucoes_abis), socInfo.abiList)
                InfoRow(Res.get(R.string.governador_de_frequencia), socInfo.governor)
            }
        }

        item {
            SectionHeader(Res.get(R.string.frequencia_dos_nucleos_em_tempo_real))
            CardContainer {
                coresInfo.forEach { core ->
                    val ratio = if (core.maxMhz > 0 && core.currentMhz > 0) {
                        (core.currentMhz / core.maxMhz.toFloat()).coerceIn(0.05f, 1.0f)
                    } else 0f

                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(Res.get(R.string.nucleo_x, (core.coreIndex)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (core.isOnline && core.currentMhz > 0) Res.get(R.string.x_mhz, (core.currentMhz)) else Res.get(R.string.offline_standby),
                                color = if (core.isOnline && core.currentMhz > 0) AccentGreen else Color(0xFF8E99B0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentGreen,
                            trackColor = CardBorder
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(Res.get(R.string.graficos_renderizador_gpu))
            CardContainer {
                InfoRow(Res.get(R.string.vendedor_gpu), socInfo.gpuVendor)
                InfoRow(Res.get(R.string.renderizador_gpu), socInfo.gpuRenderer)
                InfoRow(Res.get(R.string.suporte_a_vulkan), if (socInfo.vulkanSupported) Res.get(R.string.suportado_nativo) else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.versao_opengl_es), socInfo.openGlVersion)
            }
        }
    }
}

// ==========================================
// 5. BATERIA TAB
// ==========================================
@Composable
fun BatteryTabContent(context: Context) {
    var battery by remember { mutableStateOf(DeviceInfoExtractor.getBatteryTelemetry(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            battery = DeviceInfoExtractor.getBatteryTelemetry(context)
            delay(1500)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Hero Battery Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBackground)
                    .border(1.dp, AccentOrange.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { battery.level / 100f },
                            modifier = Modifier.size(90.dp),
                            color = AccentOrange,
                            strokeWidth = 8.dp,
                            trackColor = CardBorder,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            Res.get(R.string.x_40, (battery.level)),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Column {
                        Text(Res.get(R.string.status_x, (battery.status)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(Res.get(R.string.saude_x, (battery.health)), color = AccentGreen, fontSize = 13.sp)
                        Text(Res.get(R.string.fonte_x, (battery.plugged)), color = Color(0xFF8E99B0), fontSize = 13.sp)
                        if (battery.currentNowMa != 0) {
                            Text(Res.get(R.string.corrente_x_ma, (battery.currentNowMa)), color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(Res.get(R.string.telemetria_eletrica))
            CardContainer {
                InfoRow("Voltagem", Res.get(R.string.x_mv, (battery.voltageMv)))
                InfoRow("Temperatura", "${String.format(Locale.US, "%.1f", battery.temperatureC)} °C")
                if (battery.powerWatts > 0) {
                    InfoRow(Res.get(R.string.potencia_instantanea), String.format(Locale.US, "%.2f Watts", battery.powerWatts))
                }
                if (battery.capacityMah > 0) {
                    InfoRow(Res.get(R.string.capacidade_estimada), Res.get(R.string.x_mah, (battery.capacityMah)))
                }
                InfoRow("Tecnologia", battery.technology)
                InfoRow(Res.get(R.string.saude), battery.health)
            }
        }
    }
}

// ==========================================
// 6. REDE TAB
// ==========================================
@Composable
fun NetworkTabContent(context: Context) {
    val net = remember(context) { DeviceInfoExtractor.getNetworkTelemetry(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.conexao_ativa))
            CardContainer {
                InfoRow(Res.get(R.string.tipo_de_conexao), net.connectionType)
                InfoRow("Status", if (net.isConnected) Res.get(R.string.conectado_a_internet) else "Desconectado")
                InfoRow(Res.get(R.string.endereco_ipv4), net.ipv4)
                InfoRow(Res.get(R.string.endereco_ipv6), net.ipv6)
                InfoRow("Gateway", net.gateway)
                InfoRow(Res.get(R.string.mascara_de_sub_rede), net.subnetMask)
                InfoRow(Res.get(R.string.dns_primario_1), net.dns1)
                InfoRow(Res.get(R.string.dns_secundario_2), net.dns2)
            }
        }

        if (net.connectionType == Res.get(R.string.wi_fi)) {
            item {
                SectionHeader(Res.get(R.string.detalhes_da_rede_wi_fi))
                CardContainer {
                    InfoRow(Res.get(R.string.nome_da_rede_ssid_2), net.ssid)
                    InfoRow("BSSID", net.bssid)
                    InfoRow(Res.get(R.string.sinal_rssi), Res.get(R.string.x_dbm_4, (net.signalDbm)))
                    InfoRow(Res.get(R.string.velocidade_do_link), Res.get(R.string.x_mbps_2, (net.linkSpeedMbps)))
                    InfoRow(Res.get(R.string.frequencia), Res.get(R.string.x_mhz_x, (net.frequencyMhz), (net.wifiBand)))
                    InfoRow(Res.get(R.string.canal_wi_fi), if (net.channel > 0) Res.get(R.string.canal_x, (net.channel)) else Res.get(R.string.n_a))
                }
            }
        }

        item {
            SectionHeader(Res.get(R.string.rede_celular_dados_moveis))
            CardContainer {
                InfoRow("Operadora", net.carrier)
                InfoRow(Res.get(R.string.tipo_de_rede_celular), net.mobileNetworkType)
                InfoRow("Roaming", if (net.isRoaming) Res.get(R.string.sim_ativo) else Res.get(R.string.nao))
            }
        }
    }
}

// ==========================================
// 7. CONECTIVIDADE TAB
// ==========================================
@Composable
fun ConnectivityTabContent(context: Context) {
    val conn = remember(context) { DeviceInfoExtractor.getConnectivityInfo(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader("Bluetooth")
            CardContainer {
                InfoRow(Res.get(R.string.bluetooth_suportado), if (conn.bluetoothSupported) Res.get(R.string.sim) else Res.get(R.string.nao))
                InfoRow(Res.get(R.string.bluetooth_low_energy_ble), if (conn.bleSupported) Res.get(R.string.sim) else Res.get(R.string.nao))
                InfoRow(Res.get(R.string.le_audio), if (conn.bleAudioSupported) Res.get(R.string.sim) else Res.get(R.string.nao))
                InfoRow(Res.get(R.string.multiple_advertisement), if (conn.multipleAdvertisementSupported) Res.get(R.string.sim) else Res.get(R.string.nao))
            }
        }

        item {
            SectionHeader(Res.get(R.string.wi_fi_p2p))
            CardContainer {
                InfoRow(Res.get(R.string.wi_fi_direct_p2p), if (conn.wifiDirectSupported) "Suportado" else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.wi_fi_aware_nan), if (conn.wifiAwareSupported) "Suportado" else Res.get(R.string.nao_suportado))
            }
        }

        item {
            SectionHeader(Res.get(R.string.nfc_uwb_sensores_especiais))
            CardContainer {
                InfoRow("NFC", if (conn.nfcSupported) Res.get(R.string.presente) else Res.get(R.string.ausente))
                InfoRow(Res.get(R.string.host_card_emulation_hce), if (conn.nfcHceSupported) Res.get(R.string.suportado) else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.ultra_wideband_uwb), if (conn.uwbSupported) Res.get(R.string.suportado) else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.emissor_infravermelho_ir), if (conn.irBlasterSupported) Res.get(R.string.presente) else Res.get(R.string.ausente))
            }
        }

        item {
            SectionHeader(Res.get(R.string.conexoes_usb))
            CardContainer {
                InfoRow(Res.get(R.string.usb_host_otg), if (conn.usbHostSupported) "Suportado" else Res.get(R.string.nao_suportado))
                InfoRow(Res.get(R.string.usb_accessory), if (conn.usbAccessorySupported) "Suportado" else Res.get(R.string.nao_suportado))
            }
        }
    }
}

// ==========================================
// 8. TELA TAB
// ==========================================
@Composable
fun DisplayTabContent(context: Context) {
    val display = remember(context) { DeviceInfoExtractor.getDisplayInfo(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.especificacoes_da_tela))
            CardContainer {
                InfoRow(Res.get(R.string.resolucao_nativa), Res.get(R.string.x_x_x_px_3, (display.widthPixels), (display.heightPixels)))
                InfoRow(Res.get(R.string.densidade_dpi), Res.get(R.string.x_dpi, (display.densityDpi)))
                InfoRow(Res.get(R.string.escala_de_densidade), Res.get(R.string.xx_8, (display.densityScale)))
                InfoRow(Res.get(R.string.taxa_de_atualizacao), Res.get(R.string.x_hz_2, (display.refreshRateHz)))
                InfoRow(Res.get(R.string.tamanho_fisico_estimado), String.format(Locale.US, Res.get(R.string.n_1f_polegadas), display.physicalSizeInches))
                InfoRow(Res.get(R.string.proporcao_aspect_ratio), display.aspectRatio)
            }
        }

        item {
            SectionHeader(Res.get(R.string.hdr_recursos_visuais))
            CardContainer {
                InfoRow(Res.get(R.string.suporte_a_hdr), if (display.isHdrSupported) Res.get(R.string.sim) else Res.get(R.string.nao))
                InfoRow(Res.get(R.string.formatos_hdr_detectados), display.hdrCapabilities)
                InfoRow(Res.get(R.string.brilho_da_tela), Res.get(R.string.x_41, (display.brightnessPercent)))
                InfoRow(Res.get(R.string.brilho_automatico), if (display.isAutoBrightness) "Ativado" else "Desativado")
                InfoRow(Res.get(R.string.modo_escuro_noturno), if (display.isNightMode) "Ativado" else "Desativado")
            }
        }
    }
}

// ==========================================
// 9. MEMORY TAB
// ==========================================
@Composable
fun MemoryTabContent(context: Context) {
    var mem by remember { mutableStateOf(DeviceInfoExtractor.getMemoryTelemetry(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            mem = DeviceInfoExtractor.getMemoryTelemetry(context)
            delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.memoria_ram))
            CardContainer {
                InfoRow(Res.get(R.string.ram_total), String.format(Locale.US, "%.2f GB", mem.ramTotalGb))
                InfoRow(Res.get(R.string.ram_usada), String.format(Locale.US, "%.2f GB (%d%%)", mem.ramUsedGb, mem.ramUsedPercent))
                InfoRow(Res.get(R.string.ram_livre), String.format(Locale.US, "%.2f GB", mem.ramFreeGb))
                InfoRow(Res.get(R.string.alerta_pouca_memoria), if (mem.isLowMemory) Res.get(R.string.sim_atencao) else "Normal")
                InfoRow(Res.get(R.string.limite_threshold), String.format(Locale.US, "%.2f GB", mem.thresholdGb))
            }
        }

        item {
            SectionHeader(Res.get(R.string.armazenamento_interno))
            CardContainer {
                InfoRow(Res.get(R.string.espaco_total), String.format(Locale.US, "%.2f GB", mem.internalTotalGb))
                InfoRow(Res.get(R.string.espaco_usado), String.format(Locale.US, "%.2f GB (%d%%)", mem.internalUsedGb, mem.internalUsedPercent))
                InfoRow(Res.get(R.string.espaco_livre), String.format(Locale.US, "%.2f GB", mem.internalFreeGb))
            }
        }

        if (mem.externalMounted) {
            item {
                SectionHeader(Res.get(R.string.cartao_sd_armazenamento_externo))
                CardContainer {
                    InfoRow("Status", "Montado")
                    InfoRow(Res.get(R.string.capacidade_total), String.format(Locale.US, "%.2f GB", mem.externalTotalGb))
                    InfoRow(Res.get(R.string.espaco_livre), String.format(Locale.US, "%.2f GB", mem.externalFreeGb))
                }
            }
        }
    }
}

// ==========================================
// 10. CAMERA TAB
// ==========================================
@Composable
fun CameraTabContent(context: Context) {
    val cameras = remember(context) { DeviceInfoExtractor.getCameraList(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.cameras_detectadas_x, (cameras.size)))
        }

        items(cameras) { cam ->
            CardContainer {
                Text(Res.get(R.string.sensor_id_x_x, (cam.id), (cam.facing)), color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                InfoRow(Res.get(R.string.megapixels_estimados), cam.megapixels)
                InfoRow(Res.get(R.string.resolucao_maxima), cam.resolution)
                InfoRow("Abertura", cam.aperture)
                InfoRow(Res.get(R.string.distancia_focal), cam.focalLengths)
                InfoRow("Flash", if (cam.hasFlash) Res.get(R.string.disponivel) else "Ausente")
                InfoRow(Res.get(R.string.foco_automatico), cam.afModes)
                InfoRow(Res.get(R.string.estabilizacao_optica_ois), if (cam.opticalStabilization) Res.get(R.string.sim) else Res.get(R.string.nao))
                InfoRow(Res.get(R.string.estabilizacao_eletronica_eis), if (cam.videoStabilization) Res.get(R.string.sim) else Res.get(R.string.nao))
                InfoRow(Res.get(R.string.faixa_de_sensibilidade_iso), cam.isoRange)
            }
        }
    }
}

// ==========================================
// 11. THERMAL TAB
// ==========================================
@Composable
fun ThermalTabContent(context: Context) {
    var zones by remember { mutableStateOf(DeviceInfoExtractor.getThermalZones()) }
    val battery = remember(context) { DeviceInfoExtractor.getBatteryTelemetry(context) }

    LaunchedEffect(Unit) {
        while (true) {
            zones = DeviceInfoExtractor.getThermalZones()
            delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.temperatura_da_bateria))
            CardContainer {
                InfoRow(Res.get(R.string.bateria_c), "${String.format(Locale.US, "%.1f", battery.temperatureC)} °C")
                InfoRow(Res.get(R.string.bateria_f), "${String.format(Locale.US, "%.1f", (battery.temperatureC * 9 / 5) + 32)} °F")
            }
        }

        item {
            SectionHeader(Res.get(R.string.zonas_termicas_do_sistema_x, (zones.size)))
        }

        if (zones.isEmpty()) {
            item {
                CardContainer {
                    Text(
                        Res.get(R.string.leitura_direta_de_sys_class_thermal_rest),
                        color = Color(0xFF8E99B0),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(zones) { zone ->
                CardContainer {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(Res.get(R.string.zona_x_x, (zone.zoneIndex), (zone.type)), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(
                            "${String.format(Locale.US, "%.1f", zone.tempCelsius)} °C",
                            color = if (zone.tempCelsius > 55f) AccentPink else if (zone.tempCelsius > 40f) AccentOrange else AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 12. SENSORES TAB
// ==========================================
@Composable
fun SensorsTabContent(context: Context) {
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensorList = remember(sensorManager) { sensorManager.getSensorList(Sensor.TYPE_ALL) } // DEV-R04

    var selectedSensor by remember { mutableStateOf<Sensor?>(null) }
    var sensorValues by remember { mutableStateOf("0.0, 0.0, 0.0") }

    DisposableEffect(selectedSensor) {
        if (selectedSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null) {
                        sensorValues = event.values.take(3).joinToString(", ") { String.format(Locale.US, "%.2f", it) }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, selectedSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose {}
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(Res.get(R.string.sensores_fisicos_instalados_x, (sensorList.size)))
        }

        if (selectedSensor != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan.copy(alpha = 0.1f))
                        .border(1.dp, AccentCyan, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(Res.get(R.string.telemetria_ao_vivo_x_name, (selectedSensor?.name)), color = AccentCyan, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { selectedSensor = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = Res.get(R.string.fechar), tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(Res.get(R.string.valores_x, sensorValues), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        items(sensorList) { sensor ->
            CardContainer(
                modifier = Modifier.clickable { selectedSensor = sensor }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sensor.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(Res.get(R.string.x_consumo_x_ma, (sensor.vendor), (sensor.power)), color = Color(0xFF8E99B0), fontSize = 11.sp)
                    }
                    Text(Res.get(R.string.toque_p_live), color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 13. APPS TAB
// ==========================================
@Composable
fun AppsTabContent(context: Context) {
    val allApps = remember(context) { DeviceInfoExtractor.getInstalledApps(context) }
    var selectedFilter by remember { mutableStateOf(0) } // 0 = User, 1 = System
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(allApps, selectedFilter, searchQuery) {
        allApps.filter { app ->
            val matchFilter = if (selectedFilter == 0) !app.isSystemApp else app.isSystemApp
            val matchSearch = searchQuery.isBlank() || app.appName.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true) // DEV-R14
            matchFilter && matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(Res.get(R.string.buscar_aplicativo), color = Color(0xFF8E99B0), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                )
            )
        }

        item {
            // Segmented Chips (User vs System)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 },
                    label = { Text(Res.get(R.string.apps_do_usuario_x, allApps.count { !it.isSystemApp })) }
                )
                FilterChip(
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 },
                    label = { Text(Res.get(R.string.sistema_x, allApps.count { it.isSystemApp })) }
                )
            }
        }

        items(filteredApps) { app ->
            CardContainer(
                modifier = Modifier.clickable {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(app.packageName, color = Color(0xFF8E99B0), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Res.get(R.string.vx_instalado_em_x, (app.versionName), (app.installTimeFormatted)), color = Color(0xFF8E99B0), fontSize = 10.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Detalhes", tint = AccentCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ==========================================
// REUSABLE UI CARDS & HELPERS
// ==========================================

@Composable
fun CardContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF8E99B0),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
fun CircularGaugeCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    percentage: Int,
    color: Color,
    icon: ImageVector
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier.size(76.dp),
                    color = color,
                    strokeWidth = 7.dp,
                    trackColor = CardBorder,
                    strokeCap = StrokeCap.Round
                )
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color(0xFF8E99B0), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LinearMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    progress: Float,
    color: Color,
    subtext: String,
    icon: ImageVector
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = CardBorder,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtext, color = Color(0xFF8E99B0), fontSize = 10.sp)
        }
    }
}

@Composable
fun StatBadgeCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(label, color = Color(0xFF8E99B0), fontSize = 10.sp)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
