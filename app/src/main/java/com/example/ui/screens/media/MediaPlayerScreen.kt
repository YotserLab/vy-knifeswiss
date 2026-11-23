package com.example.ui.screens.media

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import com.example.cast.control.CastSessionManager
import com.example.cast.discovery.CastDiscovery
import com.example.cast.media.CastMedia
import com.example.cast.ui.CastDevicePicker
import com.example.cast.ui.CastRemoteSheet
import com.example.utils.EqualizerManager
import com.example.R
import com.example.Res

// ── helpers ──────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    if (ms < 0) return "00:00"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "flv", "wmv", "m4v", "mpeg", "mpg")
private val AUDIO_EXTS = setOf("mp3", "aac", "ogg", "flac", "wav", "m4a", "opus", "wma", "aiff")
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")

// ── main screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerScreen(encodedPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val filePath = remember(encodedPath) {
        if (encodedPath.isNullOrEmpty()) ""
        else runCatching { URLDecoder.decode(encodedPath, "UTF-8") }.getOrElse { encodedPath }
    }

    var showFilePicker by remember { mutableStateOf(filePath.isEmpty()) }
    var pickedFilePath by remember { mutableStateOf(filePath) }

    val file = remember(pickedFilePath) { if (pickedFilePath.isNotEmpty()) File(pickedFilePath) else null }
    val isVideo = remember(pickedFilePath) { file?.extension?.lowercase() in VIDEO_EXTS }
    val isImage = remember(pickedFilePath) { file?.extension?.lowercase() in IMAGE_EXTS }

    // Cast to TV — session lives in CastSessionManager so it survives
    // leaving this screen; we only track picker/dismiss state locally.
    val castDiscovery = remember { CastDiscovery(context.applicationContext) }
    val castState by CastSessionManager.state.collectAsState()
    var showCastPicker by remember { mutableStateOf(false) }
    // Dismissed by default — the remote only auto-opens when a NEW session
    // object appears, not when entering with an existing one.
    var castRemoteDismissed by remember { mutableStateOf(true) }
    var lastCastSession by remember { mutableStateOf(castState.session) }
    val canCast = remember(file) { file?.exists() == true }

    val metadata = remember(pickedFilePath) {
        if (pickedFilePath.isEmpty()) Triple(Res.get(R.string.nenhum_arquivo), "", "")
        else runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(pickedFilePath)
            Triple(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file?.nameWithoutExtension ?: Res.get(R.string.desconhecido),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            ).also { r.release() }
        }.getOrElse { Triple(file?.nameWithoutExtension ?: Res.get(R.string.desconhecido), "", "") }
    }

    // AudioManager for hardware volume control
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val mediaPlayer = remember { MediaPlayer() } // MED-R01
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var showEqualizer by remember { mutableStateOf(false) }

    var surfaceReady by remember { mutableStateOf(false) }
    var playerPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    
    // Video dimensions for Aspect Ratio
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Gesture feedback overlay
    var gestureLabel by remember { mutableStateOf("") }
    var gestureVisible by remember { mutableStateOf(false) }

    // Zoom & Pan
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val accentColor = Color(0xFFFF6B00)
    val bgColor = Color(0xFF000000)

    // Keep screen on while playing
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Fullscreen orientation
    DisposableEffect(isFullscreen, isVideo) {
        if (isVideo && isFullscreen) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // Release on exit
    DisposableEffect(Unit) {
        onDispose {
            try { equalizer?.release() } catch (_: Exception) {}
            try { mediaPlayer.stop() } catch (_: Exception) {}
            mediaPlayer.release()
        }
    }

    fun applySpeed(speed: Float) {
        playbackSpeed = speed
        if (Build.VERSION.SDK_INT >= 23 && playerPrepared) {
            try { mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed) } catch (_: Exception) {}
        }
    }

    fun initEqualizer() {
        try {
            equalizer?.release()
            equalizer = EqualizerManager.applyTo(context, mediaPlayer.audioSessionId)
        } catch (_: Exception) {}
    }

    // Load file from path with SurfaceHolder
    fun loadMedia(holder: SurfaceHolder?) {
        val f = file ?: return
        if (!f.exists()) { errorMessage = Res.get(R.string.arquivo_nao_encontrado_x, (f.path)); return }
        try {
            mediaPlayer.reset()
            playerPrepared = false
            if (holder != null) mediaPlayer.setDisplay(holder)
            mediaPlayer.setDataSource(context, Uri.fromFile(f))
            mediaPlayer.setOnVideoSizeChangedListener { _, w, h -> videoWidth = w; videoHeight = h }
            mediaPlayer.setOnPreparedListener { mp ->
                playerPrepared = true
                duration = mp.duration.toLong()
                videoWidth = mp.videoWidth
                videoHeight = mp.videoHeight
                applySpeed(playbackSpeed)
                initEqualizer()
                mp.start()
                isPlaying = true
                showFilePicker = false
                errorMessage = null
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                if (isRepeat) { it.seekTo(0); it.start(); isPlaying = true }
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                errorMessage = Res.get(R.string.erro_de_playback_what_x_extra_x, what, extra)
                isPlaying = false
                true
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) { errorMessage = Res.get(R.string.erro_ao_carregar_x, (e.message)) }
    }

    val holderRef = remember { mutableStateOf<SurfaceHolder?>(null) }

    LaunchedEffect(pickedFilePath, surfaceReady) {
        if (pickedFilePath.isEmpty() || isImage) return@LaunchedEffect
        if (isVideo && !surfaceReady) return@LaunchedEffect
        loadMedia(if (isVideo) holderRef.value else null)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && playerPrepared) {
            currentPosition = mediaPlayer.currentPosition.toLong()
            delay(300)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && playerPrepared && isPlaying) {
            delay(3500)
            if (isPlaying) showControls = false
        }
    }

    LaunchedEffect(gestureVisible) {
        if (gestureVisible) { delay(800); gestureVisible = false }
    }

    // Surface cast failures once per error value — consumeError()
    // prevents the same error from re-toasting on recomposition.
    LaunchedEffect(castState.lastError) {
        castState.lastError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            CastSessionManager.consumeError()
        }
    }
    // Auto-open the remote only when a NEW session object appears —
    // entering with an existing session keeps it dismissed
    // (the cast button re-opens it).
    LaunchedEffect(castState.session) {
        if (castState.session != null && castState.session !== lastCastSession) {
            castRemoteDismissed = false
        }
        lastCastSession = castState.session
    }
    DisposableEffect(Unit) { onDispose { castDiscovery.stopScan() } }

    if (showFilePicker) {
        // ... (File picker omitted for brevity, keeping simple fallback)
        Box(modifier = Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Button(onClick = onBack) { Text(Res.get(R.string.voltar)) }
        }
        return
    }

    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = accentColor, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMessage!!, color = Color.White, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text(Res.get(R.string.voltar)) }
            }
        }
        return
    }

    // ── Equalizer Dialog ─────────────────────────────────────────────────────
    if (showEqualizer && equalizer != null) {
        var isEqEnabled by remember { mutableStateOf(EqualizerManager.isEnabled(context)) }
        var currentPreset by remember { mutableStateOf(EqualizerManager.getCurrentPreset(context)) }
        var expanded by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showEqualizer = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Equalizador", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isEqEnabled,
                        onCheckedChange = { 
                            isEqEnabled = it
                            EqualizerManager.setEnabled(context, it)
                            equalizer?.enabled = it
                        }
                    )
                }
            },
            text = {
                val numBands = equalizer!!.numberOfBands
                val minLevel = equalizer!!.bandLevelRange[0]
                val maxLevel = equalizer!!.bandLevelRange[1]
                
                Column {
                    // Preset Dropdown
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(currentPreset)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            EqualizerManager.customPresets.keys.forEach { presetName ->
                                DropdownMenuItem(
                                    text = { Text(presetName) },
                                    onClick = {
                                        currentPreset = presetName
                                        EqualizerManager.setCurrentPreset(context, presetName)
                                        // Re-apply preset to equalizer
                                        if (presetName != "Custom") {
                                            val levels = EqualizerManager.customPresets[presetName]!!
                                            for (i in 0 until numBands) {
                                                if (i < levels.size) {
                                                    try { equalizer!!.setBandLevel(i.toShort(), levels[i]) } catch(e:Exception){}
                                                }
                                            }
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max=300.dp)) {
                        items(numBands.toInt()) { i ->
                            val band = i.toShort()
                            val freq = equalizer!!.getCenterFreq(band) / 1000
                            // Force recomposition when preset changes by using currentPreset as a key
                            var level by remember(currentPreset) { mutableStateOf(equalizer!!.getBandLevel(band).toFloat()) }
                            
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(Res.get(R.string.xhz, (freq)), fontSize = 12.sp)
                                    Text(Res.get(R.string.x_100_db, (level.toInt() / 100)), fontSize = 12.sp)
                                }
                                Slider(
                                    value = level,
                                    onValueChange = { 
                                        level = it
                                        try { 
                                            equalizer!!.setBandLevel(band, it.toInt().toShort()) 
                                            EqualizerManager.saveBandLevel(context, band, it.toInt().toShort())
                                            if (currentPreset != "Custom") {
                                                currentPreset = "Custom"
                                            }
                                        } catch(_: Exception) {}
                                    },
                                    valueRange = minLevel.toFloat()..maxLevel.toFloat()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEqualizer = false }) { Text(Res.get(R.string.fechar)) } }
        )
    }

    // ── Player UI ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(isVideo, playerPrepared) {
                if (!playerPrepared) return@pointerInput
                var horizontalAccumulator = 0f
                detectTransformGestures { _, pan, zoom, _ ->
                    // Zoom
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    
                    if (scale > 1f) {
                        // Pan if zoomed
                        offsetX += pan.x * scale
                        offsetY += pan.y * scale
                    } else {
                        // Reset offsets when not zoomed
                        offsetX = 0f
                        offsetY = 0f
                        
                        // Seek (fast-forward) if scale == 1f and X pan is significant
                        if (abs(pan.x) > abs(pan.y)) {
                            horizontalAccumulator += pan.x
                            if (abs(horizontalAccumulator) > 10f) {
                                val seekDelta = horizontalAccumulator * 150
                                val newPos = (currentPosition + seekDelta).coerceIn(0f, duration.toFloat())
                                mediaPlayer.seekTo(newPos.toInt())
                                currentPosition = newPos.toLong()
                                gestureLabel = if (horizontalAccumulator > 0) Res.get(R.string.x_17, (formatDuration(newPos.toLong()))) else formatDuration(newPos.toLong())
                                gestureVisible = true
                                horizontalAccumulator = 0f
                            }
                        }
                    }
                }
            }
            .clickable { if (playerPrepared) showControls = !showControls }
    ) {
        // ── Video Surface ─────────────────────────────────────────────────
        if (isVideo) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Applies Aspect Ratio dynamically when dimensions are known
                val modifierRatio = if (videoWidth > 0 && videoHeight > 0) {
                    Modifier.aspectRatio(videoWidth.toFloat() / videoHeight.toFloat())
                } else Modifier.fillMaxSize()

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
                                    surfaceReady = false
                                    holderRef.value = null
                                }
                            })
                        }
                    },
                    modifier = modifierRatio.graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                )
            }
        }

        // ── Image viewer ─────────────────────────────────────────────────
        if (isImage) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = file,
                    contentDescription = "Imagem",
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.Black.copy(alpha=0.5f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = Color.White) }
            }
        }

        // ── Audio artwork placeholder ─────────────────────────────────────
        if (!isVideo && !isImage) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(20.dp)).background(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0D0D0D)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor.copy(alpha=0.8f), modifier = Modifier.size(96.dp)) }
                Spacer(modifier = Modifier.height(20.dp))
                Text(metadata.first, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
                if (metadata.second.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(Res.get(R.string.x_x_12, (metadata.second), (metadata.third)), color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        }

        // ── Gesture feedback ─────────────────────────────────────────────
        AnimatedVisibility(visible = gestureVisible, modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Surface(color = Color.Black.copy(alpha=0.7f), shape = RoundedCornerShape(12.dp)) {
                Text(gestureLabel, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }

        // ── Loading indicator ─────────────────────────────────────────────
        if (!playerPrepared && !showFilePicker && errorMessage == null && !isImage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
        }

        // ── Controls overlay ─────────────────────
        AnimatedVisibility(visible = showControls && !isImage, modifier = Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.75f), Color.Transparent))).align(Alignment.TopCenter)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { try { mediaPlayer.stop() } catch (_: Exception) {}; onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = Color.White) }
                        Text(if (isVideo) (file?.name ?: Res.get(R.string.video_2)) else metadata.first, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal=4.dp))
                        
                        // Cast button — active session re-opens the remote sheet
                        if (canCast || castState.session != null) {
                            IconButton(onClick = {
                                if (castState.session != null) castRemoteDismissed = false
                                else showCastPicker = true
                            }) { Icon(Icons.Default.Cast, contentDescription = Res.get(R.string.transmitir_para_tv), tint = if (castState.session != null) accentColor else Color.White) }
                        }
                        
                        // Equalizer button
                        if (equalizer != null) {
                            IconButton(onClick = { showEqualizer = true }) { Icon(Icons.Default.GraphicEq, contentDescription = "Equalizador", tint = Color.White) }
                        }
                        
                        // Speed button
                        TextButton(onClick = {
                            val nextSpeed = when (playbackSpeed) { 0.5f -> 1.0f; 1.0f -> 1.5f; 1.5f -> 2.0f; else -> 0.5f }
                            applySpeed(nextSpeed)
                            gestureLabel = Res.get(R.string.xx_velocidade, (nextSpeed))
                            gestureVisible = true
                        }) { Text(Res.get(R.string.xx_4, (playbackSpeed)), color = Color.White, fontWeight = FontWeight.Bold) }

                        if (isVideo) {
                            IconButton(onClick = { isFullscreen = !isFullscreen }) { Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White) }
                        }
                    }
                }

                // Bottom controls
                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.85f)))).padding(bottom=12.dp)) {
                    Column(modifier = Modifier.padding(horizontal=16.dp)) {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { ratio -> val pos = (ratio * duration).toLong(); mediaPlayer.seekTo(pos.toInt()); currentPosition = pos },
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = Color(0xFF555555)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(currentPosition), color = Color.LightGray, fontSize = 12.sp)
                            Text(formatDuration(duration), color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal=12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isRepeat = !isRepeat; mediaPlayer.isLooping = isRepeat }) { Icon(Icons.Default.Repeat, contentDescription = "Repetir", tint = if (isRepeat) accentColor else Color.Gray, modifier = Modifier.size(24.dp)) }
                        IconButton(onClick = { val p = (currentPosition - 10000L).coerceAtLeast(0L); mediaPlayer.seekTo(p.toInt()); currentPosition = p }) { Icon(Icons.Default.Replay10, contentDescription = Res.get(R.string.n_10s), tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(accentColor).clickable { if (!playerPrepared) return@clickable; if (isPlaying) { mediaPlayer.pause(); isPlaying = false } else { mediaPlayer.start(); isPlaying = true } }, contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = Res.get(R.string.play_pause), tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = { val p = (currentPosition + 10000L).coerceAtMost(duration); mediaPlayer.seekTo(p.toInt()); currentPosition = p }) { Icon(Icons.Default.Forward10, contentDescription = Res.get(R.string.n_10s_2), tint = Color.White, modifier = Modifier.size(30.dp)) }
                        IconButton(onClick = { val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (cur + 1).coerceAtMost(maxVolume), AudioManager.FLAG_SHOW_UI) }) { Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color.Gray, modifier = Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }

    if (showCastPicker && pickedFilePath.isNotEmpty()) {
        CastDevicePicker(
            discovery = castDiscovery,
            onDeviceSelected = { device ->
                scope.launch {
                    if (CastSessionManager.connect(context, device)) {
                        val loaded = CastSessionManager.load(
                            context,
                            CastMedia.fromPath(pickedFilePath, durationMs = duration)
                        )
                        if (loaded) {
                            // pause local playback only once the cast session
                            // actually takes over — on load failure nothing
                            // would play anywhere, so keep the local player
                            // running and the picker open for a retry
                            if (isPlaying) {
                                try { mediaPlayer.pause() } catch (_: Exception) {} // MED-R14
                                isPlaying = false
                            }
                            showCastPicker = false
                        }
                    }
                    // on failure the picker stays open for another try —
                    // lastError is toasted by the LaunchedEffect above
                }
            },
            onDismiss = { showCastPicker = false }
        )
    }

    // Held back while the device picker is up — the picker stays on top
    // and the sheet appears after it closes.
    if (castState.session != null && !castRemoteDismissed && !showCastPicker) {
        CastRemoteSheet(
            state = castState,
            onPlayPause = {
                scope.launch {
                    if (castState.isPlaying) CastSessionManager.pause() else CastSessionManager.play()
                }
            },
            onSeek = { ms -> scope.launch { CastSessionManager.seek(ms) } },
            onVolume = { level -> scope.launch { CastSessionManager.setVolume(level) } },
            onStop = { scope.launch { CastSessionManager.stop() } },
            onDisconnect = {
                castRemoteDismissed = true
                scope.launch { CastSessionManager.disconnect() }
            },
            onDismiss = { castRemoteDismissed = true }
        )
    }
}
