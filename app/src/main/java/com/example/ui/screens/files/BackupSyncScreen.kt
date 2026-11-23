package com.example.ui.screens.files
import com.example.ui.screens.system.CloudProvider
import com.example.ui.screens.system.CloudSyncManager
import com.example.ui.screens.system.CloudTokenStore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val kanbanDao = remember { database.kanbanDao() }
    val alarmDao = remember { database.smartAlarmDao() }
    val calendarPrefs = remember { context.getSharedPreferences("calendar_backup_prefs", Context.MODE_PRIVATE) }
    val historyDao = remember { database.historyDao() }

    // Backup & Restore state variables
    var backupJsonPreview by remember { mutableStateOf("") }
    var inputRestoreJson by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var currentBackupLocalPath by remember { mutableStateOf("") }

    // Toggle options for backup content selective matching
    var includeKanban by remember { mutableStateOf(true) }
    var includeAlarms by remember { mutableStateOf(true) }
    var includeCalendar by remember { mutableStateOf(true) }

    // Cloud sync state
    val tokenStore = remember { CloudTokenStore(context) }
    val syncLogList = remember { mutableStateListOf<String>() }
    var activeSyncProvider by remember { mutableStateOf<CloudProvider?>(null) }
    var connectedProviders by remember { mutableStateOf(CloudProvider.entries.filter { tokenStore.isConnected(it) }) }

    fun refreshConnected() { connectedProviders = CloudProvider.entries.filter { tokenStore.isConnected(it) } }

    val backupHistoryList by historyDao.getAllLogs().collectAsState(initial = emptyList())
    val filteredBackups = remember(backupHistoryList) {
        backupHistoryList.filter { it.toolName == "BACKUP_PAYLOAD" }
    }

    LaunchedEffect(Unit) {
        syncLogList.add(Res.get(R.string.sistema_console_de_sincronizacao_inicial))
        syncLogList.add(Res.get(R.string.info_providers_disponiveis_google_drive))
        syncLogList.add(Res.get(R.string.info_conectados_x, connectedProviders.map { it.label }.ifEmpty { listOf(Res.get(R.string.nenhum)) }.joinToString(", ")))
    }

    // Detects browser return after OAuth — polls every 1s for up to 60s
    LaunchedEffect(Unit) {
        repeat(60) {
            delay(1000)
            val updated = CloudProvider.entries.filter { tokenStore.isConnected(it) }
            if (updated != connectedProviders) {
                connectedProviders = updated
                val newlyConnected = updated - connectedProviders.toSet()
                newlyConnected.forEach { p -> syncLogList.add(Res.get(R.string.ok_x_conectado_sucesso, p.label)) }
            }
        }
    }

    // Export complete settings package to a JSON string
    suspend fun generateBackupJsonString(): String {
        val rootObj = JSONObject()
        rootObj.put("backup_version", 3)
        rootObj.put("timestamp", System.currentTimeMillis())
        rootObj.put("device_info", android.os.Build.MODEL)

        // 1. Kanban Columns & Notes
        if (includeKanban) {
            val columnsList = kanbanDao.getAllColumns().first()
            val notesList = kanbanDao.getAllNotes().first()

            val colsArray = JSONArray()
            for (col in columnsList) {
                colsArray.put(JSONObject().apply {
                    put("id", col.id)
                    put("name", col.name)
                    put("position", col.position)
                })
            }
            rootObj.put("kanban_columns", colsArray)

            val notesArray = JSONArray()
            for (note in notesList) {
                notesArray.put(JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("columnId", note.columnId)
                    put("position", note.position)
                    put("colorHex", note.colorHex)
                    put("timestamp", note.timestamp)
                })
            }
            rootObj.put("kanban_notes", notesArray)
        }

        // 2. Alarms
        if (includeAlarms) {
            val alarmsList = alarmDao.getAllAlarms().first()
            val alarmsArray = JSONArray()
            for (al in alarmsList) {
                alarmsArray.put(JSONObject().apply {
                    put("id", al.id)
                    put("hour", al.hour)
                    put("minute", al.minute)
                    put("isEnabled", al.isEnabled)
                    put("label", al.label)
                    put("soundName", al.soundName)
                    put("isGradualVolume", al.isGradualVolume)
                    put("snoozeMinutes", al.snoozeMinutes)
                    put("maxSnoozeCount", al.maxSnoozeCount)
                })
            }
            rootObj.put("smart_alarms", alarmsArray)
        }

        // 3. Calendar backup entries
        if (includeCalendar) {
            val calendarArray = JSONArray()
            val keys = calendarPrefs.all
            for ((key, value) in keys) {
                if (key.startsWith("evt_")) {
                    val parts = (value as? String)?.split("||") ?: emptyList()
                    if (parts.size >= 4) {
                        calendarArray.put(JSONObject().apply {
                            put("key", key)
                            put("title", parts[0])
                            put("description", parts[1])
                            put("dateString", parts[2])
                            put("timeString", parts[3])
                            put("calendarName", parts.getOrNull(4) ?: Res.get(R.string.calendario_offline))
                        })
                    }
                }
            }
            rootObj.put("calendar_events", calendarArray)
        }

        return rootObj.toString(4) // Formatted JSON indentation
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val dataJson = generateBackupJsonString()
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        outStream.write(dataJson.toByteArray())
                    }
                    historyDao.insertLog(HistoryLog(toolName = "BACKUP_PAYLOAD", resultSummary = dataJson, timestamp = System.currentTimeMillis()))
                    Toast.makeText(context, Res.get(R.string.backup_salvo_com_sucesso), Toast.LENGTH_LONG).show()
                    syncLogList.add(Res.get(R.string.sistema_backup_exportado_para_outro_luga))
                } catch (e: Exception) {
                    Toast.makeText(context, Res.get(R.string.erro_ao_salvar_backup_x, (e.message)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Apply JSON data to Local SQLite Room / Preferences databases
    suspend fun restoreBackupFromJson(jsonString: String, overwriteMode: Boolean): Boolean {
        return try {
            val root = JSONObject(jsonString)

            // 1. Kanban
            if (root.has("kanban_columns") && includeKanban) {
                if (overwriteMode) {
                    val cols = kanbanDao.getAllColumns().first()
                    for (c in cols) {
                        kanbanDao.deleteColumn(c.id)
                        kanbanDao.deleteNotesByColumn(c.id)
                    }
                }

                val colsArr = root.getJSONArray("kanban_columns")
                // Map of old column ID to new database inserted ID to retain mapping safely
                val colIdMapping = mutableMapOf<Long, Long>()

                for (i in 0 until colsArr.length()) {
                    val colObj = colsArr.getJSONObject(i)
                    val oldId = colObj.optLong("id", 0L)
                    val name = colObj.getString("name")
                    val pos = colObj.optInt("position", i)

                    val newId = kanbanDao.insertColumn(KanbanColumn(name = name, position = pos))
                    colIdMapping[oldId] = newId
                }

                if (root.has("kanban_notes")) {
                    val notesArr = root.getJSONArray("kanban_notes")
                    for (i in 0 until notesArr.length()) {
                        val noteObj = notesArr.getJSONObject(i)
                        val title = noteObj.getString("title")
                        val content = noteObj.getString("content")
                        val oldColId = noteObj.getLong("columnId")
                        val realColId = colIdMapping[oldColId] ?: colIdMapping.values.firstOrNull() ?: 1L
                        val pos = noteObj.optInt("position", 0)
                        val color = noteObj.optString("colorHex", "#FFF9C4")
                        val time = noteObj.optLong("timestamp", System.currentTimeMillis())

                        kanbanDao.insertNote(
                            KanbanNote(
                                title = title,
                                content = content,
                                columnId = realColId,
                                position = pos,
                                colorHex = color,
                                timestamp = time
                            )
                        )
                    }
                }
            }

            // 2. Alarms
            if (root.has("smart_alarms") && includeAlarms) {
                if (overwriteMode) {
                    val currentAlarms = alarmDao.getAllAlarms().first()
                    for (al in currentAlarms) {
                        alarmDao.deleteAlarm(al.id)
                    }
                }

                val alarmsArr = root.getJSONArray("smart_alarms")
                for (i in 0 until alarmsArr.length()) {
                    val alObj = alarmsArr.getJSONObject(i)
                    val hour = alObj.getInt("hour")
                    val min = alObj.getInt("minute")
                    val enabled = alObj.optBoolean("isEnabled", true)
                    val label = alObj.optString("label", "Alarme")
                    val sound = alObj.optString("soundName", Res.get(R.string.default_buzzer))
                    val gradual = alObj.optBoolean("isGradualVolume", true)
                    val snooze = alObj.optInt("snoozeMinutes", 5)
                    val maxSnooze = alObj.optInt("maxSnoozeCount", 3)

                    alarmDao.insertAlarm(
                        SmartAlarm(
                            hour = hour,
                            minute = min,
                            isEnabled = enabled,
                            label = label,
                            soundName = sound,
                            isGradualVolume = gradual,
                            snoozeMinutes = snooze,
                            maxSnoozeCount = maxSnooze
                        )
                    )
                }
            }

            // 3. Calendar backup events
            if (root.has("calendar_events") && includeCalendar) {
                val editor = calendarPrefs.edit()
                if (overwriteMode) {
                    editor.clear()
                }
                val calendarArr = root.getJSONArray("calendar_events")
                for (i in 0 until calendarArr.length()) {
                    val evObj = calendarArr.getJSONObject(i)
                    val key = evObj.getString("key")
                    val title = evObj.getString("title")
                    val desc = evObj.getString("description")
                    val dStr = evObj.getString("dateString")
                    val tStr = evObj.getString("timeString")
                    val calName = evObj.optString("calendarName", Res.get(R.string.calendario_offline))

                    val payload = "$title||$desc||$dStr||$tStr||$calName"
                    editor.putString(key, payload)
                }
                editor.apply()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.backup_integracao_drive)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section 1: Dynamic Status configuration selector checkbox
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, "Filtro", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Res.get(R.string.selecione_o_que_incluir_no_backup),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeKanban, onCheckedChange = { includeKanban = it })
                            Text(Res.get(R.string.quadro_kanban), fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeAlarms, onCheckedChange = { includeAlarms = it })
                            Text("Alarmes", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeCalendar, onCheckedChange = { includeCalendar = it })
                            Text("Agenda", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Section 2: Cloud Sync — multi-provider real
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, "Cloud", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Res.get(R.string.sincronizacao_em_nuvem), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        Res.get(R.string.conecte_sua_conta_em_um_ou_mais_servicos),
                        fontSize = 12.sp, color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CloudProvider.entries.forEach { provider ->
                        val isConnected = provider in connectedProviders
                        val isSyncing = activeSyncProvider == provider
                        val lastSync = tokenStore.getLastSync(provider)
                        val lastSyncStr = if (lastSync == 0L) "Nunca" else
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(lastSync))

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isConnected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(provider.iconEmoji, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(provider.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text(
                                                if (isConnected) Res.get(R.string.ultimo_sync_x, lastSyncStr) else Res.get(R.string.nao_conectado),
                                                fontSize = 11.sp,
                                                color = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isConnected) Color(0xFFC8E6C9) else Color(0xFFFFCDD2))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            if (isConnected) Res.get(R.string.connected).uppercase() else "OFFLINE",
                                            color = if (isConnected) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                            fontWeight = FontWeight.Bold, fontSize = 9.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!isConnected) {
                                        Button(
                                            onClick = {
                                                syncLogList.add(Res.get(R.string.auth_abrindo_login_x_no_browser, (provider.label)))
                                                CloudSyncManager.launchOAuth(context, provider, tokenStore)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Conectar", fontSize = 11.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                activeSyncProvider = provider
                                                coroutineScope.launch {
                                                    val json = generateBackupJsonString()
                                                    val ok = CloudSyncManager.uploadBackup(provider, json, tokenStore) { syncLogList.add(it) }
                                                    if (ok) {
                                                        historyDao.insertLog(HistoryLog(toolName = "BACKUP_PAYLOAD", resultSummary = json, timestamp = System.currentTimeMillis()))
                                                        Toast.makeText(context, Res.get(R.string.backup_enviado_para_x, (provider.label)), Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, Res.get(R.string.falha_no_upload_para_x, (provider.label)), Toast.LENGTH_SHORT).show()
                                                    }
                                                    activeSyncProvider = null
                                                }
                                            },
                                            enabled = !isSyncing,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            if (isSyncing) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isSyncing) Res.get(R.string.enviando) else Res.get(R.string.enviar), fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                activeSyncProvider = provider
                                                coroutineScope.launch {
                                                    val json = CloudSyncManager.downloadBackup(provider, tokenStore) { syncLogList.add(it) }
                                                    activeSyncProvider = null
                                                    if (json != null) {
                                                        inputRestoreJson = json
                                                        showRestoreDialog = true
                                                    } else {
                                                        Toast.makeText(context, Res.get(R.string.nenhum_backup_encontrado_no_x, (provider.label)), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            enabled = !isSyncing,
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restaurar", fontSize = 11.sp)
                                        }

                                        IconButton(
                                            onClick = {
                                                tokenStore.disconnect(provider)
                                                refreshConnected()
                                                syncLogList.add(Res.get(R.string.info_x_desconectado, (provider.label)))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.LinkOff, "Desconectar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Log console
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(Res.get(R.string.log_de_sincronizacao), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(syncLogList.toList().asReversed()) { logLine ->
                                Text(
                                    text = logLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = when {
                                        logLine.contains("[OK]") || logLine.contains("sucesso") || logLine.contains("success") -> Color(0xFF66BB6A)
                                        logLine.contains("[ERRO]") -> Color(0xFFEF5350)
                                        logLine.contains("[AUTH]") -> Color(0xFFFFD54F)
                                        logLine.contains("[INFO]") -> Color(0xFF4FC3F7)
                                        else -> Color.White
                                    },
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        Res.get(R.string.i_para_usar_registre_um_app_nas_consoles),
                        fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Section 3: Offline Physical Files / Local Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, Res.get(R.string.salvar_local), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Res.get(R.string.backup_local_manual_exportar_importar),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = Res.get(R.string.exporte_as_colecoes_diretamente_para_seu),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Generate Backup action button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val dataJson = generateBackupJsonString()
                                    backupJsonPreview = dataJson
                                         
                                    // Save physical file backup configuration to downloads
                                    try {
                                        val state = Environment.getExternalStorageState()
                                        val baseFolder = if (Environment.MEDIA_MOUNTED == state) {
                                            context.getExternalFilesDir(null)
                                        } else {
                                            context.filesDir
                                        }
                                        val file = File(baseFolder, "canivete_suico_backup.json")
                                        val out = FileOutputStream(file)
                                        out.write(dataJson.toByteArray())
                                        out.close()
                                        currentBackupLocalPath = file.absolutePath
                                        
                                        historyDao.insertLog(HistoryLog(id = 0, toolName = "BACKUP_PAYLOAD", resultSummary = dataJson, timestamp = System.currentTimeMillis()))
                                        syncLogList.add(Res.get(R.string.sistema_arquivo_fisico_gerado_em_x, (file.name)))
                                        Toast.makeText(context, Res.get(R.string.backup_json_criado_e_copiado), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, Res.get(R.string.armazenado_na_area_de_transferencia), Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    // Copy to clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(Res.get(R.string.backup_canivete_suico), dataJson)
                                    clipboard.setPrimaryClip(clip)
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Code, "JSON", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Res.get(R.string.exportar_json), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Save As / Download Button
                        Button(
                            onClick = {
                                val fileName = "canivete_suico_backup_${System.currentTimeMillis() / 1000}.json"
                                createDocumentLauncher.launch(fileName)
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Download, "Download", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Res.get(R.string.salvar_como), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Open Import dialog action button
                        OutlinedButton(
                            onClick = {
                                inputRestoreJson = ""
                                showRestoreDialog = true
                            },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Input, Res.get(R.string.importar_json), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Res.get(R.string.importar_json), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (backupJsonPreview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(Res.get(R.string.visualizacao_do_json_exportado), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.5.dp, Color.Gray, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = backupJsonPreview,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                        if (currentBackupLocalPath.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Res.get(R.string.fisicamente_salvo_em_x, currentBackupLocalPath),
                                fontSize = 9.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Backup Recording History section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, Res.get(R.string.historico), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Res.get(R.string.historico_de_gravacoes_x, (filteredBackups.size)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = Res.get(R.string.lista_de_backups_salvos_automaticamente),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredBackups.isEmpty()) {
                        Text(
                            Res.get(R.string.nenhum_historico_gravado_ainda_crie_um_b),
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        // Display list of local backup logs
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredBackups.forEach { entry ->
                                val dateStr = remember(entry.timestamp) {
                                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                                    sdf.format(java.util.Date(entry.timestamp))
                                }
                                val payloadSize = remember(entry.resultSummary) {
                                    entry.resultSummary.toByteArray().size
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = Res.get(R.string.backup_x, dateStr),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = Res.get(R.string.x_bytes_de_dados, payloadSize),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        historyDao.deleteLog(entry)
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = Res.get(R.string.excluir_registro),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    inputRestoreJson = entry.resultSummary
                                                    showRestoreDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Restore,
                                                    contentDescription = "Restaurar",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Restaurar", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // Modal dialogue to import JSON backup configuration details with clear merge strategy
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(Res.get(R.string.restaurar_configuracoes)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        Res.get(R.string.cole_o_texto_json_do_backup_ou_revise_a),
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = inputRestoreJson,
                        onValueChange = { inputRestoreJson = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = { Text(Res.get(R.string.conteudo_json_do_backup)) },
                        placeholder = { Text(Res.get(R.string.cole_o_json_do_backup_aqui)) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        Res.get(R.string.estrategia_de_importacao),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        Res.get(R.string.mesclar_junta_os_itens_da_copia_com_os_d),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Strategy 1: Merge
                    TextButton(
                        onClick = {
                            if (inputRestoreJson.trim().isNotEmpty()) {
                                coroutineScope.launch {
                                    val fine = restoreBackupFromJson(inputRestoreJson, overwriteMode = false)
                                    if (fine) {
                                        Toast.makeText(context, Res.get(R.string.dados_integrados_com_sucesso_mesclado), Toast.LENGTH_LONG).show()
                                        showRestoreDialog = false
                                        syncLogList.add(Res.get(R.string.sistema_importacao_concluida_com_estrate))
                                    } else {
                                        Toast.makeText(context, Res.get(R.string.erro_ao_analisar_o_json_do_backup_verifi), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("MESCLAR", fontWeight = FontWeight.Bold)
                    }

                    // Strategy 2: Overwrite
                    Button(
                        onClick = {
                            if (inputRestoreJson.trim().isNotEmpty()) {
                                coroutineScope.launch {
                                    val fine = restoreBackupFromJson(inputRestoreJson, overwriteMode = true)
                                    if (fine) {
                                        Toast.makeText(context, Res.get(R.string.bancos_limpos_e_sobrescritos_com_sucesso), Toast.LENGTH_LONG).show()
                                        showRestoreDialog = false
                                        syncLogList.add(Res.get(R.string.sistema_importacao_concluida_com_estrate_2))
                                    } else {
                                        Toast.makeText(context, Res.get(R.string.erro_ao_analisar_o_json_do_backup_verifi_2), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("SOBRESCREVER", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}
}
