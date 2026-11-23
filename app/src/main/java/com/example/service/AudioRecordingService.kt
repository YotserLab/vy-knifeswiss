package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.example.Res

class AudioRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null

    companion object {
        const val CHANNEL_ID = "audio_recording_service_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_RECORDING = "com.example.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.STOP_RECORDING"

        val isRecordingState = mutableStateOf(false)
        val elapsedSecondsState = mutableStateOf(0)
        val amplitudeState = mutableStateOf(0)
        val amplitudeHistoryState = mutableStateListOf<Float>()
        val currentFileState = mutableStateOf<File?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecordingState.value) return

        val fileName = "REC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.m4a" // MED-R02
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val audioFile = File(storageDir, fileName)
        currentFileState.value = audioFile

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(applicationContext)
        else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100) // MED-R02
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                isRecordingState.value = true
            } catch (e: IOException) {
                e.printStackTrace()
                stopRecording()
                return
            }
        }

        // Start Foreground Service with type Microphone
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) // MED-R03
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Timer and Waveform Amplitude polling
        recordingJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val amp = mediaRecorder?.maxAmplitude ?: 0
                amplitudeState.value = amp
                val norm = if (amp > 0) amp.coerceIn(0, 32767).toFloat() / 32767f else 0f
                
                withContext(Dispatchers.Main) {
                    amplitudeHistoryState.add(norm)
                    if (amplitudeHistoryState.size > 120) {
                        amplitudeHistoryState.removeAt(0)
                    }
                    elapsedSecondsState.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                }
                delay(50)
            }
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null

        isRecordingState.value = false
        amplitudeState.value = 0
        amplitudeHistoryState.clear()
        elapsedSecondsState.value = 0

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Res.get(R.string.gravador_de_audio_em_background),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Res.get(R.string.mantem_a_gravacao_de_audio_ativa_com_a_t)
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

        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Res.get(R.string.gravador_de_audio_ativo))
            .setContentText(Res.get(R.string.gravando_audio_em_segundo_plano))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                Res.get(R.string.parar_gravacao),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }
}
