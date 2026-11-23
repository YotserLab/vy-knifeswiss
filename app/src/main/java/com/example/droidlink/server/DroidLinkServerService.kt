package com.example.droidlink.server
import com.example.utils.net.NetworkUtils

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.droidlink.model.DroidLinkSettingsRepository
import com.example.droidlink.protocol.FrameType
import com.example.droidlink.protocol.SessionCrypto
import com.example.droidlink.protocol.WireFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.SecretKey
import com.example.R
import com.example.Res

// ─────────────────────────────────────────────────────────────────────────────
// State model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents the runtime state of [DroidLinkServerService].
 * Observed by the UI via [DroidLinkServerService.serverState].
 */
sealed class DroidLinkServerState {
    /** Service is not running. */
    data object Stopped : DroidLinkServerState()

    /** Service is starting up (binding socket). */
    data object Starting : DroidLinkServerState()

    /**
     * ServerSocket is open and accepting connections.
     * @param port     The TCP port being listened on.
     * @param localIps List of Wi-Fi / network interface IPs displayed to the user.
     */
    data class Listening(
        val port: Int,
        val localIps: List<String>,
        val tailscaleIp: String? = null
    ) : DroidLinkServerState()

    /**
     * A remote client completed the handshake and is currently connected.
     * @param clientIp  Remote IP address of the connected client.
     * @param fps       Current video-stream frame rate (updated via TELEMETRY frames).
     * @param latencyMs Round-trip latency in milliseconds.
     */
    data class ClientConnected(
        val clientIp: String,
        val fps: Float = 0f,
        val latencyMs: Long = 0L
    ) : DroidLinkServerState()

    /** A non-recoverable error occurred. */
    data class Error(val message: String) : DroidLinkServerState()
}

// ─────────────────────────────────────────────────────────────────────────────
// Service
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DroidLink Remote — foreground server service.
 *
 * This service:
 *  1. Opens a [ServerSocket] on the configured port.
 *  2. Performs a secure handshake (AUTH_CHALLENGE → AUTH_RESPONSE → AUTH_OK/FAIL).
 *  3. Derives the session key from the salt provided via [EXTRA_SALT].
 *  4. Keeps the session key in memory only — it is never written to disk or logged.
 *
 * Security requirements observed here:
 *  - [android:exported] must be "false" in AndroidManifest.xml (enforced there).
 *  - Passwords are never accepted or stored; only the [EXTRA_SALT] is passed.
 *  - No plaintext secrets appear in logs.
 *  - Backoff is applied after a failed handshake attempt.
 *
 * Manifest requirements (inform the manifest worker):
 *  - <service android:name=".droidlink.server.DroidLinkServerService"
 *             android:exported="false"
 *             android:foregroundServiceType="mediaProjection|dataSync" />
 *  - Permissions needed:
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
 *      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
 */
class DroidLinkServerService : Service() {

    companion object {
        private const val TAG = "DroidLinkServer"

        // Intent actions
        const val ACTION_START = "com.example.droidlink.ACTION_START"
        const val ACTION_STOP  = "com.example.droidlink.ACTION_STOP"
        const val ACTION_MEDIA_PROJECTION_GRANT = "com.example.droidlink.ACTION_MEDIA_PROJECTION_GRANT"

        // Intent extras
        const val EXTRA_PORT = "extra_port"
        /** Base64-encoded salt bytes. The password is NEVER passed via Intent. */
        const val EXTRA_SALT = "extra_salt"

        // Notification
        private const val NOTIFICATION_ID     = 0xD401
        private const val CHANNEL_ID          = "droidlink_server"
        private const val CHANNEL_NAME        = "DroidLink Remote Server"

        // Public state observable by the UI without binding
        private val _serverState = MutableStateFlow<DroidLinkServerState>(DroidLinkServerState.Stopped)
        val serverState: StateFlow<DroidLinkServerState> = _serverState.asStateFlow()

        /**
         * Convenience helper to build a start intent with the required extras.
         * The caller must pass [saltBytes] derived from the password outside this service.
         */
        fun buildStartIntent(context: Context, port: Int, saltBytes: ByteArray): Intent =
            Intent(context, DroidLinkServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_SALT, saltBytes)
            }

        fun resolveLocalIps(context: Context): List<String> {
            val ips = mutableListOf<String>()
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                val ipInt = wifiInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    val ipBytes = ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(ipInt)
                        .array()
                    val ip = ipBytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
                    ips.add(ip)
                }
            } catch (e: Exception) {
                Log.w(TAG, "WifiManager IP lookup failed: ${e.message}")
            }

            try {
                NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                    if (iface.isLoopback || !iface.isUp) return@forEach
                    iface.inetAddresses.toList().forEach { addr ->
                        val hostAddr = addr.hostAddress ?: return@forEach
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            if (!ips.contains(hostAddr)) {
                                ips.add(hostAddr)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "NetworkInterface enumeration failed: ${e.message}")
            }
            return ips
        }
    }

    // ── coroutine scope ──────────────────────────────────────────────────────
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null

    // ── runtime state ────────────────────────────────────────────────────────
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var mediaProjection: MediaProjection? = null
    private var audioCapture: DroidLinkAudioCapture? = null
    private var originalMusicVolume: Int? = null
    @Volatile private var ephemeralKey: SecretKey? = null  // in-memory only, never logged
    @Volatile private var fixedKey: SecretKey? = null      // in-memory only, never logged
    @Volatile private var sessionKey: SecretKey? = null    // matched key for active session
    @Volatile private var configuredPort: Int = 8989 // DLK-R09
    @Volatile private var salt: ByteArray = ByteArray(0)

    // ── failed-handshake back-off ────────────────────────────────────────────
    private var backoffMs = 500L

    // ── notification manager ─────────────────────────────────────────────────
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null  // Not a bound service

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                configuredPort = intent.getIntExtra(EXTRA_PORT, 8989)
                salt = intent.getByteArrayExtra(EXTRA_SALT) ?: ByteArray(0)
                // Consume the pre-derived keys from the in-memory holder (never from Intent)
                PendingKeyHolder.consumeKeys()?.let { keys ->
                    ephemeralKey = keys.ephemeralKey
                    fixedKey = keys.fixedKey
                }
                startForegroundWithNotification()
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
            ACTION_MEDIA_PROJECTION_GRANT -> {
                val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>("data")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    mediaProjection = mpManager?.getMediaProjection(resultCode, data)
                    Log.i(TAG, "MediaProjection successfully acquired by service")
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Foreground notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(Res.get(R.string.starting)))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = Res.get(R.string.droidlink_remote_server_status)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val stopIntent = Intent(this, DroidLinkServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(Res.get(R.string.droidlink_remote))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_share) // Replace with Icons.Default.Cast equivalent drawable
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MAX)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(statusText: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server socket loop
    // ─────────────────────────────────────────────────────────────────────────

    private fun startServer() {
        serverJob?.cancel()
        serverJob = serviceScope.launch {
            _serverState.value = DroidLinkServerState.Starting

            try {
                val ss = ServerSocket(configuredPort).also { serverSocket = it }
                val localIps = resolveLocalIps()
                val tailscaleIp = com.example.utils.net.NetworkUtils.getTailscaleIpAddressSync()

                _serverState.value = DroidLinkServerState.Listening(
                    port = ss.localPort,
                    localIps = localIps,
                    tailscaleIp = tailscaleIp
                )
                updateNotification(Res.get(R.string.listening_on_port_x, (ss.localPort)))
                Log.i(TAG, "Server listening on port ${ss.localPort}")

                while (isActive && !ss.isClosed) {
                    val clientSocket = try {
                        ss.accept()
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.w(TAG, "Accept interrupted: ${e.message}")
                        }
                        break
                    }

                    // Handle one client at a time (sequential for simplicity)
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                val msg = Res.get(R.string.server_error_x, (e.message))
                Log.e(TAG, msg)
                _serverState.value = DroidLinkServerState.Error(msg)
                updateNotification(Res.get(R.string.error_x, (e.message)))
            } finally {
                _serverState.value = DroidLinkServerState.Stopped
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        serverSocket?.runCatching { close() }
        serverSocket = null
        audioCapture?.stop()
        audioCapture = null
        mediaProjection?.stop()
        mediaProjection = null
        ephemeralKey = null  // discard keys from memory
        fixedKey = null
        sessionKey = null
        _serverState.value = DroidLinkServerState.Stopped
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handshake
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Performs the DroidLink Remote handshake protocol:
     *
     *   Server → Client: AUTH_CHALLENGE  (nonce[16] || salt[16])
     *   Client → Server: AUTH_RESPONSE   (HMAC-SHA256(derivedKey, nonce)[32])
     *   Server → Client: AUTH_OK | AUTH_FAIL
     *
     * On success the session key is stored in [sessionKey] (in memory only).
     * On failure a progressive back-off is applied before closing the socket.
     *
     * Security notes:
     *  - The derived key is never logged or persisted.
     *  - The password is not held by this service; only the salt is available.
     *    The ViewModel derives the key before the handshake and passes it here
     *    indirectly through the shared [sessionKey] field (set by the ViewModel
     *    via [setSessionKey] before accepting connections).
     *
     * Note: Because the service only receives the salt (not the password), the
     * actual key must be derived by [DroidLinkServerViewModel] and injected via
     * [setDerivedKey] prior to starting the service's accept loop.
     */
    private suspend fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress.hostAddress ?: "unknown"
        Log.i(TAG, "Client connected from $clientIp")
        var activeRecordId: String? = null

        try {
            socket.use { s ->
                val outputStream = s.getOutputStream()
                val inputStream  = s.getInputStream()

                // ── Step 1: AUTH_CHALLENGE ────────────────────────────────
                val nonce = SessionCrypto.generateNonce()
                val challengePayload = buildChallengePayload(nonce, salt)
                val challengeFrame = WireFrame(
                    type      = FrameType.AUTH_CHALLENGE,
                    requestId = 0,
                    payload   = challengePayload
                )
                outputStream.write(WireFrame.encode(challengeFrame))
                outputStream.flush()

                // ── Step 2: AUTH_RESPONSE ─────────────────────────────────
                val responseFrame = WireFrame.fromStream(inputStream)
                if (responseFrame.type != FrameType.AUTH_RESPONSE) {
                    Log.w(TAG, "Unexpected frame type during handshake: ${responseFrame.type}")
                    sendAuthFail(outputStream)
                    applyBackoff()
                    return
                }

                val receivedHmac = responseFrame.payload
                val eph = ephemeralKey  // snapshot for thread safety
                val fix = fixedKey

                // DLK-R14: dual auth — HMAC verified against ephemeral key or fixed key (SecurePrefs)
                // ── Step 3: Verify HMAC against ephemeral or fixed key ────
                val matchedKey = when {
                    eph != null && SessionCrypto.verifyHmac(eph, nonce, receivedHmac) -> eph
                    fix != null && SessionCrypto.verifyHmac(fix, nonce, receivedHmac) -> fix
                    else -> null
                }

                if (matchedKey != null) {
                    sessionKey = matchedKey
                    // Auth succeeded
                    backoffMs = 500L  // reset backoff on success
                    sendAuthOk(outputStream)
                    val clientState = DroidLinkServerState.ClientConnected(clientIp = clientIp)
                    _serverState.value = clientState
                    updateNotification(Res.get(R.string.connected_x, clientIp))
                    Log.i(TAG, "Client $clientIp authenticated")

                    val authType = if (matchedKey === fix) Res.get(R.string.senha_fixa) else Res.get(R.string.senha_temporaria)
                    activeRecordId = DroidLinkConnectionHistory.recordConnection(applicationContext, clientIp, authType)

                    // Hand off to session loop (placeholder; implemented by session worker)
                    runSessionLoop(inputStream, outputStream, clientIp)
                } else {
                    // Auth failed — do NOT log the HMAC or key
                    Log.w(TAG, "Handshake HMAC verification failed for $clientIp")
                    sendAuthFail(outputStream)
                    applyBackoff()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client handler error for $clientIp: ${e.javaClass.simpleName}")
        } finally {
            activeRecordId?.let { id ->
                DroidLinkConnectionHistory.recordDisconnection(applicationContext, id)
            }
            val port = serverSocket?.localPort ?: configuredPort
            val tailscaleIp = com.example.utils.net.NetworkUtils.getTailscaleIpAddressSync()
            _serverState.value = DroidLinkServerState.Listening(port, resolveLocalIps(), tailscaleIp)
            updateNotification(Res.get(R.string.listening_on_port_x_2, port))
        }
    }

    private fun buildChallengePayload(nonce: ByteArray, salt: ByteArray): ByteArray {
        // Payload: nonce (16 bytes) || salt (16 bytes) = 32 bytes
        return nonce + salt
    }

    private fun sendAuthOk(outputStream: java.io.OutputStream) {
        val frame = WireFrame(type = FrameType.AUTH_OK, requestId = 0, payload = ByteArray(0))
        outputStream.write(WireFrame.encode(frame))
        outputStream.flush()
    }

    private fun sendAuthFail(outputStream: java.io.OutputStream) {
        val frame = WireFrame(type = FrameType.AUTH_FAIL, requestId = 0, payload = ByteArray(0))
        outputStream.write(WireFrame.encode(frame))
        outputStream.flush()
    }

    private suspend fun applyBackoff() {
        Log.d(TAG, "Applying backoff: ${backoffMs}ms")
        delay(backoffMs)
        backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
    }

    /**
     * Main session loop after a successful handshake.
     * Streams audio/video and processes gestures/actions based on session settings.
     */
    private fun runSessionLoop(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        clientIp: String
    ) {
        val settings = DroidLinkSettingsRepository.load(applicationContext)

        // 1. Server-side audio output control (keeps speaker on or mutes per setting)
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (!settings.keepServerSpeakerActive && am != null) {
            originalMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }

        // 2. Audio capture and streaming to the client
        if (settings.transmitAudio && mediaProjection != null) {
            val capture = DroidLinkAudioCapture(applicationContext, mediaProjection) { pcmChunk ->
                try {
                    val frame = WireFrame(type = FrameType.AUDIO_FRAME, requestId = 0, payload = pcmChunk)
                    synchronized(outputStream) {
                        outputStream.write(WireFrame.encode(frame))
                        outputStream.flush()
                    }
                } catch (_: Exception) {}
            }
            audioCapture = capture
            capture.start(serviceScope)
        }

        try {
            while (true) {
                val frame = WireFrame.fromStream(inputStream)
                when (frame.type) {
                    FrameType.HEARTBEAT -> {
                        // Echo heartbeat back
                        val pong = WireFrame(
                            type      = FrameType.HEARTBEAT,
                            requestId = frame.requestId,
                            payload   = ByteArray(0)
                        )
                        synchronized(outputStream) {
                            outputStream.write(WireFrame.encode(pong))
                            outputStream.flush()
                        }
                    }
                    FrameType.DISCONNECT -> {
                        Log.i(TAG, "Client $clientIp sent DISCONNECT")
                        break
                    }
                    FrameType.TELEMETRY -> {
                        parseTelemetryAndUpdateState(frame.payload, clientIp)
                    }
                    FrameType.SYSTEM_ACTION -> {
                        if (settings.allowSystemActions && frame.payload.isNotEmpty()) {
                            val actionCode = frame.payload[0]
                            val action = com.example.droidlink.client.SystemAction.entries.firstOrNull { it.code == actionCode }
                            if (action != null) {
                                RemoteInputAccessibilityService.executeSystemAction(action)
                            }
                        }
                    }
                    FrameType.INPUT_GESTURE -> {
                        if (settings.allowRemoteInput) {
                            handleInputGesture(frame.payload)
                        }
                    }
                    else -> {
                        Log.d(TAG, "Received frame ${frame.type}")
                    }
                }
            }
        } catch (e: java.io.EOFException) {
            Log.i(TAG, "Client $clientIp disconnected (EOF)")
        } finally {
            audioCapture?.stop()
            audioCapture = null
            if (!settings.keepServerSpeakerActive && originalMusicVolume != null) {
                am?.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume!!, 0)
                originalMusicVolume = null
            }
        }
    }

    private fun parseTelemetryAndUpdateState(payload: ByteArray, clientIp: String) {
        if (payload.size < 8) return
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val fps       = buf.float
        val latencyMs = buf.int.toLong()
        _serverState.value = DroidLinkServerState.ClientConnected(
            clientIp  = clientIp,
            fps       = fps,
            latencyMs = latencyMs
        )
    }

    private fun handleInputGesture(payload: ByteArray) {
        if (payload.size < 9) return
        try {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            val typeCode = buffer.get()
            val normX = buffer.float
            val normY = buffer.float

            val dm = resources.displayMetrics
            val realX = normX * dm.widthPixels
            val realY = normY * dm.heightPixels

            val type = com.example.droidlink.client.GestureType.entries.firstOrNull { it.code == typeCode }
            when (type) {
                com.example.droidlink.client.GestureType.TAP -> {
                    RemoteInputAccessibilityService.tap(realX, realY, 50L)
                }
                com.example.droidlink.client.GestureType.LONG_PRESS -> {
                    RemoteInputAccessibilityService.tap(realX, realY, 800L)
                }
                else -> {
                    RemoteInputAccessibilityService.tap(realX, realY, 50L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle input gesture: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session key injection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by [DroidLinkServerViewModel] after key derivation, immediately
     * before starting the service. Stores the key in memory only.
     * Never passes the password or the key via Intent.
     */
    fun setDerivedKey(key: SecretKey) {
        sessionKey = key
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Collects all local IPv4 addresses from Wi-Fi and network interfaces.
     * Returns addresses suitable for display in the QR code / connection UI.
     */
    @Suppress("DEPRECATION")
    private fun resolveLocalIps(): List<String> {
        val ips = mutableListOf<String>()

        // 1. Wi-Fi manager (deprecated API still works up to Android 12 for local IP)
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ipBytes = ByteBuffer.allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(ipInt)
                    .array()
                val ip = ipBytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
                ips.add(ip)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager IP lookup failed: ${e.message}")
        }

        // 2. NetworkInterface enumeration (broader, works for hotspot / Ethernet too)
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (iface.isLoopback || !iface.isUp) return@forEach
                iface.inetAddresses.toList().forEach { addr ->
                    val hostAddr = addr.hostAddress ?: return@forEach
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        if (!ips.contains(hostAddr)) ips.add(hostAddr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetworkInterface enumeration failed: ${e.message}")
        }

        return ips.ifEmpty { listOf("127.0.0.1") }
    }
}
