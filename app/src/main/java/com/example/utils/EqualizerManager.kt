package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log
import com.example.R
import com.example.Res

object EqualizerManager {
    private const val PREFS_NAME = "EqualizerPrefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    
    // Custom presets (Band levels in millibels, where 100 mB = 1 dB)
    // Most devices have 5 bands (e.g. 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
    // We will apply levels dynamically up to the number of bands available.
    val customPresets = mapOf(
        "Custom" to listOf<Short>(), // Loaded dynamically from prefs
        "Normal" to listOf<Short>(0, 0, 0, 0, 0),
        Res.get(R.string.som_ambiente) to listOf<Short>(100, -100, 200, -100, 100),
        "Teatro" to listOf<Short>(300, 100, -100, 100, 300),
        "Pop" to listOf<Short>(-150, 200, 300, 150, -100),
        "Rock" to listOf<Short>(400, -200, -100, 150, 300),
        Res.get(R.string.classico) to listOf<Short>(300, 200, -150, 200, 300),
        Res.get(R.string.bass_boost) to listOf<Short>(600, 300, 0, 0, 0)
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getCurrentPreset(context: Context): String {
        return getPrefs(context).getString(KEY_PRESET, "Normal") ?: "Normal"
    }

    fun setCurrentPreset(context: Context, presetName: String) {
        getPrefs(context).edit().putString(KEY_PRESET, presetName).apply()
    }

    fun saveBandLevel(context: Context, bandIndex: Short, level: Short) {
        getPrefs(context).edit().putInt("band_$bandIndex", level.toInt()).apply()
        setCurrentPreset(context, "Custom") // Changing a band manually sets to Custom
    }

    fun getSavedBandLevel(context: Context, bandIndex: Short, defaultLevel: Short = 0): Short {
        return getPrefs(context).getInt("band_$bandIndex", defaultLevel.toInt()).toShort()
    }

    fun applyTo(context: Context, audioSessionId: Int): Equalizer? {
        return try {
            val equalizer = Equalizer(0, audioSessionId) // EXT-R04
            equalizer.enabled = isEnabled(context)
            
            val presetName = getCurrentPreset(context)
            val numBands = equalizer.numberOfBands
            
            if (presetName == "Custom") {
                for (i in 0 until numBands) {
                    val band = i.toShort()
                    val savedLevel = getSavedBandLevel(context, band, 0)
                    try { equalizer.setBandLevel(band, savedLevel) } catch (e: Exception) {}
                }
            } else {
                val presetLevels = customPresets[presetName] ?: customPresets["Normal"]!!
                for (i in 0 until numBands) {
                    if (i < presetLevels.size) {
                        try { equalizer.setBandLevel(i.toShort(), presetLevels[i]) } catch (e: Exception) {}
                    }
                }
            }
            equalizer
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error initializing global Equalizer: \${e.message}")
            null
        }
    }
}
