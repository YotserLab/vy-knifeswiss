package com.example.cast.ui
import com.example.ui.screens.files.FileManagerScreen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.cast.control.CastSessionManager
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDiscovery
import com.example.cast.media.CastMedia
import com.example.cast.media.MediaLibraryQuery
import com.example.ui.navigation.Screen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.DarkNavyVariant
import com.example.ui.theme.DeepDark
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceLight
import com.example.ui.theme.OnSurfaceMuted
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.io.File
import com.example.R
import com.example.Res

/**
 * Cast-to-TV hub: device discovery on top, the local media library in
 * tabs below. Tapping a media item either casts it to the active
 * session or opens [CastDevicePicker] when nothing is connected yet.
 * The session lives in [CastSessionManager], so it survives leaving
 * this screen — we only stop the discovery scan on dispose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val discovery = remember { CastDiscovery(context.applicationContext) }
    val library = remember { MediaLibraryQuery(context) }

    val devices by discovery.devices.collectAsState()
    val scanning by discovery.scanning.collectAsState()
    val state by CastSessionManager.state.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var pendingMedia by remember { mutableStateOf<CastMedia?>(null) }
    // Dismissed by default — the remote only auto-opens when a NEW session
    // object appears (see LaunchedEffect below), not on screen entry.
    var remoteDismissed by remember { mutableStateOf(true) }
    var lastCastSession by remember { mutableStateOf(state.session) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showManualIp by remember { mutableStateOf(false) }

    // Serving local files requires readable storage: MANAGE_EXTERNAL_STORAGE
    // on R+ (CastMediaServer.serve checks File.canRead), READ_EXTERNAL_STORAGE
    // below that. Without it MediaStore queries come back empty anyway, so we
    // gate the library tabs behind this flag.
    var hasStoragePermission by remember {
        mutableStateOf(storagePermissionGranted(context))
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasStoragePermission = storagePermissionGranted(context) }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasStoragePermission = storagePermissionGranted(context) }

    var videos by remember { mutableStateOf<List<CastMedia>?>(null) }
    var images by remember { mutableStateOf<List<CastMedia>?>(null) }
    var audios by remember { mutableStateOf<List<CastMedia>?>(null) }

    // Wi-Fi transport tracking — the whole protocol needs both ends on
    // the same LAN, so surface that prominently instead of failing later.
    var onWifi by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        fun currentWifi(): Boolean = cm?.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        onWifi = currentWifi()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { onWifi = currentWifi() }
            override fun onLost(network: Network) { onWifi = currentWifi() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                onWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose {
            if (cm != null) {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }

    LaunchedEffect(Unit) { discovery.startScan() }
    DisposableEffect(Unit) { onDispose { discovery.stopScan() } }

    // Surface connect/load failures once per error value — consumeError()
    // prevents a stale error from re-toasting on the next screen entry.
    LaunchedEffect(state.lastError) {
        state.lastError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            CastSessionManager.consumeError()
        }
    }
    // Auto-open the remote only when a NEW session object appears —
    // entering with an existing session keeps it dismissed (FAB re-opens).
    LaunchedEffect(state.session) {
        if (state.session != null && state.session !== lastCastSession) {
            remoteDismissed = false
        }
        lastCastSession = state.session
    }

    // Lazy per-tab library loads — MediaStore queries stay off the path
    // until the user actually opens that tab, and are skipped entirely
    // while storage access is missing (results would be empty/unreadable).
    LaunchedEffect(selectedTab, hasStoragePermission) {
        if (!hasStoragePermission) {
            videos = null
            images = null
            audios = null
            return@LaunchedEffect
        }
        when (selectedTab) {
            0 -> if (videos == null) videos = library.videos()
            1 -> if (images == null) images = library.images()
            2 -> if (audios == null) audios = library.audios()
        }
    }

    val onMediaClick: (CastMedia) -> Unit = { item ->
        if (state.session == null) {
            pendingMedia = item
            showPicker = true
        } else {
            scope.launch { CastSessionManager.load(context, item) }
        }
    }

    // Local playback reuses the in-app media player — it handles video,
    // audio and images by path and still offers cast from inside it.
    val onPlayLocally: (CastMedia) -> Unit = { item ->
        navController.navigate(Screen.MediaPlayer.withPath(item.path))
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.cast_to_tv),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = CyanNeon
                        )
                    }
                },
                actions = {
                    Icon(
                        imageVector = if (onWifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = if (onWifi) Res.get(R.string.wi_fi_conectado) else Res.get(R.string.sem_wi_fi),
                        tint = if (onWifi) SuccessGreen else DangerRed,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 4.dp)
                            .size(20.dp)
                    )
                    IconButton(
                        onClick = {
                            discovery.clear()
                            discovery.startScan()
                        },
                        enabled = !scanning
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = Res.get(R.string.escanear_dispositivos),
                            tint = if (scanning) OnSurfaceDim else CyanNeon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        floatingActionButton = {
            if (state.session != null && remoteDismissed) {
                ExtendedFloatingActionButton(
                    onClick = { remoteDismissed = false },
                    containerColor = AmberGold,
                    contentColor = DeepDark
                ) {
                    Icon(Icons.Default.Cast, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.controle_remoto))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark)
        ) {
            if (!onWifi) {
                Surface(
                    color = AmberGold.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            Res.get(R.string.conecte_se_a_mesma_rede_wi_fi_da_tv),
                            color = AmberGold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (!hasStoragePermission) {
                Surface(
                    color = DarkNavy,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            Res.get(R.string.permissao_necessaria),
                            color = DangerRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Res.get(R.string.permissao_de_acesso_aos_arquivos_necessa),
                            color = OnSurfaceMuted,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        settingsLauncher.launch(
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                    } catch (e: Exception) {
                                        settingsLauncher.launch(
                                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        )
                                    }
                                } else {
                                    readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Conceder", color = AmberGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Devices card
            Surface(
                color = DarkNavy,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Dispositivos",
                            color = OnSurfaceLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (scanning) {
                            CircularProgressIndicator(
                                color = CyanNeon,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    when {
                        devices.isEmpty() && scanning -> Text(
                            Res.get(R.string.procurando_dispositivos),
                            color = OnSurfaceMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        devices.isEmpty() -> Text(
                            Res.get(R.string.nenhum_dispositivo_encontrado_na_rede),
                            color = OnSurfaceMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        else -> devices.forEach { device ->
                            val connected = state.session != null && state.device?.id == device.id
                            DeviceListItem(
                                device = device,
                                connecting = state.connecting && state.device?.id == device.id,
                                connected = connected,
                                // a second tap mid-connect would tear down
                                // the in-flight session via disconnectInternal
                                enabled = !state.connecting,
                                onClick = {
                                    if (connected) {
                                        // Tapping the connected target must NOT
                                        // re-run connect (that would disconnect
                                        // and kill playback) — open the remote.
                                        remoteDismissed = false
                                    } else {
                                        scope.launch { CastSessionManager.connect(context, device) }
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualIp = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Res.get(R.string.adicionar_por_ip),
                            color = AmberGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Media library tabs
            val tabs = listOf(Res.get(R.string.videos), "Fotos", Res.get(R.string.audio_2))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DeepDark,
                contentColor = AmberGold,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                        color = AmberGold
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) AmberGold else OnSurfaceMuted,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                2 -> MediaAudioList(audios, onMediaClick, onPlayLocally)
                else -> MediaGrid(
                    items = if (selectedTab == 0) videos else images,
                    onClick = onMediaClick,
                    onPlayLocally = onPlayLocally
                )
            }
        }
    }

    if (showPicker) {
        CastDevicePicker(
            discovery = discovery,
            onDeviceSelected = { device ->
                scope.launch {
                    if (CastSessionManager.connect(context, device)) {
                        showPicker = false
                        pendingMedia?.let { CastSessionManager.load(context, it) }
                        pendingMedia = null
                    }
                    // on failure the sheet stays open for another pick —
                    // lastError is toasted by the LaunchedEffect above
                }
            },
            onDismiss = {
                showPicker = false
                pendingMedia = null
            }
        )
    }

    if (showManualIp) {
        ManualIpDialog(
            onConfirm = { ip ->
                discovery.addManualDevice(ip)
                showManualIp = false
            },
            onDismiss = { showManualIp = false }
        )
    }

    // Held back while the device picker is up — the picker stays on top.
    if (state.session != null && !remoteDismissed && !showPicker) {
        CastRemoteSheet(
            state = state,
            onPlayPause = {
                scope.launch {
                    if (state.isPlaying) CastSessionManager.pause() else CastSessionManager.play()
                }
            },
            onSeek = { ms -> scope.launch { CastSessionManager.seek(ms) } },
            onVolume = { level -> scope.launch { CastSessionManager.setVolume(level) } },
            onStop = { scope.launch { CastSessionManager.stop() } },
            onDisconnect = {
                remoteDismissed = true
                scope.launch { CastSessionManager.disconnect() }
            },
            onDismiss = { remoteDismissed = true }
        )
    }
}

/** Small green chip marking the device the active session is bound to. */
@Composable
private fun ConnectedChip() {
    Surface(
        color = SuccessGreen.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
    ) {
        Text(
            text = Res.get(R.string.conectado),
            color = SuccessGreen,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DeviceListItem(
    device: CastDevice,
    connecting: Boolean,
    connected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    CastDeviceRow(
        device = device,
        onClick = onClick,
        enabled = enabled,
        trailing = {
            if (connecting) {
                CircularProgressIndicator(
                    color = CyanNeon,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else if (connected) {
                ConnectedChip()
            }
        }
    )
}

/** Grid of video/photo thumbnails; Coil shows the icon behind it on decode failure. */
@Composable
private fun MediaGrid(
    items: List<CastMedia>?,
    onClick: (CastMedia) -> Unit,
    onPlayLocally: (CastMedia) -> Unit
) {
    if (items == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CyanNeon)
        }
        return
    }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Res.get(R.string.nenhum_arquivo_encontrado), color = OnSurfaceMuted, fontSize = 14.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { it.path }) { media ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkNavy)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable { onClick(media) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(DarkNavyVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (media.kind) {
                            CastMedia.Kind.VIDEO -> Icons.Default.Movie
                            CastMedia.Kind.AUDIO -> Icons.Default.MusicNote
                            CastMedia.Kind.IMAGE -> Icons.Default.Image
                        },
                        contentDescription = null,
                        tint = OnSurfaceDim,
                        modifier = Modifier.size(32.dp)
                    )
                    AsyncImage(
                        model = File(media.path),
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                ) {
                    Text(
                        text = media.title,
                        color = OnSurfaceMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp)
                    )
                    IconButton(
                        onClick = { onPlayLocally(media) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = Res.get(R.string.tocar_no_aparelho),
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether local media files are readable for casting. On R+ the app
 * relies on MANAGE_EXTERNAL_STORAGE (mirrors FileManagerScreen); below
 * that READ_EXTERNAL_STORAGE suffices.
 */
private fun storagePermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

/** Audio files get list rows instead of a grid — no thumbnails to show. */
@Composable
private fun MediaAudioList(
    items: List<CastMedia>?,
    onClick: (CastMedia) -> Unit,
    onPlayLocally: (CastMedia) -> Unit
) {
    if (items == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CyanNeon)
        }
        return
    }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Res.get(R.string.nenhum_arquivo_encontrado), color = OnSurfaceMuted, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(items, key = { it.path }) { media ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(media) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = media.title,
                    color = OnSurfaceLight,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onPlayLocally(media) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = Res.get(R.string.tocar_no_aparelho),
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
