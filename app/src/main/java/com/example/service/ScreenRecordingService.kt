package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
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

class ScreenRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recordingJob: Job? = null

    companion object {
        const val CHANNEL_ID = "screen_recording_service_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START_RECORDING = "com.example.START_SCREEN_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.STOP_SCREEN_RECORDING"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_INTENT_DATA = "intent_data"

        val isRecordingState = mutableStateOf(false)
        val elapsedSecondsState = mutableStateOf(0)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val intentData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_INTENT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_INTENT_DATA)
                }
                
                if (resultCode != -1 && intentData != null) {
                    startRecording(resultCode, intentData)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, intentData: Intent) {
        if (isRecordingState.value) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        val fileName = "SCREEN_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4" // MED-R04
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val videoFile = File(storageDir, fileName)

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(applicationContext)
        else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(720, 1280)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(2_000_000)
            setOutputFile(videoFile.absolutePath)
            try {
                prepare()
            } catch (e: IOException) {
                e.printStackTrace()
                stopRecording()
                return
            }
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, intentData) // MED-R04

        val metrics = resources.displayMetrics
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            Res.get(R.string.screenrecord),
            720,
            1280,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        )

        mediaRecorder?.start()
        isRecordingState.value = true

        recordingJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                withContext(Dispatchers.Main) {
                    elapsedSecondsState.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                }
                delay(1000)
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

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        isRecordingState.value = false
        elapsedSecondsState.value = 0

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Res.get(R.string.gravador_de_tela),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Res.get(R.string.mantem_a_gravacao_de_tela_ativa)
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

        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Res.get(R.string.gravando_a_tela))
            .setContentText(Res.get(R.string.a_gravacao_de_tela_esta_em_andamento))
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
