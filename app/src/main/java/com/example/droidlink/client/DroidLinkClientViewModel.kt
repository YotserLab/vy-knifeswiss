package com.example.droidlink.client

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.droidlink.protocol.FrameType
import com.example.droidlink.protocol.SessionCrypto
import com.example.droidlink.protocol.WireFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.Socket
import javax.crypto.SecretKey
import com.example.R
import com.example.Res

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

enum class QualityMode(val label: String) {
    LOW_LATENCY("LOW"),
    BALANCED("BAL"),
    HIGH_DETAIL("HD")
}

enum class GestureType(val code: Byte) {
    TAP(0x01),
    DRAG_START(0x02),
    DRAG_MOVE(0x03),
    DRAG_END(0x04),
    LONG_PRESS(0x05),
    SCROLL(0x06)
}

enum class SystemAction(val code: Byte) {
    BACK(0x01),
    HOME(0x02),
    RECENTS(0x03),
    NOTIFICATIONS(0x04),
    LOCK_SCREEN(0x05)
}

data class RemoteResolution(val width: Int, val height: Int, val rotation: Int)

/**
 * A previously used host — stored in plain SharedPreferences because it contains
 * NO sensitive data (no passwords, no tokens).
 */
data class SavedHostProfile(
    val host: String,
    val port: Int,
    val label: String?,
    val lastUsed: Long
)

// ---------------------------------------------------------------------------
// State machine
// ---------------------------------------------------------------------------

sealed class DroidLinkClientState {
    object Disconnected : DroidLinkClientState()
    object Connecting : DroidLinkClientState()
    object Authenticating : DroidLinkClientState()

    /**
     * Successfully authenticated session.
     *
     * @param sessionToken opaque identifier for the session (not a password or key —
     *   it is a random token assigned by the server in AUTH_OK).
     */
    data class Connected(
        val latencyMs: Long,
        val qualityMode: QualityMode,
        val sessionToken: String
    ) : DroidLinkClientState()

    object Reconnecting : DroidLinkClientState()

    data class Error(val message: String) : DroidLinkClientState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel for the DroidLink client (technician console).
 *
 * Security rules:
 *  - Passwords are **never** stored or logged — they are only held in memory
 *    during the handshake and cleared immediately after key derivation.
 *  - [SharedPreferences] stores only non-sensitive data (host/port/label/timestamp).
 *  - No internal state (session keys, raw sockets) is exposed publicly.
 */
class DroidLinkClientViewModel : ViewModel() {

    // ── Public state ────────────────────────────────────────────────────────

    private val _clientState = MutableStateFlow<DroidLinkClientState>(DroidLinkClientState.Disconnected)
    val clientState: StateFlow<DroidLinkClientState> = _clientState.asStateFlow()

    private val _recentHosts = MutableStateFlow<List<SavedHostProfile>>(emptyList())
    val recentHosts: StateFlow<List<SavedHostProfile>> = _recentHosts.asStateFlow()

    // ── Private session state ────────────────────────────────────────────────

    @Volatile private var socket: Socket? = null
    @Volatile private var outputStream: OutputStream? = null

    /** Derived session key — held only in memory, never persisted or logged. */
    @Volatile private var sessionKey: SecretKey? = null

    private var heartbeatJob: Job? = null
    private var readLoopJob: Job? = null
    private var requestIdCounter = 0

    private fun nextRequestId(): Int = ++requestIdCounter

    // ── SharedPreferences (non-sensitive history) ────────────────────────────

    private var prefs: SharedPreferences? = null
    private val PREFS_NAME = "droidlink_client_hosts"
    private val PREFS_KEY_HOSTS = "recent_hosts"
    private val MAX_RECENT_HOSTS = 10

    /**
     * Must be called once with an application [Context] so the ViewModel can
     * persist the host history. Using applicationContext avoids leaking an Activity.
     */
    fun init(context: Context) {
        // Plain SharedPreferences — intentionally so. The data stored here is
        // non-sensitive (host, port, optional human label, last-used timestamp).
        // See secure_coding SKILL: EncryptedSharedPreferences is required ONLY
        // for sensitive data such as tokens, passwords, or cryptographic keys.
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadRecentHosts()
    }

    // ── Connection lifecycle ─────────────────────────────────────────────────

    /**
     * Opens a TCP connection to [host]:[port], performs the DroidLink handshake,
     * and transitions [clientState] through Connecting → Authenticating → Connected.
     *
     * The [password] is used only during key derivation and is **not** stored,
     * logged, or transmitted in plaintext.
     */
    fun connect(host: String, port: Int, password: String) {
        if (_clientState.value is DroidLinkClientState.Connecting ||
            _clientState.value is DroidLinkClientState.Authenticating
        ) return

        viewModelScope.launch(Dispatchers.IO) {
            _clientState.value = DroidLinkClientState.Connecting
            try {
                val s = Socket(host, port)
                socket = s
                outputStream = s.getOutputStream()

                _clientState.value = DroidLinkClientState.Authenticating

                // ── Handshake ──────────────────────────────────────────────
                // 1. Receive AUTH_CHALLENGE  (nonce 16 bytes | salt 16 bytes)
                val challenge = WireFrame.fromStream(s.getInputStream())
                require(challenge.type == FrameType.AUTH_CHALLENGE) {
                    "Expected AUTH_CHALLENGE, got ${challenge.type}"
                }
                val payload = challenge.payload
                require(payload.size >= 32) { "AUTH_CHALLENGE payload too short" }

                val nonce = payload.copyOfRange(0, 16)
                val salt  = payload.copyOfRange(16, 32)

                // 2. Derive session key (password is cleared inside deriveKey)
                val key = SessionCrypto.deriveKey(password, salt)
                sessionKey = key

                // 3. Compute HMAC and send AUTH_RESPONSE
                val hmac = SessionCrypto.computeHmac(key, nonce)
                val responseFrame = WireFrame(
                    type      = FrameType.AUTH_RESPONSE,
                    requestId = nextRequestId(),
                    payload   = hmac
                )
                sendFrame(responseFrame)

                // 4. Await AUTH_OK or AUTH_FAIL
                val authResult = WireFrame.fromStream(s.getInputStream())
                when (authResult.type) {
                    FrameType.AUTH_OK -> {
                        // Session token is a human-readable, non-sensitive identifier
                        val token = authResult.payload
                            .toString(Charsets.UTF_8)
                            .ifBlank { "session-${System.currentTimeMillis()}" }

                        _clientState.value = DroidLinkClientState.Connected(
                            latencyMs   = 0L,
                            qualityMode = QualityMode.BALANCED,
                            sessionToken = token
                        )

                        // Persist host in history (no password stored)
                        saveHostToHistory(host, port)

                        // Start heartbeat and read loops
                        startHeartbeat()
                        startReadLoop(s)
                    }
                    FrameType.AUTH_FAIL -> {
                        safeCloseSocket()
                        _clientState.value = DroidLinkClientState.Error(Res.get(R.string.autenticacao_falhou_verifique_a_senha))
                    }
                    else -> {
                        safeCloseSocket()
                        _clientState.value = DroidLinkClientState.Error(Res.get(R.string.resposta_inesperada_do_servidor_x, (authResult.type)))
                    }
                }
            } catch (e: Exception) {
                safeCloseSocket()
                _clientState.value = DroidLinkClientState.Error(
                    e.message?.take(120) ?: Res.get(R.string.erro_de_conexao_desconhecido)
                )
            }
        }
    }

    /** Sends a DISCONNECT frame and closes the socket cleanly. */
    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendFrame(WireFrame(type = FrameType.DISCONNECT, requestId = nextRequestId(), payload = ByteArray(0)))
            } catch (_: Exception) { /* best-effort */ }
            safeCloseSocket()
        }
    }

    // ── Quality control ──────────────────────────────────────────────────────

    fun setQuality(mode: QualityMode) {
        val current = _clientState.value
        if (current !is DroidLinkClientState.Connected) return
        _clientState.value = current.copy(qualityMode = mode)

        viewModelScope.launch(Dispatchers.IO) {
            val payload = byteArrayOf(mode.ordinal.toByte())
            sendFrame(WireFrame(type = FrameType.VIDEO_CONFIG, requestId = nextRequestId(), payload = payload))
        }
    }

    // ── Input forwarding ─────────────────────────────────────────────────────

    /**
     * Sends a gesture event to the server.
     *
     * @param x Normalized X coordinate in [0.0, 1.0]
     * @param y Normalized Y coordinate in [0.0, 1.0]
     */
    fun sendGesture(x: Float, y: Float, type: GestureType) {
        viewModelScope.launch(Dispatchers.IO) {
            // Payload: gestureType(1) | x(4, float BE) | y(4, float BE)
            val payload = java.nio.ByteBuffer.allocate(9).apply {
                order(java.nio.ByteOrder.BIG_ENDIAN)
                put(type.code)
                putFloat(x.coerceIn(0f, 1f))
                putFloat(y.coerceIn(0f, 1f))
            }.array()
            sendFrame(WireFrame(type = FrameType.INPUT_GESTURE, requestId = nextRequestId(), payload = payload))
        }
    }

    /** Sends a system navigation/action to the remote device. */
    fun sendSystemAction(action: SystemAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val payload = byteArrayOf(action.code)
            sendFrame(WireFrame(type = FrameType.SYSTEM_ACTION, requestId = nextRequestId(), payload = payload))
        }
    }

    // ── Heartbeat ────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && socket?.isConnected == true) {
                try {
                    sendHeartbeat()
                } catch (_: Exception) { break }
                delay(1_000L)
            }
        }
    }

    private fun sendHeartbeat() {
        sendFrame(WireFrame(type = FrameType.HEARTBEAT, requestId = nextRequestId(), payload = ByteArray(0)))
    }

    // ── Read loop (server → client) ──────────────────────────────────────────

    private fun startReadLoop(s: Socket) {
        readLoopJob?.cancel()
        readLoopJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                while (isActive && s.isConnected && !s.isClosed) {
                    val frame = WireFrame.fromStream(s.getInputStream())
                    handleIncomingFrame(frame)
                }
            } catch (_: Exception) {
                val current = _clientState.value
                if (current is DroidLinkClientState.Connected) {
                    _clientState.value = DroidLinkClientState.Reconnecting
                    safeCloseSocket()
                    _clientState.value = DroidLinkClientState.Error(Res.get(R.string.conexao_perdida_com_o_servidor))
                }
            }
        }
    }

    // ── Audio player ────────────────────────────────────────────────────────
    private val audioPlayer = DroidLinkAudioPlayer()
    val isAudioMuted = MutableStateFlow(false)
    val audioVolume = MutableStateFlow(1.0f)

    fun toggleAudioMute() {
        val next = !isAudioMuted.value
        isAudioMuted.value = next
        audioPlayer.isMuted = next
    }

    fun setAudioVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        audioVolume.value = clamped
        audioPlayer.volume = clamped
    }

    private fun handleIncomingFrame(frame: WireFrame) {
        when (frame.type) {
            FrameType.HEARTBEAT -> {
                // Update latency based on round-trip (placeholder; server embeds timestamp)
                val current = _clientState.value
                if (current is DroidLinkClientState.Connected && frame.payload.size >= 8) {
                    val serverTs = java.nio.ByteBuffer.wrap(frame.payload).getLong(0)
                    val latency = (System.currentTimeMillis() - serverTs).coerceAtLeast(0)
                    _clientState.value = current.copy(latencyMs = latency)
                }
            }
            FrameType.AUDIO_FRAME -> {
                audioPlayer.playChunk(frame.payload)
            }
            FrameType.DISCONNECT -> {
                safeCloseSocket()
                _clientState.value = DroidLinkClientState.Disconnected
            }
            else -> { /* other frames (video, files, processes) handled by dedicated consumers */ }
        }
    }

    // ── Socket helpers ───────────────────────────────────────────────────────

    @Synchronized
    private fun sendFrame(frame: WireFrame) {
        val os = outputStream ?: return
        val bytes = WireFrame.encode(frame)
        os.write(bytes)
        os.flush()
    }

    private fun safeCloseSocket() {
        heartbeatJob?.cancel()
        readLoopJob?.cancel()
        audioPlayer.stop()
        sessionKey = null // wipe key reference from memory
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        outputStream = null
        if (_clientState.value !is DroidLinkClientState.Error &&
            _clientState.value !is DroidLinkClientState.Disconnected
        ) {
            _clientState.value = DroidLinkClientState.Disconnected
        }
    }

    // ── Host history (SharedPreferences — non-sensitive only) ────────────────

    private fun saveHostToHistory(host: String, port: Int) {
        val prefs = prefs ?: return
        val existing = _recentHosts.value.toMutableList()
        existing.removeAll { it.host == host && it.port == port }
        existing.add(0, SavedHostProfile(host = host, port = port, label = null, lastUsed = System.currentTimeMillis()))
        val trimmed = existing.take(MAX_RECENT_HOSTS)
        _recentHosts.value = trimmed

        val jsonArray = JSONArray()
        trimmed.forEach { profile ->
            val obj = JSONObject().apply {
                put("host", profile.host)
                put("port", profile.port)
                put("label", profile.label ?: JSONObject.NULL)
                put("lastUsed", profile.lastUsed)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(PREFS_KEY_HOSTS, jsonArray.toString()).apply()
    }

    private fun loadRecentHosts() {
        val prefs = prefs ?: return
        val json = prefs.getString(PREFS_KEY_HOSTS, null) ?: return
        try {
            val array = JSONArray(json)
            val hosts = (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                SavedHostProfile(
                    host     = obj.getString("host"),
                    port     = obj.getInt("port"),
                    label    = if (obj.isNull("label")) null else obj.getString("label"),
                    lastUsed = obj.getLong("lastUsed")
                )
            }
            _recentHosts.value = hosts
        } catch (_: Exception) { /* corrupt prefs — ignore */ }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        safeCloseSocket()
    }
}
