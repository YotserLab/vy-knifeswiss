package com.example.ui.screens.files

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

data class AppItem(val name: String, val packageName: String, val apkPath: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppExtractorScreen(onAppSelected: (Uri, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appList = packages.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.map {
                AppItem(
                    name = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    apkPath = it.sourceDir // ARQ-R09
                )
            }.sortedBy { it.name }
            apps = appList
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.extrair_aplicativo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(apps) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.packageName) },
                        modifier = Modifier.clickable {
                            val file = File(app.apkPath)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            onAppSelected(uri, "${app.name}.apk")
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
