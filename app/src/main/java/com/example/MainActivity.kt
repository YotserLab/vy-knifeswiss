package com.example
import com.example.ui.screens.files.BackupSyncScreen
import com.example.ui.screens.system.AboutScreen
import com.example.ui.screens.system.CloudProvider
import com.example.ui.screens.system.CloudSyncManager
import com.example.ui.screens.system.CloudTokenStore
import com.example.ui.screens.system.DashboardScreen
import com.example.ui.screens.system.MiniAppRuntimeScreen
import com.example.ui.screens.system.MiniAppStoreScreen
import com.example.ui.screens.system.SettingsScreen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.plugin.manager.PluginManager
import com.example.plugin.registry.NativePluginRegistry
import com.example.plugin.ui.PluginManagerScreen
import com.example.service.LocalChatService
import com.example.ui.model.ToolItem
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.components.FloatingBubbleOverlay
import com.example.ui.screens.chat.LocalChatRepository
import com.example.ui.screens.chat.MeshInfoScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Shared state for OAuth callback — observed by BackupSyncScreen
object OAuthCallbackState {
    var pendingCode: String? = null
    var pendingProvider: CloudProvider? = null
}

var pendingNavigateToChat by mutableStateOf(false)
var pendingRoute by mutableStateOf<String?>(null)

object SharedContentState {
    var pendingSharedText by mutableStateOf<String?>(null)
    var pendingSharedUris by mutableStateOf<List<Uri>?>(null)
}

class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
        handleSharedIntent(intent)
        handleClipboardIntent(intent)
        handleRouteIntent(intent)
    }

    private fun handleRouteIntent(intent: Intent?) {
        val extraRoute = intent?.getStringExtra("route")
        if (!extraRoute.isNullOrEmpty()) {
            pendingRoute = extraRoute
            return
        }
        val data = intent?.data
        if (data?.scheme == "vyknifeswiss" && data.host == "tool") { // PLAT-R01
            val toolRoute = data.path?.removePrefix("/")
            if (!toolRoute.isNullOrEmpty()) {
                pendingRoute = toolRoute
            }
        }
    }

    private fun handleClipboardIntent(intent: Intent?) {
        val text = intent?.getStringExtra("CLIPBOARD_SYNC_TEXT")
        if (!text.isNullOrEmpty()) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(Res.get(R.string.copied_via_local_chat), text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, Res.get(R.string.texto_copiado_para_a_area_de_transferenc), android.widget.Toast.LENGTH_SHORT).show()
            intent.removeExtra("CLIPBOARD_SYNC_TEXT")
        }
    }

    private fun handleOAuthIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data?.scheme == "vyknifeswiss" && data.host == "oauth") { // PLAT-R02
            val code = data.getQueryParameter("code") ?: return
            val state = data.getQueryParameter("state") ?: ""
            val provider = when {
                state.contains("gdrive", ignoreCase = true) -> CloudProvider.GDRIVE
                state.contains("onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
                state.contains("dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
                state.contains("pcloud", ignoreCase = true) -> CloudProvider.PCLOUD
                else -> {
                    // Try to detect by last launched provider stored
                    val tokenStore = CloudTokenStore(this)
                    CloudProvider.entries.firstOrNull { tokenStore.getPkceVerifier(it) != null }
                }
            } ?: return

            OAuthCallbackState.pendingCode = code
            OAuthCallbackState.pendingProvider = provider

            val tokenStore = CloudTokenStore(this)
            CoroutineScope(Dispatchers.IO).launch {
                CloudSyncManager.handleOAuthCallback(provider, code, tokenStore) { log ->
                    android.util.Log.d("OAuthCallback", log)
                }
                OAuthCallbackState.pendingCode = null
                OAuthCallbackState.pendingProvider = null
            }
        }
    }

    private fun handleSharedIntent(intent: Intent) {
        val urisToShare = mutableListOf<Uri>()
        var sharedText: String? = null
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    sharedText = text
                }
                
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    urisToShare.add(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uris != null && uris.isNotEmpty()) {
                    urisToShare.addAll(uris)
                }
            }
        }
        if (sharedText != null) {
            SharedContentState.pendingSharedText = sharedText
        }
        if (urisToShare.isNotEmpty()) {
            SharedContentState.pendingSharedUris = urisToShare
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSharedIntent(intent)
        handleClipboardIntent(intent)
        handleRouteIntent(intent)

        // Initialize plugin system & register all native plugins
        NativePluginRegistry.registerAll()
        PluginManager.init(this)
        
        // Start chat service for background message reception if the user enabled it
        if (LocalChatService.isServiceEnabled(this)) {
            try {
                val intent = Intent(this, LocalChatService::class.java).apply {
                    action = LocalChatService.ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                val currentRoute by remember {
                    derivedStateOf {
                        navController.currentBackStackEntry?.destination?.route
                    }
                }
                
                val sharedText = SharedContentState.pendingSharedText
                val sharedUris = SharedContentState.pendingSharedUris
                if (sharedText != null || sharedUris != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { 
                            SharedContentState.pendingSharedText = null
                            SharedContentState.pendingSharedUris = null
                        },
                        title = { Text(Res.get(R.string.compartilhar_com_vyswissknife)) },
                        text = { Text(Res.get(R.string.onde_voce_deseja_salvar_ou_enviar_este_c)) },
                        confirmButton = { },
                        dismissButton = {
                            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.material3.Button(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    onClick = {
                                        if (sharedText != null) LocalChatRepository.pendingSharedText.value = sharedText
                                        if (sharedUris != null) LocalChatRepository.pendingSharedUris.value = sharedUris
                                        SharedContentState.pendingSharedText = null
                                        SharedContentState.pendingSharedUris = null
                                        navController.navigate(Screen.LocalChat.route)
                                    }
                                ) { Text(Res.get(R.string.local_chat)) }
                                
                                if (sharedText != null) {
                                    androidx.compose.material3.Button(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        onClick = {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                com.example.data.AppDatabase.getDatabase(context).kanbanDao().insertNote(
                                                    com.example.data.KanbanNote(title = "Compartilhado", content = sharedText, columnId = 1L)
                                                )
                                            }
                                            SharedContentState.pendingSharedText = null
                                            SharedContentState.pendingSharedUris = null
                                            navController.navigate(Screen.Notes.route)
                                        }
                                    ) { Text(Res.get(R.string.salvar_em_anotacoes)) }
                                    
                                    androidx.compose.material3.Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                com.example.data.AppDatabase.getDatabase(context).historyDao().insertLog(
                                                    com.example.data.HistoryLog(
                                                        toolName = "Compartilhamento",
                                                        resultSummary = sharedText,
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                            SharedContentState.pendingSharedText = null
                                            SharedContentState.pendingSharedUris = null
                                            navController.navigate(Screen.History.route)
                                        }
                                    ) { Text(Res.get(R.string.salvar_no_historico)) }
                                }
                            }
                        }
                    )
                }
                
                LaunchedEffect(pendingNavigateToChat) {
                    if (pendingNavigateToChat) {
                        navController.navigate(Screen.LocalChat.route)
                        pendingNavigateToChat = false
                    }
                }

                LaunchedEffect(pendingRoute) {
                    val target = pendingRoute
                    if (target != null) {
                        try {
                            navController.navigate(target)
                        } catch (e: Throwable) {
                            android.util.Log.e("MainActivity", "Failed to navigate to $target", e)
                        }
                        pendingRoute = null
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box {
                        NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
                            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
                            composable(Screen.Settings.route) { SettingsScreen { navController.popBackStack() } }
                            composable(Screen.About.route) { AboutScreen(navController) }
                            composable(Screen.PluginManager.route) { PluginManagerScreen(navController) }
                            composable(Screen.MiniAppStore.route) { MiniAppStoreScreen(navController) }
                            composable(
                                Screen.MiniAppRuntime.route,
                                arguments = listOf(navArgument("appId") { type = NavType.StringType })
                            ) { backStack ->
                                val appId = backStack.arguments?.getString("appId") ?: ""
                                MiniAppRuntimeScreen(navController, appId)
                            }

                            // Dynamic registration of all plugins (native + dynamic)
                            PluginManager.registerAllNavigation(this, navController)
                        }

                        // Floating bubble for chat notifications
                        if (currentRoute != Screen.LocalChat.route && LocalChatRepository.unreadCount.value > 0) {
                            FloatingBubbleOverlay(
                                unreadCount = LocalChatRepository.unreadCount.value,
                                onClick = { navController.navigate(Screen.LocalChat.route) }
                            )
                        }
                    }
                }
            }
        }
    }
}
