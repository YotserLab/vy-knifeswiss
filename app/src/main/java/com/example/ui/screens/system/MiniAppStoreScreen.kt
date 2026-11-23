package com.example.ui.screens.system

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.components.CategoryHeader
import com.example.ui.miniapp.MiniApp
import com.example.ui.miniapp.MiniAppManager
import com.example.ui.miniapp.accentColorCompose
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppStoreScreen(navController: NavController) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(MiniAppManager.installedApps(context)) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tempFile = java.io.File.createTempFile("miniapp", ".vyp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val result = MiniAppManager.install(context, tempFile)
            result.onSuccess { app ->
                Toast.makeText(context, Res.get(R.string.x_instalado_2, (app.manifest.name)), Toast.LENGTH_SHORT).show()
                apps = MiniAppManager.installedApps(context)
            }.onFailure { e ->
                error = e.message
            }
        } finally {
            tempFile.delete()
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.mini_apps_2), color = OnSurfaceLight) },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Dashboard.route)
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = CyanNeon,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { picker.launch("*/*") },
                containerColor = CyanNeon,
                contentColor = DeepDark,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Instalar")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark)
                .padding(horizontal = 16.dp),
        ) {
            CategoryHeader(
                label = "INSTALADAS",
                accentColor = ElectricViolet,
                horizontalPadding = 0.dp,
            )

            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(Res.get(R.string.nenhuma_mini_app_instalada), color = OnSurfaceMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.id }) { app ->
                        MiniAppListItem(
                            app = app,
                            onOpen = { navController.navigate("miniapp/${app.id}") },
                            onUninstall = {
                                MiniAppManager.uninstall(context, app.id)
                                apps = MiniAppManager.installedApps(context)
                            },
                        )
                    }
                }
            }

            error?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Text(Res.get(R.string.erro_x_5, msg), color = DangerRed)
            }
        }
    }
}

@Composable
private fun MiniAppListItem(
    app: MiniApp,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = app.accentColorCompose(),
                )
                Text(
                    text = Res.get(R.string.x_x_14, (app.manifest.version), (app.manifest.author)),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                )
                if (app.manifest.description.isNotBlank()) {
                    Text(
                        text = app.manifest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceLight,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Default.Delete, contentDescription = "Desinstalar", tint = DangerRed)
            }
        }
    }
}
