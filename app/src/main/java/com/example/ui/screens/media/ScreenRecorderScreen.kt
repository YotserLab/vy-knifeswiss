package com.example.ui.screens.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.ScreenRecordingService
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.example.R
import com.example.Res

private fun fmtMs(ms: Long): String {
    if (ms < 0) return "00:00"
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecorderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isRecording = ScreenRecordingService.isRecordingState.value
    val elapsedSeconds = ScreenRecordingService.elapsedSecondsState.value
    var videoFiles by remember { mutableStateOf(listOf<File>()) }

    // Dialog states
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var playerFile by remember { mutableStateOf<File?>(null) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var fileToShowPath by remember { mutableStateOf<File?>(null) }
    var fileToShare by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }

    fun refreshVideoFiles() {
        scope.launch(Dispatchers.IO) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val list = dir?.listFiles { f -> f.extension in listOf("mp4", "mkv", "mov", "avi") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
            withContext(Dispatchers.Main) { videoFiles = list }
        }
    }

    LaunchedEffect(Unit) { refreshVideoFiles() }

    fun stopRecording() {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)
        scope.launch {
            delay(500)
            refreshVideoFiles()
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_START_RECORDING
                putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordingService.EXTRA_INTENT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun requestScreenCapture() {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager // EXT-R05
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    fileToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(Res.get(R.string.remover_gravacao)) },
            text = { Text(Res.get(R.string.deseja_remover_permanentemente_o_arquivo, (f.name))) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) { f.delete(); refreshVideoFiles() }
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(Res.get(R.string.remover)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }

    // ── Video player dialog ───────────────────────────────────────────────────
    playerFile?.let { f ->
        VideoPlayerDialog(file = f, onDismiss = { playerFile = null })
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.gravador_de_tela)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer
            if (isRecording) {
                Text(
                    text = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val alpha by pulse.animateFloat(
                        1f, 0.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a"
                    )
                    Canvas(modifier = Modifier.size(12.dp)) { drawCircle(Color.Red.copy(alpha = alpha)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Res.get(R.string.gravando_tela), color = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.Default.Videocam, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Res.get(R.string.pronto_para_gravar), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Record/Stop button
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    .clickable { if (isRecording) stopRecording() else requestScreenCapture() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        if (isRecording) "Parar" else "Gravar",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Res.get(R.string.gravacoes_x, (videoFiles.size)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (videoFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideocamOff, contentDescription = null,
                            tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(Res.get(R.string.nenhuma_gravacao_ainda), color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(videoFiles) { file ->
                        VideoRecordingItem(
                            file = file,
                            onPlay = { playerFile = file },
                            onDelete = { fileToDelete = file },
                            onRename = {
                                fileToRename = file
                                newName = file.nameWithoutExtension
                            },
                            onShowPath = { fileToShowPath = file },
                            onShare = { fileToShare = file }
                        )
                    }
                }
            }
        }
    }

    // Rename file dialog
    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text(Res.get(R.string.renomear_arquivo_2)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(Res.get(R.string.novo_nome)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newFile = File(fileToRename!!.parent, Res.get(R.string.xx_extension, newName, (fileToRename!!.extension)))
                        if (fileToRename!!.renameTo(newFile)) {
                            refreshVideoFiles()
                        }
                        fileToRename = null
                    }
                ) {
                    Text(Res.get(R.string.salvar))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }

    // Show path dialog
    if (fileToShowPath != null) {
        AlertDialog(
            onDismissRequest = { fileToShowPath = null },
            title = { Text(Res.get(R.string.caminho_do_arquivo)) },
            text = {
                Column {
                    Text(fileToShowPath!!.name, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(fileToShowPath!!.absolutePath, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        fileToShowPath!!.parentFile ?: fileToShowPath!!
                                    ),
                                    "resource/folder"
                                )
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            fileToShowPath = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Res.get(R.string.abrir_no_gerenciador))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fileToShowPath = null }) {
                    Text(Res.get(R.string.fechar))
                }
            }
        )
    }

    // Share dialog
    if (fileToShare != null) {
        AlertDialog(
            onDismissRequest = { fileToShare = null },
            title = { Text(Res.get(R.string.compartilhar_arquivo_2)) },
            text = { Text(Res.get(R.string.deseja_compartilhar_x_name, (fileToShare!!.name))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            fileToShare!!
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(uri) ?: "video/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Compartilhar"))
                        fileToShare = null
                    }
                ) {
                    Text("Compartilhar")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToShare = null }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun VideoRecordingItem(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShowPath: () -> Unit,
    onShare: () -> Unit
) {
    val sizeMb = Res.get(R.string.n_1f_mb).format(file.length() / 1_048_576f)
    val date = remember(file) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayCircle, contentDescription = "Assistir",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(Res.get(R.string.x_x_20, date, sizeMb), color = Color.Gray, fontSize = 12.sp)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = Res.get(R.string.mais_opcoes))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Res.get(R.string.ver_caminho)) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShowPath()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartilhar") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Deletar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(file: File, onDismiss: () -> Unit) {
    val accentColor = Color(0xFFFF6B00)

    val mediaPlayer = remember { MediaPlayer() }
    var playerPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var surfaceReady by remember { mutableStateOf(false) }
    val holderRef = remember { mutableStateOf<SurfaceHolder?>(null) }
    var equalizer by remember { mutableStateOf<android.media.audiofx.Equalizer?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun loadVideo() {
        try {
            mediaPlayer.reset()
            playerPrepared = false
            holderRef.value?.let { mediaPlayer.setDisplay(it) }
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnPreparedListener { mp ->
                playerPrepared = true
                duration = mp.duration.toLong()
                try { equalizer = com.example.utils.EqualizerManager.applyTo(context, mp.audioSessionId) } catch (_: Exception) {}
                mp.start(); isPlaying = true
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                if (isRepeat) { it.seekTo(0); it.start(); isPlaying = true }
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {}
    }

    LaunchedEffect(surfaceReady) { if (surfaceReady) loadVideo() }

    LaunchedEffect(isPlaying) {
        while (isPlaying && playerPrepared) {
            currentPosition = mediaPlayer.currentPosition.toLong(); delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { equalizer?.release() } catch (_: Exception) {}
            try { mediaPlayer.stop() } catch (_: Exception) {}
            mediaPlayer.release()
        }
    }

    Dialog(onDismissRequest = {
        try { mediaPlayer.stop() } catch (_: Exception) {}
        onDismiss()
    }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, contentDescription = null,
                        tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(file.nameWithoutExtension, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        try { mediaPlayer.stop() } catch (_: Exception) {}
                        onDismiss()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = Res.get(R.string.fechar),
                            tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SurfaceView video area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) {
                                        holderRef.value = h
                                        mediaPlayer.setDisplay(h)
                                        surfaceReady = true
                                    }
                                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                                    override fun surfaceDestroyed(h: SurfaceHolder) {
                                        surfaceReady = false; holderRef.value = null
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!playerPrepared) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(40.dp))
                    }
                    // Tap overlay to play/pause
                    Box(modifier = Modifier.fillMaxSize().clickable {
                        if (!playerPrepared) return@clickable
                        if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                        else { mediaPlayer.start(); isPlaying = true }
                    })
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Seek bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { ratio ->
                        val pos = (ratio * duration).toLong()
                        mediaPlayer.seekTo(pos.toInt()); currentPosition = pos
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0xFF444444)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtMs(currentPosition), color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(fmtMs(duration), color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val p = (currentPosition - 10000L).coerceAtLeast(0L)
                        mediaPlayer.seekTo(p.toInt()); currentPosition = p
                    }) {
                        Icon(Icons.Default.Replay10, contentDescription = Res.get(R.string.n_10s),
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable {
                                if (!playerPrepared) return@clickable
                                if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                                else { mediaPlayer.start(); isPlaying = true }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = Res.get(R.string.play_pause),
                            tint = Color.White, modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(onClick = {
                        val p = (currentPosition + 10000L).coerceAtMost(duration)
                        mediaPlayer.seekTo(p.toInt()); currentPosition = p
                    }) {
                        Icon(Icons.Default.Forward10, contentDescription = Res.get(R.string.n_10s_2),
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    IconButton(onClick = {
                        isRepeat = !isRepeat; mediaPlayer.isLooping = isRepeat
                    }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Repetir",
                            tint = if (isRepeat) accentColor else Color.Gray,
                            modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
