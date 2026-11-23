package com.example.droidlink.model

import android.content.Context
import android.content.SharedPreferences
import com.example.droidlink.client.QualityMode

/**
 * AnyDesk-style operational settings for DroidLink Remote sessions.
 */
// DLK-R15: session settings — audio, video quality/fps and granular remote permissions (AnyDesk-style)
data class DroidLinkSettings(
    // ── Audio (AnyDesk) ──
    val transmitAudio: Boolean = true,
    val keepServerSpeakerActive: Boolean = true, // Keeps audio playing on the server speaker
    val playAudioOnClient: Boolean = true,       // Reproduz som nos alto-falantes do cliente
    val clientVolume: Float = 1.0f,              // 0.0 a 1.0

    // ── Video & Performance (AnyDesk) ──
    val qualityMode: QualityMode = QualityMode.BALANCED,
    val targetFps: Int = 30,

    // ── Permissions & Control (AnyDesk) ──
    val allowRemoteInput: Boolean = true,        // Permitir toque/gestos remotos
    val allowSystemActions: Boolean = true,      // Permitir teclas Voltar, Home, Recentes
    val allowFileTransfer: Boolean = true,       // Permitir envio e recebimento de arquivos
    val keepScreenAwake: Boolean = true          // Keep the server screen always on
)

object DroidLinkSettingsRepository {
    private const val PREFS_NAME = "droidlink_settings_prefs"

    private const val KEY_TRANSMIT_AUDIO = "transmit_audio"
    private const val KEY_KEEP_SPEAKER_ACTIVE = "keep_server_speaker_active"
    private const val KEY_PLAY_AUDIO_CLIENT = "play_audio_on_client"
    private const val KEY_CLIENT_VOLUME = "client_volume"
    private const val KEY_QUALITY_MODE = "quality_mode"
    private const val KEY_TARGET_FPS = "target_fps"
    private const val KEY_ALLOW_INPUT = "allow_remote_input"
    private const val KEY_ALLOW_ACTIONS = "allow_system_actions"
    private const val KEY_ALLOW_FILES = "allow_file_transfer"
    private const val KEY_KEEP_AWAKE = "keep_screen_awake"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): DroidLinkSettings {
        val prefs = getPrefs(context)
        val qualityName = prefs.getString(KEY_QUALITY_MODE, QualityMode.BALANCED.name)
        val qualityMode = try {
            QualityMode.valueOf(qualityName ?: QualityMode.BALANCED.name)
        } catch (_: Exception) {
            QualityMode.BALANCED
        }
        val defaultFps = when (qualityMode) {
            QualityMode.LOW_LATENCY -> 20
            QualityMode.BALANCED -> 30
            QualityMode.HIGH_DETAIL -> 60
        }

        return DroidLinkSettings(
            transmitAudio = prefs.getBoolean(KEY_TRANSMIT_AUDIO, true),
            keepServerSpeakerActive = prefs.getBoolean(KEY_KEEP_SPEAKER_ACTIVE, true),
            playAudioOnClient = prefs.getBoolean(KEY_PLAY_AUDIO_CLIENT, true),
            clientVolume = prefs.getFloat(KEY_CLIENT_VOLUME, 1.0f),
            qualityMode = qualityMode,
            targetFps = prefs.getInt(KEY_TARGET_FPS, defaultFps),
            allowRemoteInput = prefs.getBoolean(KEY_ALLOW_INPUT, true),
            allowSystemActions = prefs.getBoolean(KEY_ALLOW_ACTIONS, true),
            allowFileTransfer = prefs.getBoolean(KEY_ALLOW_FILES, true),
            keepScreenAwake = prefs.getBoolean(KEY_KEEP_AWAKE, true)
        )
    }

    fun save(context: Context, settings: DroidLinkSettings) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_TRANSMIT_AUDIO, settings.transmitAudio)
            putBoolean(KEY_KEEP_SPEAKER_ACTIVE, settings.keepServerSpeakerActive)
            putBoolean(KEY_PLAY_AUDIO_CLIENT, settings.playAudioOnClient)
            putFloat(KEY_CLIENT_VOLUME, settings.clientVolume)
            putString(KEY_QUALITY_MODE, settings.qualityMode.name)
            putInt(KEY_TARGET_FPS, settings.targetFps)
            putBoolean(KEY_ALLOW_INPUT, settings.allowRemoteInput)
            putBoolean(KEY_ALLOW_ACTIONS, settings.allowSystemActions)
            putBoolean(KEY_ALLOW_FILES, settings.allowFileTransfer)
            putBoolean(KEY_KEEP_AWAKE, settings.keepScreenAwake)
            apply()
        }
    }
}
