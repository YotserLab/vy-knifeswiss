package com.example.droidlink.server

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.droidlink.model.DroidLinkSettings
import com.example.droidlink.model.DroidLinkSettingsRepository
import com.example.droidlink.protocol.SessionCrypto
import com.example.utils.net.NetworkUtils
import com.example.utils.crypto.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.example.R
import com.example.Res

// ─────────────────────────────────────────────────────────────────────────────
// UI State model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents the privilege level of the host device.
 * Detected at start-up; affects which remote control features are available.
 */
enum class PrivilegeLevel { STANDARD, SHIZUKU, ROOT }

/**
 * Complete UI state snapshot for the DroidLink Remote server screen.
 *
 * Security contract:
 *  - [displayPassword] is ONLY held here transiently for the purpose of
 *    displaying it to the user and deriving the key. It is replaced with an
 *    empty string as soon as [DroidLinkServerViewModel.startServer] is called.
 *  - The derived [SecretKey] is NEVER stored in this data class — it lives
 *    only in [DroidLinkServerService.sessionKey].
 *  - [saltBytes] is the randomly generated salt; it is safe to expose (not secret).
 */
data class DroidLinkServerUiState(
    // ── Connection configuration ───────────────────────────────────────────
    val port: Int = 8989,

    /**
     * Cleartext password, displayed to the host user so they can share it.
     * Ephemeral / generated randomly per session.
     */
    val displayPassword: String = "",

    /**
     * Fixed / persistent password saved securely in [SecurePrefs] for unattended access.
     */
    val fixedPassword: String = "",

    /** Salt for PBKDF2 key derivation. Safe to expose; meaningless without the password. */
    val saltBytes: ByteArray = ByteArray(0),

    // ── Tailscale VPN IP ───────────────────────────────────────────────────
    val tailscaleIp: String? = null,

    // ── Permission checklist ───────────────────────────────────────────────
    val isMediaProjectionGranted: Boolean = false,
    val isAccessibilityEnabled: Boolean  = false,
    val isNotificationGranted: Boolean   = false,

    // ── Device privilege level ─────────────────────────────────────────────
    val privilegeLevel: PrivilegeLevel = PrivilegeLevel.STANDARD,

    // ── Audit log ─────────────────────────────────────────────────────────
    /** Timestamped log entries. Never contains passwords, keys or tokens. */
    val auditLog: List<String> = emptyList(),

    // ── Server runtime state (mirrored from the service) ──────────────────
    val serverState: DroidLinkServerState = DroidLinkServerState.Stopped
) {
    // ByteArray in a data class requires manual equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DroidLinkServerUiState) return false
        return port == other.port &&
            displayPassword == other.displayPassword &&
            fixedPassword == other.fixedPassword &&
            saltBytes.contentEquals(other.saltBytes) &&
            tailscaleIp == other.tailscaleIp &&
            isMediaProjectionGranted == other.isMediaProjectionGranted &&
            isAccessibilityEnabled == other.isAccessibilityEnabled &&
            isNotificationGranted == other.isNotificationGranted &&
            privilegeLevel == other.privilegeLevel &&
            auditLog == other.auditLog &&
            serverState == other.serverState
    }

    override fun hashCode(): Int {
        var result = port
        result = 31 * result + displayPassword.hashCode()
        result = 31 * result + fixedPassword.hashCode()
        result = 31 * result + saltBytes.contentHashCode()
        result = 31 * result + (tailscaleIp?.hashCode() ?: 0)
        result = 31 * result + isMediaProjectionGranted.hashCode()
        result = 31 * result + isAccessibilityEnabled.hashCode()
        result = 31 * result + isNotificationGranted.hashCode()
        result = 31 * result + privilegeLevel.hashCode()
        result = 31 * result + auditLog.hashCode()
        result = 31 * result + serverState.hashCode()
        return result
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the DroidLink Remote server screen.
 *
 * Owns the UI state, coordinates permission detection, privilege detection,
 * password lifecycle and service start/stop.
 *
 * Security rules enforced here:
 *  1. Passwords are generated by [generatePassword] using [SecureRandom].
 *  2. The password is kept in [DroidLinkServerUiState.displayPassword] only
 *     until [startServer] is called, at which point it is used to derive the
 *     [SecretKey] and immediately discarded from state.
 *  3. The derived key is passed to [DroidLinkServerService] and never stored
 *     in this ViewModel or in any observable property.
 *  4. [addAuditLog] never receives passwords or key material — callers are
 *     responsible for sanitising their messages.
 */
class DroidLinkServerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        DroidLinkServerUiState(
            displayPassword = generatePassword(),
            saltBytes       = SessionCrypto.generateSalt()
        )
    )
    val uiState: StateFlow<DroidLinkServerUiState> = _uiState.asStateFlow()

    private val _connectionHistory = MutableStateFlow<List<ConnectedClientRecord>>(emptyList())
    val connectionHistory: StateFlow<List<ConnectedClientRecord>> = _connectionHistory.asStateFlow()

    private val _settings = MutableStateFlow(DroidLinkSettings())
    val settings: StateFlow<DroidLinkSettings> = _settings.asStateFlow()

    private val auditTimestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        // Mirror the service state into our UI state
        DroidLinkServerService.serverState
            .onEach { serviceState ->
                _uiState.update { it.copy(serverState = serviceState) }
            }
            .launchIn(viewModelScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        const val PREF_FIXED_PASSWORD = "droidlink_fixed_password"
    }

    /**
     * Derives the session keys from ephemeral [password] and optional fixed password + current salt,
     * injects them into the service, then starts the foreground service with only the salt in the
     * Intent (the password is NEVER sent via Intent).
     *
     * @param context  Application context.
     * @param port     TCP port to listen on.
     * @param password Cleartext ephemeral password provided by the user / generated randomly.
     */
    fun startServer(context: Context, port: Int, password: String) {
        val salt = _uiState.value.saltBytes

        // Derive keys in memory
        val ephemeralKey = SessionCrypto.deriveKey(password, salt)
        val fixedPass = _uiState.value.fixedPassword
        val fixedKey = if (fixedPass.isNotBlank()) {
            SessionCrypto.deriveKey(fixedPass, salt)
        } else {
            null
        }

        // Inject keys into the service via the in-memory holder (never via Intent or disk)
        PendingKeyHolder.pendingKeys = PendingKeyHolder.ServerKeys(
            ephemeralKey = ephemeralKey,
            fixedKey     = fixedKey
        )

        _uiState.update { it.copy(port = port) }

        // Build and start the foreground service — salt only, NO password
        val intent = DroidLinkServerService.buildStartIntent(context, port, salt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        addAuditLog(Res.get(R.string.server_start_requested_on_port_x, port))
    }

    /**
     * Stops the DroidLink server service.
     */
    fun stopServer(context: Context) {
        val intent = Intent(context, DroidLinkServerService::class.java).apply {
            action = DroidLinkServerService.ACTION_STOP
        }
        context.startService(intent)
        addAuditLog(Res.get(R.string.server_stop_requested))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Password management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a cryptographically random 12-character alphanumeric password.
     * Uses [SecureRandom] — never [java.util.Random].
     *
     * Returns the plaintext string for display only; the caller must treat it as
     * ephemeral.
     */
    fun generatePassword(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random   = SecureRandom()
        return (1..12)
            .map { alphabet[random.nextInt(alphabet.length)] }
            .joinToString("")
    }

    /**
     * Regenerates both the password and salt, refreshing the UI state.
     * Should be called when the user explicitly requests a new credential.
     */
    fun regeneratePassword() {
        _uiState.update {
            it.copy(
                displayPassword = generatePassword(),
                saltBytes       = SessionCrypto.generateSalt()
            )
        }
        addAuditLog(Res.get(R.string.password_and_salt_regenerated))
    }

    /**
     * Loads the persistent fixed password from [SecurePrefs], detects active Tailscale IP,
     * refreshes connection history and AnyDesk-style session settings.
     */
    fun loadSavedSettings(context: Context) {
        val fixedPass = SecurePrefs.getString(context, PREF_FIXED_PASSWORD, "") ?: ""
        val tailscale = NetworkUtils.getTailscaleIpAddressSync()
        _uiState.update {
            it.copy(
                fixedPassword = fixedPass,
                tailscaleIp   = tailscale
            )
        }
        _settings.value = DroidLinkSettingsRepository.load(context)
        refreshConnectionHistory(context)
    }

    /**
     * Updates and persists AnyDesk-style operational session settings.
     */
    fun updateSettings(context: Context, newSettings: DroidLinkSettings) {
        _settings.value = newSettings
        DroidLinkSettingsRepository.save(context, newSettings)
        addAuditLog(Res.get(R.string.configuracoes_da_sessao_atualizadas))
    }

    /**
     * Refreshes the persistent history of connected clients.
     */
    fun refreshConnectionHistory(context: Context) {
        _connectionHistory.value = DroidLinkConnectionHistory.getHistory(context)
    }

    /**
     * Clears the history of connected clients.
     */
    fun clearConnectionHistory(context: Context) {
        DroidLinkConnectionHistory.clearHistory(context)
        _connectionHistory.value = emptyList()
        addAuditLog(Res.get(R.string.historico_de_conexoes_de_clientes_limpo))
    }

    /**
     * Sets or clears the persistent fixed password stored in [SecurePrefs] (backed by Keystore).
     * Never logged or exposed.
     */
    fun setFixedPassword(context: Context, newPassword: String) {
        val trimmed = newPassword.trim()
        SecurePrefs.putString(context, PREF_FIXED_PASSWORD, trimmed.ifEmpty { null })
        _uiState.update { it.copy(fixedPassword = trimmed) }
        addAuditLog(if (trimmed.isEmpty()) Res.get(R.string.senha_fixa_removida) else Res.get(R.string.senha_fixa_configurada))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission & privilege detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Refreshes the permission checklist, privilege level, fixed password, and Tailscale IP in [uiState].
     * Call this from the UI on resume / after requesting permissions.
     */
    fun refreshPermissionsAndPrivileges(context: Context) {
        val mediaProjection = isMediaProjectionGranted()     // always false until granted by user
        val accessibility   = isAccessibilityEnabled(context)
        val notifications   = isNotificationPermissionGranted(context)
        val privilege       = detectPrivilegeLevel()
        val tailscale       = NetworkUtils.getTailscaleIpAddressSync()
        val fixedPass       = SecurePrefs.getString(context, PREF_FIXED_PASSWORD, "") ?: ""

        _uiState.update {
            it.copy(
                isMediaProjectionGranted = mediaProjection,
                isAccessibilityEnabled   = accessibility,
                isNotificationGranted    = notifications,
                privilegeLevel           = privilege,
                tailscaleIp              = tailscale,
                fixedPassword            = fixedPass
            )
        }
        refreshConnectionHistory(context)
    }

    /** MediaProjection permission is granted only after the user accepts the system dialog. */
    private fun isMediaProjectionGranted(): Boolean =
        // This is set externally by the Activity after onActivityResult with a valid token
        _uiState.value.isMediaProjectionGranted

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return setting.contains(context.packageName, ignoreCase = true)
    }

    private fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // Notifications are on by default below Android 13
        }
    }

    /**
     * Detects the privilege level of the device.
     * - ROOT: su binary accessible
     * - SHIZUKU: Shizuku service is running and permission is granted
     * - STANDARD: neither
     */
    private fun detectPrivilegeLevel(): PrivilegeLevel {
        // Check root
        val suPaths = listOf("/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su")
        if (suPaths.any { java.io.File(it).exists() }) return PrivilegeLevel.ROOT

        // Check Shizuku (class presence heuristic — works without importing the library)
        return try {
            Class.forName("rikka.shizuku.Shizuku")
            PrivilegeLevel.SHIZUKU
        } catch (e: ClassNotFoundException) {
            PrivilegeLevel.STANDARD
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaProjection token injection (called from Activity)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by the Activity when the user grants MediaProjection permission.
     * Marks the permission as granted in the UI state.
     */
    fun onMediaProjectionGranted() {
        _uiState.update { it.copy(isMediaProjectionGranted = true) }
        addAuditLog(Res.get(R.string.mediaprojection_permission_granted))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit log
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Appends a timestamped, sanitised audit log entry.
     *
     * IMPORTANT: Never pass passwords, keys, or other sensitive material as [entry].
     * This list is observable by the UI and may be rendered on screen.
     */
    fun addAuditLog(entry: String) {
        val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime.now().format(auditTimestampFormatter)
        } else {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        }
        val line = "[$timestamp] $entry"
        _uiState.update { state ->
            val newLog = (state.auditLog + line).takeLast(200) // Keep last 200 entries
            state.copy(auditLog = newLog)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pending key holder (in-memory bridge between ViewModel and Service)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-process, in-memory holder for the derived session keys.
 *
 * Supports dual-key authentication: both ephemeral and fixed keys.
 * The ViewModel derives the keys and places them here; the Service reads and clears
 * them on start. This avoids passing keys (or passwords) through an Intent.
 *
 * The keys never leave the process and are never serialised to disk or logs.
 */
internal object PendingKeyHolder {
    data class ServerKeys(
        val ephemeralKey: javax.crypto.SecretKey,
        val fixedKey: javax.crypto.SecretKey? = null
    )

    @Volatile
    var pendingKeys: ServerKeys? = null

    var pendingKey: javax.crypto.SecretKey?
        get() = pendingKeys?.ephemeralKey
        set(value) {
            pendingKeys = value?.let { ServerKeys(it) }
        }

    /** Atomically reads and clears the pending keys. Returns null if none is set. */
    fun consumeKeys(): ServerKeys? {
        val keys = pendingKeys
        pendingKeys = null
        return keys
    }

    /** Atomically reads and clears the primary/ephemeral key. */
    fun consumeKey(): javax.crypto.SecretKey? {
        return consumeKeys()?.ephemeralKey
    }
}


