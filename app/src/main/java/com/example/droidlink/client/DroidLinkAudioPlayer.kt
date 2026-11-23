package com.example.droidlink.client

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Audio player on the DroidLink client device.
 * Receives PCM audio chunks and plays them in real time using low-latency AudioTrack.
 */
class DroidLinkAudioPlayer {
    companion object {
        private const val TAG = "DroidLinkAudioPlayer"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_STEREO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null

    var isMuted: Boolean = false
        set(value) {
            field = value
            applyVolume()
        }

    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume()
        }

    private fun applyVolume() {
        val target = if (isMuted) 0f else volume
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioTrack?.setVolume(target)
            } else {
                @Suppress("DEPRECATION")
                audioTrack?.setStereoVolume(target, target)
            }
        } catch (_: Exception) {}
    }

    fun start() {
        if (audioTrack != null) return
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(ENCODING)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_MASK)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_MASK,
                    ENCODING,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            track.play()
            audioTrack = track
            applyVolume()
            Log.i(TAG, "AudioTrack started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}")
        }
    }

    fun playChunk(data: ByteArray) {
        if (audioTrack == null) start()
        if (!isMuted) {
            try {
                audioTrack?.write(data, 0, data.size)
            } catch (e: Exception) {
                Log.w(TAG, "Audio write error: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        Log.i(TAG, "AudioTrack stopped")
    }
}

