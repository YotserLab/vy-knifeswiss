package com.example.ui.screens.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.utils.net.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.R
import com.example.Res

private data class DeviceInfo(
    val ip: String,
    val hostname: String,
    val macAddress: String,
    val vendor: String,
    val os: String,
    val responseTimeMs: Long,
    val openPorts: List<Int>,
    val isGateway: Boolean = false,
    val isLocalDevice: Boolean = false
)

private data class ScanCache<T>(val data: T, val timestamp: Long) // RED-R03

private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

private fun <T> ScanCache<T>.isExpired() = System.currentTimeMillis() - timestamp > CACHE_TTL_MS

private enum class ActionType { Ports, Ping, Traceroute, Web }

private data class PortAction(val url: String, val label: String)

private fun portActions(ip: String, port: Int): PortAction? = when (port) {
    80   -> PortAction("http://$ip",         Res.get(R.string.abrir_http))
    443  -> PortAction("https://$ip",        Res.get(R.string.abrir_https))
    8080 -> PortAction("http://$ip:8080",    Res.get(R.string.abrir_8080))
    8443 -> PortAction("https://$ip:8443",   Res.get(R.string.abrir_8443))
    8888 -> PortAction("http://$ip:8888",    Res.get(R.string.abrir_8888))
    3000 -> PortAction("http://$ip:3000",    Res.get(R.string.abrir_3000))
    22   -> PortAction("ssh://$ip",          "SSH")
    23   -> PortAction("telnet://$ip",       "Telnet")
    21   -> PortAction("ftp://$ip",          "FTP")
    3389 -> PortAction("rdp://$ip",          "RDP")
    5900 -> PortAction("vnc://$ip",          "VNC")
    else -> null
}

private fun openPortLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// RED-R13 scans only on user demand
fun SubnetScannerScreen(navController: NavController? = null) {
    var localIp by remember { mutableStateOf("") }
    var gateway by remember { mutableStateOf("") }
    var dnsList by remember { mutableStateOf(listOf<String>()) }
    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var isScanning by remember { mutableStateOf(false) }
    var devicesFound by remember { mutableStateOf(0) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var expandedDeviceIndex by remember { mutableStateOf(-1) }
    
    var customSubnetPrefix by remember { mutableStateOf<String?>(null) }
    var showEditSubnetDialog by remember { mutableStateOf(false) }
    var portScanCache by remember { mutableStateOf(mapOf<String, ScanCache<List<Int>>>()) }
    var pingCache by remember { mutableStateOf(mapOf<String, ScanCache<String>>()) }
    var tracerouteCache by remember { mutableStateOf(mapOf<String, ScanCache<String>>()) }
    var webCheckCache by remember { mutableStateOf(mapOf<String, ScanCache<String>>()) }
    
    // Active filters
    var currentFilter by remember { mutableStateOf("TODOS") } // "TODOS", "ONLINE", "DESCONHECIDO", "GATEWAY"

    val scanningPortsFor = remember { mutableStateListOf<String>() }

    var activeAction by remember { mutableStateOf<ActionType?>(null) }
    var actionDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var actionRunning by remember { mutableStateOf(false) }
    var actionResult by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        localIp = NetworkUtils.getLocalIpAddress() ?: ""
        gateway = NetworkUtils.getGateway() ?: ""
        dnsList = NetworkUtils.getDnsServers()
    }
    
    val currentSubnetPrefix = customSubnetPrefix ?: if (localIp.isNotEmpty()) localIp.substringBeforeLast(".") else "192.168.1"

    fun clearCache() {
        portScanCache = emptyMap()
        pingCache = emptyMap()
        tracerouteCache = emptyMap()
        webCheckCache = emptyMap()
    }

    fun autoScanPorts(device: DeviceInfo) {
        val ip = device.ip
        val cached = portScanCache[ip]
        if (cached != null && !cached.isExpired()) return
        if (ip in scanningPortsFor) return
        scanningPortsFor.add(ip)
        scope.launch {
            val ports = NetworkUtils.scanPorts(
                ip,
                listOf(21,22,23,25,53,80,110,135,139,143,443,445,
                       3000,3306,3389,5900,8080,8443,8888),
                600
            )
            portScanCache = portScanCache + (ip to ScanCache(ports, System.currentTimeMillis()))
            devices = devices.map { d ->
                if (d.ip == ip) d.copy(openPorts = ports) else d
            }
            scanningPortsFor.remove(ip)
        }
    }

    fun deviceIcon(d: DeviceInfo): ImageVector = when {
        d.isGateway -> Icons.Default.Router
        d.isLocalDevice -> Icons.Default.PhoneAndroid
        d.os == "Apple" -> Icons.Default.PhoneIphone
        d.os == "Android" -> Icons.Default.PhoneAndroid
        d.os == "Windows" -> Icons.Default.DesktopWindows
        d.os == Res.get(R.string.linux_unix) -> Icons.Default.Terminal
        d.os == Res.get(R.string.smart_tv) -> Icons.Default.Tv
        d.os == "NAS" -> Icons.Default.Storage
        d.os == Res.get(R.string.iot_smart_home) -> Icons.Default.Lightbulb
        d.os == Res.get(R.string.router_network) -> Icons.Default.Router
        d.hostname.contains("tv", true) || d.hostname.contains("samsung", true) -> Icons.Default.Tv
        d.hostname.contains("nas", true) -> Icons.Default.Storage
        d.openPorts.contains(22) || d.openPorts.contains(23) -> Icons.Default.Terminal
        else -> Icons.Default.Laptop
    }

    fun runAction(device: DeviceInfo, action: ActionType) {
        activeAction = action
        actionDevice = device
        actionResult = ""
        actionRunning = true

        val ip = device.ip
        when (action) {
            ActionType.Ports -> {
                val cached = portScanCache[ip]
                if (cached != null && !cached.isExpired()) {
                    actionResult = cached.data.joinToString("\n") { Res.get(R.string.x_x_22, it, (NetworkUtils.portServiceName(it))) }
                    actionRunning = false
                } else {
                    scope.launch {
                        val ports = NetworkUtils.scanPorts(ip, listOf(21,22,23,25,53,80,110,135,139,143,443,445,
                            3000,3306,3389,5900,8080,8443,8888), 600)
                        portScanCache = portScanCache + (ip to ScanCache(ports, System.currentTimeMillis()))
                        devices = devices.map { d -> if (d.ip == ip) d.copy(openPorts = ports) else d }
                        actionResult = if (ports.isEmpty()) Res.get(R.string.nenhuma_porta_comum_aberta)
                        else ports.joinToString("\n") { Res.get(R.string.x_x_22, it, (NetworkUtils.portServiceName(it))) }
                        actionRunning = false
                    }
                }
            }
            ActionType.Ping -> {
                val cached = pingCache[ip]
                if (cached != null && !cached.isExpired()) {
                    actionResult = cached.data
                    actionRunning = false
                } else {
                    scope.launch {
                        val result = NetworkUtils.ping(ip, 4) // RED-R02
                        pingCache = pingCache + (ip to ScanCache(result, System.currentTimeMillis()))
                        actionResult = result
                        actionRunning = false
                    }
                }
            }
            ActionType.Traceroute -> {
                val cached = tracerouteCache[ip]
                if (cached != null && !cached.isExpired()) {
                    actionResult = cached.data
                    actionRunning = false
                } else {
                    scope.launch {
                        val result = NetworkUtils.traceroute(ip)
                        tracerouteCache = tracerouteCache + (ip to ScanCache(result, System.currentTimeMillis()))
                        actionResult = result
                        actionRunning = false
                    }
                }
            }
            ActionType.Web -> {
                val cached = webCheckCache[ip]
                if (cached != null && !cached.isExpired()) {
                    actionResult = cached.data
                    actionRunning = false
                } else {
                    scope.launch {
                        val webPorts = listOf(80, 443, 8080, 8443)
                        val results = mutableListOf<String>()
                        for (p in webPorts) {
                            val open = NetworkUtils.scanPorts(ip, listOf(p), 800)
                            if (open.isNotEmpty()) {
                                val scheme = if (p == 443 || p == 8443) "https" else "http"
                                results.add(Res.get(R.string.x_x_x_aberta, scheme, ip, p))
                            }
                        }
                        val output = if (results.isEmpty()) Res.get(R.string.nenhum_servidor_web_encontrado_nportas_8)
                        else "Servidor web encontrado:\n${results.joinToString("\n")}"
                        webCheckCache = webCheckCache + (ip to ScanCache(output, System.currentTimeMillis()))
                        actionResult = output
                        actionRunning = false
                    }
                }
            }
        }
    }

    fun closeDialog() {
        activeAction = null
        actionDevice = null
        actionResult = ""
        actionRunning = false
    }

    fun timeStr(ts: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))

    // Filter devices list
    val filteredDevices = remember(devices, currentFilter) {
        when (currentFilter) {
            "ONLINE" -> devices.filter { it.responseTimeMs > 0 }
            "DESCONHECIDO" -> devices.filter { it.hostname.contains(Res.get(R.string.desconhecido), true) || it.hostname.isBlank() }
            "GATEWAY" -> devices.filter { it.isGateway }
            else -> devices
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.subnet_scanner),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            // ── Target Subnet Settings Card (Rede Alvo) ──────────────
            if (!isScanning) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Lan, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(Res.get(R.string.rede_alvo), color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Text(Res.get(R.string.ip_range_cidr), color = OnSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A2035))
                                    .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(8.dp))
                                    .clickable { showEditSubnetDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Res.get(R.string.x_24_2, currentSubnetPrefix), // RED-R01
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(Icons.Default.Edit, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Res.get(R.string.ip_inicial), color = OnSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A2035).copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(Res.get(R.string.x_29, currentSubnetPrefix), color = OnSurfaceMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Res.get(R.string.ip_final), color = OnSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A2035).copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(Res.get(R.string.x_30, currentSubnetPrefix), color = OnSurfaceMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(18.dp))
                            
                            GradientButton(
                                text = Res.get(R.string.iniciar_scan),
                                onClick = {
                                    devices = emptyList()
                                    devicesFound = 0
                                    scanProgress = 0f
                                    expandedDeviceIndex = -1
                                    clearCache()
                                    isScanning = true
                                    NetworkUtils.startMdnsDiscovery(context)
                                    scanJob = scope.launch {
                                        NetworkUtils.scanSubnetDetailed(
                                            rangeStart = Res.get(R.string.x_29, currentSubnetPrefix),
                                            rangeEnd = Res.get(R.string.x_30, currentSubnetPrefix),
                                            onHostFound = { host ->
                                                devices = devices + DeviceInfo(
                                                    ip = host.ip,
                                                    hostname = host.hostname,
                                                    macAddress = host.macAddress,
                                                    vendor = host.vendor,
                                                    os = host.os,
                                                    responseTimeMs = host.responseTimeMs,
                                                    openPorts = emptyList(),
                                                    isGateway = host.ip == gateway,
                                                    isLocalDevice = host.ip == localIp
                                                )
                                                devicesFound = devices.size
                                            },
                                            onProgress = { current, total ->
                                                scanProgress = if (total > 0) current.toFloat() / total else 0f
                                            }
                                        )
                                        NetworkUtils.stopMdnsDiscovery()
                                        isScanning = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // ── Scanner Scanning In Progress Card ────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .border(1.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val currentIpIdx = (scanProgress * 254).toInt().coerceIn(1, 254)
                                    Text(Res.get(R.string.escaneando_xx, currentSubnetPrefix, currentIpIdx), color = CyanNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(Res.get(R.string.x_dispositivos_encontrados, devicesFound), color = OnSurfaceMuted, fontSize = 12.sp)
                                }
                                Text(
                                    Res.get(R.string.scanprogress_100_toint, ((scanProgress * 100).toInt())),
                                    color = CyanNeon,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = CyanNeon,
                                trackColor = Color(0xFF1E2D4A)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    scanJob?.cancel()
                                    NetworkUtils.stopMdnsDiscovery()
                                    isScanning = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B1E2D)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CANCELAR", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Filters Chips ────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TODOS", "ONLINE", "DESCONHECIDO", "GATEWAY").forEach { filter ->
                        val isSel = currentFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF1E2D4A) else Color.Transparent)
                                .border(1.dp, if (isSel) CyanNeon else Color(0xFF1E2D4A), RoundedCornerShape(8.dp))
                                .clickable { currentFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when (filter) {
                                    "TODOS" -> Res.get(R.string.todos).uppercase()
                                    "DESCONHECIDO" -> Res.get(R.string.desconhecido).uppercase()
                                    else -> filter
                                },
                                color = if (isSel) CyanNeon else OnSurfaceMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Devices Found Header ─────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Box(modifier = Modifier.width(3.dp).height(12.dp).background(CyanNeon))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Res.get(R.string.dispositivos_encontrados_x, (filteredDevices.size)),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Empty state for devices
            if (filteredDevices.isEmpty() && !isScanning) {
                item {
                    Text(
                        Res.get(R.string.nenhum_dispositivo_encontrado_nesta_cate),
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            } else {
                itemsIndexed(filteredDevices) { idx, device ->
                    val isExpanded = expandedDeviceIndex == idx
                    
                    DeviceItemStitch(
                        device = device,
                        icon = deviceIcon(device),
                        isExpanded = isExpanded,
                        onExpandClick = {
                            val newIdx = if (isExpanded) -1 else idx
                            expandedDeviceIndex = newIdx
                            if (newIdx == idx) autoScanPorts(device)
                        },
                        onActionClick = { action ->
                            runAction(device, action)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Modal for Quick Actions (Ping, Traceroute, Web Server)
    if (activeAction != null && actionDevice != null) {
        val dev = actionDevice!!
        val aa = activeAction!!
        val title = when (aa) {
            ActionType.Ports -> Res.get(R.string.port_scan)
            ActionType.Ping -> "Ping"
            ActionType.Traceroute -> "Traceroute"
            ActionType.Web -> Res.get(R.string.verificar_web)
        }

        AlertDialog(
            onDismissRequest = { closeDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            title = {
                Text(Res.get(R.string.x_x_23, title, (dev.ip)), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (actionRunning) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CyanNeon)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A2035))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = actionResult,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { closeDialog() }) { Text(Res.get(R.string.fechar), color = CyanNeon) }
            }
        )
    }

    if (showEditSubnetDialog) {
        var inputPrefix by remember { mutableStateOf(currentSubnetPrefix) }
        AlertDialog(
            onDismissRequest = { showEditSubnetDialog = false },
            title = { Text(Res.get(R.string.editar_faixa_de_ip), color = CyanNeon) },
            text = {
                Column {
                    Text(Res.get(R.string.digite_os_3_primeiros_octetos_da_rede_qu), color = OnSurfaceLight, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPrefix,
                        onValueChange = { inputPrefix = it },
                        label = { Text(Res.get(R.string.prefixo_de_ip)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanNeon,
                            focusedLabelColor = CyanNeon
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    customSubnetPrefix = inputPrefix
                    showEditSubnetDialog = false
                }) {
                    Text(Res.get(R.string.salvar), color = CyanNeon, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSubnetDialog = false }) {
                    Text(Res.get(R.string.cancelar), color = OnSurfaceMuted)
                }
            },
            containerColor = Color(0xFF111827)
        )
    }
}

@Composable
private fun DeviceItemStitch(
    device: DeviceInfo,
    icon: ImageVector,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onActionClick: (ActionType) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827))
            .border(
                1.dp,
                if (isExpanded) CyanNeon.copy(alpha = 0.5f) else Color(0xFF1E2D4A),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Device Icon Circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2D4A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.ip,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (device.isGateway) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CyanNeon.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("GATEWAY", color = CyanNeon, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (device.isLocalDevice) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ElectricViolet.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(Res.get(R.string.meu_dispositivo), color = ElectricViolet, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = if (device.hostname.isNotBlank()) device.hostname else Res.get(R.string.dispositivo_desconhecido),
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Ping Badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(Res.get(R.string.xms_2, (device.responseTimeMs)), color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onExpandClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = OnSurfaceMuted
                        )
                    }
                }
            }

            // Action Details when expanded
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                VYDivider(color = Color(0xFF1E2D4A))
                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Details
                Text(Res.get(R.string.mac_x, (device.macAddress)), color = OnSurfaceMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (device.vendor.isNotBlank()) {
                    Text(Res.get(R.string.fabricante_x, (device.vendor)), color = OnSurfaceMuted, fontSize = 11.sp)
                }
                if (device.os.isNotBlank() && device.os != Res.get(R.string.desconhecido)) {
                    Text(Res.get(R.string.os_deduzido_x, (device.os)), color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (device.openPorts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Res.get(R.string.portas_abertas_x_2, (device.openPorts.joinToString())),
                        color = SuccessGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onActionClick(ActionType.Ping) },
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("PING", color = OnSurfaceLight, fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { onActionClick(ActionType.Traceroute) },
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("TRACEROUTE", color = OnSurfaceLight, fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { onActionClick(ActionType.Web) },
                        border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("WEB", color = OnSurfaceLight, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
