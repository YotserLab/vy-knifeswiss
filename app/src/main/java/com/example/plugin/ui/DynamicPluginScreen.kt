package com.example.plugin.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.plugin.bridge.PluginJsRuntime
import com.example.plugin.bridge.VyPluginBridge
import com.example.plugin.core.DynamicWebPlugin
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepDark
import com.example.ui.theme.OnSurfaceLight
import com.example.ui.theme.OnSurfaceMuted
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DynamicPluginScreen(
    plugin: DynamicWebPlugin,
    navController: NavController
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableStateOf(0) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            navController.popBackStack()
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = plugin.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceLight
                        )
                        Text(
                            text = Res.get(R.string.plugin_vx_x, (plugin.manifest.version), (plugin.manifest.author)),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = CyanNeon
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recarregar",
                            tint = CyanNeon
                        )
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = Res.get(R.string.sobre_o_plugin),
                            tint = OnSurfaceMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
        ) {
            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = Res.get(R.string.erro_ao_carregar_o_plugin),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        errorMessage = null
                        webViewInstance?.reload()
                    }) {
                        Text(Res.get(R.string.tentar_novamente))
                    }
                }
            } else {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF0A0E17.toInt())

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = false
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                displayZoomControls = false
                                setSupportZoom(true)
                            }

                            val bridge = VyPluginBridge( // PLAT-R06
                                context = context,
                                pluginId = plugin.id,
                                webView = this,
                                onClose = { navController.popBackStack() }
                            )
                            addJavascriptInterface(bridge, Res.get(R.string.vybridge))

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    isLoading = newProgress < 100
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    view?.evaluateJavascript(PluginJsRuntime.BOOTSTRAP_JS, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(PluginJsRuntime.BOOTSTRAP_JS, null)
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        errorMessage = error?.description?.toString() ?: Res.get(R.string.falha_de_carregamento)
                                    }
                                }
                            }

                            val entryFile = plugin.entryPointFile
                            if (entryFile.exists()) {
                                loadUrl("file://${entryFile.absolutePath}")
                            } else {
                                errorMessage = Res.get(R.string.arquivo_de_entrada_x_nao_encontrado_em_x, (plugin.manifest.entryPoint), (plugin.pluginDir.name))
                            }

                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = CyanNeon,
                        trackColor = DeepDark,
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(plugin.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Res.get(R.string.id_x, (plugin.manifest.id)), style = MaterialTheme.typography.bodySmall)
                    Text(Res.get(R.string.versao_x_x, (plugin.manifest.version), (plugin.manifest.versionCode)), style = MaterialTheme.typography.bodySmall)
                    Text(Res.get(R.string.autor_x, (plugin.manifest.author)), style = MaterialTheme.typography.bodySmall)
                    Text(Res.get(R.string.categoria_x, (plugin.manifest.category.displayName)), style = MaterialTheme.typography.bodySmall)
                    if (plugin.manifest.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(plugin.manifest.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(Res.get(R.string.fechar))
                }
            }
        )
    }
}
