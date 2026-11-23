package com.example.ui.screens.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.Res

private data class AppInfo(
    val packageName: String,
    val appName: String,
    val activities: List<ActivityInfo>
)

private data class ActivityInfo(
    val name: String,
    val exported: Boolean,
    val label: String?
)

private data class ShortcutRequest(
    val packageName: String,
    val activityName: String,
    val appName: String,
    val activityLabel: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLauncherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedApp by remember { mutableStateOf<String?>(null) }
    var shortcutDialog by remember { mutableStateOf<ShortcutRequest?>(null) }

    LaunchedEffect(Unit) {
        apps = loadApps(context)
        loading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.activity_launcher)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(Res.get(R.string.buscar_app_ou_pacote)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (searchQuery.isNotBlank()) Res.get(R.string.nenhum_app_encontrado)
                            else Res.get(R.string.nenhum_app_instalado),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppCard(
                                app = app,
                                isExpanded = expandedApp == app.packageName,
                                onExpand = { expandedApp = if (expandedApp == app.packageName) null else app.packageName },
                                onLaunchActivity = { activityName ->
                                    launchActivity(context, app.packageName, activityName) // DEV-R11
                                },
                                onRequestShortcut = { packageName, activityName ->
                                    val activityLabel = app.activities.find { it.name == activityName }?.label
                                    shortcutDialog = ShortcutRequest(
                                        packageName = packageName,
                                        activityName = activityName,
                                        appName = app.appName,
                                        activityLabel = activityLabel
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog to create shortcut
    shortcutDialog?.let { request ->
        AlertDialog(
            onDismissRequest = { shortcutDialog = null },
            title = {
                Text(Res.get(R.string.criar_atalho))
            },
            text = {
                Column {
                    Text(Res.get(R.string.app_x, (request.appName)))
                    Text(Res.get(R.string.package_x_2, (request.packageName)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(Res.get(R.string.activity_x, (request.activityName.substringAfterLast('.'))), fontWeight = FontWeight.Bold)
                    if (request.activityLabel != null) {
                        Text(Res.get(R.string.label_x, (request.activityLabel)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(Res.get(R.string.dica_teste_a_activity_antes_de_criar_o_a), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    createShortcut(context, request)
                    shortcutDialog = null
                }) {
                    Text(Res.get(R.string.criar_atalho_2))
                }
            },
            dismissButton = {
                TextButton(onClick = { shortcutDialog = null }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun AppCard(
    app: AppInfo,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onLaunchActivity: (String) -> Unit,
    onRequestShortcut: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onExpand() }
            ) {
                // DEV-R12 App icon placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        app.packageName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Res.get(R.string.x_activities, (app.activities.size)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (app.activities.isEmpty()) {
                    Text(
                        Res.get(R.string.nenhuma_activity_encontrada),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    app.activities.forEach { activity ->
                        ActivityRow(
                            activity = activity,
                            appPackageName = app.packageName,
                            appName = app.appName,
                            onLaunch = { onLaunchActivity(activity.name) },
                            onRequestShortcut = { onRequestShortcut(app.packageName, activity.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    activity: ActivityInfo,
    appPackageName: String,
    appName: String,
    onLaunch: () -> Unit,
    onRequestShortcut: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (activity.exported) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                activity.name.substringAfterLast('.'),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                activity.name,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (activity.exported) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Text(
                        "exported",
                        fontSize = 10.sp,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onLaunch,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Launch,
                    contentDescription = Res.get(R.string.lancar),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onRequestShortcut,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.AddToHomeScreen,
                    contentDescription = Res.get(R.string.criar_atalho_3),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val apps = mutableListOf<AppInfo>()

    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    for (pkg in packages) {
        try {
            val activities = mutableListOf<ActivityInfo>()
            val activitiesInfo = pm.getPackageInfo(
                pkg.packageName,
                PackageManager.GET_ACTIVITIES
            ).activities

            activitiesInfo?.forEach { act ->
                if (act.exported) {
                    activities.add(
                        ActivityInfo(
                            name = act.name,
                            exported = act.exported,
                            label = act.loadLabel(pm)?.toString()
                        )
                    )
                }
            }

            // Filtrar apps sem activities
            if (activities.isEmpty()) continue

            val appName = pkg.loadLabel(pm).toString()
            apps.add(
                AppInfo(
                    packageName = pkg.packageName,
                    appName = appName,
                    activities = activities
                )
            )
        } catch (e: Exception) {
            // Skip apps that can't be accessed
        }
    }

    return apps.sortedBy { it.appName.lowercase() }
}

private fun launchActivity(context: Context, packageName: String, activityName: String) {
    try {
        val intent = Intent().apply {
            setClassName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error
    }
}

private fun createShortcut(context: Context, request: ShortcutRequest) {
    try {
        val shortcutManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.getSystemService(android.content.pm.ShortcutManager::class.java)
        } else {
            null
        }

        if (shortcutManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Android 8.0+ usa ShortcutManager
            val intent = Intent().apply {
                setClassName(request.packageName, request.activityName)
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val shortcutInfo = android.content.pm.ShortcutInfo.Builder(context, Res.get(R.string.x_x_8, (request.packageName), (request.activityName)))
                .setShortLabel(request.activityLabel ?: request.activityName.substringAfterLast('.'))
                .setLongLabel(Res.get(R.string.x_x_request_activityname_substringafterl, (request.appName), (request.activityLabel ?: request.activityName.substringAfterLast('.'))))
                .setIcon(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_compass))
                .setIntent(intent)
                .build()

            shortcutManager.requestPinShortcut(shortcutInfo, null)
        } else {
            // Android < 8.0 usa Intent com ACTION_CREATE_SHORTCUT
            val shortcutIntent = Intent().apply {
                setClassName(request.packageName, request.activityName)
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, request.activityLabel ?: request.activityName.substringAfterLast('.'))
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, android.content.Intent.ShortcutIconResource.fromContext(context, android.R.drawable.ic_menu_compass))
            }

            context.sendBroadcast(addIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
