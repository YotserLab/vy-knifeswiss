package com.example.ui.screens.files

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

data class SyncTask(val id: String, val name: String, val path: String, var isEnabled: Boolean = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSynchronizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("sync_tasks_prefs", Context.MODE_PRIVATE) }

    fun loadTasks(): List<SyncTask> {
        val raw = sharedPrefs.getString("tasks_json", "[]") ?: "[]"
        val list = mutableListOf<SyncTask>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SyncTask(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Tarefa"),
                        path = obj.optString("path", "/sdcard"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTasks(tasks: List<SyncTask>) {
        val array = JSONArray()
        tasks.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("path", t.path)
                put("isEnabled", t.isEnabled)
            }
            array.put(obj)
        }
        sharedPrefs.edit().putString("tasks_json", array.toString()).apply()
    }

    var syncTasks by remember { mutableStateOf(loadTasks()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var taskName by remember { mutableStateOf("") }
    var taskPath by remember { mutableStateOf("/sdcard/Downloads") }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.sincronizacao_de_arquivos), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = CyanNeon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Res.get(R.string.adicionar_pasta_p_sincronizar), color = CyanNeon, fontWeight = FontWeight.Bold) // ARQ-R08
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (syncTasks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(Res.get(R.string.nenhuma_tarefa_de_sincronizacao_cadastra), color = OnSurfaceMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(syncTasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavy)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = CyanNeon)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.name, style = MaterialTheme.typography.titleMedium, color = OnSurfaceLight)
                                    Text(task.path, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                                }
                                Switch(
                                    checked = task.isEnabled,
                                    onCheckedChange = { checked ->
                                        val updated = syncTasks.map { if (it.id == task.id) it.copy(isEnabled = checked) else it }
                                        syncTasks = updated
                                        saveTasks(updated)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(Res.get(R.string.nova_tarefa_de_sincronizacao), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = taskName,
                        onValueChange = { taskName = it },
                        label = { Text(Res.get(R.string.nome_da_tarefa)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taskPath,
                        onValueChange = { taskPath = it },
                        label = { Text(Res.get(R.string.caminho_da_pasta)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (taskName.isNotBlank() && taskPath.isNotBlank()) {
                            val newTask = SyncTask(java.util.UUID.randomUUID().toString(), taskName, taskPath, true)
                            val updated = syncTasks + newTask
                            syncTasks = updated
                            saveTasks(updated)
                            taskName = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text(Res.get(R.string.adicionar), color = CyanNeon)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(Res.get(R.string.cancelar), color = OnSurfaceMuted)
                }
            },
            containerColor = DarkNavy
        )
    }
}
