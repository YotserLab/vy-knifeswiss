package com.example.ui.screens.media

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaManagerScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var files by remember { mutableStateOf(listOf<File>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val videoDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val downDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            
            val allFiles = (musicDir?.listFiles() ?: emptyArray()) + 
                           (videoDir?.listFiles() ?: emptyArray()) +
                           (downDir?.listFiles() ?: emptyArray())
            files = allFiles.filter { it.extension.lowercase() in listOf("mp3", "aac", "mp4", "avi", "jpg", "jpeg", "png", "webp", "gif") } // EXT-R11
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.gestor_de_midia)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(files) { file ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val ext = file.extension.lowercase()
                        val mimeType = when (ext) {
                            "mp3", "aac" -> "audio/*"
                            "mp4", "avi" -> "video/*"
                            else -> "image/*"
                        }
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ext = file.extension.lowercase()
                        val icon = when (ext) {
                            "mp3", "aac" -> Icons.Default.AudioFile
                            "mp4", "avi" -> Icons.Default.VideoFile
                            else -> Icons.Default.Image
                        }
                        Icon(icon, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
