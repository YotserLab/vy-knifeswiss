package com.example.ui.screens.docs

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.FolderOpen
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(encodedPath /* DOC-R07 */: String, onBack: () -> Unit, onPickFile: (() -> Unit)? = null) {
    val filePath = remember(encodedPath) {
        if (encodedPath.isEmpty()) "" else runCatching { URLDecoder.decode(encodedPath, "UTF-8") }.getOrElse { encodedPath }
    }
    
    val file = remember(filePath) { File(filePath) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableStateOf(0) }

    // Transform (Zoom/Pan)
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    DisposableEffect(file) {
        if (file.exists() && file.extension.lowercase() == "pdf") {
            try {
                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer!!.pageCount
            } catch (e: Exception) {
                errorMessage = Res.get(R.string.erro_ao_carregar_pdf_x, (e.message))
            }
        } else {
            errorMessage = Res.get(R.string.arquivo_nao_encontrado_ou_nao_e_pdf)
        }

        onDispose {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { fileDescriptor?.close() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0))) {
        // Toolbar
        TopAppBar(
            title = { Text(file.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    if (onPickFile != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onPickFile) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(Res.get(R.string.abrir_pelo_gerenciador_de_arquivos))
                        }
                    }
                }
            }
        } else if (pdfRenderer != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x * scale
                                offsetY += pan.y * scale
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(pageCount) { index ->
                        PdfPageRenderer(pdfRenderer = pdfRenderer!!, pageIndex = index)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageRenderer(pdfRenderer: PdfRenderer, pageIndex: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            var page: PdfRenderer.Page? = null
            try {
                // DOC-R02 Must synchronize as PdfRenderer is not thread-safe for multiple pages simultaneously
                synchronized(pdfRenderer) {
                    page = pdfRenderer.openPage(pageIndex)
                    // Render at 2x resolution for better clarity
                    val width = page!!.width * 2
                    val height = page!!.height * 2
                    val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Fill background white
                    val canvas = android.graphics.Canvas(bm)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(bm, 0f, 0f, null)
                    
                    page!!.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) // DOC-R01
                    bitmap = bm
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { page?.close() } catch (_: Exception) {}
            }
        }
    }

    if (bitmap != null) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = Res.get(R.string.pagina_x_1, (pageIndex + 1)),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(0.7f)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
