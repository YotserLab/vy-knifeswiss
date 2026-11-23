package com.example.ui.screens.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.R
import com.example.Res


data class WifiApInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: String,
    val capabilities: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WifiMonitorScreen(navController: androidx.navigation.NavController? = null) {
    val context = LocalContext.current
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val scope = rememberCoroutineScope()

    var rssiHistory by remember { mutableStateOf(listOf<Int>()) }
    var scanResults by remember { mutableStateOf(listOf<WifiApInfo>()) }
    var connectedSsid by remember { mutableStateOf("") }
    var connectedRssi by remember { mutableStateOf(0) }
    var connectedFreq by remember { mutableStateOf(0) }
    var connectedBssid by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf("") }

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) // RED-R11
    )

    val scanReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) { // EXT-R06
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    if (success) {
                        val results = wifiManager?.scanResults ?: emptyList()
                            val apList = results.map { sr ->
                            val ch = if (sr.frequency in 2412..2484) (sr.frequency - 2407) / 5
                            else if (sr.frequency in 5170..5825) (sr.frequency - 5000) / 5
                            else 0
                            val band = if (sr.frequency < 3000) "2.4GHz" else Res.get(R.string.n_5ghz)
                            WifiApInfo(
                                ssid = sr.SSID ?: Res.get(R.string.hidden),
                                bssid = sr.BSSID ?: "",
                                rssi = sr.level,
                                frequency = sr.frequency,
                                channel = ch,
                                band = band,
                                capabilities = sr.capabilities ?: ""
                            )
                        }.sortedByDescending { it.rssi }
                        scanResults = apList
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).apply {
                            lastScanTime = format(java.util.Date())
                        }
                        isScanning = false
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(scanReceiver) }
    }

    fun doScan() {
        if (isScanning) return
        isScanning = true
        wifiManager?.startScan()
        scope.launch {
            delay(5000)
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        if (locationPermissionsState.allPermissionsGranted) {
            delay(500)
            doScan()
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            try {
                val wifiInfo = wifiManager?.connectionInfo
                if (wifiInfo != null) {
                    connectedSsid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
                    connectedRssi = wifiInfo.rssi
                    connectedFreq = wifiInfo.frequency
                    connectedBssid = wifiInfo.bssid ?: ""
                    rssiHistory = (rssiHistory + connectedRssi).takeLast(60)
                }
            } catch (_: Exception) {}
        }
    }

    fun rssiToQuality(rssi: Int): Float = ((rssi + 100).coerceIn(0, 100)) / 100f

    fun rssiColor(rssi: Int): Color = when {
        rssi >= -50 -> Color(0xFF4CAF50)
        rssi >= -67 -> Color(0xFF8BC34A)
        rssi >= -75 -> Color(0xFFFFC107)
        rssi >= -85 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.wifi_monitor),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = { 
                            if (!navController.popBackStack()) {
                                navController.navigate(com.example.ui.navigation.Screen.Dashboard.route)
                            }
                        }) {
                            Icon(
                                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Res.get(R.string.voltar),
                                tint = CyanNeon,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { doScan() }, enabled = !isScanning) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Escanear",
                            tint = if (isScanning) OnSurfaceDim else CyanNeon,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        if (!locationPermissionsState.allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(DeepDark)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    Res.get(R.string.permissao_de_localizacao_necessaria_para),
                    color = OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { locationPermissionsState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                ) {
                    Text(Res.get(R.string.conceder_permissao), color = DeepDark, fontWeight = FontWeight.Bold)
                }
            }
            return@Scaffold
        }

        if (wifiManager == null) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().background(DeepDark).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(Res.get(R.string.wifi_nao_disponivel_neste_dispositivo), color = OnSurfaceMuted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Connected network card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkNavy)
                        .border(1.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    if (connectedSsid.isNotEmpty()) {
                        val band = if (connectedFreq < 3000) "2.4GHz" else Res.get(R.string.n_5ghz)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = rssiColor(connectedRssi), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(connectedSsid, fontWeight = FontWeight.Bold, color = OnSurfaceLight, style = MaterialTheme.typography.titleMedium)
                                Text(Res.get(R.string.conectado), color = SuccessGreen, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            VYBadge(band, CyanNeon)
                        }
                        Spacer(Modifier.height(12.dp))
                        VYDivider(color = DarkBorder)
                        Spacer(Modifier.height(10.dp))
                        InfoRow("RSSI", Res.get(R.string.x_dbm_2, connectedRssi), rssiColor(connectedRssi))
                        InfoRow(Res.get(R.string.frequencia), Res.get(R.string.xmhz_x, (connectedFreq), band), OnSurfaceLight)
                        InfoRow("BSSID", connectedBssid, OnSurfaceMuted)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Res.get(R.string.sem_conexao_wifi_ativa), color = OnSurfaceMuted)
                        }
                    }
                }
            }

            // RSSI Graph
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkNavy)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    CategoryHeader(label = Res.get(R.string.sinal_rssi_ultimos_60s), accentColor = CyanNeon, modifier = Modifier.padding(0.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Canvas(modifier = Modifier.height(100.dp).fillMaxWidth()) {
                            val pts = rssiHistory
                            if (pts.size > 1) {
                                val stepX = size.width / (pts.size - 1)
                                val path = Path()
                                pts.forEachIndexed { i, v ->
                                    val y = ((v + 100).coerceIn(0, 100) / 100f) * size.height
                                    val x = i * stepX
                                    if (i == 0) path.moveTo(x, size.height - y) else path.lineTo(x, size.height - y)
                                }
                                drawPath(path, color = rssiColor(pts.last()), style = Stroke(2.dp.toPx()))
                                val fill = Path()
                                fill.addPath(path)
                                fill.lineTo((pts.size - 1) * stepX, size.height)
                                fill.lineTo(0f, size.height)
                                fill.close()
                                drawPath(fill, color = rssiColor(pts.last()).copy(alpha = 0.15f))
                                // Grid lines
                                for (i in 0..4) {
                                    val y = size.height * i / 4
                                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                                }
                            } else {
                                drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
                            }
                        }
                        if (rssiHistory.isNotEmpty()) {
                            Text(Res.get(R.string.agora_x_dbm, (rssiHistory.last())), fontSize = 11.sp, color = OnSurfaceMuted)
                        }
                }
            }

            // Channels overview (2.4GHz)
            item {
                val channels24 = scanResults.filter { it.band == "2.4GHz" }.groupBy { it.channel }
                if (channels24.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(Res.get(R.string.canais_2_4ghz), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            val chRange = (1..13)
                            Canvas(modifier = Modifier.height(40.dp).fillMaxWidth()) {
                                val barW = size.width / 13f
                                chRange.forEach { ch ->
                                    val count = channels24[ch]?.size ?: 0
                                    if (count > 0) {
                                        val barH = (count.coerceAtMost(5) / 5f) * size.height
                                        val x = (ch - 1) * barW
                                        drawRect(
                                            color = Color(0xFF2196F3).copy(alpha = 0.6f),
                                            topLeft = Offset(x, size.height - barH),
                                            size = androidx.compose.ui.geometry.Size(barW - 1.dp.toPx(), barH)
                                        )
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Canal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${channels24.values.sumOf { it.size }} APs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // APs list header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(3.dp).height(16.dp)
                            .background(CyanNeon, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Res.get(R.string.redes_proximas_x, (scanResults.size)),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon,
                        modifier = Modifier.weight(1f),
                    )
                    if (lastScanTime.isNotEmpty()) {
                        Text(lastScanTime, fontSize = 11.sp, color = OnSurfaceDim)
                    }
                }
            }

            items(scanResults) { ap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (ap.ssid == connectedSsid) CyanNeonAlpha else DarkNavy)
                        .border(
                            1.dp,
                            if (ap.ssid == connectedSsid) CyanNeon.copy(alpha = 0.5f) else DarkBorder,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint   = rssiColor(ap.rssi),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ap.ssid, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight, style = MaterialTheme.typography.bodyMedium)
                        Text(Res.get(R.string.ch_x_x, (ap.channel), (ap.bssid.take(17))), fontSize = 11.sp, color = OnSurfaceDim)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bandColor = if (ap.band == Res.get(R.string.n_5ghz)) CyanNeon else ElectricViolet
                        VYBadge(ap.band, bandColor)
                        Text(Res.get(R.string.x_dbm_3, (ap.rssi)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rssiColor(ap.rssi))
                    }
                }
            }

            if (scanResults.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = CyanNeon, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(Res.get(R.string.escaneando_redes_wifi), color = OnSurfaceMuted)
                        } else {
                            Text(
                                Res.get(R.string.pressione_o_icone_de_atualizar_para_esca),
                                textAlign = TextAlign.Center,
                                color = OnSurfaceDim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Scan button
            item {
                GradientButton(
                    text     = if (isScanning) Res.get(R.string.escaneando) else Res.get(R.string.atualizar_scan),
                    onClick  = { doScan() },
                    enabled  = !isScanning,
                    modifier = Modifier.fillMaxWidth(),
                    icon     = {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint     = DeepDark,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}
