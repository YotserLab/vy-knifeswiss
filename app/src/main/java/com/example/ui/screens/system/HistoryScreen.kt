package com.example.ui.screens.system

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.AppDatabase
import com.example.data.HistoryLog
import com.example.data.HistoryRepository
import com.example.ui.components.AppTopAppBar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val repository = remember { HistoryRepository(dao) }
    
    val logs by repository.allLogs.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = Res.get(R.string.audit_history_pdf),
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch { repository.clear() } // PLAT-R09
                        Toast.makeText(context, Res.get(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = Res.get(R.string.clear_history))
                    }
                    IconButton(onClick = {
                        exportPdf(context, logs)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = Res.get(R.string.export_pdf))
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(Res.get(R.string.no_entries_found))
            }
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.padding(16.dp)) {
                items(logs) { log ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            val dateStr = sdf.format(Date(log.timestamp))
                            
                            Text(log.toolName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(dateStr, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(log.resultSummary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

fun exportPdf(context: Context, logs: List<HistoryLog>) {
    try {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        paint.textSize = 14f

        var yPos = 50f
        canvas.drawText(Res.get(R.string.network_toolbox_audit_report), 50f, yPos, paint)
        yPos += 30f
        paint.textSize = 12f

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        for (log in logs) {
            val dateStr = sdf.format(Date(log.timestamp))
            canvas.drawText(Res.get(R.string.x_x_11, (log.toolName), dateStr), 50f, yPos, paint)
            yPos += 20f
            // Split long summaries into basic lines
            val chunks = log.resultSummary.chunked(70)
            for (chunk in chunks) {
                canvas.drawText(chunk, 50f, yPos, paint)
                yPos += 20f
            }
            yPos += 10f

            if (yPos > 800) {
                pdfDocument.finishPage(page)
                pageNumber++
                val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                yPos = 50f
            }
        }
        
        pdfDocument.finishPage(page)

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "NetworkAuditReport.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        
        Toast.makeText(context, Res.get(R.string.pdf_saved_to_documents_folder), Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        Toast.makeText(context, Res.get(R.string.failed_to_export_pdf_x, (e.message)), Toast.LENGTH_SHORT).show()
    }
}
