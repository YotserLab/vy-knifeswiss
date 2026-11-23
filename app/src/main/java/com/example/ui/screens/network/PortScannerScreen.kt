package com.example.ui.screens.network

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.AppDatabase
import com.example.data.HistoryLog
import com.example.data.HistoryRepository
import com.example.ui.components.*
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.utils.net.NetworkUtils
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import com.example.R
import com.example.Res

private data class OpenPortResult(val port: Int, val hint: String? = null)

private data class OnvifDevice(val ip: String, val xaddr: String, val types: String = "")

// HTTP probes for web-interface ports; OPTIONS for RTSP; passive listen for binary protocols
private val rtspPorts = setOf(554, 8554, 7070)
private val passivePorts = setOf(34567, 37777, 37778, 8000, 9527, 18004)

private suspend fun probeService(host: String, port: Int): String? = withContext(Dispatchers.IO) {
    try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 600) // RED-R05
            s.soTimeout = 900

            if (passivePorts.contains(port)) {
                // Binary protocols (DVRIP/XMeye/Hikvision SDK) — listens for the initial banner only
            } else if (rtspPorts.contains(port)) {
                s.getOutputStream().write(
                    "OPTIONS rtsp://$host:$port/ RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()
                )
            } else {
                s.getOutputStream().write(
                    "GET / HTTP/1.0\r\nHost: $host\r\nUser-Agent: VYKnifeSwiss/1.0\r\nAccept: */*\r\n\r\n".toByteArray()
                )
            }
            s.getOutputStream().flush()

            val buf = ByteArray(1024)
            val n = try {
                s.getInputStream().read(buf)
            } catch (e: SocketTimeoutException) {
                -1
            }
            if (n <= 0) return@withContext null
            classifyResponse(buf.copyOf(n))
        }
    } catch (e: Exception) {
        null
    }
}

private fun classifyResponse(buf: ByteArray): String? {
    val text = String(buf, Charsets.ISO_8859_1)
    val low = text.lowercase()
    val server = Regex("server:\\s*([^\\r\\n<]+)", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim()
    val realm = Regex("realm=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim()
    val title = Regex("<title>([^<]{3,80})</title>", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim()

    return when {
        "netsurveillance" in low -> Res.get(R.string.xiongmai_xmeye_detectado)
        "hikvision" in low -> Res.get(R.string.hikvision_detectado)
        "dahua" in low -> Res.get(R.string.dahua_detectado)
        "gsoap" in low || "onvif" in low -> Res.get(R.string.possivel_onvif) + (server?.let { Res.get(R.string.x_22, it) } ?: "")
        "rtsp/1.0" in low -> Res.get(R.string.rtsp_confirmado) + (server?.let { Res.get(R.string.x_22, it) } ?: "")
        "ipcam" in low || Res.get(R.string.ip_camera) in low || "netcam" in low -> Res.get(R.string.camera_ip)
        " camera" in low || "cam " in low -> Res.get(R.string.possivel_camera)
        "dvr" in low || "nvr" in low -> Res.get(R.string.possivel_dvr_nvr)
        "http/1." in low -> server?.let { Res.get(R.string.http_x, it) }
            ?: realm?.let { Res.get(R.string.http_x, it) }
            ?: title?.let { Res.get(R.string.http_x, it) }
            ?: "HTTP"
        else -> null
    }
}

private const val TAG_ONVIF = "VYOnvif"

private fun onvifLog(msg: String, onLog: (String) -> Unit) {
    android.util.Log.d(TAG_ONVIF, msg)
    onLog(msg)
}

private fun onvifProbeXml(msgId: String, types: String?): String = """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
 xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
 xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
 xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<e:Header>
<w:MessageID>urn:uuid:$msgId</w:MessageID>
<w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005-04:discovery</w:To>
<w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
</e:Header>
<e:Body>
<d:Probe>${types?.let { "<d:Types>$it</d:Types>" } ?: ""}</d:Probe>
</e:Body>
</e:Envelope>""".trimIndent()

private suspend fun discoverOnvifDevices(
    context: Context,
    onLog: (String) -> Unit = {},
): List<OnvifDevice> = withContext(Dispatchers.IO) {
    val devices = mutableListOf<OnvifDevice>()
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? android.net.wifi.WifiManager
    val lock = wifi?.createMulticastLock("vy_onvif_probe")?.apply {
        setReferenceCounted(false)
        acquire()
    }
    onvifLog(Res.get(R.string.multicastlock_adquirido_x_isheld_true, (lock?.isHeld == true)), onLog)

    try {
        // Discovers the Wi-Fi IPv4 interface to bind the multicast
        val wifiIface = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull { ni ->
                    ni.isUp && !ni.isLoopback && ni.inetAddresses.toList()
                        .any { it is java.net.Inet4Address && !it.isLoopbackAddress }
                }
        }.getOrNull()
        onvifLog(
            "Interface multicast: ${wifiIface?.name ?: Res.get(R.string.padrao_do_sistema)} (${wifiIface?.inetAddresses?.toList()?.joinToString { it.hostAddress ?: "" } ?: "-"})",
            onLog,
        )

        val sock = java.net.MulticastSocket()
        sock.broadcast = true
        sock.soTimeout = 500
        sock.timeToLive = 2
        wifiIface?.let { runCatching { sock.networkInterface = it } }
        onvifLog("Socket UDP porta local ${sock.localPort}, TTL=2", onLog)

        val group = InetAddress.getByName("239.255.255.250")
        val groupAddr = InetSocketAddress(group, 3702)
        runCatching { sock.joinGroup(groupAddr, wifiIface) }
        onvifLog(Res.get(R.string.joingroup_239_255_255_250_3702_ok), onLog)

        // Probe 1: typed (NetworkVideoTransmitter). Probe 2: generic (some devices only respond without Types)
        val probeTyped = onvifProbeXml(UUID.randomUUID().toString(), "dn:NetworkVideoTransmitter")
        val probeGeneric = onvifProbeXml(UUID.randomUUID().toString(), null)

        var sent = 0
        for (probe in listOf(probeTyped, probeGeneric)) {
            try {
                val data = probe.toByteArray(Charsets.UTF_8)
                sock.send(DatagramPacket(data, data.size, groupAddr))
                sent++
                onvifLog(Res.get(R.string.probe_enviado_x_bytes_tipo_x, data.size, if (probe.contains("Types>dn")) "NVT" else Res.get(R.string.generico)), onLog)
            } catch (e: Exception) {
                onvifLog(Res.get(R.string.falha_ao_enviar_probe_x, e.message), onLog)
            }
        }

        val deadline = System.currentTimeMillis() + 4500
        var packets = 0
        while (System.currentTimeMillis() < deadline) {
            try {
                val buf = ByteArray(65536)
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
                packets++
                val src = "${pkt.address?.hostAddress}:${pkt.port}"
                val resp = String(pkt.data, 0, pkt.length)
                onvifLog(Res.get(R.string.pacote_x_de_x_b_x, packets, src, pkt.length, resp.take(160).replace("\n", " ")), onLog)

                val xaddr = Regex("<[^>]*XAddrs[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
                    .find(resp)?.groupValues?.get(1)?.trim()?.split("\\s+".toRegex())?.firstOrNull()
                val types = Regex("<[^>]*Types[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
                    .find(resp)?.groupValues?.get(1)?.trim() ?: ""
                if (!xaddr.isNullOrBlank()) {
                    devices += OnvifDevice(
                        ip = pkt.address?.hostAddress ?: "",
                        xaddr = xaddr,
                        types = types,
                    )
                    onvifLog("XAddr: $xaddr", onLog)
                } else {
                    onvifLog(Res.get(R.string.sem_xaddr_no_pacote_de_x, src), onLog)
                }
            } catch (t: SocketTimeoutException) {
                // waits for the next packet until the deadline
            }
        }
        sock.close()
        onvifLog(Res.get(R.string.fim_da_janela_x_pacote_s_recebido_s_x_di, packets, (devices.size)), onLog)
    } catch (e: Exception) {
        onvifLog(Res.get(R.string.erro_x_x, (e.javaClass.simpleName), (e.message)), onLog)
    } finally {
        runCatching { lock?.release() }
    }

    devices.distinctBy { it.xaddr }
}

private fun portRiskColor(port: Int): Color = when (port) {
    21, 23, 445, 3389, 37777, 34567 -> DangerRed  // FTP, Telnet, SMB, RDP, DVRIP — alto risco
    22, 25, 110, 143, 3306,                      // SSH, SMTP, POP3, IMAP, MySQL — medium risk
    554, 8554, 8899, 8000, 8081, 8200, 9000, 9527 -> WarningAmber // cameras/DVR
    else -> SuccessGreen                      // HTTP, HTTPS, etc — baixo risco
}

private fun portRiskLabel(port: Int): String = when (port) {
    21 -> "FTP"
    22 -> "SSH"
    23 -> "Telnet"
    25 -> "SMTP"
    53 -> "DNS"
    80 -> "HTTP"
    110 -> "POP3"
    135 -> "RPC"
    139 -> Res.get(R.string.netbios)
    143 -> "IMAP"
    443 -> "HTTPS"
    445 -> "SMB"
    554 -> "RTSP"
    2020 -> "ONVIF"
    3306 -> Res.get(R.string.mysql)
    3389 -> "RDP"
    5000 -> Res.get(R.string.upnp_dvr)
    7070 -> Res.get(R.string.realserver)
    8000 -> Res.get(R.string.hikvision_sdk)
    8080 -> Res.get(R.string.http_alt)
    8081 -> Res.get(R.string.camera_web)
    8200 -> Res.get(R.string.dvr_web)
    8554 -> Res.get(R.string.rtsp_alt)
    8899 -> Res.get(R.string.onvif_xm)
    9000 -> Res.get(R.string.dvr_service)
    9527 -> Res.get(R.string.xmeye)
    18004 -> Res.get(R.string.dahua_audio)
    34567 -> Res.get(R.string.xmeye_dvr)
    37777 -> Res.get(R.string.dahua_dvrip)
    37778 -> Res.get(R.string.dahua_dvrip_a)
    else -> Res.get(R.string.porta_x, port)
}

private fun portRiskDesc(port: Int): String = when (port) {
    21 -> Res.get(R.string.insecure_protocol)
    22 -> Res.get(R.string.secure_shell)
    23 -> Res.get(R.string.unencrypted_terminal)
    25 -> Res.get(R.string.mail_transfer)
    53 -> Res.get(R.string.domain_name_system)
    80 -> Res.get(R.string.web_server)
    110 -> Res.get(R.string.post_office_protocol)
    135 -> Res.get(R.string.remote_procedure_call)
    139 -> Res.get(R.string.netbios_session_service)
    143 -> Res.get(R.string.internet_message_access)
    443 -> Res.get(R.string.secure_web_server)
    445 -> Res.get(R.string.microsoft_ds_active_directory)
    554 -> Res.get(R.string.video_streaming_rtsp)
    2020 -> Res.get(R.string.onvif_camera_service)
    3306 -> Res.get(R.string.mysql_database)
    3389 -> Res.get(R.string.remote_desktop)
    5000 -> Res.get(R.string.upnp_dvr_web_service)
    7070 -> Res.get(R.string.realserver_streaming)
    8000 -> Res.get(R.string.hikvision_camera_dvr_sdk)
    8080 -> Res.get(R.string.web_proxy)
    8081 -> Res.get(R.string.alternate_camera_web_interface)
    8200 -> Res.get(R.string.hikvision_dahua_web_alternate)
    8554 -> Res.get(R.string.alternate_rtsp_stream)
    8899 -> Res.get(R.string.onvif_xiongmai_camera)
    9000 -> Res.get(R.string.camera_dvr_service_reolink_cs)
    9527 -> Res.get(R.string.xiongmai_camera_service)
    18004 -> Res.get(R.string.dahua_audio_service)
    34567 -> Res.get(R.string.xiongmai_dvrip_xmeye)
    37777 -> Res.get(R.string.dahua_dvrip_protocol)
    37778 -> Res.get(R.string.dahua_dvrip_audio)
    else -> Res.get(R.string.generic_network_service)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScannerScreen(initialHost: String? = null, navController: NavController? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val repository = remember { HistoryRepository(dao) } // PLAT-R08

    var host by remember(initialHost) { mutableStateOf(initialHost ?: "") }
    var localIp by remember { mutableStateOf<String?>(null) }
    var gatewayIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialHost) {
        val detectedIp = NetworkUtils.getWifiIpAddress() ?: NetworkUtils.getLocalIpAddress()
        val detectedGw = NetworkUtils.getGateway()
        localIp = detectedIp
        gatewayIp = detectedGw

        if (initialHost.isNullOrBlank()) {
            if (!detectedIp.isNullOrBlank()) {
                host = detectedIp
            } else if (!detectedGw.isNullOrBlank()) {
                host = detectedGw
            } else {
                host = "192.168.1.1"
            }
        }
    }

    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var openPorts by remember { mutableStateOf<List<OpenPortResult>>(emptyList()) }
    var isDiscovering by remember { mutableStateOf(false) }
    var onvifDevices by remember { mutableStateOf<List<OnvifDevice>>(emptyList()) }
    var discoveryDone by remember { mutableStateOf(false) }
    var discoveryLog by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // States for progress info
    var scanProgress by remember { mutableStateOf(0f) }
    var percentage by remember { mutableStateOf(0) }
    var currentScanningPort by remember { mutableStateOf(0) }
    var estimatedTimeLeft by remember { mutableStateOf(Res.get(R.string.calculando)) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    // Configuration states
    var selectedTab by remember { mutableStateOf("top100") }
    var startPort by remember { mutableStateOf("1") }
    var endPort by remember { mutableStateOf("1024") }

    val commonPorts = remember { listOf(21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 3306, 3389, 8080) } // RED-R04
    val cameraPorts = remember {
        listOf(
            80, 81, 554, 2020, 5000, 7070, 8000, 8080, 8081, 8200,
            8554, 8888, 8899, 9000, 9527, 18004, 34567, 37777, 37778
        )
    }
    val top100Ports = remember {
        listOf(
            20, 21, 22, 23, 25, 53, 67, 68, 69, 80, 110, 119, 123, 135, 137, 138, 139,
            143, 161, 162, 179, 389, 443, 445, 465, 514, 515, 548, 554, 587, 636, 993,
            995, 1080, 1433, 1434, 1521, 1723, 1812, 1813, 2049, 3306, 3389, 3689, 4443,
            5060, 5061, 5432, 5631, 5900, 5901, 6000, 6001, 8000, 8080, 8443, 8554, 8888,
            8899, 9000, 9100, 9527, 9999, 34567, 37777
        )
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.port_scanner),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                actions = {
                    IconButton(onClick = { navController?.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = Res.get(R.string.configuracoes), tint = OnSurfaceMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        },
        floatingActionButton = {
            if (hasScanned && openPorts.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val shareText = "Port Scanner - Host: $host\nPortas abertas: ${openPorts.joinToString(", ") { it.port.toString() }}"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_resultados))
                        context.startActivity(shareIntent)
                    },
                    containerColor = WarningAmber,
                    contentColor = DeepDark,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Target input section ─────────────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .background(CyanNeon)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "ALVO",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        leadingIcon = {
                            Text(
                                "@",
                                color = CyanNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                            )
                        },
                        trailingIcon = {
                            if (host.isNotEmpty() && !isScanning) {
                                IconButton(onClick = { host = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = OnSurfaceMuted)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    if (localIp?.isNotBlank() == true || gatewayIp?.isNotBlank() == true) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            localIp?.takeIf { it.isNotBlank() }?.let { ip ->
                                AssistChip(
                                    onClick = { host = ip },
                                    label = { Text(Res.get(R.string.meu_ip_x, ip), fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Wifi,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = CyanNeon
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color.White
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        enabled = true,
                                        borderColor = if (host == ip) CyanNeon else Color(0xFF1E293B)
                                    )
                                )
                            }

                            gatewayIp?.takeIf { it.isNotBlank() }?.let { gw ->
                                AssistChip(
                                    onClick = { host = gw },
                                    label = { Text(Res.get(R.string.gateway_x, gw), fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Router,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = WarningAmber
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color.White
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        enabled = true,
                                        borderColor = if (host == gw) WarningAmber else Color(0xFF1E293B)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Start & End Port Range custom inputs ─────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            Res.get(R.string.start_port),
                            color = OnSurfaceMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = startPort,
                            onValueChange = { startPort = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedTab == "all" && !isScanning,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                disabledContainerColor = Color(0xFF070B14),
                                disabledBorderColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            Res.get(R.string.end_port),
                            color = OnSurfaceMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = endPort,
                            onValueChange = { endPort = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedTab == "all" && !isScanning,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                disabledContainerColor = Color(0xFF070B14),
                                disabledBorderColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // ── Quick selection chips ────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("top100" to Res.get(R.string.top_100), "common" to "Common", "cameras" to Res.get(R.string.cameras), "all" to "All")
                    tabs.forEach { (tabId, label) ->
                        val isSelected = selectedTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) CyanNeon.copy(alpha = 0.1f) else Color(0xFF0F172A))
                                .border(1.dp, if (isSelected) CyanNeon else Color(0xFF1E293B), RoundedCornerShape(20.dp))
                                .clickable(enabled = !isScanning) { selectedTab = tabId }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CyanNeon else OnSurfaceMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ── ONVIF camera discovery (WS-Discovery UDP 3702) ───────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .background(ElectricViolet)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            Res.get(R.string.descoberta_de_cameras),
                            color = ElectricViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            isDiscovering = true
                            discoveryDone = false
                            onvifDevices = emptyList()
                            coroutineScope.launch {
                                discoveryLog = emptyList()
                                onvifDevices = discoverOnvifDevices(context) { line ->
                                    discoveryLog = discoveryLog + line
                                }
                                isDiscovering = false
                                discoveryDone = true
                            }
                        },
                        enabled = !isDiscovering && !isScanning,
                        border = BorderStroke(1.dp, ElectricViolet),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ElectricViolet,
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricViolet,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(Res.get(R.string.procurando_onvif), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(Res.get(R.string.descobrir_cameras_onvif), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (discoveryDone && onvifDevices.isEmpty()) {
                        Text(
                            Res.get(R.string.nenhum_dispositivo_onvif_respondeu_udp_3),
                            color = OnSurfaceDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (discoveryLog.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF070B14))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "LOG",
                                color = OnSurfaceDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            discoveryLog.forEach { line ->
                                Text(
                                    text = line,
                                    color = OnSurfaceMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    onvifDevices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable(enabled = !isScanning) {
                                    host = dev.ip.ifBlank {
                                        Regex("""\d+\.\d+\.\d+\.\d+""").find(dev.xaddr)?.value ?: host
                                    }
                                    selectedTab = "cameras"
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dev.ip.ifBlank { dev.xaddr },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    dev.xaddr,
                                    color = OnSurfaceMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                Res.get(R.string.scan),
                                color = ElectricViolet,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Iniciar Scan button ──────────────────────────────────
            item {
                if (!isScanning) {
                    GradientButton(
                        text = Res.get(R.string.iniciar_scan),
                        onClick = {
                            val activePorts = when (selectedTab) {
                                "top100" -> top100Ports
                                "common" -> commonPorts
                                "cameras" -> cameraPorts
                                else -> {
                                    val start = startPort.toIntOrNull() ?: 1
                                    val end = endPort.toIntOrNull() ?: 1024
                                    val rangeStart = minOf(start, end)
                                    val rangeEnd = maxOf(start, end)
                                    (rangeStart..rangeEnd).toList()
                                }
                            }

                            if (activePorts.isEmpty()) {
                                Toast.makeText(context, Res.get(R.string.defina_uma_faixa_de_portas_valida), Toast.LENGTH_SHORT).show()
                                return@GradientButton
                            }

                            isScanning = true
                            hasScanned = false
                            openPorts = emptyList()
                            scanProgress = 0f
                            percentage = 0
                            estimatedTimeLeft = Res.get(R.string.calculando)

                            scanJob = coroutineScope.launch {
                                var scanned = 0
                                val total = activePorts.size
                                val chunkSize = 8 // Concurrent connections
                                val chunks = activePorts.chunked(chunkSize)

                                for (chunk in chunks) {
                                    if (!isScanning) break

                                    val jobs = chunk.map { port ->
                                        async(Dispatchers.IO) {
                                            currentScanningPort = port
                                            val isOpen = try {
                                                val socket = Socket()
                                                socket.connect(InetSocketAddress(host, port), 250) // 250ms timeout
                                                socket.close()
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                            if (!isOpen) return@async null
                                            val hint = probeService(host, port)
                                            OpenPortResult(port, hint)
                                        }
                                    }

                                    val results = jobs.awaitAll()
                                    val detectedOpen = results.filterNotNull()
                                    if (detectedOpen.isNotEmpty()) {
                                        openPorts = (openPorts + detectedOpen)
                                            .distinctBy { it.port }
                                            .sortedBy { it.port }
                                    }

                                    scanned += chunk.size
                                    scanProgress = minOf(scanned.toFloat() / total, 1f)
                                    percentage = (scanProgress * 100).toInt()

                                    // Tempo restante aproximado
                                    val remainingChunks = (total - scanned + chunkSize - 1) / chunkSize
                                    val remainingSeconds = remainingChunks * 0.3 // aprox 300ms por bloco
                                    estimatedTimeLeft = if (remainingSeconds > 0) Res.get(R.string.xs_3, (remainingSeconds.toInt())) else Res.get(R.string.concluindo)
                                }

                                isScanning = false
                                hasScanned = true
                                scanProgress = 1f
                                percentage = 100

                                // Save to history
                                repository.insert(HistoryLog(
                                    toolName = Res.get(R.string.port_scanner),
                                    resultSummary = "Host $host - Portas abertas: ${openPorts.joinToString { "${it.port}${it.hint?.let { h -> Res.get(R.string.x_23, h) } ?: ""}" }}",
                                ))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = host.isNotBlank()
                    )
                }
            }

            // ── Neon progress circular indicator section ──────────────
            if (isScanning) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(170.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(150.dp),
                                color = Color(0xFF0F172A),
                                strokeWidth = 8.dp,
                            )
                            CircularProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier.size(150.dp),
                                color = CyanNeon,
                                strokeWidth = 8.dp,
                                trackColor = Color.Transparent
                            )
                            
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = Res.get(R.string.x_24, percentage),
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "%",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanNeon,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Text(
                            text = Res.get(R.string.escaneando_porta_x, currentScanningPort),
                            color = CyanNeon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Res.get(R.string.tempo_restante_x, estimatedTimeLeft),
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        OutlinedButton(
                            onClick = {
                                scanJob?.cancel()
                                isScanning = false
                            },
                            border = BorderStroke(1.dp, DangerRed),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DangerRed,
                                containerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.width(130.dp)
                        ) {
                            Text(
                                "CANCELAR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // ── Open ports list section ──────────────────────────────
            if (hasScanned || openPorts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CyanNeon)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Res.get(R.string.portas_abertas_x, (openPorts.size)),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = host,
                            color = OnSurfaceMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                if (openPorts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SuccessGreen.copy(alpha = 0.08f))
                                .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(Res.get(R.string.host_seguro_nenhuma_porta_comum_aberta), color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    items(openPorts, key = { it.port }) { result ->
                        val port = result.port
                        val riskColor = portRiskColor(port)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF070B14))
                                    .border(1.dp, riskColor, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = Res.get(R.string.x_25, port),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(14.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = portRiskLabel(port),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    if (portRiskColor(port) == DangerRed) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Risco",
                                            tint = WarningAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = portRiskDesc(port),
                                    color = OnSurfaceMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                                if (!result.hint.isNullOrBlank()) {
                                    Text(
                                        text = Res.get(R.string.id_x_2, (result.hint)),
                                        color = ElectricViolet,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SuccessGreen.copy(alpha = 0.15f))
                                    .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "OPEN",
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
