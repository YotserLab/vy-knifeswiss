package com.example.ui.screens.files
import com.example.ui.screens.docs.EpubViewerScreen
import com.example.ui.screens.docs.PdfViewerScreen
import com.example.ui.screens.docs.TextEditorScreen
import com.example.ui.screens.media.MediaPlayerScreen
import com.example.ui.screens.media.MediaStoreHelper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import java.net.URLEncoder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.cast.control.CastSessionManager
import com.example.cast.discovery.CastDiscovery
import com.example.cast.media.CastMedia
import com.example.cast.ui.CastDevicePicker
import com.example.cast.ui.CastRemoteSheet
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.data.AppDatabase
import com.example.ui.navigation.Screen
import com.example.ui.screens.filemanager.*
import kotlinx.coroutines.launch
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// ARQ-R01
fun FileManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val sharedPrefs = remember { context.getSharedPreferences("net_connections_pref", Context.MODE_PRIVATE) }
    var connectionsList by remember { mutableStateOf<List<NetworkConnection>>(emptyList()) }
    
    val database = remember { AppDatabase.getDatabase(context) }
    val playlistDao = remember { database.playlistDao() }
    val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    val playlistsCount = playlists.size
    
    var mediaImages by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var mediaAudio by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var mediaVideos by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var mediaDocs by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    
    var currentProvider by remember { mutableStateOf<FileSystemProvider?>(null) }
    var currentPath by remember { mutableStateOf("/") }
    var browserFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    // Special media filter (when selected on home)
    var activeFilter by remember { mutableStateOf<FileType?>(null) }
    
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAddConnectionDialog by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var fileToView by remember { mutableStateOf<FileItem?>(null) }

    // Cast to TV — the session lives in CastSessionManager, so it
    // survives leaving this screen; we only track picker/dismiss state.
    val castDiscovery = remember { CastDiscovery(context.applicationContext) }
    val castState by CastSessionManager.state.collectAsState()
    var castTargetFile by remember { mutableStateOf<FileItem?>(null) }
    // Dismissed by default — the remote only auto-opens when a NEW session
    // object appears (see LaunchedEffect below), not on screen entry.
    var castRemoteDismissed by remember { mutableStateOf(true) }
    var lastCastSession by remember { mutableStateOf(castState.session) }
    
    val sdCardPath = remember {
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        if (dirs.size > 1 && dirs[1] != null) {
            // Extracts the SD card root (e.g., /storage/1234-5678)
            val path = dirs[1]!!.absolutePath
            val rootPath = path.substringBefore("/Android/data")
            rootPath
        } else null
    }
    
    var hasManageStoragePermission by remember { 
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasManageStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    }
    
    fun loadConnections() {
        val savedString = sharedPrefs.getString("connections_data", "") ?: ""
        if (savedString.isNotEmpty()) {
            connectionsList = savedString.split("||").mapNotNull { it.toNetworkConnection() }
        }
    }
    
    fun saveConnections(list: List<NetworkConnection>) {
        val str = list.joinToString("||") { it.toSaveString() }
        sharedPrefs.edit().putString("connections_data", str).apply()
        connectionsList = list
    }
    
    val baseDir = remember { Environment.getExternalStorageDirectory() }
    val ftpServer = remember { FTPServer(coroutineScope, baseDir) }
    var isFtpRunning by remember { mutableStateOf(ftpServer.isRunning) }
    var ftpAddress by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        loadConnections()
        mediaImages = MediaStoreHelper.queryImages(context)
        mediaAudio = MediaStoreHelper.queryAudio(context)
        mediaVideos = MediaStoreHelper.queryVideos(context)
        mediaDocs = MediaStoreHelper.queryDocuments(context)
    }

    // Surface cast failures once per error value — consumeError()
    // prevents the same error from re-toasting on recomposition. When the
    // fileToView dialog hosts MediaPlayerScreen, it toasts/consumes
    // itself — skip only the toast here to avoid doubles.
    LaunchedEffect(castState.lastError) {
        castState.lastError?.let {
            if (fileToView == null) {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
            CastSessionManager.consumeError()
        }
    }
    // Auto-open the remote only when a NEW session object appears —
    // entering with an existing session keeps it dismissed (FAB re-opens).
    LaunchedEffect(castState.session) {
        if (castState.session != null && castState.session !== lastCastSession) {
            castRemoteDismissed = false
        }
        lastCastSession = castState.session
    }
    DisposableEffect(Unit) { onDispose { castDiscovery.stopScan() } }
    
    fun openDirectory(provider: FileSystemProvider, path: String) {
        currentProvider = provider
        currentPath = path
        activeFilter = null
        isLoading = true
        errorMsg = null
        coroutineScope.launch {
            try {
                browserFiles = provider.listFiles(path)
            } catch (e: Exception) {
                errorMsg = e.message ?: Res.get(R.string.erro_ao_carregar_pasta)
            } finally {
                isLoading = false
            }
        }
    }

    // Intercept Back press
    BackHandler(enabled = currentProvider != null || activeFilter != null) {
        if (activeFilter != null) {
            activeFilter = null // Volta pra home
        } else if (currentProvider != null) {
            if (currentPath == "/" || currentPath == "/sdcard") {
                currentProvider = null // Volta pra home
            } else {
                // Tenta ir pro pai
                val parent = java.io.File(currentPath).parent
                if (parent != null) {
                    openDirectory(currentProvider!!, parent)
                } else {
                    currentProvider = null
                }
            }
        }
    }

    if (showSettings) {
        FileManagerSettingsScreen(onBack = { showSettings = false })
        return
    }
    
    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (activeFilter != null) activeFilter!!.name 
                        else if (currentProvider != null) currentProvider!!.connectionName 
                        else Res.get(R.string.gerenciador_de_arquivos),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentProvider == null && activeFilter == null) navController.popBackStack()
                        else {
                            currentProvider = null
                            activeFilter = null
                        }
                    }) {
                        Icon(if (currentProvider == null && activeFilter == null) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = AmberGold)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = Res.get(R.string.mais_opcoes), tint = AmberGold)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkNavyVariant).border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(text = { Text(Res.get(R.string.configuracoes), color = OnSurfaceLight) }, onClick = { showMenu = false; showSettings = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        floatingActionButton = {
            if (castState.session != null && castRemoteDismissed && fileToView == null) {
                ExtendedFloatingActionButton(
                    onClick = { castRemoteDismissed = false },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(DeepDark)) {
            if (!hasManageStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                NeonCard(
                    borderColor = DangerRed,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column {
                        Text(Res.get(R.string.permissao_necessaria_2), fontWeight = FontWeight.Bold, color = DangerRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(Res.get(R.string.para_exibir_seus_arquivos_precisamos_da), fontSize = 14.sp, color = OnSurfaceLight)
                        Spacer(modifier = Modifier.height(12.dp))
                        GradientButton(
                            text = Res.get(R.string.conceder_permissao),
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    permissionLauncher.launch(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    permissionLauncher.launch(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            startColor = DangerRed,
                            endColor = DangerRed.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (currentProvider == null && activeFilter == null) {
                OwlHomeTab(
                    sdCardPath = sdCardPath,
                    localFilesCount = 0,
                    imgCount = mediaImages.size,
                    vidCount = mediaVideos.size,
                    audCount = mediaAudio.size,
                    docCount = mediaDocs.size,
                    connectionsList = connectionsList,
                    playlistsCount = playlistsCount,
                    isFtpRunning = isFtpRunning,
                    ftpAddress = ftpAddress,
                    onToggleFtp = {
                        if (isFtpRunning) {
                            ftpServer.stop()
                            isFtpRunning = false
                        } else {
                            ftpServer.start()
                            isFtpRunning = true
                            // Just to get the simplified local IP from the log
                            ftpAddress = ftpServer.log.find { it.startsWith(Res.get(R.string.ftp_ativo)) }?.substringAfter(Res.get(R.string.ftp_ativo_em)) ?: Res.get(R.string.ftp_127_0_0_1_2221)
                        }
                    },
                    onNavigateLocal = { path -> openDirectory(LocalFileSystemProvider(), path) },
                    onNavigateFilter = { filter -> activeFilter = filter },
                    onNavigateRemote = { conn -> 
                        if (conn.protocol == Res.get(R.string.googledrive)) {
                            openDirectory(GoogleDriveProvider(context), "root")
                        } else {
                            openDirectory(RemoteFileSystemProvider(conn), conn.remotePath) 
                        }
                    },
                    onAddConnection = { showAddConnectionDialog = true },
                    onManageConnections = { /* TODO future */ },
                    onManagePlaylists = { /* TODO future */ }
                )
            } else if (activeFilter != null) {
                // MediaStore media viewing mode
                val filteredList = when (activeFilter) {
                    FileType.IMAGE -> mediaImages
                    FileType.VIDEO -> mediaVideos
                    FileType.AUDIO -> mediaAudio
                    FileType.DOCUMENT -> mediaDocs
                    else -> emptyList()
                }
                OwlBrowserTab(
                    currentPath = Res.get(R.string.midia_x_name, (activeFilter!!.name)),
                    files = filteredList,
                    isLoading = false,
                    error = null,
                    isGridView = isGridView,
                    onToggleGrid = { isGridView = !isGridView },
                    onFileClick = { file -> 
                        fileToView = file
                    },
                    onFileAction = { file, action ->
                        when (action.lowercase()) {
                            "cast" -> castTargetFile = file
                            else -> Toast.makeText(context, Res.get(R.string.acao_nao_suportada_aqui), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                OwlBrowserTab(
                    currentPath = currentPath,
                    files = browserFiles,
                    isLoading = isLoading,
                    error = errorMsg,
                    isGridView = isGridView,
                    onToggleGrid = { isGridView = !isGridView },
                    onFileClick = { file ->
                        if (file.type == FileType.DIRECTORY) {
                            openDirectory(currentProvider!!, file.path)
                        } else {
                            fileToView = file
                        }
                    },
                    onFileAction = { file, action ->
                        val actionLower = action.lowercase()
                        if ((actionLower == "delete" || actionLower == "rename") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = android.net.Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                                android.widget.Toast.makeText(context, Res.get(R.string.conceda_permissao_total_de_arquivos_e_te), android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            when(actionLower) {
                                "delete" -> {
                                    coroutineScope.launch {
                                        if (currentProvider!!.delete(file)) openDirectory(currentProvider!!, currentPath) // ARQ-R11
                                        else android.widget.Toast.makeText(context, Res.get(R.string.falha_ao_deletar), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "cast" -> castTargetFile = file
                                else -> android.widget.Toast.makeText(context, Res.get(R.string.acao_x_em_dev, action), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onNavigatePath = { path ->
                        openDirectory(currentProvider!!, path)
                    }
                )
            }
        }
    }
    
    if (showAddConnectionDialog) {
        AddConnectionDialog(
            connectionsList = connectionsList,
            onSaveConnections = { saveConnections(it) },
            onDismiss = { showAddConnectionDialog = false }
        )
    }

    if (castTargetFile != null) {
        val target = castTargetFile!!
        CastDevicePicker(
            discovery = castDiscovery,
            onDeviceSelected = { device ->
                coroutineScope.launch {
                    if (CastSessionManager.connect(context, device)) {
                        CastSessionManager.load(context, CastMedia.fromPath(target.path))
                        castTargetFile = null
                    }
                    // on failure the picker stays open for another try —
                    // lastError is toasted by the LaunchedEffect above
                }
            },
            onDismiss = { castTargetFile = null }
        )
    }

    // Gated on fileToView — while the dialog hosts MediaPlayerScreen its
    // own remote sheet is the one the user interacts with. Also held back
    // while the device picker is up so the picker stays on top.
    if (castState.session != null && !castRemoteDismissed &&
        fileToView == null && castTargetFile == null) {
        CastRemoteSheet(
            state = castState,
            onPlayPause = {
                coroutineScope.launch {
                    if (castState.isPlaying) CastSessionManager.pause() else CastSessionManager.play()
                }
            },
            onSeek = { ms -> coroutineScope.launch { CastSessionManager.seek(ms) } },
            onVolume = { level -> coroutineScope.launch { CastSessionManager.setVolume(level) } },
            onStop = { coroutineScope.launch { CastSessionManager.stop() } },
            onDisconnect = {
                castRemoteDismissed = true
                coroutineScope.launch { CastSessionManager.disconnect() }
            },
            onDismiss = { castRemoteDismissed = true }
        )
    }

    if (fileToView != null) {
        Dialog(
            onDismissRequest = { fileToView = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (fileToView!!.type == FileType.AUDIO || fileToView!!.type == FileType.VIDEO || fileToView!!.type == FileType.IMAGE) {
                    MediaPlayerScreen(
                        encodedPath = URLEncoder.encode(fileToView!!.path, "UTF-8"),
                        onBack = { fileToView = null }
                    )
                } else if (fileToView!!.type == FileType.PDF) {
                    PdfViewerScreen(
                        encodedPath = URLEncoder.encode(fileToView!!.path, "UTF-8"),
                        onBack = { fileToView = null }
                    )
                } else if (fileToView!!.path.lowercase(java.util.Locale.ROOT).endsWith(".epub")) {
                    EpubViewerScreen(
                        encodedPath = URLEncoder.encode(fileToView!!.path, "UTF-8"),
                        onBack = { fileToView = null }
                    )
                } else {
                    TextEditorScreen(
                        encodedPath = URLEncoder.encode(fileToView!!.path, "UTF-8"),
                        onBack = { fileToView = null }
                    )
                }
            }
        }
    }
}
