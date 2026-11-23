package com.example.cast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cast.control.CastSessionManager
import com.example.cast.control.UpnpSoap
import com.example.cast.media.CastMedia
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.DarkNavyVariant
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceLight
import com.example.ui.theme.OnSurfaceMuted
import com.example.R
import com.example.Res

/**
 * Pure-presentational remote control for the active cast session.
 * Every action is a callback — the owning screen maps them onto
 * [CastSessionManager]'s suspend functions inside a coroutine scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastRemoteSheet(
    state: CastSessionManager.CastState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int) -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val session = state.session
    val media = state.media
    val device = state.device
    val canSeek = session?.supportsSeek == true && state.durationMs > 0
    val canChangeVolume = session?.supportsVolume == true && state.volume >= 0

    // Local drag position — while the user is scrubbing we show their
    // finger's value instead of the 1s-polled position, and only push
    // the final value through onSeek on release.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var volumeDrag by remember { mutableStateOf<Float?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AmberGold) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Now playing
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = DarkNavyVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = when (media?.kind) {
                            CastMedia.Kind.AUDIO -> Icons.Default.MusicNote
                            else -> Icons.Default.Movie
                        },
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = media?.title ?: Res.get(R.string.nenhuma_midia_carregada),
                        color = OnSurfaceLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device?.name ?: "",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Seek bar
            if (canSeek) {
                Spacer(Modifier.height(16.dp))
                val shownPosition = scrubPosition ?: state.positionMs.toFloat()
                Slider(
                    value = shownPosition.coerceIn(0f, state.durationMs.toFloat()),
                    onValueChange = { scrubPosition = it },
                    onValueChangeFinished = {
                        scrubPosition?.let { onSeek(it.toLong()) }
                        scrubPosition = null
                    },
                    valueRange = 0f..state.durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AmberGold,
                        activeTrackColor = AmberGold,
                        inactiveTrackColor = DarkNavyVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = UpnpSoap.msToHms((scrubPosition ?: state.positionMs.toFloat()).toLong()),
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = UpnpSoap.msToHms(state.durationMs),
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Transport controls
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Parar",
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                if (canSeek) {
                    IconButton(onClick = {
                        onSeek((state.positionMs - 10_000L).coerceAtLeast(0L))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = Res.get(R.string.voltar_10s),
                            tint = OnSurfaceLight,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = AmberGold,
                        contentColor = DarkNavy
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproduzir",
                        modifier = Modifier.size(32.dp)
                    )
                }
                if (canSeek) {
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = {
                        onSeek((state.positionMs + 10_000L).coerceAtMost(state.durationMs))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = Res.get(R.string.avancar_10s),
                            tint = OnSurfaceLight,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Volume
            if (canChangeVolume) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = (volumeDrag ?: state.volume.toFloat()).coerceIn(0f, 100f),
                        onValueChange = { volumeDrag = it },
                        onValueChangeFinished = {
                            volumeDrag?.let { onVolume(it.toInt()) }
                            volumeDrag = null
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanNeon,
                            activeTrackColor = CyanNeon,
                            inactiveTrackColor = DarkNavyVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Res.get(R.string.volumedrag_state_volume_tofloat_toint, ((volumeDrag ?: state.volume.toFloat()).toInt())),
                        color = OnSurfaceMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }

            // Disconnect
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Desconectar", color = DangerRed, fontSize = 15.sp)
            }
        }
    }
}
