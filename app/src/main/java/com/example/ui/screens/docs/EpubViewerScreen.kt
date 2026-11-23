package com.example.ui.screens.docs

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.FolderOpen
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubViewerScreen(encodedPath: String, onBack: () -> Unit, onPickFile: (() -> Unit)? = null) {
    val filePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
    val file = File(filePath)
    
    var htmlChapters by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentChapterIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    
    // Parse the EPUB in background
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val zip = ZipFile(file)
                val chapters = mutableListOf<String>()
                
                // Very basic fallback parser: just find all html/xhtml files if OPF parsing fails
                // But let's try to be smart:
                var opfPath = ""
                val containerEntry = zip.getEntry("META-INF/container.xml")
                if (containerEntry != null) {
                    val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                    val match = Regex("full-path=\"([^\"]+)\"").find(containerXml)
                    if (match != null) opfPath = match.groupValues[1]
                }
                
                if (opfPath.isNotEmpty()) {
                    val opfEntry = zip.getEntry(opfPath)
                    if (opfEntry != null) {
                        val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
                        val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
                        
                        // Extract manifest items (id -> href)
                        val manifestRegex = Regex("<item[^>]+id=\"([^\"]+)\"[^>]+href=\"([^\"]+)\"")
                        val idToHref = mutableMapOf<String, String>()
                        manifestRegex.findAll(opfContent).forEach { m ->
                            idToHref[m.groupValues[1]] = m.groupValues[2]
                        }
                        
                        // Extract spine order
                        val spineRegex = Regex("<itemref[^>]+idref=\"([^\"]+)\"")
                        spineRegex.findAll(opfContent).forEach { m ->
                            val href = idToHref[m.groupValues[1]]
                            if (href != null && (href.endsWith(".html") || href.endsWith(".xhtml") || href.endsWith(".htm"))) {
                                chapters.add(basePath + href)
                            }
                        }
                    }
                }
                
                // Fallback: just list all HTMLs
                if (chapters.isEmpty()) {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.endsWith(".html") || e.name.endsWith(".xhtml")) {
                            chapters.add(e.name)
                        }
                    }
                    chapters.sort() // Rough sorting
                }
                
                if (chapters.isNotEmpty()) {
                    htmlChapters = chapters
                } else {
                    errorMsg = Res.get(R.string.nenhum_conteudo_legivel_encontrado_neste)
                }
            } catch (e: Exception) {
                errorMsg = Res.get(R.string.erro_ao_abrir_epub_x, (e.message))
            } finally {
                isLoading = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(file.name, maxLines = 1)
                        if (htmlChapters.isNotEmpty()) {
                            Text(Res.get(R.string.capitulo_x_1_de_x, (currentChapterIndex + 1), (htmlChapters.size)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (currentChapterIndex > 0) currentChapterIndex-- },
                        enabled = currentChapterIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.anterior))
                    }
                    IconButton(
                        onClick = { if (currentChapterIndex < htmlChapters.size - 1) currentChapterIndex++ },
                        enabled = currentChapterIndex < htmlChapters.size - 1
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = Res.get(R.string.proximo))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMsg != null) {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMsg!!, color = Color.Red)
                    if (onPickFile != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onPickFile) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(Res.get(R.string.abrir_pelo_gerenciador_de_arquivos))
                        }
                    }
                }
            } else if (htmlChapters.isNotEmpty()) {
                val currentEntryName = htmlChapters[currentChapterIndex]
                
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val url = request?.url?.toString() ?: return null
                                    // Intercept local assets (images/css) requested by the HTML inside the epub
                                    if (url.startsWith("epub://")) { // DOC-R03, DOC-R04
                                        val assetPath = url.substring("epub://".length)
                                        return try {
                                            val zip = ZipFile(file)
                                            val entry = zip.getEntry(assetPath)
                                            if (entry != null) {
                                                val mime = when {
                                                    assetPath.endsWith(".css") -> "text/css"
                                                    assetPath.endsWith(".png") -> "image/png"
                                                    assetPath.endsWith(".jpg") || assetPath.endsWith(".jpeg") -> "image/jpeg"
                                                    assetPath.endsWith(".svg") -> "image/svg+xml"
                                                    else -> "application/octet-stream"
                                                }
                                                // Read to byte array to avoid closing stream too early
                                                val bytes = zip.getInputStream(entry).readBytes()
                                                WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(bytes))
                                            } else null
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }
                        }
                    },
                    update = { webView ->
                        // Load the HTML content of the current chapter
                        try {
                            val zip = ZipFile(file)
                            val entry = zip.getEntry(currentEntryName)
                            if (entry != null) {
                                var htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                                
                                // Inject custom CSS for better mobile reading
                                val customCss = """
                                    <style>
                                        body { font-family: sans-serif; font-size: 18px; line-height: 1.6; padding: 16px; word-wrap: break-word; color: #333; }
                                        img { max-width: 100%; height: auto; }
                                    </style>
                                """.trimIndent()
                                
                                htmlContent = htmlContent.replace("</head>", "$customCss</head>", ignoreCase = true)
                                
                                // Rewrite internal asset links to our custom epub:// scheme
                                // Simple regex to replace src="..." and href="..."
                                val basePath = if (currentEntryName.contains("/")) currentEntryName.substringBeforeLast("/") + "/" else ""
                                htmlContent = htmlContent.replace(Regex("(src|href)=[\"']([^\"']+)[\"']")) { match ->
                                    val attr = match.groupValues[1]
                                    val link = match.groupValues[2]
                                    if (!link.startsWith("http") && !link.startsWith("data:")) {
                                        // Resolve relative path
                                        val resolvedPath = resolveRelativePath(basePath, link)
                                        "$attr=\"epub://$resolvedPath\""
                                    } else {
                                        match.value
                                    }
                                }
                                
                                webView.loadDataWithBaseURL("epub://", htmlContent, "text/html", "UTF-8", null)
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Helper to resolve relative paths inside ZIP (e.g., "OEBPS/Text/", "../Images/pic.jpg" -> "OEBPS/Images/pic.jpg")
private fun resolveRelativePath(base: String, rel: String): String {
    if (rel.startsWith("/")) return rel.substring(1)
    val parts = (base + rel).split("/")
    val resolved = mutableListOf<String>()
    for (p in parts) {
        if (p == "..") {
            if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
        } else if (p != "." && p.isNotEmpty()) {
            resolved.add(p)
        }
    }
    return resolved.joinToString("/")
}
