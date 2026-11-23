package com.example.ui.screens.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.service.AudioRecordingService
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.example.R
import com.example.Res

private fun fmtDuration(ms: Long): String {
    if (ms < 0) return "00:00"
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecorderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Recording States from service
    val isRecording = AudioRecordingService.isRecordingState.value
    val amplitude = AudioRecordingService.amplitudeState.value
    val elapsedSeconds = AudioRecordingService.elapsedSecondsState.value
    val amplitudeHistory = AudioRecordingService.amplitudeHistoryState

    // UI Local lists and dialog triggers
    var audioFiles by remember { mutableStateOf(listOf<File>()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }

    // Audio format and quality chips selectors
    var selectedFormat by remember { mutableStateOf("AAC") }
    var selectedQuality by remember { mutableStateOf("High") }

    // Media Player Local playback variables
    var playingFile by remember { mutableStateOf<File?>(null) }
    var localPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var playPosition by remember { mutableStateOf(0L) }
    var playDuration by remember { mutableStateOf(0L) }

    fun refreshAudioFiles() {
        scope.launch(Dispatchers.IO) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val list = dir?.listFiles { f -> f.extension in listOf("mp3", "m4a", "aac", "wav") /* MED-R05 */ }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
            withContext(Dispatchers.Main) { audioFiles = list }
        }
    }

    LaunchedEffect(Unit) { refreshAudioFiles() }

    // Audio Player tracking loop
    LaunchedEffect(isPlayingAudio, playingFile) {
        while (isPlayingAudio && localPlayer != null) {
            try {
                playPosition = localPlayer?.currentPosition?.toLong() ?: 0L
            } catch (e: Exception) {}
            delay(100)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    DisposableEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        onDispose {
            localPlayer?.release()
        }
    }

    fun startRecording() {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START_RECORDING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopRecording() {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)
        scope.launch {
            delay(300)
            refreshAudioFiles()
        }
    }

    // Playback logic
    fun togglePlayFile(file: File) {
        if (playingFile == file) {
            if (isPlayingAudio) {
                localPlayer?.pause()
                isPlayingAudio = false
            } else {
                localPlayer?.start()
                isPlayingAudio = true
            }
        } else {
            localPlayer?.release()
            localPlayer = MediaPlayer().apply {
                try {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    playDuration = duration.toLong()
                    isPlayingAudio = true
                    playingFile = file
                    setOnCompletionListener {
                        isPlayingAudio = false
                        playPosition = 0L
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, Res.get(R.string.erro_ao_reproduzir_audio), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.gravador_de_audio),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Configurações adicionais */ }) {
                        Icon(Icons.Default.Settings, contentDescription = Res.get(R.string.configuracoes), tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            // ── Microphone Glowing Center ────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CyanNeon.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                        .border(
                            1.dp,
                            if (isRecording) CyanNeon else Color(0xFF1E2D4A),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF111827))
                            .border(1.dp, CyanNeon.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // ── Giant Neon Timer ─────────────────────────────────────
            item {
                val formattedTime = if (isRecording) {
                    "%02d:%02d:%02d".format(elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60)
                } else {
                    "00:00:00"
                }
                Text(
                    text = formattedTime,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // ── Audio Waveform Visualizer ────────────────────────────
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 10.dp)
                    ) {
                        val barCount = 30
                        val spacing = 8.dp.toPx()
                        val barWidth = (size.width - (barCount - 1) * spacing) / barCount
                        val maxBarHeight = size.height

                        for (i in 0 until barCount) {
                            val ampVal = if (isRecording && amplitudeHistory.size > i) {
                                amplitudeHistory[amplitudeHistory.size - 1 - i]
                            } else {
                                0.05f
                            }
                            val barHeight = (ampVal * maxBarHeight).coerceIn(4.dp.toPx(), maxBarHeight)
                            val x = i * (barWidth + spacing)
                            val y = (size.height - barHeight) / 2f

                            drawRoundRect(
                                color = CyanNeon,
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = Res.get(R.string.n_44_1_khz_stereo_high_quality),
                        color = OnSurfaceMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ── Action Buttons Row (Pause, Record/Stop, Bookmark) ────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause/Resume Button
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2D4A).copy(alpha = 0.4f))
                            .border(1.dp, AmberGold.copy(alpha = 0.7f), CircleShape)
                            .clickable { /* Pause/Resume */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pausar",
                            tint = AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Main Record/Stop
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF111827))
                            .border(1.dp, if (isRecording) DangerRed else CyanNeon, CircleShape)
                            .clickable {
                                if (isRecording) stopRecording() else startRecording()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DangerRed)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(CyanNeon)
                            )
                        }
                    }

                    // Bookmark flag
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2D4A).copy(alpha = 0.4f))
                            .border(1.dp, ElectricViolet.copy(alpha = 0.7f), CircleShape)
                            .clickable { /* Bookmark flag */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "Marcador",
                            tint = ElectricViolet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Settings Quick Selection ─────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // FORMATO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FORMATO", color = OnSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("MP3", "AAC", "WAV").forEach { format ->
                                val isSel = selectedFormat == format
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSel) Color(0xFF1E2D4A) else Color.Transparent)
                                        .border(1.dp, if (isSel) CyanNeon else Color(0xFF1E2D4A), CircleShape)
                                        .clickable { selectedFormat = format }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(format, color = if (isSel) CyanNeon else OnSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // QUALIDADE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("QUALIDADE", color = OnSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Low", "Medium", "High").forEach { qual ->
                                val isSel = selectedQuality == qual
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSel) Color(0xFF1E2D4A) else Color.Transparent)
                                        .border(1.dp, if (isSel) CyanNeon else Color(0xFF1E2D4A), CircleShape)
                                        .clickable { selectedQuality = qual }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(qual, color = if (isSel) CyanNeon else OnSurfaceMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ── Recent Recordings Header ─────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Box(modifier = Modifier.width(3.dp).height(12.dp).background(CyanNeon))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Res.get(R.string.gravacoes_recentes),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ── List of Recordings ───────────────────────────────────
            if (audioFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Res.get(R.string.nenhuma_gravacao_recente), color = OnSurfaceMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(audioFiles) { file ->
                    val isPlayingThis = playingFile == file
                    
                    RecordingItemStitch(
                        file = file,
                        isPlaying = isPlayingThis && isPlayingAudio,
                        currentPos = if (isPlayingThis) playPosition else 0L,
                        duration = if (isPlayingThis) playDuration else file.length() * 10L, // Dummy estimate when not open
                        onTogglePlay = { togglePlayFile(file) },
                        onShare = {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, Res.get(R.string.compartilhar_gravacao)))
                        },
                        onDelete = { fileToDelete = file }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Deletar Dialog
    fileToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(Res.get(R.string.excluir_gravacao)) },
            text = { Text(Res.get(R.string.tem_certeza_que_deseja_apagar_x, (f.name))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        f.delete()
                        refreshAudioFiles()
                        fileToDelete = null
                        if (playingFile == f) {
                            localPlayer?.release()
                            localPlayer = null
                            playingFile = null
                            isPlayingAudio = false
                        }
                    }
                ) { Text(Res.get(R.string.excluir), color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }
}

@Composable
private fun RecordingItemStitch(
    file: File,
    isPlaying: Boolean,
    currentPos: Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeMb = remember(file) {
        String.format(Locale.US, Res.get(R.string.n_1f_mb), file.length() / (1024f * 1024f))
    }
    val dateStr = remember(file) {
        SimpleDateFormat("dd/10/2023  •  HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827))
            .border(
                1.dp,
                if (isPlaying) CyanNeon.copy(alpha = 0.5f) else Color(0xFF1E2D4A),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play/Pause indicator circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2D4A))
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = Res.get(R.string.x_7, dateStr),
                        color = OnSurfaceMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = sizeMb,
                    color = OnSurfaceMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Waveform and Expanded controls when selected/playing
            if (isPlaying) {
                Spacer(modifier = Modifier.height(14.dp))
                
                // Active waveform
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                ) {
                    val barCount = 20
                    val spacing = 6.dp.toPx()
                    val barWidth = (size.width - (barCount - 1) * spacing) / barCount
                    
                    val progressRatio = if (duration > 0) currentPos.toFloat() / duration else 0f
                    
                    for (i in 0 until barCount) {
                        val barHeight = (10.dp.toPx() + (i % 3) * 6.dp.toPx())
                        val x = i * (barWidth + spacing)
                        val y = (size.height - barHeight) / 2f
                        
                        val isActive = (i.toFloat() / barCount) <= progressRatio
                        
                        drawRoundRect(
                            color = if (isActive) CyanNeon else Color(0xFF1E2D4A),
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fmtDuration(currentPos),
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.excluir), tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = Res.get(R.string.x_currentpos_coerceatleast_0l, (fmtDuration((duration - currentPos).coerceAtLeast(0L)))),
                        color = OnSurfaceMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
