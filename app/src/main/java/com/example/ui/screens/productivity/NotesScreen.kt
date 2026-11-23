package com.example.ui.screens.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.KanbanNote
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).kanbanDao() } // PROD-R04, PROD-R07
    val notesFlow = remember { dao.getAllNotes() }
    val notes by notesFlow.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.bloco_de_notas_rapidas), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyanNeon,
                contentColor = DeepDark
            ) {
                Icon(Icons.Default.Add, contentDescription = Res.get(R.string.nova_nota))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
                .padding(16.dp)
        ) {
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        Res.get(R.string.nenhuma_nota_encontrada_toque_no_botao_p),
                        color = OnSurfaceMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkNavy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (note.title.isNotBlank()) {
                                        Text(note.title, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Text(note.content, color = OnSurfaceLight, fontSize = 14.sp)
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        dao.deleteNote(note.id)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.excluir_nota), tint = DangerRed)
                                }
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
            title = { Text(Res.get(R.string.nova_nota), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(Res.get(R.string.titulo_opcional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text(Res.get(R.string.conteudo_da_nota)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newContent.isNotBlank() || newTitle.isNotBlank()) {
                            coroutineScope.launch {
                                dao.insertNote(
                                    KanbanNote(
                                        title = newTitle.ifBlank { "Nota" },
                                        content = newContent,
                                        columnId = 0L,
                                        position = 0,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                newTitle = ""
                                newContent = ""
                                showAddDialog = false
                            }
                        }
                    }
                ) {
                    Text(Res.get(R.string.salvar), color = CyanNeon)
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
