package com.example.ui.screens.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.Manifest
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.ui.miniapp.MiniAppManifest
import com.example.ui.miniapp.MiniAppManager
import com.example.ui.theme.*
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppRuntimeScreen(navController: NavController, appId: String) {
    val context = LocalContext.current
    val app = remember(appId) { MiniAppManager.installedApps(context).find { it.id == appId } }
    var canGoBack by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
        } else {
            pendingPermissionRequest?.deny()
        }
        pendingPermissionRequest = null
    }

    if (app == null) {
        Text(Res.get(R.string.mini_app_nao_encontrada), color = OnSurfaceLight, modifier = Modifier.padding(16.dp))
        return
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(app.manifest.name, color = OnSurfaceLight) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        },
    ) { padding ->
        val baseUrl = "https://miniapp.local/${app.id}/"

        AndroidView(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark),
            factory = {
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.databaseEnabled = false
                    settings.setGeolocationEnabled(false)
                    settings.mediaPlaybackRequiresUserGesture = false

                    addJavascriptInterface(MiniAppBridge(context, app.manifest), "VYBridge")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val url = request.url.toString()

                            // Strict Sandbox: only allow local origin https://miniapp.local/<appId>/
                            if (!url.startsWith(baseUrl)) {
                                // Check if host is in allowedDomains whitelist
                                val host = request.url.host ?: ""
                                val isAllowed = app.manifest.allowedDomains.any { allowed ->
                                    host.equals(allowed, ignoreCase = true) || host.endsWith(Res.get(R.string.x_18, allowed), ignoreCase = true)
                                }
                                if (!isAllowed) {
                                    android.util.Log.w("MiniAppSecurity", "Blocked unauthorized network request from ${app.id}: $url")
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        403,
                                        "Forbidden",
                                        mapOf("Access-Control-Allow-Origin" to "*"),
                                        ByteArrayInputStream(Res.get(R.string.acesso_a_rede_bloqueado_pela_politica_de).toByteArray(StandardCharsets.UTF_8))
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            val path = url.removePrefix(baseUrl).substringBefore("?").substringBefore("#")
                            val file = File(app.installPath, path.ifBlank { app.manifest.entryPoint })

                            // Canonical path traversal check
                            if (!file.exists() || file.isDirectory || !file.canonicalPath.startsWith(app.installPath.canonicalPath)) {
                                return WebResourceResponse("text/plain", "UTF-8", 404, Res.get(R.string.not_found), null, ByteArrayInputStream(Res.get(R.string.not_found).toByteArray()))
                            }

                            val mime = when (file.extension.lowercase()) {
                                "html" -> "text/html"
                                "js" -> "text/javascript"
                                "css" -> "text/css"
                                "svg" -> "image/svg+xml"
                                "png" -> "image/png"
                                "jpg", "jpeg" -> "image/jpeg"
                                else -> "application/octet-stream"
                            }

                            val headers = mutableMapOf(
                                "X-Content-Type-Options" to "nosniff",
                                "X-Frame-Options" to "DENY",
                                "Content-Security-Policy" to "default-src 'self' 'unsafe-inline' data:; connect-src 'self' ${app.manifest.allowedDomains.joinToString(" ")}"
                            )

                            return WebResourceResponse(mime, "UTF-8", 200, "OK", headers, file.inputStream())
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                if (app.manifest.permissions.contains("audio")) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        request.grant(request.resources)
                                    } else {
                                        pendingPermissionRequest = request
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    android.util.Log.w("MiniAppSecurity", "App ${app.id} requested microphone but does not have 'audio' permission in manifest.")
                                    request.deny()
                                }
                            } else {
                                request.deny()
                            }
                        }
                    }

                    loadUrl(baseUrl)
                }
            },
            update = {},
        )
    }
}

/**
 * Hardened JavaScript bridge with strict permission checks.
 */
class MiniAppBridge(
    private val context: Context,
    private val manifest: MiniAppManifest
) {

    private fun hasPermission(permission: String): Boolean {
        return manifest.permissions.contains(permission)
    }

    private fun permissionDenied(permission: String): String {
        val msg = Res.get(R.string.permissao_negada_x_nao_declarada_no_mani, permission)
        android.util.Log.e("MiniAppSecurity", "[${manifest.id}] $msg")
        return org.json.JSONObject().apply {
            put("error", "PERMISSION_DENIED")
            put("message", msg)
            put("permission", permission)
        }.toString()
    }

    @JavascriptInterface
    fun getManifest(): String {
        return org.json.JSONObject().apply {
            put("id", manifest.id)
            put("name", manifest.name)
            put("version", manifest.version)
            put("author", manifest.author)
            put("description", manifest.description)
            put("category", manifest.category)
            put("accentColor", manifest.accentColor)
            put("entryPoint", manifest.entryPoint)
            put("permissions", org.json.JSONArray(manifest.permissions))
            put("allowedDomains", org.json.JSONArray(manifest.allowedDomains))
        }.toString()
    }

    @JavascriptInterface
    fun log(message: String) {
        if (!hasPermission("log")) return
        android.util.Log.d("MiniApp:${manifest.id}", message.take(1000))
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        if (!hasPermission("deviceInfo")) {
            return permissionDenied("deviceInfo")
        }
        return org.json.JSONObject().apply {
            put("model", Build.MODEL ?: "Unknown")
            put("manufacturer", Build.MANUFACTURER ?: "Unknown")
            put("android", Build.VERSION.RELEASE ?: "?")
            put("sdk", Build.VERSION.SDK_INT)
        }.toString()
    }

    @JavascriptInterface
    fun toast(message: String) {
        if (!hasPermission("ui") && !hasPermission("toast")) {
            android.util.Log.w("MiniAppSecurity", Res.get(R.string.x_toast_bloqueado_por_falta_de_permissao, (manifest.id)))
            return
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message.take(120), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        if (!hasPermission("vibrate")) {
            android.util.Log.w("MiniAppSecurity", Res.get(R.string.x_vibracao_bloqueada_por_falta_de_permis, (manifest.id)))
            return
        }
        val safeDuration = durationMs.coerceIn(10L, 500L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(safeDuration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(safeDuration)
            }
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun checkPermission(permission: String): Boolean {
        return hasPermission(permission)
    }
}
