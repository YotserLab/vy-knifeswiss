package com.example.droidlink.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.droidlink.client.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.R
import com.example.Res

/**
 * Main remote-control console for a connected DroidLink session.
 *
 * Layout:
 *  - Compact top bar: latency badge · lock icon · disconnect button
 *  - Tab row (bottom nav style): Screen | Files | Processes
 *
 * @param viewModel Shared ViewModel from the Connect screen.
 * @param onDisconnected Called after the user confirms disconnect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidLinkConsoleScreen(
    viewModel: DroidLinkClientViewModel,
    onDisconnected: () -> Unit
) {
    val clientState by viewModel.clientState.collectAsStateWithLifecycle()
    val connected = clientState as? DroidLinkClientState.Connected

    // Navigate back if we lose connection
    LaunchedEffect(clientState) {
        if (clientState is DroidLinkClientState.Disconnected ||
            clientState is DroidLinkClientState.Error
        ) onDisconnected()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tela", "Arquivos", "Processos")

    var showSettingsDialog by remember { mutableStateOf(false) }
    val isAudioMuted by viewModel.isAudioMuted.collectAsStateWithLifecycle()
    val audioVolume by viewModel.audioVolume.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            ConsoleTopBar(
                latencyMs = connected?.latencyMs ?: 0L,
                onOpenSettings = { showSettingsDialog = true },
                onDisconnect = { viewModel.disconnect() }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DarkNavy) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tabIcon(index),
                                contentDescription = label,
                                tint = if (selectedTab == index) CyanNeon else OnSurfaceMuted
                            )
                        },
                        label = {
                            Text(
                                label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (selectedTab == index) CyanNeon else OnSurfaceMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanNeon,
                            indicatorColor = CyanNeonAlpha
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ScreenTab(viewModel = viewModel, connected = connected)
                1 -> FilesTab()
                2 -> ProcessesTab()
            }
        }
    }

    if (showSettingsDialog) {
        ClientSettingsDialog(
            isAudioMuted = isAudioMuted,
            audioVolume = audioVolume,
            onToggleAudioMute = { viewModel.toggleAudioMute() },
            onVolumeChange = { viewModel.setAudioVolume(it) },
            currentQuality = connected?.qualityMode ?: QualityMode.BALANCED,
            onQualityChange = { viewModel.setQuality(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsoleTopBar(
    latencyMs: Long,
    onOpenSettings: () -> Unit,
    onDisconnect: () -> Unit
) {
    val latencyColor = when {
        latencyMs < 80  -> SuccessGreen
        latencyMs < 200 -> AmberGold
        else            -> DangerRed
    }

    TopAppBar(
        modifier = Modifier.height(48.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Latency badge
                Text(
                    text = Res.get(R.string.xms, (latencyMs)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = latencyColor
                )
                // Lock icon (always shown — indicates encrypted session)
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = Res.get(R.string.sessao_criptografada),
                    tint = SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = Res.get(R.string.configuracoes_da_sessao),
                    tint = CyanNeon,
                    modifier = Modifier.size(20.dp)
                )
            }
            TextButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
            ) {
                Text(
                    "Desconectar",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy)
    )
}

@Composable
private fun ClientSettingsDialog(
    isAudioMuted: Boolean,
    audioVolume: Float,
    onToggleAudioMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    currentQuality: QualityMode,
    onQualityChange: (QualityMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(Res.get(R.string.configuracoes_da_sessao_anydesk), color = OnSurfaceLight, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Remote Audio
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = if (isAudioMuted) OnSurfaceMuted else CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(Res.get(R.string.audio_remoto), color = OnSurfaceLight, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = !isAudioMuted,
                            onCheckedChange = { onToggleAudioMute() },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeonAlpha)
                        )
                    }

                    if (!isAudioMuted) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Res.get(R.string.volume_audiovolume_100_toint, ((audioVolume * 100).toInt())), fontSize = 11.sp, color = OnSurfaceMuted)
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = audioVolume,
                                onValueChange = onVolumeChange,
                                colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkNavy)

                // Qualidade
                Column {
                    Text(Res.get(R.string.qualidade_de_imagem), color = OnSurfaceLight, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QualityMode.entries.forEach { mode ->
                            val isSel = currentQuality == mode
                            SuggestionChip(
                                onClick = { onQualityChange(mode) },
                                label = { Text(mode.label, fontSize = 11.sp, color = if (isSel) DarkNavy else OnSurfaceLight) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = if (isSel) CyanNeon else CyanNeonAlpha),
                                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = CyanNeon.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Res.get(R.string.fechar), color = CyanNeon)
            }
        },
        containerColor = DarkNavy
    )
}

// ── Screen Tab ───────────────────────────────────────────────────────────────

@Composable
private fun ScreenTab(
    viewModel: DroidLinkClientViewModel,
    connected: DroidLinkClientState.Connected?
) {
    val scope = rememberCoroutineScope()

    // Touch indicator state
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val touchAlpha = remember { Animatable(0f) }

    // Container size for coordinate normalization
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Collapsible system-action bar
    var showSystemBar by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050810))
    ) {
        // ── Video surface placeholder (H.264 decode via MediaCodec goes here) ──
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Future: configure MediaCodec decoder and attach to this surface
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // Future: release MediaCodec decoder
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Video placeholder text (shown until stream starts) ───────────────
        Text(
            text = Res.get(R.string.aguardando_stream_de_video),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = OnSurfaceDim,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── Touch capture layer ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(containerSize) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                val nx = offset.x / containerSize.width
                                val ny = offset.y / containerSize.height
                                viewModel.sendGesture(nx, ny, GestureType.TAP)
                                touchPos = offset
                                scope.launch {
                                    touchAlpha.snapTo(1f)
                                    touchAlpha.animateTo(0f, tween(400))
                                }
                            }
                        },
                        onLongPress = { offset ->
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                val nx = offset.x / containerSize.width
                                val ny = offset.y / containerSize.height
                                viewModel.sendGesture(nx, ny, GestureType.LONG_PRESS)
                                touchPos = offset
                                scope.launch {
                                    touchAlpha.snapTo(0.8f)
                                    touchAlpha.animateTo(0f, tween(600))
                                }
                            }
                        }
                    )
                }
                .pointerInput(containerSize) {
                    var dragging = false
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                val nx = offset.x / containerSize.width
                                val ny = offset.y / containerSize.height
                                viewModel.sendGesture(nx, ny, GestureType.DRAG_START)
                            }
                        },
                        onDrag = { change, _ ->
                            val offset = change.position
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                val nx = offset.x / containerSize.width
                                val ny = offset.y / containerSize.height
                                viewModel.sendGesture(nx, ny, GestureType.DRAG_MOVE)
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            viewModel.sendGesture(0f, 0f, GestureType.DRAG_END)
                        },
                        onDragCancel = {
                            dragging = false
                            viewModel.sendGesture(0f, 0f, GestureType.DRAG_END)
                        }
                    )
                }
        )

        // ── Touch ripple indicator ────────────────────────────────────────────
        touchPos?.let { pos ->
            val alpha = touchAlpha.value
            if (alpha > 0f) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (pos.x.toInt() - 20).dp,
                            y = (pos.y.toInt() - 20).dp
                        )
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyanNeon.copy(alpha = alpha * 0.5f))
                )
            }
        }

        // ── Bottom overlay: quality chips + system-bar toggle ─────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quality chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                QualityMode.values().forEach { mode ->
                    val selected = connected?.qualityMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setQuality(mode) },
                        label = {
                            Text(
                                mode.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanNeonAlpha,
                            selectedLabelColor = CyanNeon,
                            containerColor = DarkNavy,
                            labelColor = OnSurfaceMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = CyanNeon,
                            borderColor = DarkBorder
                        )
                    )
                }

                // Toggle system-action bar
                FilterChip(
                    selected = showSystemBar,
                    onClick = { showSystemBar = !showSystemBar },
                    label = {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = Res.get(R.string.acoes_do_sistema),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DarkNavyVariant,
                        selectedLabelColor = OnSurfaceLight,
                        containerColor = DarkNavy,
                        labelColor = OnSurfaceMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showSystemBar,
                        selectedBorderColor = OnSurfaceDim,
                        borderColor = DarkBorder
                    )
                )
            }

            // Collapsible system-action bar
            AnimatedVisibility(
                visible = showSystemBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                SystemActionBar(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SystemActionBar(viewModel: DroidLinkClientViewModel) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(DarkNavy.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SystemActionButton(
            icon = Icons.Default.ArrowBack,
            label = Res.get(R.string.voltar),
            onClick = { viewModel.sendSystemAction(SystemAction.BACK) }
        )
        SystemActionButton(
            icon = Icons.Default.Home,
            label = "Home",
            onClick = { viewModel.sendSystemAction(SystemAction.HOME) }
        )
        SystemActionButton(
            icon = Icons.Default.GridView,
            label = "Recents",
            onClick = { viewModel.sendSystemAction(SystemAction.RECENTS) }
        )
        SystemActionButton(
            icon = Icons.Default.Notifications,
            label = "Notifs",
            onClick = { viewModel.sendSystemAction(SystemAction.NOTIFICATIONS) }
        )
        SystemActionButton(
            icon = Icons.Default.Lock,
            label = "Bloquear",
            onClick = { viewModel.sendSystemAction(SystemAction.LOCK_SCREEN) }
        )
    }
}

@Composable
private fun SystemActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = OnSurfaceLight,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = OnSurfaceMuted
            )
        }
    }
}

// ── Files Tab ────────────────────────────────────────────────────────────────

@Composable
private fun FilesTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = Res.get(R.string.explorador_de_arquivos),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = OnSurfaceLight
            )
            Text(
                text = Res.get(R.string.a_implementar_futuramente),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = OnSurfaceMuted
            )
        }
    }
}

// ── Processes Tab ─────────────────────────────────────────────────────────────

@Composable
private fun ProcessesTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = ElectricViolet,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = Res.get(R.string.processos_telemetria),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = OnSurfaceLight
            )
            Text(
                text = Res.get(R.string.a_implementar_futuramente),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = OnSurfaceMuted
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun tabIcon(index: Int): ImageVector = when (index) {
    0    -> Icons.Default.DesktopWindows
    1    -> Icons.Default.Folder
    else -> Icons.Default.Memory
}
