package com.example.droidlink.server

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures internal system audio (media, games, apps) using AudioPlaybackCapture API on Android 10+.
 * Streams raw PCM chunks via [onAudioChunk].
 */
// DLK-R13
class DroidLinkAudioCapture(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "DroidLinkAudioCapture"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) {
            Log.w(TAG, "AudioPlaybackCapture requires Android 10+ (API 29+) and active MediaProjection")
            return
        }

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_MASK)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord initialization failed")
                return
            }

            record.startRecording()
            audioRecord = record

            captureJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        onAudioChunk(buffer.copyOf(readBytes))
                    }
                }
            }
            Log.i(TAG, "Audio capture started at ${SAMPLE_RATE}Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback capture: ${e.message}")
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        Log.i(TAG, "Audio capture stopped")
    }
}

