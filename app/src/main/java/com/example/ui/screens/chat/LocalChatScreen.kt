package com.example.ui.screens.chat
import com.example.ui.screens.files.AppExtractorScreen
import com.example.utils.net.WifiDirectManager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.net.SocketTimeoutException
import java.net.SocketException
import com.example.ui.components.AppTopAppBar
import com.example.utils.crypto.LocalChatCrypto
import com.example.utils.net.NetworkUtils
import com.example.service.LocalChatService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.*
import com.example.R
import com.example.Res

// Domain models
data class DevicePeer(
    val name: String,
    val ip: String,
    val lastSeen: Long
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean = false,
    val isFileType: Boolean = false,
    val fileName: String = "",
    val fileUriStr: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0L,
    val peerIp: String = ""
)

// Persistent State Repository to maintain peers and messages on navigation
object LocalChatRepository {
    val peersList = mutableStateOf<Set<DevicePeer>>(emptySet())
    val selectedPeer = mutableStateOf<DevicePeer?>(null)
    val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    val unreadCount = mutableStateOf(0)
    val pendingSharedUris = mutableStateOf<List<Uri>>(emptyList())
    val pendingSharedText = mutableStateOf<String?>(null)
    private var isLoaded = false

    suspend fun loadInitialData(context: android.content.Context) {
        if (!isLoaded) {
            val (peers, messages) = LocalChatStorage.loadChatHistory(context)
            if (peersList.value.isEmpty()) peersList.value = peers
            if (chatMessages.value.isEmpty()) chatMessages.value = messages
            isLoaded = true
        }
    }

    suspend fun saveToStorage(context: android.content.Context) {
        if (isLoaded) {
            LocalChatStorage.saveChatHistory(context, peersList.value, chatMessages.value)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalChatScreen(onNavigate: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var myIp by remember { mutableStateOf("127.0.0.1") }
    var wifiIp by remember { mutableStateOf<String?>(null) }
    var tailscaleIp by remember { mutableStateOf<String?>(null) }
    val myDeviceName = remember { android.os.Build.MODEL ?: Res.get(R.string.dispositivo_android) }
    
    // Marks chat screen as visible on enter
    LaunchedEffect(Unit) {
        LocalChatRepository.loadInitialData(context)
        LocalChatService.setChatScreenVisible(context, true)
        // Resets unread message counter when opening the chat
        LocalChatRepository.unreadCount.value = 0
    }

    LaunchedEffect(LocalChatRepository.chatMessages.value, LocalChatRepository.peersList.value) {
        LocalChatRepository.saveToStorage(context)
    }

    // Marks chat screen as invisible on exit
    DisposableEffect(Unit) {
        onDispose {
            LocalChatService.setChatScreenVisible(context, false)
        }
    }
    
    // UI Local State
    val sharedPref = remember { context.getSharedPreferences("local_chat_prefs", Context.MODE_PRIVATE) }
    val isVibrationEnabled = remember { mutableStateOf(sharedPref.getBoolean("vibrate_on_msg", true)) }
    val isBackgroundServiceEnabled = remember { mutableStateOf(LocalChatService.isServiceEnabled(context)) }
    val isClipboardSyncEnabled = remember { mutableStateOf(sharedPref.getBoolean("sync_clipboard_enabled", false)) }
    var showClipboardRiskDialog by remember { mutableStateOf(false) }
    
    var peersList by LocalChatRepository.peersList
    var selectedPeer by LocalChatRepository.selectedPeer
    var messageInput by remember { mutableStateOf("") }
    var showAppExtractor by remember { mutableStateOf(false) }
    var showDirectIpDialog by remember { mutableStateOf(false) }
    var directIpInput by remember { mutableStateOf("") }
    var directNameInput by remember { mutableStateOf("") }
    
    // Auto-fill text input from pending text
    LaunchedEffect(LocalChatRepository.pendingSharedText.value) {
        val text = LocalChatRepository.pendingSharedText.value
        if (!text.isNullOrEmpty()) {
            messageInput = if (messageInput.isEmpty()) text else Res.get(R.string.x_nx, messageInput, text)
            LocalChatRepository.pendingSharedText.value = null
        }
    }
    var chatMessages by LocalChatRepository.chatMessages
    var isSendingFile by remember { mutableStateOf(false) }

    // Voice communication states
    var isVoiceChatting by remember { mutableStateOf(false) }
    val isVoiceReceiverActive = remember { mutableStateOf(true) }
    var isReceivingVoice by remember { mutableStateOf(false) }

    val micPermission = Manifest.permission.RECORD_AUDIO
    val hasMicPermission = ContextCompat.checkSelfPermission(
        context, micPermission
    ) == PackageManager.PERMISSION_GRANTED

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (LocalChatRepository.selectedPeer.value != null) {
                isVoiceChatting = true
            }
        } else {
            Toast.makeText(context, Res.get(R.string.permissao_de_microfone_negada_nao_e_poss), Toast.LENGTH_SHORT).show()
        }
    }
    
    var p2pGroupInfo by remember { mutableStateOf<android.net.wifi.p2p.WifiP2pGroup?>(null) }

    val p2pPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            com.example.utils.net.WifiDirectManager.createGroup(
                context,
                onSuccess = {
                    Toast.makeText(context, Res.get(R.string.ponto_de_acesso_wi_fi_direct_criado), Toast.LENGTH_LONG).show()
                },
                onGroupInfoAvailable = { group ->
                    p2pGroupInfo = group
                },
                onFailure = { reason ->
                    Toast.makeText(context, Res.get(R.string.falha_ao_criar_erro_x, reason), Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, Res.get(R.string.permissoes_p2p_negadas), Toast.LENGTH_SHORT).show()
        }
    }
    
    // Message Deletion & Selection States
    var isBatchSelectionMode by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var messageToDeleteSingle by remember { mutableStateOf<ChatMessage?>(null) }
    
    val listState = rememberLazyListState()

    // Refresh IPs periodically to catch Wi-Fi/Tailscale changes while the screen is open
    LaunchedEffect(Unit) {
        while (isActive) {
            wifiIp = NetworkUtils.getWifiIpAddress()
            tailscaleIp = NetworkUtils.getTailscaleIpAddress()
            myIp = wifiIp ?: tailscaleIp ?: NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
            delay(5000)
        }
    }

    // Scroll to bottom when messages list size changes for the selected peer
    val filteredMessagesSize = remember(chatMessages, selectedPeer) {
        if (selectedPeer != null) {
            chatMessages.filter { it.peerIp == selectedPeer!!.ip }.size
        } else {
            0
        }
    }
    LaunchedEffect(filteredMessagesSize) {
        if (filteredMessagesSize > 0) {
            listState.animateScrollToItem(filteredMessagesSize - 1)
        }
    }

    // UDP Voice Transmitter Loop
    LaunchedEffect(isVoiceChatting, selectedPeer) {
        if (isVoiceChatting && selectedPeer != null) {
            val peerIp = selectedPeer!!.ip
            withContext(Dispatchers.IO) {
                var record: android.media.AudioRecord? = null
                var socket: DatagramSocket? = null
                try {
                    val minBuffer = android.media.AudioRecord.getMinBufferSize(8000, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    val bufferSize = if (minBuffer > 0) minBuffer * 2 else 2048
                    
                    record = android.media.AudioRecord(
                        android.media.MediaRecorder.AudioSource.MIC,
                        8000,
                        android.media.AudioFormat.CHANNEL_IN_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    
                    if (record.state == android.media.AudioRecord.STATE_INITIALIZED) {
                        socket = DatagramSocket()
                        val destAddress = InetAddress.getByName(peerIp)
                        val buffer = ByteArray(1024)
                        
                        record.startRecording()
                        
                        while (isActive && isVoiceChatting) {
                            val readBytes = record.read(buffer, 0, buffer.size)
                            if (readBytes > 0) {
                                try {
                                    val packet = DatagramPacket(buffer, readBytes, destAddress, 9990)
                                    socket.send(packet)
                                } catch (e: Exception) {
                                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                                }
                            }
                            yield()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, Res.get(R.string.microfone_indisponivel_ou_sem_suporte_do), Toast.LENGTH_SHORT).show()
                            isVoiceChatting = false
                        }
                    }
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.get(R.string.erro_ao_transmitir_audio_x, (e.message)), Toast.LENGTH_SHORT).show()
                        isVoiceChatting = false
                    }
                } finally {
                    try {
                        if (record != null) {
                            if (record.state == android.media.AudioRecord.STATE_INITIALIZED && record.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                                record.stop()
                            }
                            record.release()
                        }
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    }
                    try {
                        socket?.close()
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    }
                }
            }
        }
    }

    // UDP Voice Receiver Loop
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            var track: android.media.AudioTrack? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9990)) // CHAT-R03
                }
                socket?.soTimeout = 2000
                
                val minBuffer = android.media.AudioTrack.getMinBufferSize(8000, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                val bufferSize = if (minBuffer > 0) minBuffer * 2 else 2048
                
                track = android.media.AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    8000,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    android.media.AudioTrack.MODE_STREAM
                )
                
                if (track.state == android.media.AudioTrack.STATE_INITIALIZED) {
                    track.play()
                    val buffer = ByteArray(1024)
                    var lastActivityTime = 0L
                    
                    coroutineContext[Job]?.invokeOnCompletion {
                        try {
                            socket?.close()
                        } catch (e: Exception) { if (com.example.BuildConfig.DEBUG) e.printStackTrace() }
                        try {
                            if (track != null) {
                                if (track.state == android.media.AudioTrack.STATE_INITIALIZED && track.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                                    track.stop()
                                }
                                track.release()
                            }
                        } catch (e: Exception) { if (com.example.BuildConfig.DEBUG) e.printStackTrace() }
                    }
                    
                    while (isActive && isVoiceReceiverActive.value) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket?.receive(packet)
                            
                            if (packet.length > 0) {
                                track.write(buffer, 0, packet.length)
                                lastActivityTime = System.currentTimeMillis()
                                if (!isReceivingVoice) {
                                    withContext(Dispatchers.Main) {
                                        isReceivingVoice = true
                                    }
                                }
                            }
                        } catch (timeout: SocketTimeoutException) {
                            if (isReceivingVoice && (System.currentTimeMillis() - lastActivityTime) > 3000) {
                                withContext(Dispatchers.Main) {
                                    isReceivingVoice = false
                                }
                            }
                        } catch (e: Exception) {
                            if (e is SocketException || !isActive) {
                                break
                            }
                            delay(250)
                        }
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) { if (com.example.BuildConfig.DEBUG) e.printStackTrace() }
                try {
                    if (track != null) {
                        if (track.state == android.media.AudioTrack.STATE_INITIALIZED && track.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                            track.stop()
                        }
                        track.release()
                    }
                } catch (e: Exception) { if (com.example.BuildConfig.DEBUG) e.printStackTrace() }
            }
        }
    }

    // Periodic self-presence broadcast loop (every 5 seconds) to keep neighbors updated)
    LaunchedEffect(myIp, isBackgroundServiceEnabled.value) {
        if (myIp != "127.0.0.1" && !isBackgroundServiceEnabled.value) {
            var counter = 0
            while (isActive) {
                withContext(Dispatchers.IO) {
                    broadcastPresence(myIp, myDeviceName)
                    if (counter % 4 == 0) {
                        sweepLocalSubnet(myIp, myDeviceName)
                    }
                }
                counter++
                delay(5000)
            }
        }
    }

    // Periodic inactive peer pruner (runs every 15 seconds, keeps inactive peers for up to 10 minutes)
    LaunchedEffect(Unit, isBackgroundServiceEnabled.value) {
        while (isActive) {
            delay(15000)
            val now = System.currentTimeMillis()
            withContext(Dispatchers.Main) {
                val updated = LocalChatRepository.peersList.value.filter {
                    (now - it.lastSeen) < 10 * 60 * 1000 // 10 minutes timeout threshold
                }.toSet()
                if (updated.size != LocalChatRepository.peersList.value.size) {
                    LocalChatRepository.peersList.value = updated
                    if (LocalChatRepository.selectedPeer.value != null && 
                        !updated.any { it.ip == LocalChatRepository.selectedPeer.value?.ip }) {
                        LocalChatRepository.selectedPeer.value = null
                    }
                }
            }
        }
    }

    // UDP Discover Socket Loop (only active while this Composable is in active use AND service is not enabled)
    LaunchedEffect(myIp, isBackgroundServiceEnabled.value) {
        if (isBackgroundServiceEnabled.value) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                // Acquire MulticastLock to make sure Android doesn't filter out broadcast UDP packets
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    if (wifiManager != null) {
                        multicastLock = wifiManager.createMulticastLock("LocalChatMulticastLock").apply {
                            setReferenceCounted(false)
                        }
                        multicastLock.acquire()
                    }
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                }

                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9988))
                }
                udpSocket.soTimeout = 2000
                val buffer = ByteArray(1024)
                
                // Safe socket cleanup on coroutine cancellation to prevent port leaks/ANRs
                coroutineContext[Job]?.invokeOnCompletion {
                    try {
                        udpSocket?.close()
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    }
                }
                
                // Immediately announce presence and sweep subnet on startup
                launch {
                    broadcastPresence(myIp, myDeviceName)
                    sweepLocalSubnet(myIp, myDeviceName)
                }
                
                while (this.coroutineContext.isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        udpSocket.receive(packet)
                        val receivedStr = String(packet.data, 0, packet.length).trim()
                        if (receivedStr.startsWith("CHAT_DISCOVER:")) {
                            val parts = receivedStr.split(":")
                            if (parts.size >= 3) {
                                val peerNick = parts[1]
                                val peerIp = parts[2].trim()
                                if (!NetworkUtils.isOwnIp(peerIp)) {
                                    withContext(Dispatchers.Main) {
                                        peersList = peersList.filter { it.ip != peerIp }.toSet() + 
                                                DevicePeer(peerNick, peerIp, System.currentTimeMillis())
                                    }
                                    // Respond directly to announce ourselves
                                    launch {
                                        announceToPeerDirect(peerIp, myDeviceName, myIp)
                                    }
                                }
                            }
                        } else if (receivedStr.startsWith("CHAT_ANNOUNCE:")) {
                            val parts = receivedStr.split(":")
                            if (parts.size >= 3) {
                                val peerNick = parts[1]
                                val peerIp = parts[2].trim()
                                if (!NetworkUtils.isOwnIp(peerIp)) {
                                    withContext(Dispatchers.Main) {
                                        peersList = peersList.filter { it.ip != peerIp }.toSet() + 
                                                DevicePeer(peerNick, peerIp, System.currentTimeMillis())
                                    }
                                }
                            }
                        }
                    } catch (timeout: java.net.SocketTimeoutException) {
                        // Safe timeout, loop again and check context status
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                        if (e is java.net.SocketException || !this.coroutineContext.isActive) {
                            break
                        }
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                try {
                    udpSocket?.close()
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                }
                try {
                    if (multicastLock?.isHeld == true) {
                        multicastLock.release()
                    }
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                }
            }
        }
    }

    // TCP Chat Server (only active while this Composable is in active use AND service is not enabled)
    LaunchedEffect(Unit, isBackgroundServiceEnabled.value) {
        if (isBackgroundServiceEnabled.value) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9989))
                }
                if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", "TCP Server started on port 9989")
                
                // Safe socket cleanup on coroutine cancellation to prevent port leaks/ANRs
                coroutineContext[Job]?.invokeOnCompletion {
                    try {
                        serverSocket?.close()
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    }
                }
                
                while (this.coroutineContext.isActive) {
                    try {
                        val clientSocket = serverSocket.accept()
                        launch {
                            try {
                                val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                                val jsonBuilder = java.lang.StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    jsonBuilder.append(line).append("\n")
                                }
                                val payloadStr = jsonBuilder.toString().trim()
                                if (payloadStr.isNotEmpty()) {
                                    val jsonObj = JSONObject(payloadStr)
                                    val type = jsonObj.optString("type", "TEXT")
                                    val senderName = jsonObj.optString("sender", "Dispositivo")
                                    val rawContent = jsonObj.optString("content", "")
                                    val content = LocalChatCrypto.decrypt(rawContent) ?: rawContent
                                    val senderIp = clientSocket.inetAddress.hostAddress ?: ""
                                    if (NetworkUtils.isOwnIp(senderIp)) return@launch

                                    if (type == "HANDSHAKE" || type == "PING") {
                                        launch(Dispatchers.IO) {
                                            try {
                                                val pongObj = JSONObject().apply {
                                                    put("type", "PONG")
                                                    put("sender", myDeviceName)
                                                    put("content", "PONG")
                                                }
                                                sendPayloadToPeer(senderIp, pongObj)
                                            } catch (e: Exception) {
                                                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (selectedPeer == null) {
                                                selectedPeer = DevicePeer(senderName, senderIp, System.currentTimeMillis())
                                            }
                                            peersList = peersList.filter { it.ip != senderIp }.toSet() + 
                                                    DevicePeer(senderName, senderIp, System.currentTimeMillis())
                                        }
                                        return@launch
                                    } else if (type == "PONG") {
                                        withContext(Dispatchers.Main) {
                                            if (selectedPeer == null) {
                                                selectedPeer = DevicePeer(senderName, senderIp, System.currentTimeMillis())
                                            }
                                            peersList = peersList.filter { it.ip != senderIp }.toSet() + 
                                                    DevicePeer(senderName, senderIp, System.currentTimeMillis())
                                        }
                                        return@launch
                                    }

                                    val incomingMessage = if (type == "CLIPBOARD_SYNC") {
                                        null // handled by service, ignore here if service is off
                                    } else if (type == "NUDGE") {
                                        ChatMessage(
                                            sender = senderName,
                                            content = Res.get(R.string.chamou_sua_atencao),
                                            timestamp = System.currentTimeMillis(),
                                            isMe = false,
                                            peerIp = senderIp
                                        )
                                    } else if (type == "FILE" || type == "FILE_STREAM") {
                                        val fileName = jsonObj.optString("fileName", "arquivo")
                                        val base64Data = jsonObj.optString("fileBytesBase64", "")
                                        val url = jsonObj.optString("url", "")
                                        val fileSize = jsonObj.optLong("fileSize", 0)
                                        
                                        var savedFilePath = ""
                                        if (base64Data.isNotEmpty()) {
                                            val savedFile = saveBase64ToFile(context, fileName, base64Data)
                                            savedFilePath = savedFile?.absolutePath ?: ""
                                        } else if (url.isNotEmpty()) {
                                            savedFilePath = withContext(Dispatchers.IO) {
                                                com.example.utils.net.NetworkUtils.downloadFile(context, url, fileName) ?: ""
                                            }
                                        }
                                        if (savedFilePath.isBlank()) {
                                            savedFilePath = resolveLocalFile(context, null, fileName)?.absolutePath ?: ""
                                        }
                                        
                                        ChatMessage(
                                            sender = senderName,
                                            content = Res.get(R.string.recebeu_x_x, fileName, (formatFileSize(fileSize))),
                                            timestamp = System.currentTimeMillis(),
                                            isMe = false,
                                            isFileType = true,
                                            fileName = fileName,
                                            fileUriStr = savedFilePath,
                                            downloadUrl = url,
                                            fileSize = fileSize,
                                            peerIp = senderIp
                                        )
                                    } else {
                                        ChatMessage(
                                            sender = senderName,
                                            content = content,
                                            timestamp = System.currentTimeMillis(),
                                            isMe = false,
                                            peerIp = senderIp
                                        )
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        if (incomingMessage != null) {
                                            chatMessages = chatMessages + incomingMessage
                                        }
                                        if (type == "NUDGE") {
                                            vibrateDeviceHeavy(context)
                                            Toast.makeText(context, Res.get(R.string.x_esta_chamando_sua_atencao, senderName), Toast.LENGTH_LONG).show()
                                        } else if (isVibrationEnabled.value) {
                                            vibrateDevice(context)
                                        }
                                        // Try auto-selecting sender if user hasn't selected any peer yet
                                        if (selectedPeer == null) {
                                            selectedPeer = DevicePeer(senderName, senderIp, System.currentTimeMillis())
                                        }
                                        // Update peers list
                                        peersList = peersList.filter { it.ip != senderIp }.toSet() + 
                                                DevicePeer(senderName, senderIp, System.currentTimeMillis())
                                    }
                                }
                            } catch (e: Exception) {
                                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                            } finally {
                                clientSocket.close()
                            }
                        }
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                        if (e is java.net.SocketException || !this.coroutineContext.isActive) {
                            break
                        }
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                }
            }
        }
    }

    val sendFile = { uri: Uri, peer: DevicePeer, customFileName: String? ->
        scope.launch {
            isSendingFile = true
            try {
                val details = getFileNameAndSize(context, uri)
                val fileName = customFileName ?: details.first
                var fileSize = details.second
                
                // CHAT-R07 For files up to 2MB (photos, screenshots, light PDFs) we send inline via Base64, guaranteeing 100% direct delivery
                val smallBytes = if (fileSize in 1..(2 * 1024 * 1024)) {
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } catch (_: Exception) { null }
                    }
                } else null

                if (smallBytes != null) {
                    fileSize = smallBytes.size.toLong()
                    val base64Data = android.util.Base64.encodeToString(smallBytes, android.util.Base64.NO_WRAP)
                    val payload = JSONObject().apply {
                        put("type", "FILE")
                        put("sender", myDeviceName)
                        put("fileName", fileName)
                        put("fileSize", fileSize)
                        put("fileBytesBase64", base64Data)
                    }

                    withContext(Dispatchers.IO) {
                        sendPayloadToPeer(peer.ip, payload)
                    }

                    // Add own message
                    chatMessages = chatMessages + ChatMessage(
                        sender = "Eu",
                        content = Res.get(R.string.enviou_x_x, fileName, (formatFileSize(fileSize))),
                        timestamp = System.currentTimeMillis(),
                        isMe = true,
                        isFileType = true,
                        fileName = fileName,
                        fileUriStr = uri.toString(),
                        fileSize = fileSize,
                        peerIp = peer.ip
                    )

                    playSendSound(context, scope)
                    Toast.makeText(context, Res.get(R.string.arquivo_enviado), Toast.LENGTH_SHORT).show()
                } else {
                    // For larger files, HTTP streaming through a secure cache copy
                    val token = withContext(Dispatchers.IO) {
                        com.example.service.InternalHttpFileServer.hostUri(context, uri, fileName)
                    }
                    if (token == null) {
                        Toast.makeText(context, Res.get(R.string.erro_ao_preparar_arquivo_para_envio), Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val hostIp = withContext(Dispatchers.IO) {
                        try {
                            java.net.Socket().use { s ->
                                s.connect(java.net.InetSocketAddress(peer.ip, 9989), 1000)
                                s.localAddress?.hostAddress?.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "127.0.0.1" }
                            }
                        } catch (_: Exception) { null }
                    } ?: wifiIp ?: tailscaleIp ?: NetworkUtils.getLocalIpAddress() ?: myIp

                    val url = "http://$hostIp:${com.example.service.InternalHttpFileServer.port}/download/$token" // CHAT-R08

                    val payload = JSONObject().apply {
                        put("type", "FILE_STREAM")
                        put("sender", myDeviceName)
                        put("fileName", fileName)
                        put("fileSize", fileSize)
                        put("url", url)
                    }

                    withContext(Dispatchers.IO) {
                        sendPayloadToPeer(peer.ip, payload)
                    }

                    // Add own message
                    chatMessages = chatMessages + ChatMessage(
                        sender = "Eu",
                        content = Res.get(R.string.enviou_x_x, fileName, (formatFileSize(fileSize))),
                        timestamp = System.currentTimeMillis(),
                        isMe = true,
                        isFileType = true,
                        fileName = fileName,
                        fileUriStr = uri.toString(),
                        fileSize = fileSize,
                        peerIp = peer.ip
                    )

                    playSendSound(context, scope)
                    Toast.makeText(context, Res.get(R.string.arquivo_enviado), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                Toast.makeText(context, Res.get(R.string.erro_ao_transferir_x, (e.message)), Toast.LENGTH_LONG).show()
            } finally {
                isSendingFile = false
            }
        }
    }

    // Process pending shared uris when a peer is selected
    LaunchedEffect(selectedPeer, LocalChatRepository.pendingSharedUris.value) {
        val peer = selectedPeer
        val uris = LocalChatRepository.pendingSharedUris.value
        if (peer != null && uris.isNotEmpty()) {
            uris.forEach { uri ->
                sendFile(uri, peer, null)
            }
            LocalChatRepository.pendingSharedUris.value = emptyList() // clear after sending
        }
    }

    // File selection launcher
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedPeer != null) {
            sendFile(uri, selectedPeer!!, null)
        }
    }

    if (p2pGroupInfo != null) {
        AlertDialog(
            onDismissRequest = { p2pGroupInfo = null },
            title = { Text(Res.get(R.string.ponto_de_acesso_criado)) },
            text = {
                Column {
                    Text(Res.get(R.string.conecte_outros_dispositivos_a_esta_rede))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(Res.get(R.string.nome_da_rede_ssid), fontWeight = FontWeight.Bold)
                    Text(p2pGroupInfo?.networkName ?: Res.get(R.string.desconhecido))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(Res.get(R.string.senha), fontWeight = FontWeight.Bold)
                    Text(p2pGroupInfo?.passphrase ?: Res.get(R.string.sem_senha_desconhecida))
                }
            },
            confirmButton = {
                TextButton(onClick = { p2pGroupInfo = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (showAppExtractor) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAppExtractor = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppExtractorScreen(
                    onAppSelected = { uri, fileName ->
                        showAppExtractor = false
                        selectedPeer?.let { peer ->
                            sendFile(uri, peer, fileName)
                        }
                    },
                    onBack = { showAppExtractor = false }
                )
            }
        }
    }

    if (showDirectIpDialog) {
        AlertDialog(
            onDismissRequest = { showDirectIpDialog = false },
            title = { Text(Res.get(R.string.conectar_por_ip)) }, // CHAT-R06
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        Res.get(R.string.digite_o_ip_do_outro_aparelho_wi_fi_loca),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = directIpInput,
                        onValueChange = { directIpInput = it.trim() },
                        label = { Text(Res.get(R.string.endereco_ip_ex_192_168_7_62)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_ip_input")
                    )
                    OutlinedTextField(
                        value = directNameInput,
                        onValueChange = { directNameInput = it },
                        label = { Text(Res.get(R.string.nome_do_aparelho_opcional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("confirm_direct_ip_button"),
                    onClick = {
                        val ip = directIpInput.trim()
                        if (ip.isNotEmpty()) {
                            val name = directNameInput.trim().ifEmpty { Res.get(R.string.dispositivo_x, ip) }
                            val newPeer = DevicePeer(name, ip, System.currentTimeMillis())
                            peersList = peersList.filter { it.ip != ip }.toSet() + newPeer
                            selectedPeer = newPeer
                            showDirectIpDialog = false
                            
                            // Send TCP handshake to peer
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val handshakeObj = JSONObject().apply {
                                        put("type", "HANDSHAKE")
                                        put("sender", myDeviceName)
                                        put("content", "HANDSHAKE")
                                    }
                                    sendPayloadToPeer(ip, handshakeObj)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, Res.get(R.string.conectado_a_x, name), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, Res.get(R.string.peer_adicionado_mensagens_serao_entregue), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Conectar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectIpDialog = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (selectedPeer != null) {
            selectedPeer = null
        } else {
            onNavigate?.invoke(com.example.ui.navigation.Screen.Dashboard.route)
        }
    }

    Scaffold(
        topBar = {
            if (selectedPeer == null) {
                AppTopAppBar(
                    title = Res.get(R.string.whatsapp_local),
                    onBack = { onNavigate?.invoke(com.example.ui.navigation.Screen.Dashboard.route) },
                    actions = {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = Res.get(R.string.opcoes))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Res.get(R.string.criar_ponto_p2p_sem_internet)) },
                                onClick = {
                                    menuExpanded = false
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        p2pPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.NEARBY_WIFI_DEVICES
                                            )
                                        )
                                    } else {
                                        p2pPermissionLauncher.launch(
                                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding()
        ) {
            // Detect theme brightness for WhatsApp style colors
            val isDark = MaterialTheme.colorScheme.background.red < 0.5f

            if (selectedPeer == null) {
                // SCREEN CHATS SUMMARY: Battery Warning & Profile details (WhatsApp contact style)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatterySaver,
                            contentDescription = Res.get(R.string.bateria_segura),
                            tint = Color(0xFF25D366) // Green theme indicator
                        )
                        Text(
                            text = Res.get(R.string.whatsapp_local_economia_de_energia),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Text(
                        text = Res.get(R.string.a_escuta_de_rede_e_recebimento_de_mensag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Profile card (Device information)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF25D366),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = Res.get(R.string.meu_aparelho_x, myDeviceName),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeviceHub,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = Res.get(R.string.wi_fi_rede_local_x_myip, (wifiIp ?: myIp)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VpnKey,
                                                contentDescription = null,
                                                tint = if (tailscaleIp != null) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Tailscale: ${tailscaleIp ?: Res.get(R.string.nao_conectado)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = if (tailscaleIp != null) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showDirectIpDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp).testTag("open_direct_ip_dialog_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(Res.get(R.string.conectar_ip), fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    broadcastPresence(myIp, myDeviceName)
                                                    sweepLocalSubnet(myIp, myDeviceName)
                                                }
                                                Toast.makeText(context, Res.get(R.string.buscando_dispositivos), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp).testTag("rescan_peers_btn")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Reescanear", fontSize = 11.sp)
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Vibration,
                                        contentDescription = Res.get(R.string.vibracao_de_mensagem),
                                        tint = if (isVibrationEnabled.value) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = Res.get(R.string.vibrar_nas_mensagens),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = Res.get(R.string.dar_feedback_ao_receber_de_outros),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isVibrationEnabled.value,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF25D366)
                                    ),
                                    onCheckedChange = { isEnabled ->
                                        isVibrationEnabled.value = isEnabled
                                        sharedPref.edit().putBoolean("vibrate_on_msg", isEnabled).apply()
                                        if (isEnabled) {
                                            vibrateDevice(context)
                                        }
                                    }
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = Res.get(R.string.servico_em_background),
                                        tint = if (isBackgroundServiceEnabled.value) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = Res.get(R.string.chat_em_background),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = Res.get(R.string.receber_mensagens_com_app_fechado_consom),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isBackgroundServiceEnabled.value,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF25D366)
                                    ),
                                    onCheckedChange = { isEnabled ->
                                        isBackgroundServiceEnabled.value = isEnabled
                                        LocalChatService.setServiceEnabled(context, isEnabled)
                                        
                                        if (isEnabled) {
                                            val intent = Intent(context, LocalChatService::class.java).apply {
                                                action = LocalChatService.ACTION_START_SERVICE
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                            Toast.makeText(context, Res.get(R.string.servico_de_chat_em_background_ativado), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val intent = Intent(context, LocalChatService::class.java).apply {
                                                action = LocalChatService.ACTION_STOP_SERVICE
                                            }
                                            context.startService(intent)
                                            Toast.makeText(context, Res.get(R.string.servico_de_chat_em_background_desativado), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = Res.get(R.string.sincronizar_clipboard),
                                        tint = if (isClipboardSyncEnabled.value) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = Res.get(R.string.sincronizar_area_de_transferencia),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = Res.get(R.string.envia_copias_para_todos_os_dispositivos),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isClipboardSyncEnabled.value,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFFFA000)
                                    ),
                                    onCheckedChange = { isEnabled ->
                                        if (isEnabled) {
                                            showClipboardRiskDialog = true
                                        } else {
                                            isClipboardSyncEnabled.value = false
                                            sharedPref.edit().putBoolean("sync_clipboard_enabled", false).apply()
                                            Toast.makeText(context, Res.get(R.string.sincronizacao_de_clipboard_desativada), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // CHATS LIST: Stable sorting of online neighbors
                val stablySortedPeers = peersList.toList().sortedWith(compareBy({ it.name }, { it.ip }))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("chats_list"),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Text(
                            text = Res.get(R.string.conversas_disponiveis_x, (stablySortedPeers.size)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF25D366) else Color(0xFF075E54),
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    if (stablySortedPeers.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = Res.get(R.string.nenhum_celular_ativo_no_momento),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Res.get(R.string.certifique_se_de_que_os_aparelhos_vizinh),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { showDirectIpDialog = true },
                                    modifier = Modifier.testTag("empty_state_connect_ip_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(Res.get(R.string.conectar_por_ip_manualmente))
                                }
                            }
                        }
                    } else {
                        items(stablySortedPeers, key = { it.ip }) { peer ->
                            val isOnline = (System.currentTimeMillis() - peer.lastSeen) < 30000
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPeer = peer }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("chat_row_${peer.ip}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar circle with online green badge
                                Box(modifier = Modifier.size(52.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(getAvatarColor(peer.name)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = peer.name.take(1).uppercase(),
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    val indicatorColor = if (isOnline) Color(0xFF25D366) else Color(0xFF9E9E9E)
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .align(Alignment.BottomEnd)
                                            .padding(1.5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(indicatorColor)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = peer.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isOnline) Res.get(R.string.ativo) else Res.get(R.string.ausente),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOnline) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isOnline) Res.get(R.string.conectado_toque_para_conversar) else Res.get(R.string.inativo_recentemente),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOnline) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            } else {
                val peer = selectedPeer!!

                // FULL SCREEN CHAT HEADER (WhatsApp Style with standard status bar container)
                Surface(
                    tonalElevation = 4.dp,
                    color = if (isDark) Color(0xFF1F2C34) else Color(0xFF075E54),
                    modifier = Modifier.fillMaxWidth().testTag("whatsapp_chat_header")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Back Navigation Button
                            IconButton(
                                onClick = { selectedPeer = null },
                                modifier = Modifier.testTag("close_chat_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = Res.get(R.string.voltar),
                                    tint = Color.White
                                )
                            }

                            // Avatar of peer
                            Box(modifier = Modifier.size(40.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(getAvatarColor(peer.name)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = peer.name.take(1).uppercase(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Small glowing dot indicating on/off status
                                val isOnline = (System.currentTimeMillis() - peer.lastSeen) < 30000
                                val dotColor = if (isOnline) Color(0xFF25D366) else Color(0xFF9E9E9E)
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF1F2C34) else Color(0xFF075E54))
                                        .align(Alignment.BottomEnd)
                                        .padding(1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = peer.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val statusText = when {
                                    isVoiceChatting -> Res.get(R.string.transmitindo_voz)
                                    isReceivingVoice -> Res.get(R.string.voz_em_tempo_real)
                                    else -> Res.get(R.string.ip_x_online, (peer.ip))
                                }
                                val statusColor = when {
                                    isVoiceChatting -> Color(0xFF25D366)
                                    isReceivingVoice -> Color(0xFF25D366)
                                    else -> Color.White.copy(alpha = 0.8f)
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }

                        // Options row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Call/Attention Button (Sinalizador)
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val payload = JSONObject().apply {
                                                put("type", "NUDGE")
                                                put("sender", myDeviceName)
                                            }
                                            withContext(Dispatchers.IO) {
                                                sendPayloadToPeer(peer.ip, payload)
                                            }
                                            chatMessages = chatMessages + ChatMessage(
                                                sender = "Eu",
                                                content = Res.get(R.string.chamou_a_atencao),
                                                timestamp = System.currentTimeMillis(),
                                                isMe = true,
                                                peerIp = peer.ip
                                            )
                                            playSendSound(context, scope)
                                            Toast.makeText(context, Res.get(R.string.sinal_enviado), Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, Res.get(R.string.erro_ao_chamar_atencao_x, (e.message)), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = Res.get(R.string.chamar_atencao),
                                    tint = Color.White
                                )
                            }

                            // Voice walkie-talkie toggle
                            val micIcon = if (isVoiceChatting) Icons.Default.Mic else Icons.Default.MicNone
                            val micTint = if (isVoiceChatting) Color(0xFF25D366) else Color.White
                            IconButton(
                                onClick = {
                                    if (isVoiceChatting) {
                                        isVoiceChatting = false
                                    } else {
                                        if (hasMicPermission) {
                                            isVoiceChatting = true
                                        } else {
                                            requestPermissionLauncher.launch(micPermission)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = micIcon,
                                    contentDescription = Res.get(R.string.conversar_por_voz),
                                    tint = micTint
                                )
                            }

                            IconButton(
                                onClick = { showDeleteAllDialog = true },
                                modifier = Modifier.testTag("delete_all_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = Res.get(R.string.limpar_conversa),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                if (isBatchSelectionMode) {
                    // Batch Mode Banner right under the header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("batch_selection_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isBatchSelectionMode = false
                                    selectedMessages = emptySet()
                                },
                                modifier = Modifier.testTag("cancel_batch_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = Res.get(R.string.cancelar_selecao),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            Text(
                                text = Res.get(R.string.x_selecionadas, (selectedMessages.size)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                chatMessages = chatMessages.filter { it.id !in selectedMessages }
                                selectedMessages = emptySet()
                                isBatchSelectionMode = false
                                Toast.makeText(context, Res.get(R.string.mensagens_apagadas), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("delete_batch_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = Res.get(R.string.apagar_selecionadas),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // CHAT CONVERSATION CONTAINER - Warm wallpaper background mimicking WhatsApp
                val wallpaperColor = if (isDark) Color(0xFF0B141A) else Color(0xFFEFEAE2)
                
                val filteredMessages = remember(chatMessages, peer) {
                    chatMessages.filter { it.peerIp == peer.ip }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(wallpaperColor)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredMessages, key = { it.id }) { message ->
                            val isMe = message.isMe
                            val isSelected = selectedMessages.contains(message.id)
                            
                            val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                            val timeString = remember(message.timestamp) { timeFormat.format(java.util.Date(message.timestamp)) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBatchSelectionMode && !isMe) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedMessages = if (checked) {
                                                selectedMessages + message.id
                                            } else {
                                                selectedMessages - message.id
                                            }
                                            if (selectedMessages.isEmpty()) {
                                                isBatchSelectionMode = false
                                            }
                                        },
                                        modifier = Modifier.padding(end = 4.dp).testTag("checkbox_${message.id}")
                                    )
                                }

                                // WhatsApp Tail Balloon-styled Cards
                                Card(
                                    shape = if (isMe) {
                                        RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)
                                    } else {
                                        RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp)
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else if (isMe) {
                                            if (isDark) Color(0xFF005C4B) else Color(0xFFE7FFDB)
                                        } else {
                                            if (isDark) Color(0xFF202C33) else Color(0xFFFFFFFF)
                                        }
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier
                                        .widthIn(max = 290.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isBatchSelectionMode) {
                                                    selectedMessages = if (isSelected) {
                                                        selectedMessages - message.id
                                                    } else {
                                                        selectedMessages + message.id
                                                    }
                                                    if (selectedMessages.isEmpty()) {
                                                        isBatchSelectionMode = false
                                                    }
                                                } else if (isMessageFile(message)) {
                                                    val effName = getEffectiveFileName(message)
                                                    val localFile = resolveLocalFile(context, message.fileUriStr, effName)
                                                    if (localFile != null) {
                                                        openFileInApp(context, localFile.absolutePath) { route ->
                                                            onNavigate?.invoke(route)
                                                        }
                                                    } else if (message.downloadUrl.isNotBlank()) {
                                                        scope.launch {
                                                            Toast.makeText(context, Res.get(R.string.baixando_x_2, effName), Toast.LENGTH_SHORT).show()
                                                            val downloaded = NetworkUtils.downloadFile(context, message.downloadUrl, effName)
                                                            if (!downloaded.isNullOrBlank()) {
                                                                chatMessages = chatMessages.map { if (it.id == message.id) it.copy(fileUriStr = downloaded, isFileType = true, fileName = effName) else it }
                                                                Toast.makeText(context, Res.get(R.string.download_concluido), Toast.LENGTH_SHORT).show()
                                                                openFileInApp(context, downloaded) { route ->
                                                                    onNavigate?.invoke(route)
                                                                }
                                                            } else {
                                                                Toast.makeText(context, Res.get(R.string.nao_foi_possivel_baixar_o_arquivo_verifi), Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    } else {
                                                        Toast.makeText(context, Res.get(R.string.arquivo_nao_encontrado_localmente), Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    messageToDeleteSingle = message
                                                }
                                            },
                                            onLongClick = {
                                                if (!isBatchSelectionMode) {
                                                    isBatchSelectionMode = true
                                                    selectedMessages = setOf(message.id)
                                                }
                                            }
                                        )
                                        .testTag("chat_card_${message.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        if (!isMe) {
                                            Text(
                                                text = message.sender,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF128C7E),
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                        
                                        Text(
                                            text = message.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                if (isDark) Color.White else Color(0xFF111B21)
                                            }
                                        )
                                        
                                        if (isMessageFile(message)) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val effName = getEffectiveFileName(message)
                                            val resolvedFile = resolveLocalFile(context, message.fileUriStr, effName)
                                            val resolvedPath = resolvedFile?.absolutePath ?: ""
                                            val file = resolvedFile
                                            val fileType = if (resolvedFile != null) getFileType(resolvedPath) else ""

                                            if (file != null && file.exists()) {

                                            if (fileType == "image" && file.exists()) {
                                                // Image preview
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 200.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            openFileInApp(context, resolvedPath) { route ->
                                                                onNavigate?.invoke(route)
                                                            }
                                                        }
                                                ) {
                                                    coil.compose.SubcomposeAsyncImage(
                                                        model = file,
                                                        contentDescription = Res.get(R.string.image_preview),
                                                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        loading = {
                                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                            }
                                                        }
                                                    )
                                                    // Little icon indicator
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .align(Alignment.TopEnd)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                            .padding(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Image,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            } else if (fileType == "video" && file.exists()) {
                                                // Video preview with play overlay
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(150.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.Black)
                                                        .clickable {
                                                            openFileInApp(context, resolvedPath) { route ->
                                                                onNavigate?.invoke(route)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayCircle,
                                                            contentDescription = Res.get(R.string.play_video),
                                                            tint = Color(0xFF25D366),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = message.fileName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(horizontal = 12.dp)
                                                        )
                                                    }
                                                }
                                            } else if (fileType == "audio" && file.exists()) {
                                                // Audio preview row
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isDark) Color(0xFF1F2C34) else Color(0xFFF0F2F5))
                                                        .clickable {
                                                            openFileInApp(context, resolvedPath) { route ->
                                                                onNavigate?.invoke(route)
                                                            }
                                                        }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Headset,
                                                        contentDescription = Res.get(R.string.audio_message),
                                                        tint = Color(0xFF128C7E),
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = message.fileName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            color = if (isDark) Color.White else Color(0xFF111B21)
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Hearing,
                                                                contentDescription = null,
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = Res.get(R.string.audio_toque_para_ouvir),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Generic file card fallback
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            color = if (isDark) Color(0xFF1F2C34) else Color(0xFFF0F2F5),
                                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            openFileInApp(context, resolvedPath) { route ->
                                                                onNavigate?.invoke(route)
                                                            }
                                                        }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                        contentDescription = Res.get(R.string.file_icon),
                                                        tint = Color(0xFF128C7E),
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            text = message.fileName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            color = if (isDark) Color.White else Color(0xFF111B21)
                                                        )
                                                        Text(
                                                            text = Res.get(R.string.format) + message.fileName.substringAfterLast('.', Res.get(R.string.desconhecido)).uppercase() + Res.get(R.string.toque_para_abrir),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF667781)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // In-app preview button (if supported)
                                                if (fileType in listOf("image", "video", "audio")) {
                                                    androidx.compose.material3.Button(
                                                        onClick = {
                                                            openFileInApp(context, resolvedPath) { route ->
                                                                onNavigate?.invoke(route)
                                                            }
                                                        },
                                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFF4CAF50),
                                                            contentColor = Color.White
                                                        ),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                        modifier = Modifier.weight(1f).height(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Visibility,
                                                            contentDescription = "Visualizar",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Visualizar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                androidx.compose.material3.Button(
                                                    onClick = {
                                                        openReceivedFile(context, resolvedPath)
                                                    },
                                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF128C7E),
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("open_file_btn_${message.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.OpenInNew,
                                                        contentDescription = Res.get(R.string.abrir),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Res.get(R.string.abrir), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }

                                                androidx.compose.material3.OutlinedButton(
                                                    onClick = {
                                                        openFolderInFileManager(context, resolvedPath)
                                                    },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF128C7E)),
                                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                                        contentColor = Color(0xFF128C7E)
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("open_folder_btn_${message.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FolderOpen,
                                                        contentDescription = "Pasta",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Pasta", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                androidx.compose.material3.OutlinedButton(
                                                    onClick = {
                                                        shareReceivedFile(context, resolvedPath)
                                                    },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF128C7E)),
                                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                                        contentColor = Color(0xFF128C7E)
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp).testTag("share_file_btn_${message.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Compartilhar",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Res.get(R.string.enviar), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            // File pending or not found locally
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = if (isDark) Color(0xFF1F2C34) else Color(0xFFF0F2F5),
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                    contentDescription = Res.get(R.string.file_icon),
                                                    tint = Color(0xFF128C7E),
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = message.fileName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        color = if (isDark) Color.White else Color(0xFF111B21)
                                                    )
                                                    Text(
                                                        text = if (message.fileSize > 0) formatFileSize(message.fileSize) else Res.get(R.string.toque_para_baixar),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF667781)
                                                    )
                                                }
                                            }
                                            if (message.downloadUrl.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                val effName = getEffectiveFileName(message)
                                                androidx.compose.material3.Button(
                                                    onClick = {
                                                        scope.launch {
                                                            Toast.makeText(context, Res.get(R.string.baixando_x_2, effName), Toast.LENGTH_SHORT).show()
                                                            val downloaded = NetworkUtils.downloadFile(context, message.downloadUrl, effName)
                                                            if (!downloaded.isNullOrBlank()) {
                                                                chatMessages = chatMessages.map { if (it.id == message.id) it.copy(fileUriStr = downloaded, isFileType = true, fileName = effName) else it }
                                                                Toast.makeText(context, Res.get(R.string.download_concluido), Toast.LENGTH_SHORT).show()
                                                                openFileInApp(context, downloaded) { route ->
                                                                    onNavigate?.invoke(route)
                                                                }
                                                            } else {
                                                                Toast.makeText(context, Res.get(R.string.nao_foi_possivel_baixar_o_arquivo_verifi), Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    },
                                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF128C7E),
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.fillMaxWidth().height(32.dp).testTag("download_file_btn_${message.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = "Baixar",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Res.get(R.string.baixar_arquivo), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = timeString,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = if (isMe) {
                                                    if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF667781)
                                                } else {
                                                    if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF667781)
                                                }
                                            )
                                            if (isMe) {
                                                Icon(
                                                    imageVector = Icons.Default.DoneAll,
                                                    contentDescription = "Entregue",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = Color(0xFF34B7F1) // WhatsApp blue tick status!
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isBatchSelectionMode && isMe) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedMessages = if (checked) {
                                                selectedMessages + message.id
                                            } else {
                                                selectedMessages - message.id
                                            }
                                            if (selectedMessages.isEmpty()) {
                                                isBatchSelectionMode = false
                                            }
                                        },
                                        modifier = Modifier.padding(start = 4.dp).testTag("checkbox_${message.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                // File uploading loader indicator
                if (isSendingFile) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(wallpaperColor)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Res.get(R.string.enviando_arquivo_via_rede), style = MaterialTheme.typography.bodySmall)
                    }
                }

                // COMPOSER CONTAINER (WhatsApp Floating Rounded Theme Input Bar)
                Surface(
                    color = wallpaperColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Fully rounded text field surface
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) Color(0xFF1F2C34) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp, max = 110.dp),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        showAppExtractor = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Android,
                                        contentDescription = Res.get(R.string.enviar_aplicativo),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        fileChooserLauncher.launch("*/*")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = Res.get(R.string.anexar_arquivo),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                OutlinedTextField(
                                    value = messageInput,
                                    onValueChange = { messageInput = it },
                                    placeholder = { Text(Res.get(R.string.mensagem), color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(24.dp),
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        errorBorderColor = Color.Transparent,
                                        disabledBorderColor = Color.Transparent,
                                        focusedTextColor = if (isDark) Color.White else Color(0xFF111B21),
                                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF111B21)
                                    )
                                )
                            }
                        }

                        // WhatsApp Green Circular Send Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00A884)) // WhatsApp vibrant green
                                .clickable {
                                    if (messageInput.isNotBlank()) {
                                        val msgText = messageInput
                                        messageInput = ""
                                        
                                        scope.launch {
                                            try {
                                                val encrypted = LocalChatCrypto.encrypt(msgText) ?: msgText
                                                val payload = JSONObject().apply {
                                                    put("type", "TEXT")
                                                    put("sender", myDeviceName)
                                                    put("content", encrypted)
                                                }
                                                
                                                withContext(Dispatchers.IO) {
                                                    if (com.example.BuildConfig.DEBUG) if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", Res.get(R.string.sending_message_to_x, (peer.ip)))
                                                    sendPayloadToPeer(peer.ip, payload)
                                                }
                                                
                                                // Append sent message
                                                chatMessages = chatMessages + ChatMessage(
                                                    sender = "Eu",
                                                    content = msgText,
                                                    timestamp = System.currentTimeMillis(),
                                                    isMe = true,
                                                    peerIp = peer.ip
                                                )
                                                playSendSound(context, scope)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, Res.get(R.string.erro_ao_enviar_x, (e.message)), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = Res.get(R.string.enviar),
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(start = 2.dp) // shift slightly for classic send orientation
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog to warn about clipboard sync privacy risk
    if (showClipboardRiskDialog) {
        AlertDialog(
            onDismissRequest = { showClipboardRiskDialog = false },
            title = { Text(Res.get(R.string.atencao_sincronizacao_de_area_de_transfe)) },
            text = { Text(Res.get(R.string.ao_ativar_tudo_que_voce_copiar_incluindo)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClipboardRiskDialog = false
                        isClipboardSyncEnabled.value = true
                        sharedPref.edit().putBoolean("sync_clipboard_enabled", true).apply()
                        Toast.makeText(context, Res.get(R.string.sincronizacao_de_clipboard_ativada), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(Res.get(R.string.ativar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClipboardRiskDialog = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }

    // Dialog to delete all messages
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(Res.get(R.string.apagar_todas_as_mensagens)) },
            text = { Text(Res.get(R.string.isso_apagara_todas_as_mensagens_desta_te)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatMessages = emptyList()
                        showDeleteAllDialog = false
                        Toast.makeText(context, Res.get(R.string.todas_as_mensagens_foram_apagadas), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_delete_all_btn")
                ) {
                    Text(Res.get(R.string.apagar_tudo), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllDialog = false },
                    modifier = Modifier.testTag("dismiss_delete_all_btn")
                ) {
                    Text(Res.get(R.string.cancelar))
                }
            },
            modifier = Modifier.testTag("delete_all_messages_dialog")
        )
    }

    // Dialog for single message options (Delete, Select multiple/Batch selection)
    if (messageToDeleteSingle != null) {
        val msg = messageToDeleteSingle!!
        AlertDialog(
            onDismissRequest = { messageToDeleteSingle = null },
            title = { Text(Res.get(R.string.opcoes_de_mensagem)) },
            text = { Text(text = if (msg.content.length > 60) msg.content.take(60) + "..." else msg.content) },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatMessages = chatMessages.filter { it.id != msg.id }
                        messageToDeleteSingle = null
                        Toast.makeText(context, Res.get(R.string.mensagem_apagada), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_delete_single_btn")
                ) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    if (isMessageFile(msg)) {
                        TextButton(
                            onClick = {
                                val effName = getEffectiveFileName(msg)
                                val localFile = resolveLocalFile(context, msg.fileUriStr, effName)
                                if (localFile != null) {
                                    openFileInApp(context, localFile.absolutePath) { route ->
                                        onNavigate?.invoke(route)
                                    }
                                } else if (msg.downloadUrl.isNotBlank()) {
                                    scope.launch {
                                        Toast.makeText(context, Res.get(R.string.baixando_x_2, effName), Toast.LENGTH_SHORT).show()
                                        val downloaded = NetworkUtils.downloadFile(context, msg.downloadUrl, effName)
                                        if (!downloaded.isNullOrBlank()) {
                                            chatMessages = chatMessages.map { if (it.id == msg.id) it.copy(fileUriStr = downloaded, isFileType = true, fileName = effName) else it }
                                            openFileInApp(context, downloaded) { route ->
                                                onNavigate?.invoke(route)
                                            }
                                        } else {
                                            Toast.makeText(context, Res.get(R.string.nao_foi_possivel_baixar_o_arquivo_o_reme), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, Res.get(R.string.arquivo_nao_encontrado_localmente), Toast.LENGTH_SHORT).show()
                                }
                                messageToDeleteSingle = null
                            }
                        ) {
                            Text(Res.get(R.string.abrir))
                        }
                    }
                    TextButton(
                        onClick = {
                            isBatchSelectionMode = true
                            selectedMessages = setOf(msg.id)
                            messageToDeleteSingle = null
                        },
                        modifier = Modifier.testTag("batch_select_mode_btn")
                    ) {
                        Text(Res.get(R.string.selecionar_multiplos))
                    }
                    TextButton(
                        onClick = { messageToDeleteSingle = null },
                        modifier = Modifier.testTag("dismiss_delete_single_btn")
                    ) {
                        Text(Res.get(R.string.cancelar))
                    }
                }
            },
            modifier = Modifier.testTag("single_message_options_dialog")
        )
    }
}

// Network Helpers
private fun sweepLocalSubnet(myIp: String, myDeviceName: String) {
    if (myIp == "127.0.0.1" || !myIp.contains(".")) return
    val lastDot = myIp.lastIndexOf('.')
    if (lastDot <= 0) return
    val prefix = myIp.substring(0, lastDot + 1)
    val myHostNum = myIp.substring(lastDot + 1).toIntOrNull() ?: -1

    var socket: DatagramSocket? = null
    try {
        val discoveryMsg = "CHAT_DISCOVER:$myDeviceName:$myIp"
        val bytes = discoveryMsg.toByteArray()
        socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
        }
        for (i in 1..254) {
            if (i == myHostNum) continue
            try {
                val target = InetAddress.getByName("$prefix$i")
                val packet = DatagramPacket(bytes, bytes.size, target, 9988)
                socket.send(packet)
            } catch (e: Exception) {
                // Ignore unreachable host
            }
        }
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
    } finally {
        try {
            socket?.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
}

private fun broadcastPresence(myIp: String, myDeviceName: String) {
    if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", Res.get(R.string.broadcasting_presence_x_x, myDeviceName, myIp))
    var socket: DatagramSocket? = null
    try {
        val discoveryMsg = "CHAT_DISCOVER:$myDeviceName:$myIp"
        val bytes = discoveryMsg.toByteArray()
        socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
        }

        // Try broadcasting to general 255.255.255.255
        try {
            val generalPacket = DatagramPacket(
                bytes, bytes.size,
                InetAddress.getByName("255.255.255.255"), 9988
            )
            socket.send(generalPacket)
            if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", Res.get(R.string.broadcast_sent_to_255_255_255_255_9988))
        } catch (e: Exception) {
            android.util.Log.e("LocalChat", Res.get(R.string.failed_to_broadcast_to_255_255_255_255_x, (e.message)))
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }

        // Try broadcasting to each network interface's specific broadcast address
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcastAddr = interfaceAddress.broadcast
                    if (broadcastAddr != null) {
                        try {
                            val directPacket = DatagramPacket(
                                bytes, bytes.size,
                                broadcastAddr, 9988
                            )
                            socket.send(directPacket)
                            if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", Res.get(R.string.broadcast_sent_to_x_9988, broadcastAddr))
                        } catch (ex: Exception) {
                            android.util.Log.e("LocalChat", Res.get(R.string.failed_to_broadcast_to_x_x, broadcastAddr, (ex.message)))
                            ex.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalChat", Res.get(R.string.failed_to_broadcast_to_interfaces_x, (e.message)))
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    } catch (e: Exception) {
        android.util.Log.e("LocalChat", Res.get(R.string.broadcast_error_x, (e.message)))
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
    } finally {
        try {
            socket?.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
}

private fun announceToPeerDirect(peerIp: String, myDeviceName: String, myIp: String) {
    var socket: DatagramSocket? = null
    try {
        val announceMsg = "CHAT_ANNOUNCE:$myDeviceName:$myIp"
        val bytes = announceMsg.toByteArray()
        socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
        }
        val packet = DatagramPacket(
            bytes, bytes.size,
            InetAddress.getByName(peerIp), 9988
        )
        socket.send(packet)
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
    } finally {
        try {
            socket?.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
}

private fun sendPayloadToPeer(peerIp: String, payload: JSONObject) {
    if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", "Sending payload to $peerIp: ${payload.optString("type")}")
    var lastException: Exception? = null
    for (attempt in 1..3) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peerIp, 9989), 5000)
                if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", Res.get(R.string.connected_to_x_sending_data, peerIp))
                PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true).use { writer ->
                    writer.println(payload.toString())
                    writer.flush()
                }
                if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChat", Res.get(R.string.data_sent_successfully_to_x, peerIp))
            }
            return // Successful delivery
        } catch (e: Exception) {
            android.util.Log.e("LocalChat", Res.get(R.string.failed_to_send_to_x_attempt_x_x, peerIp, attempt, (e.message)))
            lastException = e
            if (attempt < 3) {
                try {
                    Thread.sleep(1000)
                } catch (ie: InterruptedException) {
                    // Ignore and proceed
                }
            }
        }
    }
    android.util.Log.e("LocalChat", Res.get(R.string.failed_to_send_after_3_attempts_to_x, peerIp))
    throw lastException ?: java.io.IOException(Res.get(R.string.falha_ao_enviar_dados_apos_3_tentativas, peerIp))
}

// File Helpers
private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "arquivo"
    var size = 0L
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex != -1) name = it.getString(nameIndex)
                if (sizeIndex != -1) size = it.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
    }
    return Pair(name, size)
}

private fun readFileAsBase64(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val bytes = inputStream.readBytes()
        inputStream.close()
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        ""
    }
}

private fun saveBase64ToFile(context: Context, fileName: String, base64Data: String): File? {
    return try {
        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        downloadDir.mkdirs()
        val safeFileName = fileName.replace("/", "_").replace("\\", "_").ifBlank { "download_${System.currentTimeMillis()}" }
        val receivedFile = File(downloadDir, safeFileName)
        receivedFile.writeBytes(bytes)
        receivedFile
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        null
    }
}

private fun getEffectiveFileName(msg: ChatMessage): String {
    if (msg.fileName.isNotBlank()) return msg.fileName
    val content = msg.content
    return when {
        content.contains(Res.get(R.string.recebeu)) -> content.substringAfter(Res.get(R.string.recebeu)).substringBeforeLast(" (").trim()
        content.contains(Res.get(R.string.enviou)) -> content.substringAfter(Res.get(R.string.enviou)).substringBeforeLast(" (").trim()
        else -> ""
    }
}

private fun isMessageFile(msg: ChatMessage): Boolean {
    return msg.isFileType || msg.fileName.isNotBlank() || msg.content.contains(Res.get(R.string.recebeu)) || msg.content.contains(Res.get(R.string.enviou))
}

private fun resolveLocalFile(context: Context, fileUriStr: String?, fileName: String?): File? {
    if (!fileUriStr.isNullOrBlank()) {
        try {
            val f = File(fileUriStr)
            if (f.exists() && f.isFile && f.length() > 0) return f
        } catch (_: Exception) {}
    }
    if (!fileName.isNullOrBlank()) {
        val safe = fileName.replace("/", "_").replace("\\", "_")
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val candidates = listOf(
            File(downloadDir, safe),
            File(downloadDir, fileName),
            File(context.getExternalFilesDir(null) ?: context.filesDir, "recebido_$fileName"),
            File(context.getExternalFilesDir(null) ?: context.filesDir, "recebido_$safe"),
            File(context.filesDir, safe),
            File(context.filesDir, fileName),
            File(context.cacheDir, safe),
            File(context.cacheDir, fileName),
            File(File(context.cacheDir, "http_transfer"), safe),
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), safe),
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
        )
        for (candidate in candidates) {
            try {
                if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                    return candidate
                }
            } catch (_: Exception) {}
        }
    }
    return null
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return Res.get(R.string.n_0_b)
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(java.util.Locale.US, Res.get(R.string.n_1f_s), size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun vibrateDevice(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
    }
}

private fun vibrateDeviceHeavy(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 150, 100, 150, 100, 300)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(600)
            }
        }
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
    }
}

private fun getAvatarColor(name: String): Color {
    val hash = name.hashCode()
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
        Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DD0E1),
        Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F),
        Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFA1887F), Color(0xFF90A4AE)
    )
    val index = Math.abs(hash) % colors.size
    return colors[index]
}

private fun playSendSound(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        try {
            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val r = android.media.RingtoneManager.getRingtone(context.applicationContext, notification)
            if (r != null) {
                r.play()
            } else {
                val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
            }
        } catch (e: Exception) {
            try {
                val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}

private fun getMimeTypeForFile(file: java.io.File, uri: Uri? = null, context: Context? = null): String {
    val ext = file.extension.lowercase()
    return when (ext) {
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "txt", "log", "json", "xml", "kt", "java", "md", "csv", "ini", "yaml" -> "text/plain"
        "zip", "tar", "gz", "7z", "rar" -> "application/zip"
        else -> {
            val fromExt = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!fromExt.isNullOrEmpty()) fromExt
            else if (uri != null && context != null) context.contentResolver.getType(uri) ?: "*/*"
            else "*/*"
        }
    }
}

private fun openReceivedFile(context: Context, filePath: String) {
    try {
        var file = java.io.File(filePath)
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            val resolved = resolveLocalFile(context, filePath, file.name)
            if (resolved != null) {
                file = resolved
            } else {
                Toast.makeText(context, Res.get(R.string.arquivo_nao_encontrado_localmente), Toast.LENGTH_SHORT).show()
                return
            }
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val mimeType = getMimeTypeForFile(file, uri, context)

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = android.content.Intent.createChooser(intent, Res.get(R.string.abrir_com)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        try {
            val file = java.io.File(filePath)
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(fallbackIntent, Res.get(R.string.abrir_com)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (ex: Exception) {
            Toast.makeText(context, Res.get(R.string.nenhum_aplicativo_encontrado_para_abrir), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareReceivedFile(context: Context, filePath: String) {
    try {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, Res.get(R.string.arquivo_nao_encontrado), Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val mimeType = getMimeTypeForFile(file, uri, context)

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, Res.get(R.string.compartilhar_arquivo_2)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        Toast.makeText(context, Res.get(R.string.erro_ao_compartilhar), Toast.LENGTH_SHORT).show()
    }
}

private fun getFileType(filePath: String): String {
    val extension = filePath.substringAfterLast('.', "").lowercase()
    return when (extension) {
        in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> "image"
        in listOf("mp4", "mkv", "avi", "mov", "webm") -> "video"
        in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") -> "audio"
        "pdf" -> "pdf"
        in listOf("epub", "mobi") -> "epub"
        in listOf("txt", "log", "json", "xml", "kt", "java", "md", "csv", "ini", "yaml") -> "text"
        else -> "other"
    }
}

private fun openFileInApp(context: Context, filePath: String, onNavigate: ((String) -> Unit)? = null) {
    val file = java.io.File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, Res.get(R.string.arquivo_nao_encontrado_localmente), Toast.LENGTH_SHORT).show()
        return
    }
    val fileType = getFileType(filePath)
    val encodedPath = try {
        java.net.URLEncoder.encode(filePath, "UTF-8")
    } catch (e: Exception) {
        filePath
    }
    
    if (onNavigate != null) {
        when (fileType) {
            "image", "video", "audio" -> {
                onNavigate("media_player?path=$encodedPath")
                return
            }
            "pdf" -> {
                onNavigate("pdf_viewer?path=$encodedPath")
                return
            }
            "epub" -> {
                onNavigate("epub_viewer?path=$encodedPath")
                return
            }
            "text" -> {
                onNavigate("text_editor?path=$encodedPath")
                return
            }
        }
    }
    // Fallback to open externally if onNavigate is null or the format is not supported internally
    openReceivedFile(context, filePath)
}

private fun openFileExternally(context: Context, filePath: String) {
    openReceivedFile(context, filePath)
}

private fun openFolderInFileManager(context: Context, filePath: String) {
    try {
        val file = java.io.File(filePath)
        val parentFile = file.parentFile ?: file
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, parentFile)
        
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, Res.get(R.string.nao_foi_possivel_abrir_o_gerenciador_de), Toast.LENGTH_SHORT).show()
        }
    }
}

