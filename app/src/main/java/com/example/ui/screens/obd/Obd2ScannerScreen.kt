package com.example.ui.screens.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.transport.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import com.example.data.AppDatabase
import com.example.R
import com.example.Res

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Obd2ScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val obdManager = remember { BluetoothObdManager(context) }

    val connectionState by obdManager.connectionState.collectAsState()
    val isConnected by obdManager.isConnected.collectAsState()
    val obdData by obdManager.obdData.collectAsState()
    val extData by obdManager.extendedData.collectAsState()
    val customPids by obdManager.customPidResults.collectAsState()
    val dtcList by obdManager.dtcList.collectAsState()
    val readiness by obdManager.readinessMonitors.collectAsState()
    val freezeFrames by obdManager.freezeFrames.collectAsState()
    val mode06Results by obdManager.mode06Results.collectAsState()
    val currentTrip by obdManager.currentTrip.collectAsState()
    val performanceResults by obdManager.performanceResults.collectAsState()
    val lastError by obdManager.lastError.collectAsState()
    val obdDao = remember { AppDatabase.getDatabase(context).obdDao() }

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showWifiDialog by remember { mutableStateOf(false) }

    val bluetoothAdapter = remember {
        runCatching {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            mgr?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        }.getOrNull()
    }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var hasPermissions by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermissions = perms.values.all { it }
        if (hasPermissions) pairedDevices = obdManager.getPairedDevices()
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) pairedDevices = obdManager.getPairedDevices()
    }

    val isBtEnabled = remember(hasPermissions) {
        if (!hasPermissions) false
        else runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)
    }

    val alerts = buildObdAlerts(obdData)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Res.get(R.string.obd_ii), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
                        ObdStatusBadge(connectionState)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { obdManager.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnSurfaceLight)
                    }
                },
                actions = {
                    if (!isConnected) {
                        IconButton(onClick = { showWifiDialog = true }) {
                            Icon(Icons.Default.Wifi, null, tint = CyanNeon)
                        }
                    } else {
                        IconButton(onClick = { obdManager.disconnect() }) {
                            Icon(Icons.Default.BluetoothDisabled, null, tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        containerColor = DeepDark
    ) { padding ->

        if (!hasPermissions) {
            PermissionScreen(padding) { permissionLauncher.launch(requiredPermissions) }
            return@Scaffold
        }
        if (!isBtEnabled && !isConnected) {
            BluetoothOffScreen(padding) { pairedDevices = obdManager.getPairedDevices() }
            return@Scaffold
        }
        if (!isConnected) {
            DeviceSelectionScreen(
                padding = padding,
                pairedDevices = pairedDevices,
                isConnecting = isConnecting,
                lastError = lastError,
                onRefresh = { pairedDevices = obdManager.getPairedDevices() },
                onConnect = { device ->
                    isConnecting = true
                    scope.launch {
                        val ok = obdManager.connect(device.address)
                        isConnecting = false
                        if (ok) {
                            launch { obdManager.startPolling() }
                        }
                    }
                },
                onWifiConnect = { showWifiDialog = true }
            )
            return@Scaffold
        }

        // ── Connected ─────────────────────────────────────────────────────────
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (alerts.isNotEmpty()) AlertStrip(alerts)

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DeepDark,
                contentColor = CyanNeon,
                edgePadding = 8.dp
            ) {
                listOf("Dashboard", "Sensores", "Viagem", Res.get(R.string.dtcs), "Monitores", "Performance").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            when (index) {
                                3 -> scope.launch { obdManager.readDtcs() }
                                4 -> scope.launch {
                                    obdManager.readReadinessMonitors()
                                    if (dtcList.isNotEmpty()) obdManager.readFreezeFrames(dtcList)
                                    scope.launch { obdManager.readMode06() }
                                }
                            }
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title, fontSize = 12.sp)
                                if (index == 3 && dtcList.isNotEmpty()) {
                                    Badge(containerColor = Color.Red) { Text(Res.get(R.string.x_19, (dtcList.size))) }
                                }
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> DashboardTab(obdData, extData, obdManager)
                1 -> SensorsTab(obdData, customPids)
                2 -> TripTab(currentTrip, obdManager, onExportCsv = {
                    scope.launch {
                        val logs = obdDao.getLogsForSession(currentTrip.id)
                        ObdExportUtils.exportSessionToCsv(context, currentTrip, logs) // OBD-R08
                    }
                })
                3 -> DtcTab(dtcList, freezeFrames,
                    onClear = { scope.launch { obdManager.clearDtcs() } }, // OBD-R04
                    onRefresh = { scope.launch { obdManager.readDtcs() } },
                    onExportPdf = { ObdExportUtils.exportDiagnosticsToPdf(context, dtcList, freezeFrames, readiness) } // OBD-R08
                )
                4 -> MonitorsTab(readiness, mode06Results,
                    onRefresh = {
                        scope.launch {
                            obdManager.readReadinessMonitors()
                            obdManager.readMode06()
                        }
                    }
                )
                5 -> PerformanceTab(performanceResults,
                    onStart = { type -> scope.launch { obdManager.runPerformanceTest(type) } }
                )
            }
        }
    }

    if (showWifiDialog) {
        WifiConnectDialog(onDismiss = { showWifiDialog = false }) { host, port ->
            showWifiDialog = false
            isConnecting = true
            scope.launch {
                val ok = obdManager.connectWifi(host, port)
                isConnecting = false
                if (ok) launch { obdManager.startPolling() }
            }
        }
    }
}

// ─── Alerts ───────────────────────────────────────────────────────────────────

private data class ObdAlert(val message: String, val isCritical: Boolean) // OBD-R10

private fun buildObdAlerts(data: ObdData): List<ObdAlert> = buildList {
    data.coolantTempC?.let { if (it >= 105) add(ObdAlert(Res.get(R.string.temp_motor_critica_xc, (it)), true)) }
    data.oilTempC?.let { if (it >= 130) add(ObdAlert(Res.get(R.string.temp_oleo_critica_xc, (it)), true)) }
    data.rpm?.let { if (it >= 6500) add(ObdAlert(Res.get(R.string.rpm_alto_x, it), false)) }
    data.fuelLevelPct?.let { if (it <= 10f) add(ObdAlert(Res.get(R.string.combustivel_baixo_x, (it.toInt())), true)) }
    data.controlModuleVoltage?.let { if (it < 11.5f) add(ObdAlert(Res.get(R.string.tensao_baixa_x, "%.1f".format(it)), false)) }
    if (data.milOn) add(ObdAlert(Res.get(R.string.mil_ligada_verifique_dtcs), true))
}

@Composable
private fun AlertStrip(alerts: List<ObdAlert>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1A0000)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(alerts) { alert ->
            Surface(shape = RoundedCornerShape(8.dp), color = if (alert.isCritical) Color(0xFFCC0000) else Color(0xFF8B6914)) {
                Text(alert.message, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Tab 0: Dashboard ─────────────────────────────────────────────────────────

@Composable
private fun DashboardTab(data: ObdData, extData: ObdExtendedData, manager: BluetoothObdManager) {
    val consumptionL100 = remember(data) { manager.estimateFuelConsumptionL100() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularGauge(Modifier.weight(1f), "RPM", (data.rpm ?: 0).toFloat(), 8000f, data.rpmFormatted(), CyanNeon)
                CircularGauge(Modifier.weight(1f), Res.get(R.string.km_h), (data.speedKmh ?: 0).toFloat(), 240f, data.speedFormatted(), ElectricViolet)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ArcGauge(Modifier.weight(1f), "Motor", (data.coolantTempC ?: 0).toFloat(), -40f, 130f, 100f, data.coolantFormatted(), AmberGold, Color.Red)
                ArcGauge(Modifier.weight(1f), "Carga", data.engineLoadPct ?: 0f, 0f, 100f, 85f, data.loadFormatted(), SuccessGreen, AmberGold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricTile(Modifier.weight(1f), "Acelerador", data.throttleFormatted(), Icons.Default.Speed, ElectricViolet)
                MetricTile(Modifier.weight(1f), Res.get(R.string.combustivel), data.fuelLevelFormatted(), Icons.Default.LocalGasStation, AmberGold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricTile(Modifier.weight(1f), Res.get(R.string.consumo_est), if (consumptionL100 > 0f) "%.1f L/100".format(consumptionL100) else "---", Icons.Default.Eco, SuccessGreen)
                MetricTile(Modifier.weight(1f), Res.get(R.string.tensao), data.voltageFormatted(), Icons.Default.BatteryFull, CyanNeon)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricTile(Modifier.weight(1f), "MAF", data.mafFormatted(), Icons.Default.Air, CyanNeon)
                MetricTile(Modifier.weight(1f), Res.get(R.string.avanco_ign), data.ignitionFormatted(), Icons.Default.FlashOn, ElectricViolet)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricTile(Modifier.weight(1f), Res.get(R.string.dyno_hp), extData.hpFormatted(), Icons.Default.TrendingUp, AmberGold)
                MetricTile(Modifier.weight(1f), Res.get(R.string.dyno_torque), extData.torqueFormatted(), Icons.Default.SettingsSuggest, CyanNeon)
            }
        }
    }
}

// ─── Tab 1: Sensors ───────────────────────────────────────────────────────────

@Composable
private fun SensorsTab(data: ObdData, customPids: Map<String, String>) {
    var isProMode by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isProMode) Res.get(R.string.visao_tecnica) else Res.get(R.string.visao_motorista), color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (isProMode) Res.get(R.string.todos_os_sensores_detalhados) else Res.get(R.string.apenas_informacoes_essenciais), color = OnSurfaceDim, fontSize = 12.sp)
                }
                Switch(
                    checked = isProMode, // OBD-R06 
                    onCheckedChange = { isProMode = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = AmberGold, checkedTrackColor = AmberGold.copy(alpha=0.5f))
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item { SensorSection("Motor") }
        item { SensorRow("RPM", data.rpmFormatted(), CyanNeon) }
        item { SensorRow("Velocidade", data.speedFormatted(), CyanNeon) }
        item { SensorRow(Res.get(R.string.carga_do_motor), data.loadFormatted(), SuccessGreen) }
        if (isProMode) {
            item { SensorRow(Res.get(R.string.posicao_acelerador), data.throttleFormatted(), ElectricViolet) }
            item { SensorRow(Res.get(R.string.avanco_de_ignicao), data.ignitionFormatted(), ElectricViolet) }
            item { SensorRow(Res.get(R.string.tempo_de_funcionamento), data.runtimeFormatted(), OnSurfaceDim) }
        }

        item { Spacer(Modifier.height(4.dp)); SensorSection("Temperaturas") }
        item { SensorRow("Arrefecimento", data.coolantFormatted(), AmberGold) }
        if (isProMode) {
            item { SensorRow(Res.get(R.string.admissao_iat), data.intakeTempFormatted(), CyanNeon) }
            item { SensorRow(Res.get(R.string.oleo_do_motor), data.oilTempFormatted(), AmberGold) }
            item { SensorRow(Res.get(R.string.filtro_dpf), data.dpfTempFormatted(), Color.Red) }
            item { SensorRow("Ambiente", data.ambientTempFormatted(), CyanNeon) }
        }

        item { Spacer(Modifier.height(4.dp)); SensorSection(Res.get(R.string.combustivel_e_ar)) }
        item { SensorRow(Res.get(R.string.maf_fluxo_de_ar), data.mafFormatted(), CyanNeon) }
        item { SensorRow(Res.get(R.string.nivel_combustivel), data.fuelLevelFormatted(), AmberGold) }
        if (isProMode) {
            item { SensorRow(Res.get(R.string.pressao_combustivel), data.fuelPressureFormatted(), AmberGold) }
            item { SensorRow(Res.get(R.string.fluxo_combustivel), data.fuelFlowFormatted(), ElectricViolet) }
            item { SensorRow(Res.get(R.string.etanol), data.ethanolFormatted(), SuccessGreen) }
            item { SensorRow(Res.get(R.string.razao_equiv), data.equivRatioFormatted(), CyanNeon) }
        }

        if (isProMode) {
            item { Spacer(Modifier.height(4.dp)); SensorSection(Res.get(R.string.correcao_de_combustivel_egr)) }
            item { SensorRow(Res.get(R.string.stft_banco_1), data.stftB1Formatted(), if ((data.shortFuelTrimB1Pct ?: 0f) < -5f || (data.shortFuelTrimB1Pct ?: 0f) > 5f) Color.Red else SuccessGreen) }
            item { SensorRow(Res.get(R.string.ltft_banco_1), data.ltftB1Formatted(), if ((data.longFuelTrimB1Pct ?: 0f) < -10f || (data.longFuelTrimB1Pct ?: 0f) > 10f) Color.Red else SuccessGreen) }
            item { SensorRow(Res.get(R.string.stft_banco_2), data.stftB2Formatted(), OnSurfaceDim) }
            item { SensorRow(Res.get(R.string.ltft_banco_2), data.ltftB2Formatted(), OnSurfaceDim) }
            item { SensorRow(Res.get(R.string.egr_comandado), data.commandedEgrFormatted(), ElectricViolet) }
            item { SensorRow(Res.get(R.string.erro_egr), data.egrErrorFormatted(), if (Math.abs(data.egrErrorPct ?: 0f) > 10f) Color.Red else SuccessGreen) }

            item { Spacer(Modifier.height(4.dp)); SensorSection(Res.get(R.string.pressoes)) }
            item { SensorRow(Res.get(R.string.coletor_map), data.mapFormatted(), ElectricViolet) }
            item { SensorRow(Res.get(R.string.barometrica), data.baroPressureFormatted(), CyanNeon) }
            item { SensorRow(Res.get(R.string.vapor_evap), data.evapPressureFormatted(), OnSurfaceDim) }

            item { Spacer(Modifier.height(4.dp)); SensorSection(Res.get(R.string.sensores_o2)) }
            item { SensorRow(Res.get(R.string.o2_b1s1), data.o2B1S1Formatted(), SuccessGreen) }
            item { SensorRow(Res.get(R.string.o2_b1s2), data.o2B1S2Formatted(), SuccessGreen) }
        }

        item { Spacer(Modifier.height(4.dp)); SensorSection(Res.get(R.string.eletrico_hibrido)) }
        item { SensorRow(Res.get(R.string.tensao_obd_bateria), data.obdPortVoltageFormatted(), if ((data.obdPortVoltage ?: 14f) < 12f) Color.Red else CyanNeon) }
        item { SensorRow(Res.get(R.string.tensao_modulo), data.voltageFormatted(), CyanNeon) }
        item { SensorRow(Res.get(R.string.carga_bat_hibrida), data.hybridChargeFormatted(), SuccessGreen) }
        item { SensorRow(Res.get(R.string.tensao_bat_hibrida), data.hybridVoltageFormatted(), ElectricViolet) }
        
        item { Spacer(Modifier.height(4.dp)); SensorSection(Res.get(R.string.diagnostico_geral)) }
        data.distanceMilKm?.let { item { SensorRow(Res.get(R.string.distancia_com_mil), Res.get(R.string.x_km, it), Color.Red) } }
        data.timeMilMin?.let { item { SensorRow(Res.get(R.string.tempo_com_mil), Res.get(R.string.x_min, it), Color.Red) } }

        item { Spacer(Modifier.height(8.dp)) }

        if (customPids.isNotEmpty()) {
            item { SensorSection(Res.get(R.string.custom_pids_perfil_oem)) } // OBD-R05
            customPids.forEach { (name, value) ->
                item { SensorRow(name, value, ElectricViolet) }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── Tab 2: Trip Computer ─────────────────────────────────────────────────────

@Composable
private fun TripTab(trip: ObdTripSession, manager: BluetoothObdManager, onExportCsv: () -> Unit) {
    val consumptionL100 = manager.estimateFuelConsumptionL100()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp), color = DarkNavy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Res.get(R.string.viagem_atual), color = OnSurfaceDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(trip.durationFormatted, color = CyanNeon, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(trip.deviceName, color = OnSurfaceDim, fontSize = 11.sp)
                    }
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.Share, Res.get(R.string.exportar_csv), tint = ElectricViolet)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripStat(Modifier.weight(1f), Res.get(R.string.distancia), "%.1f km".format(trip.distanceKm), Icons.Default.Route, CyanNeon)
                TripStat(Modifier.weight(1f), Res.get(R.string.combustivel), "%.2f L".format(trip.fuelConsumedL), Icons.Default.LocalGasStation, AmberGold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripStat(Modifier.weight(1f), Res.get(R.string.l_100km), if (consumptionL100 > 0f) "%.1f".format(consumptionL100) else "---", Icons.Default.Eco, SuccessGreen)
                TripStat(Modifier.weight(1f), Res.get(R.string.carga_media), "%.0f %%".format(trip.avgEngineLoadPct), Icons.Default.Speed, ElectricViolet)
            }
        }
        item {
            SensorSection(Res.get(R.string.maximos_da_viagem))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripStat(Modifier.weight(1f), Res.get(R.string.rpm_max), Res.get(R.string.x_rpm_2, (trip.maxRpm)), Icons.Default.TrendingUp, CyanNeon)
                TripStat(Modifier.weight(1f), Res.get(R.string.vel_max), Res.get(R.string.x_km_h_2, (trip.maxSpeedKmh)), Icons.Default.Speed, ElectricViolet)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripStat(Modifier.weight(1f), Res.get(R.string.temp_max), Res.get(R.string.x_c_2, (trip.maxCoolantTempC)), Icons.Default.Thermostat, AmberGold)
                TripStat(Modifier.weight(1f), Res.get(R.string.dtcs), Res.get(R.string.x_20, (trip.dtcCount)), Icons.Default.Warning, if (trip.dtcCount > 0) Color.Red else SuccessGreen)
            }
        }
    }
}

@Composable
private fun TripStat(modifier: Modifier, label: String, value: String, icon: ImageVector, color: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = DarkNavy) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Text(label, color = OnSurfaceDim, fontSize = 11.sp)
            }
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Tab 3: DTCs ─────────────────────────────────────────────────────────────

@Composable
private fun DtcTab(dtcList: List<ObdDtcCode>, freezeFrames: List<FreezeFrame>, onClear: () -> Unit, onRefresh: () -> Unit, onExportPdf: () -> Unit) {
    var showClearDialog by remember { mutableStateOf(false) }
    var expandedDtc by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Ler")
            }
            OutlinedButton(onClick = onExportPdf, Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold)) {
                Text("PDF")
            }
            if (dtcList.isNotEmpty()) {
                Button(onClick = { showClearDialog = true }, Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(Res.get(R.string.limpar))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (dtcList.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(Res.get(R.string.sem_falhas_ativas), color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(Res.get(R.string.nenhum_codigo_de_diagnostico_encontrado), color = OnSurfaceDim, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(dtcList) { dtc ->
                    val freezeFrame = freezeFrames.find { it.triggerDtc == dtc.code }
                    DtcCard(dtc, freezeFrame, expandedDtc == dtc.code) {
                        expandedDtc = if (expandedDtc == dtc.code) null else dtc.code
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color.Red) },
            title = { Text(Res.get(R.string.limpar_codigos_de_falha)) },
            text = { Text(Res.get(R.string.isso_apagara_todos_os_dtcs_e_desligara_a)) },
            confirmButton = { TextButton(onClick = { showClearDialog = false; onClear() }) { Text(Res.get(R.string.limpar), color = Color.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(Res.get(R.string.cancelar)) } },
            containerColor = DarkNavy
        )
    }
}

@Composable
private fun DtcCard(dtc: ObdDtcCode, freeze: FreezeFrame?, isExpanded: Boolean, onToggle: () -> Unit) {
    val badgeColor = when { dtc.isPermanent -> Color(0xFF8B0000); dtc.isPending -> Color(0xFF8B6914); else -> Color.Red }
    val badgeText = when { dtc.isPermanent -> Res.get(R.string.permanente); dtc.isPending -> Res.get(R.string.pendente); else -> Res.get(R.string.ativo) }

    Surface(shape = RoundedCornerShape(14.dp), color = DarkNavy, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1A0000)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dtc.code, color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                            Text(badgeText, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 9.sp)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(dtc.description, color = OnSurfaceLight, fontSize = 13.sp)
                    if (freeze != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(Res.get(R.string.freeze_frame_disponivel_toque_para_ver), color = CyanNeon, fontSize = 11.sp)
                    }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnSurfaceDim)
            }

            AnimatedVisibility(isExpanded && freeze != null) {
                freeze?.let {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        HorizontalDivider(color = OnSurfaceDim.copy(alpha = 0.2f))
                        Spacer(Modifier.height(10.dp))
                        Text(Res.get(R.string.freeze_frame_momento_da_falha), color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FreezeCell("RPM", it.rpm?.toString() ?: "---", Modifier.weight(1f))
                            FreezeCell(Res.get(R.string.vel), it.speedKmh?.let { s -> Res.get(R.string.x_km_h_3, s) } ?: "---", Modifier.weight(1f))
                            FreezeCell(Res.get(R.string.temp), it.coolantTempC?.let { t -> Res.get(R.string.x_c_3, t) } ?: "---", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FreezeCell("Carga", it.engineLoadPct?.let { l -> Res.get(R.string.x_21, (l.toInt())) } ?: "---", Modifier.weight(1f))
                            FreezeCell("STFT", it.shortFuelTrimPct?.let { f -> "%.1f%%".format(f) } ?: "---", Modifier.weight(1f))
                            FreezeCell("LTFT", it.longFuelTrimPct?.let { f -> "%.1f%%".format(f) } ?: "---", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreezeCell(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xFF0D1117)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = OnSurfaceDim, fontSize = 10.sp)
            Text(value, color = AmberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Tab 4: Monitors ─────────────────────────────────────────────────────────

@Composable
private fun MonitorsTab(readiness: ReadinessMonitors, mode06: List<ObdMode06Result>, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(Res.get(R.string.i_m_readiness), color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(Res.get(R.string.x_x_monitores_prontos, (readiness.readyCount), (readiness.totalMonitors)), color = OnSurfaceDim, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onRefresh,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Atualizar", fontSize = 12.sp)
                }
            }
        }
        item {
            if (readiness.milOn) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF3D0000), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Text(Res.get(R.string.mil_ligada_luz_de_alerta_do_motor_ativa), color = Color.Red, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Readiness monitors grid
        val monitorList = listOf(
            Res.get(R.string.falha_de_ignicao) to readiness.misfireReady,
            Res.get(R.string.sist_combustivel) to readiness.fuelSystemReady,
            "Componentes" to readiness.componentsReady,
            "Catalisador" to readiness.catalystReady,
            Res.get(R.string.catalisador_aquec) to readiness.heatedCatalystReady,
            Res.get(R.string.sist_evap) to readiness.evapSystemReady,
            Res.get(R.string.ar_secundario) to readiness.secondaryAirReady,
            Res.get(R.string.sist_a_c) to readiness.acSystemReady,
            Res.get(R.string.sensor_o2) to readiness.oxygenSensorReady,
            Res.get(R.string.aquecedor_o2) to readiness.oxygenSensorHeaterReady,
            Res.get(R.string.sist_egr) to readiness.egrSystemReady
        ).filter { it.second != MonitorStatus.NOT_SUPPORTED }

        items(monitorList.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (name, status) ->
                    MonitorItem(Modifier.weight(1f), name, status)
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (mode06.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); SensorSection(Res.get(R.string.resultados_testes_ecu_mode_06)) }
            items(mode06) { result ->
                Mode06Item(result)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MonitorItem(modifier: Modifier, name: String, status: MonitorStatus) {
    val (bg, textColor) = when (status) {
        MonitorStatus.READY -> Color(0xFF0D2B12) to SuccessGreen
        MonitorStatus.NOT_READY -> Color(0xFF2B1A0D) to AmberGold
        MonitorStatus.NOT_SUPPORTED -> Color(0xFF1A1A2E) to OnSurfaceDim
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = bg) {
        Column(Modifier.padding(12.dp)) {
            Text(name, color = OnSurfaceLight, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(status.label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Mode06Item(result: ObdMode06Result) {
    val barColor = if (result.passed) SuccessGreen else Color.Red
    Surface(shape = RoundedCornerShape(12.dp), color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(result.description, color = OnSurfaceLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = if (result.passed) Color(0xFF0D2B12) else Color(0xFF3D0000)) {
                    Text(if (result.passed) Res.get(R.string.ok_2) else Res.get(R.string.falhou), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = barColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { result.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(4.dp))
            Text("Valor: %.0f | Min: %.0f | Max: %.0f".format(result.value, result.minValue, result.maxValue),
                color = OnSurfaceDim, fontSize = 10.sp)
        }
    }
}

// ─── Tab 5: Performance ──────────────────────────────────────────────────────

@Composable
private fun PerformanceTab(results: List<PerformanceResult>, onStart: (PerformanceType) -> Unit) {
    var activeTest by remember { mutableStateOf<PerformanceType?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(Res.get(R.string.testes_de_performance), color = OnSurfaceLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(Res.get(R.string.pare_o_veiculo_antes_de_iniciar_o_cronom), color = OnSurfaceDim, fontSize = 12.sp)
        }

        items(PerformanceType.entries) { type ->
            Surface(shape = RoundedCornerShape(14.dp), color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(type.label, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        val best = results.filter { it.type == type }.minByOrNull { it.durationMs }
                        if (best != null) {
                            Text(Res.get(R.string.melhor_x, (best.formattedTime)), color = CyanNeon, fontSize = 12.sp)
                        } else {
                            Text(Res.get(R.string.sem_registro), color = OnSurfaceDim, fontSize = 12.sp)
                        }
                    }
                    if (activeTest == type) {
                        CircularProgressIndicator(color = CyanNeon, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    } else {
                        Button(
                            onClick = {
                                activeTest = type
                                onStart(type)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Iniciar", color = DeepDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); SensorSection(Res.get(R.string.historico)) }
            items(results.take(20)) { result ->
                Surface(shape = RoundedCornerShape(10.dp), color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(result.type.label, color = OnSurfaceLight, fontSize = 13.sp)
                            Text(Res.get(R.string.vel_max_x_km_h, (result.maxSpeedKmh)), color = OnSurfaceDim, fontSize = 11.sp)
                        }
                        Text(result.formattedTime, color = CyanNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── Gauge Components ─────────────────────────────────────────────────────────

@Composable
private fun CircularGauge(modifier: Modifier, label: String, value: Float, maxValue: Float, displayText: String, color: Color) {
    val animFraction by animateFloatAsState((value / maxValue).coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "cg")
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(DarkNavy).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = OnSurfaceDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(110.dp)) {
                val sw = 10.dp.toPx()
                val arc = Size(size.width - sw, size.height - sw)
                val tl = Offset(sw / 2f, sw / 2f)
                drawArc(color.copy(alpha = 0.15f), 135f, 270f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * animFraction, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                val angle = Math.toRadians(135.0 + 270.0 * animFraction)
                val r = (size.minDimension - sw) / 2f
                val cx = size.width / 2f + (r * cos(angle)).toFloat()
                val cy = size.height / 2f + (r * sin(angle)).toFloat()
                drawCircle(color, sw / 2f, Offset(cx, cy))
            }
            AnimatedContent(displayText, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "cgv") { t ->
                Text(t, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ArcGauge(modifier: Modifier, label: String, value: Float, minValue: Float, maxValue: Float, dangerAt: Float, displayText: String, normalColor: Color, dangerColor: Color) {
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val animFraction by animateFloatAsState(fraction, tween(500, easing = FastOutSlowInEasing), label = "ag")
    val dangerF = ((dangerAt - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val active = if (fraction >= dangerF) dangerColor else normalColor
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(DarkNavy).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = OnSurfaceDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(110.dp)) {
                val sw = 10.dp.toPx()
                val arc = Size(size.width - sw, size.height - sw)
                val tl = Offset(sw / 2f, sw / 2f)
                drawArc(active.copy(alpha = 0.15f), 135f, 270f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(active, 135f, 270f * animFraction, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(displayText, color = active, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MetricTile(modifier: Modifier, label: String, value: String, icon: ImageVector, color: Color) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(DarkNavy).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = OnSurfaceDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mt") { v ->
            Text(v, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

// ─── Shared UI Components ─────────────────────────────────────────────────────

@Composable
private fun SensorSection(title: String) {
    Text(title, color = OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp, top = 4.dp))
    HorizontalDivider(color = OnSurfaceDim.copy(alpha = 0.2f))
}

@Composable
private fun SensorRow(label: String, value: String, valueColor: Color) {
    var showInfo by remember { mutableStateOf(false) }
    val helpInfo = com.example.transport.ObdHelpDictionary.glossary[label]

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DarkNavy).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = OnSurfaceDim, fontSize = 13.sp)
            if (helpInfo != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Info, 
                    contentDescription = "Ajuda", 
                    tint = ElectricViolet, 
                    modifier = Modifier.size(16.dp).clickable { showInfo = true }
                )
            }
        }
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }

    if (showInfo && helpInfo != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = DeepDark,
            title = { Text(helpInfo.title, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(helpInfo.description, color = OnSurfaceLight, fontSize = 14.sp)
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text(Res.get(R.string.faixa_normal), color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(helpInfo.normalRange, color = OnSurfaceDim, fontSize = 13.sp)
                    Text(Res.get(R.string.sintoma_de_falha), color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(helpInfo.symptomOfFailure, color = OnSurfaceDim, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(Res.get(R.string.entendi), color = AmberGold)
                }
            }
        )
    }
}

@Composable
private fun ObdStatusBadge(state: ObdConnectionState) {
    val (text, color) = when (state) {
        ObdConnectionState.CONNECTED -> Res.get(R.string.conectado) to SuccessGreen
        ObdConnectionState.CONNECTING -> Res.get(R.string.conectando) to AmberGold
        ObdConnectionState.INITIALIZING -> Res.get(R.string.inicializando) to CyanNeon
        ObdConnectionState.RECONNECTING -> Res.get(R.string.reconectando) to AmberGold
        ObdConnectionState.ERROR -> Res.get(R.string.erro) to Color.Red
        ObdConnectionState.DISCONNECTED -> return
    }
    val pulsing = state != ObdConnectionState.CONNECTED
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val alpha by infiniteTransition.animateFloat(1f, if (pulsing) 0.4f else 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Device Selection Screen ──────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun DeviceSelectionScreen(
    padding: PaddingValues, pairedDevices: List<BluetoothDevice>, isConnecting: Boolean,
    lastError: String?, onRefresh: () -> Unit, onConnect: (BluetoothDevice) -> Unit, onWifiConnect: () -> Unit
) {
    LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(Res.get(R.string.conectar_ao_obd_ii), color = OnSurfaceLight, fontSize = 18.sp, fontWeight = FontWeight.Bold) // OBD-R12
            Text(Res.get(R.string.selecione_um_adaptador_elm327_pareado_vi), color = OnSurfaceDim, fontSize = 12.sp)
        }
        if (lastError != null) {
            item {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF3D0000), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Text(lastError, color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }
        if (isConnecting) {
            item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CyanNeon)
                Spacer(Modifier.height(8.dp))
                Text(Res.get(R.string.conectando_e_inicializando_elm327), color = OnSurfaceDim, fontSize = 12.sp)
            }}
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh, Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Bluetooth")
                    }
                    Button(onClick = onWifiConnect, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)) {
                        Icon(Icons.Default.Wifi, null, tint = CyanNeon, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(Res.get(R.string.wifi_obd2), color = CyanNeon)
                    }
                }
            }
            if (pairedDevices.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Bluetooth, null, tint = OnSurfaceDim, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(Res.get(R.string.nenhum_dispositivo_pareado), color = OnSurfaceDim)
                        Text(Res.get(R.string.pareie_o_elm327_via_configuracoes_blueto), color = OnSurfaceDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                item { Text(Res.get(R.string.dispositivos_pareados), color = OnSurfaceDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                items(pairedDevices) { device ->
                    ObdDeviceCard(device) { onConnect(device) }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ObdDeviceCard(device: BluetoothDevice, onClick: () -> Unit) {
    val name = runCatching { device.name ?: Res.get(R.string.dispositivo_desconhecido) }.getOrDefault(Res.get(R.string.dispositivo_desconhecido))
    val address = runCatching { device.address ?: "" }.getOrDefault("")
    val isElm = name.contains("ELM", true) || name.contains("OBD", true) || name.contains(Res.get(R.string.v_link), true)

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkNavy).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = if (isElm) CyanNeon.copy(0.15f) else OnSurfaceDim.copy(0.1f)) {
            Icon(if (isElm) Icons.Default.DirectionsCar else Icons.Default.Bluetooth, null,
                tint = if (isElm) CyanNeon else OnSurfaceDim, modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold)
            Text(address, color = OnSurfaceDim, fontSize = 11.sp)
        }
        if (isElm) {
            Surface(shape = RoundedCornerShape(6.dp), color = CyanNeon.copy(0.15f)) {
                Text("ELM327", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = CyanNeon, fontSize = 10.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDim)
    }
}

// ─── Permission / BT Off Screens ─────────────────────────────────────────────

@Composable
private fun PermissionScreen(padding: PaddingValues, onGrant: () -> Unit) {
    Column(Modifier.padding(padding).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Bluetooth, null, tint = CyanNeon, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(Res.get(R.string.permissao_bluetooth_necessaria), color = OnSurfaceLight, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(Res.get(R.string.o_app_precisa_de_permissao_de_bluetooth), color = OnSurfaceDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)) {
            Text(Res.get(R.string.conceder_permissao), color = DeepDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BluetoothOffScreen(padding: PaddingValues, onRetry: () -> Unit) {
    Column(Modifier.padding(padding).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.BluetoothDisabled, null, tint = Color.Red, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(Res.get(R.string.bluetooth_desativado), color = OnSurfaceLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(Res.get(R.string.ative_o_bluetooth_nas_configuracoes_do_d), color = OnSurfaceDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)) {
            Icon(Icons.Default.Refresh, null, tint = CyanNeon); Spacer(Modifier.width(8.dp)); Text(Res.get(R.string.verificar_novamente), color = CyanNeon)
        }
    }
}

// ─── WiFi Dialog ──────────────────────────────────────────────────────────────

@Composable
private fun WifiConnectDialog(onDismiss: () -> Unit, onConnect: (String, Int) -> Unit) {
    var host by remember { mutableStateOf("192.168.0.10") }
    var port by remember { mutableStateOf("35000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Wifi, null, tint = CyanNeon) },
        title = { Text(Res.get(R.string.conectar_via_wifi_obd2)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Res.get(R.string.ip_e_porta_do_adaptador_elm327_wifi), color = OnSurfaceDim, fontSize = 12.sp)
                OutlinedTextField(host, { host = it }, label = { Text(Res.get(R.string.endereco_ip)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text(Res.get(R.string.porta_padrao_35000)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConnect(host, port.toIntOrNull() ?: 35000 /* OBD-R01 */) }, colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)) { Text("Conectar", color = DeepDark, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar)) } },
        containerColor = DarkNavy
    )
}
