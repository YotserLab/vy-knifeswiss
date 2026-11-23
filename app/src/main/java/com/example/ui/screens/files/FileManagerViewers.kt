package com.example.ui.screens.files

import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import com.example.R
import com.example.Res

@Composable
fun FileManagerViewerPanel(
    activeViewerFile: FileItem,
    activeViewerType: FileType,
    textEditorContent: String,
    onTextEditorContentChange: (String) -> Unit,
    onSaveLocalChanges: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Res.get(R.string.viewer_x, (activeViewerFile.name)),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = Res.get(R.string.close_viewer))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            when (activeViewerType) {
                FileType.DOCUMENT -> {
                    OutlinedTextField(
                        value = textEditorContent,
                        onValueChange = onTextEditorContentChange,
                        label = { Text(Res.get(R.string.edit_file_content)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        maxLines = 10
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSaveLocalChanges,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = Res.get(R.string.save_changes))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Res.get(R.string.save_local_changes))
                    }
                }

                FileType.AUDIO -> {
                    AudioPlayerWidget(file = activeViewerFile)
                }

                FileType.VIDEO -> {
                    VideoPlayerWidget(file = activeViewerFile)
                }

                FileType.IMAGE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(activeViewerFile.path),
                            contentDescription = activeViewerFile.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                else -> {
                    Text(Res.get(R.string.format_is_not_previewable_in_current_vie))
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(Res.get(R.string.path_x, (activeViewerFile.path)), fontSize = 11.sp)
                            Text(Res.get(R.string.size_x, (activeViewerFile.size)), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerWidget(file: FileItem) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    val mediaPlayer = remember { MediaPlayer() }
    var equalizer by remember { mutableStateOf<android.media.audiofx.Equalizer?>(null) }

    DisposableEffect(file.path) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, Uri.parse(file.path))
            mediaPlayer.prepare()
            try { equalizer = com.example.utils.EqualizerManager.applyTo(context, mediaPlayer.audioSessionId) } catch (_: Exception) {}
            duration = mediaPlayer.duration.toFloat().coerceAtLeast(1f)
        } catch (e: Exception) {
            Toast.makeText(context, Res.get(R.string.error_loading_audio_x, (e.message)), Toast.LENGTH_SHORT).show()
        }

        onDispose {
            try {
                equalizer?.release()
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                // Ignore release issues
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            try {
                mediaPlayer.start()
                while (isPlaying && mediaPlayer.isPlaying) {
                    position = mediaPlayer.currentPosition.toFloat()
                    delay(500)
                }
                // Handle natural end of audio
                if (!mediaPlayer.isPlaying) {
                    isPlaying = false
                    position = 0f
                }
            } catch (e: Exception) {
                isPlaying = false
            }
        } else {
            try {
                mediaPlayer.pause()
            } catch (e: Exception) {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = Res.get(R.string.audio_track),
            modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(Res.get(R.string.playing_x, (file.name)), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = position,
            onValueChange = {
                position = it
                try {
                    mediaPlayer.seekTo(it.toInt())
                } catch (e: Exception) {}
            },
            valueRange = 0f..duration,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                try {
                    val target = (mediaPlayer.currentPosition - 10000).coerceAtLeast(0)
                    mediaPlayer.seekTo(target)
                    position = target.toFloat()
                } catch (e: Exception) {}
            }) {
                Icon(Icons.Default.Replay10, contentDescription = Res.get(R.string.rewind_10s))
            }
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(54.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) Res.get(R.string.pause) else Res.get(R.string.play),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = {
                try {
                    val target = (mediaPlayer.currentPosition + 10000).coerceAtMost(duration.toInt())
                    mediaPlayer.seekTo(target)
                    position = target.toFloat()
                } catch (e: Exception) {}
            }) {
                Icon(Icons.Default.Forward10, contentDescription = Res.get(R.string.forward_10s))
            }
        }
    }
}

@Composable
fun VideoPlayerWidget(file: FileItem) {
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    var speed by remember { mutableStateOf(1.0f) }
    var showControls by remember { mutableStateOf(true) }

    val videoViewRef = remember { mutableStateOf<android.widget.VideoView?>(null) }
    val mediaPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            videoViewRef.value?.start()
            while (isPlaying) {
                videoViewRef.value?.let { vv ->
                    try {
                        if (vv.isPlaying) {
                            position = vv.currentPosition.toFloat()
                            val dur = vv.duration.toFloat()
                            if (dur > 0) duration = dur
                        }
                    } catch (e: Exception) {}
                }
                kotlinx.coroutines.delay(500)
            }
        } else {
            videoViewRef.value?.pause()
        }
    }

    LaunchedEffect(speed) {
        mediaPlayerRef.value?.let { mp ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            } catch (e: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable { showControls = !showControls }
        ) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(file.path)
                        setOnPreparedListener { mp ->
                            mediaPlayerRef.value = mp
                            duration = mp.duration.toFloat().coerceAtLeast(1f)
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    val params = mp.playbackParams
                                    params.speed = speed
                                    mp.playbackParams = params
                                }
                            } catch (e: Exception) {}
                            mp.start()
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            position = duration
                        }
                        videoViewRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) Res.get(R.string.pause) else Res.get(R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(java.util.Locale.getDefault(), "%02d:%02d", (position / 1000).toInt() / 60, (position / 1000).toInt() % 60),
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = position,
                onValueChange = { newPos ->
                    position = newPos
                    videoViewRef.value?.seekTo(newPos.toInt())
                },
                valueRange = 0f..duration,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = String.format(java.util.Locale.getDefault(), "%02d:%02d", (duration / 1000).toInt() / 60, (duration / 1000).toInt() % 60),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(Res.get(R.string.velocidade_xx, (speed)), color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0.5f..2.0f,
                steps = 2,
                modifier = Modifier.width(150.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}
