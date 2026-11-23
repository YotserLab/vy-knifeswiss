package com.example.service
import com.example.ui.screens.chat.ChatMessage
import com.example.ui.screens.chat.LocalChatStorage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.screens.chat.DevicePeer
import com.example.ui.screens.chat.LocalChatRepository
import com.example.utils.crypto.LocalChatCrypto
import com.example.utils.net.NetworkUtils
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*
import java.util.UUID
import com.example.Res

class LocalChatService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var udpSocket: DatagramSocket? = null
    private var tcpServer: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isServiceRunning = false
    
    private lateinit var sharedPref: SharedPreferences
    private var myIp = "127.0.0.1"
    private val myDeviceName = android.os.Build.MODEL ?: Res.get(R.string.dispositivo_android)
    
    private var lastReceivedClipboardText = ""
    private var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    
    companion object {
        const val CHANNEL_ID = "local_chat_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "com.example.START_CHAT_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.STOP_CHAT_SERVICE"
        const val PREF_CHAT_SERVICE_ENABLED = "chat_service_enabled"
        const val PREF_CHAT_SCREEN_VISIBLE = "chat_screen_visible"
        
        fun isServiceEnabled(context: Context): Boolean {
            return context.getSharedPreferences("local_chat_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_CHAT_SERVICE_ENABLED, false)
        }
        
        fun setServiceEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("local_chat_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_CHAT_SERVICE_ENABLED, enabled)
                .apply()
        }
        
        fun isChatScreenVisible(context: Context): Boolean {
            return context.getSharedPreferences("local_chat_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_CHAT_SCREEN_VISIBLE, false)
        }
        
        fun setChatScreenVisible(context: Context, visible: Boolean) {
            context.getSharedPreferences("local_chat_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_CHAT_SCREEN_VISIBLE, visible)
                .apply()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("local_chat_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        
        serviceScope.launch {
            LocalChatRepository.loadInitialData(applicationContext)
        }
        
        // Get IP (prefer Wi-Fi interface; fallback to any non-loopback)
        myIp = NetworkUtils.getWifiOrLocalIpSync()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> startChatService()
            ACTION_STOP_SERVICE -> stopChatService()
        }
        return START_STICKY
    }
    
    private fun startChatService() {
        if (isServiceRunning) return
        
        isServiceRunning = true
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start network operations
        startUdpDiscovery()
        startTcpServer()
        startPresenceBroadcast()
        startPeerPruner()
        startClipboardListener()
    }
    
    private fun stopChatService() {
        isServiceRunning = false
        cleanupSockets()
        
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboardListener?.let { clipboard.removePrimaryClipChangedListener(it) }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Res.get(R.string.whatsapp_local_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Res.get(R.string.mantem_o_chat_local_ativo_em_background)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, LocalChatService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Res.get(R.string.whatsapp_local_ativo))
            .setContentText(Res.get(R.string.recebendo_mensagens_em_background))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Parar",
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }
    
    private fun startUdpDiscovery() {
        serviceScope.launch {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null) {
                    multicastLock = wifiManager.createMulticastLock("LocalChatServiceLock").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
                
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9988)) // CHAT-R01
                }
                udpSocket?.soTimeout = 2000
                
                // Announce presence immediately
                broadcastPresence(myIp, myDeviceName)
                sweepLocalSubnet(myIp, myDeviceName) // CHAT-R10 chat is P2P on the local network only
                
                val buffer = ByteArray(1024)
                while (isServiceRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
                        val receivedStr = String(packet.data, 0, packet.length).trim()
                        
                        handleUdpMessage(receivedStr, packet.address.hostAddress ?: "")
                    } catch (e: SocketTimeoutException) {
                        // Continue loop
                    } catch (e: Exception) {
                        if (e is SocketException || !isServiceRunning) break
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                cleanupSockets()
            }
        }
    }
    
    private fun startTcpServer() {
        serviceScope.launch {
            try {
                tcpServer = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9989)) // CHAT-R02
                }
                if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChatService", "TCP Server started on port 9989")
                
                while (isServiceRunning) {
                    try {
                        val clientSocket = tcpServer?.accept()
                        if (com.example.BuildConfig.DEBUG) android.util.Log.d("LocalChatService", "TCP Client connected")
                        clientSocket?.let { handleTcpClient(it) }
                    } catch (e: Exception) {
                        if (e is SocketException || !isServiceRunning) break
                        android.util.Log.e("LocalChatService", "TCP Server error: ${e.message}")
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalChatService", "Failed to start TCP server: ${e.message}")
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                cleanupSockets()
            }
        }
    }
    
    private fun handleTcpClient(clientSocket: Socket) {
        serviceScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                val jsonBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    jsonBuilder.append(line).append("\n")
                }
                val payloadStr = jsonBuilder.toString().trim()
                
                if (payloadStr.isNotEmpty()) {
                    val jsonObj = JSONObject(payloadStr)
                    val type = jsonObj.optString("type", "TEXT")
                    val senderName = jsonObj.optString("sender", "Dispositivo")
                    val content = jsonObj.optString("content", "")
                    val senderIp = clientSocket.inetAddress.hostAddress ?: ""
                    if (NetworkUtils.isOwnIp(senderIp)) return@launch

                    if (type == "HANDSHAKE" || type == "PING") { // CHAT-R05
                        withContext(Dispatchers.Main) {
                            val currentPeers = LocalChatRepository.peersList.value
                            LocalChatRepository.peersList.value = currentPeers.filter { it.ip != senderIp }.toSet() +
                                    DevicePeer(senderName, senderIp, System.currentTimeMillis())
                            if (LocalChatRepository.selectedPeer.value == null) {
                                LocalChatRepository.selectedPeer.value = DevicePeer(senderName, senderIp, System.currentTimeMillis())
                            }
                        }
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val pongStr = JSONObject().apply {
                                    put("type", "PONG")
                                    put("sender", myDeviceName)
                                    put("content", "PONG")
                                }.toString() + "\n"
                                Socket().use { s ->
                                    s.connect(InetSocketAddress(senderIp, 9989), 2000)
                                    s.getOutputStream().apply {
                                        write(pongStr.toByteArray())
                                        flush()
                                    }
                                }
                            } catch (e: Exception) {
                                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                            }
                        }
                        return@launch
                    } else if (type == "PONG") {
                        withContext(Dispatchers.Main) {
                            val currentPeers = LocalChatRepository.peersList.value
                            LocalChatRepository.peersList.value = currentPeers.filter { it.ip != senderIp }.toSet() +
                                    DevicePeer(senderName, senderIp, System.currentTimeMillis())
                            if (LocalChatRepository.selectedPeer.value == null) {
                                LocalChatRepository.selectedPeer.value = DevicePeer(senderName, senderIp, System.currentTimeMillis())
                            }
                        }
                        return@launch
                    }

                    val incomingMessage = when (type) {
                        "CLIPBOARD_SYNC" -> {
                            val rawContent = jsonObj.optString("content", "")
                            val textContent = LocalChatCrypto.decrypt(rawContent) ?: rawContent
                            lastReceivedClipboardText = textContent
                            if (textContent.isNotEmpty()) {
                                if (isChatScreenVisible(applicationContext)) {
                                    withContext(Dispatchers.Main) {
                                        try {
                                            val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText(Res.get(R.string.copied_via_local_chat), textContent)
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(applicationContext, Res.get(R.string.area_de_transferencia_sincronizada_de_x, senderName), android.widget.Toast.LENGTH_SHORT).show()
                                        } catch(e: Exception){}
                                    }
                                } else {
                                    showClipboardNotification(senderName, textContent)
                                }
                            }
                            return@launch
                        }
                        "NUDGE" -> {
                            vibrateDeviceHeavy(applicationContext)
                            com.example.ui.screens.chat.ChatMessage(
                                sender = senderName,
                                content = Res.get(R.string.chamou_sua_atencao),
                                timestamp = System.currentTimeMillis(),
                                isMe = false,
                                peerIp = senderIp
                            )
                        }
                        "FILE", "FILE_STREAM" -> {
                            val fileName = jsonObj.optString("fileName", "arquivo")
                            val fileSize = jsonObj.optLong("fileSize", 0)
                            val url = jsonObj.optString("url", "")
                            val base64Data = jsonObj.optString("fileBytesBase64", "")
                            
                            var savedFilePath = ""
                            if (base64Data.isNotEmpty()) {
                                try {
                                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
                                    val downloadDir = applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: applicationContext.filesDir
                                    downloadDir.mkdirs()
                                    val safeFileName = fileName.replace("/", "_").replace("\\", "_").ifBlank { "download_${System.currentTimeMillis()}" }
                                    val file = java.io.File(downloadDir, safeFileName)
                                    file.writeBytes(bytes)
                                    savedFilePath = file.absolutePath
                                } catch (_: Exception) {}
                            } else if (url.isNotEmpty()) {
                                savedFilePath = NetworkUtils.downloadFile(applicationContext, url, fileName) ?: ""
                            }
                            
                            com.example.ui.screens.chat.ChatMessage(
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
                        }
                        else -> {
                            vibrateDevice(applicationContext)
                            com.example.ui.screens.chat.ChatMessage(
                                sender = senderName,
                                content = LocalChatCrypto.decrypt(content) ?: content,
                                timestamp = System.currentTimeMillis(),
                                isMe = false,
                                peerIp = senderIp
                            )
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        LocalChatRepository.chatMessages.value = LocalChatRepository.chatMessages.value + incomingMessage
                        serviceScope.launch {
                            com.example.ui.screens.chat.LocalChatStorage.saveChatHistory(
                                applicationContext, 
                                LocalChatRepository.peersList.value, 
                                LocalChatRepository.chatMessages.value
                            )
                        }

                        // Increment unread count if chat screen is not visible
                        if (!isChatScreenVisible(applicationContext)) {
                            LocalChatRepository.unreadCount.value++
                        }

                        // Show notification for received message
                        showNotificationForMessage(senderName, content, type)

                        // Auto-select peer if not selected
                        if (LocalChatRepository.selectedPeer.value == null) {
                            LocalChatRepository.selectedPeer.value = DevicePeer(senderName, senderIp, System.currentTimeMillis())
                        }

                        // Update peers list
                        val currentPeers = LocalChatRepository.peersList.value
                        LocalChatRepository.peersList.value = currentPeers.filter { it.ip != senderIp }.toSet() +
                                DevicePeer(senderName, senderIp, System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                clientSocket.close()
            }
        }
    }
    
    private fun startPresenceBroadcast() {
        serviceScope.launch {
            while (isServiceRunning) {
                if (myIp != "127.0.0.1") {
                    broadcastPresence(myIp, myDeviceName)
                }
                delay(5000)
            }
        }
    }
    
    private fun startPeerPruner() {
        serviceScope.launch {
            while (isServiceRunning) {
                delay(15000)
                val now = System.currentTimeMillis()
                withContext(Dispatchers.Main) {
                    val updated = LocalChatRepository.peersList.value.filter { peer ->
                        val hasHistory = LocalChatRepository.chatMessages.value.any { it.peerIp == peer.ip }
                        hasHistory || (now - peer.lastSeen) < 10 * 60 * 1000
                    }.toSet()
                    if (updated.size != LocalChatRepository.peersList.value.size) {
                        LocalChatRepository.peersList.value = updated
                        serviceScope.launch {
                            com.example.ui.screens.chat.LocalChatStorage.saveChatHistory(
                                applicationContext, 
                                LocalChatRepository.peersList.value, 
                                LocalChatRepository.chatMessages.value
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun handleUdpMessage(receivedStr: String, senderIp: String) {
        when {
            receivedStr.startsWith("CHAT_DISCOVER:") -> {
                val parts = receivedStr.split(":")
                if (parts.size >= 3 && !NetworkUtils.isOwnIp(senderIp)) {
                    val peerNick = parts[1]
                    val peerIp = parts[2].trim()
                    
                    serviceScope.launch(Dispatchers.Main) {
                        val currentPeers = LocalChatRepository.peersList.value
                        LocalChatRepository.peersList.value = currentPeers.filter { it.ip != peerIp }.toSet() + 
                                DevicePeer(peerNick, peerIp, System.currentTimeMillis())
                    }
                    
                    // Respond directly
                    serviceScope.launch {
                        announceToPeerDirect(peerIp, myDeviceName, myIp)
                    }
                }
            }
            receivedStr.startsWith("CHAT_ANNOUNCE:") -> {
                val parts = receivedStr.split(":")
                if (parts.size >= 3 && !NetworkUtils.isOwnIp(senderIp)) {
                    val peerNick = parts[1]
                    val peerIp = parts[2].trim()
                    
                    serviceScope.launch(Dispatchers.Main) {
                        val currentPeers = LocalChatRepository.peersList.value
                        LocalChatRepository.peersList.value = currentPeers.filter { it.ip != peerIp }.toSet() + 
                                DevicePeer(peerNick, peerIp, System.currentTimeMillis())
                    }
                }
            }
        }
    }
    
    private fun sweepLocalSubnet(myIp: String, myDeviceName: String) { // CHAT-R04
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
        try {
            val discoveryMsg = "CHAT_DISCOVER:$myDeviceName:$myIp"
            val bytes = discoveryMsg.toByteArray()
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
            }
            
            try {
                val packet = DatagramPacket(
                    bytes, bytes.size,
                    InetAddress.getByName("255.255.255.255"), 9988
                )
                socket.send(packet)
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            }
            
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        val broadcastAddr = interfaceAddress.broadcast
                        if (broadcastAddr != null) {
                            try {
                                val packet = DatagramPacket(
                                    bytes, bytes.size,
                                    broadcastAddr, 9988
                                )
                                socket.send(packet)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            }
            
            socket.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
    
    private fun announceToPeerDirect(peerIp: String, myDeviceName: String, myIp: String) {
        try {
            val announceMsg = "CHAT_ANNOUNCE:$myDeviceName:$myIp"
            val bytes = announceMsg.toByteArray()
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
            }
            val packet = DatagramPacket(
                bytes, bytes.size,
                InetAddress.getByName(peerIp), 9988
            )
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
    
    private fun cleanupSockets() {
        try {
            udpSocket?.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
        try {
            tcpServer?.close()
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
    
    private fun showNotificationForMessage(senderName: String, content: String, type: String) {
        if (isChatScreenVisible(applicationContext)) {
            return
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationContent = when (type) {
            "NUDGE" -> Res.get(R.string.chamou_sua_atencao_2)
            "FILE" -> Res.get(R.string.enviou_um_arquivo)
            else -> content
        }
        
        val shortcutId = "chat_bubble_${senderName.hashCode()}"
        val iconCompat = androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.drawable.ic_launcher_foreground)
        val person = androidx.core.app.Person.Builder()
            .setName(senderName)
            .setIcon(iconCompat)
            .setImportant(true)
            .build()
            
        val shortcutInfo = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, shortcutId)
            .setShortLabel(senderName)
            .setLongLabel(Res.get(R.string.chat_com_x, senderName))
            .setIcon(iconCompat)
            .setLongLived(true)
            .setCategories(setOf("com.example.category.CHAT"))
            .setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_VIEW })
            .setPerson(person)
            .build()
            
        androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(this, shortcutInfo)
        
        val bubbleIntent = Intent(this, com.example.ui.activities.LocalChatBubbleActivity::class.java)
        val pendingBubbleIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            bubbleIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(pendingBubbleIntent, iconCompat)
            .setDesiredHeight(600)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()
            
        val messagingStyle = NotificationCompat.MessagingStyle(
            androidx.core.app.Person.Builder().setName("Eu").build()
        ).addMessage(notificationContent, System.currentTimeMillis(), person)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setShortcutId(shortcutId)
            .addPerson(person)
            .setStyle(messagingStyle)
            .setBubbleMetadata(bubbleMetadata)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun vibrateDevice(context: Context) {
        if (!sharedPref.getBoolean("vibrate_on_msg", true)) return
        
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
    
    private fun vibrateDeviceHeavy(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 200, 100, 200)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 200, 100, 200)
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) e.printStackTrace()
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> Res.get(R.string.x_b, bytes)
            bytes < 1024 * 1024 -> Res.get(R.string.x_1024_kb_2, (bytes / 1024))
            else -> Res.get(R.string.x_1024_1024_mb, (bytes / (1024 * 1024)))
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        cleanupSockets()
        serviceScope.cancel()
    }
    
    private fun startClipboardListener() {
        if (!sharedPref.getBoolean("sync_clipboard_enabled", false)) return
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            try {
                if (!sharedPref.getBoolean("sync_clipboard_enabled", false)) return@OnPrimaryClipChangedListener
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!text.isNullOrEmpty() && text != lastReceivedClipboardText) {
                    broadcastClipboard(text)
                }
            } catch (e: Exception) { if (com.example.BuildConfig.DEBUG) e.printStackTrace() }
        }
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun broadcastClipboard(text: String) {
        if (!sharedPref.getBoolean("sync_clipboard_enabled", false)) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val encrypted = LocalChatCrypto.encrypt(text) ?: text
                val payloadStr = JSONObject().apply {
                    put("type", "CLIPBOARD_SYNC")
                    put("sender", myDeviceName)
                    put("content", encrypted)
                }.toString()
                
                val peers = LocalChatRepository.peersList.value.toList()
                for (peer in peers) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(peer.ip, 9989), 2000)
                        val out = socket.getOutputStream()
                        out.write((payloadStr + "\n").toByteArray())
                        out.flush()
                        socket.close()
                    } catch (e: Exception) {
                        if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) e.printStackTrace()
            }
        }
    }

    private fun showClipboardNotification(senderName: String, text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CLIPBOARD_SYNC_TEXT", text)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Res.get(R.string.area_de_transferencia_de_x, senderName))
            .setContentText(Res.get(R.string.toque_para_copiar_x, text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
