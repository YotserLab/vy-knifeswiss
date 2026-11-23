package com.example.ui.screens.productivity

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.KanbanColumn
import com.example.data.KanbanNote
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuickNotesKanbanScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val kanbanDao = remember { database.kanbanDao() }

    // Observe Columns and Notes from Room db
    val columns by kanbanDao.getAllColumns().collectAsState(initial = emptyList())
    val notes by kanbanDao.getAllNotes().collectAsState(initial = emptyList())

    // Tab state (0 = NOTAS, 1 = KANBAN)
    var selectedTab by remember { mutableStateOf(1) } // default KANBAN to match screenshot
    val tabTitles = listOf("NOTAS", "KANBAN")

    // Dialog state
    var showAddColumnDialog by remember { mutableStateOf(false) }
    var newColumnName by remember { mutableStateOf("") }
    var showEditColumnDialog by remember { mutableStateOf<KanbanColumn?>(null) }
    var editColumnName by remember { mutableStateOf("") }

    var showEditNoteDialog by remember { mutableStateOf<KanbanNote?>(null) }
    var editNoteTitle by remember { mutableStateOf("") }
    var editNoteContent by remember { mutableStateOf("") }
    var editNoteColor by remember { mutableStateOf("#00D4FF") }
    var editNoteColId by remember { mutableStateOf(0L) }

    // Quick add note input
    var quickNotesText by remember { mutableStateOf("") }

    val priorityColors = listOf(
        "#EF4444", // High (Red)
        "#F59E0B", // Medium (Yellow)
        "#10B981", // Low (Green)
        "#00D4FF", // Info (Cyan)
        "#7C3AED"  // System (Purple)
    )

    // Seed default columns if db is empty
    LaunchedEffect(columns) {
        if (columns.isEmpty()) {
            coroutineScope.launch {
                kanbanDao.insertColumn(KanbanColumn(name = Res.get(R.string.a_fazer), position = 0))
                kanbanDao.insertColumn(KanbanColumn(name = Res.get(R.string.em_progresso), position = 1))
                kanbanDao.insertColumn(KanbanColumn(name = Res.get(R.string.concluido), position = 2))
            }
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.notes_kanban),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = Res.get(R.string.buscar), tint = CyanNeon)
                    }
                    IconButton(onClick = { showAddColumnDialog = true }) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filtrar", tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (columns.isNotEmpty()) {
                        coroutineScope.launch {
                            kanbanDao.insertNote(
                                KanbanNote(
                                    title = Res.get(R.string.nova_tarefa),
                                    content = Res.get(R.string.escreva_o_conteudo_da_tarefa_aqui),
                                    columnId = columns.first().id,
                                    colorHex = priorityColors.random()
                                )
                            )
                        }
                    }
                },
                containerColor = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CyanNeon, Color(0xFF0099FF))
                        )
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = Res.get(R.string.adicionar_nota), tint = DeepDark, modifier = Modifier.size(24.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DeepDark)
        ) {
            // custom tab bar (NOTAS / KANBAN)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF111827),
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CyanNeon
                        )
                    }
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) CyanNeon else OnSurfaceMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Note Bar at the top of content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111827))
                    .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickNotesText,
                        onValueChange = { quickNotesText = it },
                        placeholder = { Text(Res.get(R.string.escreva_uma_anotacao_rapida), fontSize = 12.sp, color = OnSurfaceMuted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF1E2D4A),
                            focusedContainerColor = Color(0xFF1A2035),
                            unfocusedContainerColor = Color(0xFF1A2035),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CyanNeon
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (quickNotesText.trim().isNotEmpty() && columns.isNotEmpty()) {
                                coroutineScope.launch {
                                    kanbanDao.insertNote(
                                        KanbanNote(
                                            title = Res.get(R.string.nota_rapida),
                                            content = quickNotesText,
                                            columnId = columns.first().id,
                                            colorHex = "#00D4FF"
                                        )
                                    )
                                    quickNotesText = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyanNeon)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = DeepDark, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main tabs box switcher
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 1) {
                    // KANBAN VIEW
                    if (columns.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(columns) { column ->
                                val colNotes = notes.filter { it.columnId == column.id }
                                KanbanColumnStitch(
                                    column = column,
                                    notes = colNotes,
                                    allColumns = columns,
                                    onEditColumn = {
                                        editColumnName = column.name
                                        showEditColumnDialog = column
                                    },
                                    onDeleteColumn = {
                                        coroutineScope.launch {
                                            kanbanDao.deleteColumn(column.id)
                                            kanbanDao.deleteNotesByColumn(column.id)
                                        }
                                    },
                                    onDeleteNote = { noteId ->
                                        coroutineScope.launch { kanbanDao.deleteNote(noteId) }
                                    },
                                    onEditNote = { note ->
                                        editNoteTitle = note.title
                                        editNoteContent = note.content
                                        editNoteColor = note.colorHex
                                        editNoteColId = note.columnId
                                        showEditNoteDialog = note
                                    },
                                    onMoveNote = { note, targetColId ->
                                        coroutineScope.launch {
                                            kanbanDao.updateNote(note.copy(columnId = targetColId))
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CyanNeon)
                        }
                    }
                } else {
                    // GRID NOTAS VIEW
                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(Res.get(R.string.nenhuma_nota_salva), color = OnSurfaceMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notes) { note ->
                                NoteGridItem(
                                    note = note,
                                    onClick = {
                                        editNoteTitle = note.title
                                        editNoteContent = note.content
                                        editNoteColor = note.colorHex
                                        editNoteColId = note.columnId
                                        showEditNoteDialog = note
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Edit Note Dialog
    showEditNoteDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { showEditNoteDialog = null },
            title = { Text(Res.get(R.string.editar_nota), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editNoteTitle,
                        onValueChange = { editNoteTitle = it },
                        label = { Text(Res.get(R.string.titulo_2)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNoteContent,
                        onValueChange = { editNoteContent = it },
                        label = { Text(Res.get(R.string.conteudo)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    
                    // Priority Color Selector
                    Text(Res.get(R.string.prioridade_marcador), color = OnSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        priorityColors.forEach { colorStr ->
                            val colorVal = Color(android.graphics.Color.parseColor(colorStr))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .border(
                                        2.dp,
                                        if (editNoteColor == colorStr) Color.White else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { editNoteColor = colorStr }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            kanbanDao.updateNote(
                                note.copy(
                                    title = editNoteTitle,
                                    content = editNoteContent,
                                    colorHex = editNoteColor
                                )
                            )
                            showEditNoteDialog = null
                        }
                    }
                ) { Text(Res.get(R.string.salvar), color = CyanNeon) }
            },
            dismissButton = {
                TextButton(onClick = { showEditNoteDialog = null }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }

    // Modal Add Column Dialog
    if (showAddColumnDialog) {
        AlertDialog(
            onDismissRequest = { showAddColumnDialog = false },
            title = { Text(Res.get(R.string.nova_coluna_kanban), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newColumnName,
                    onValueChange = { newColumnName = it },
                    label = { Text(Res.get(R.string.nome_da_coluna)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newColumnName.trim().isNotEmpty()) {
                            coroutineScope.launch {
                                kanbanDao.insertColumn(
                                    KanbanColumn(
                                        name = newColumnName.trim(),
                                        position = columns.size
                                    )
                                )
                                newColumnName = ""
                                showAddColumnDialog = false
                            }
                        }
                    }
                ) { Text("Criar", color = CyanNeon) }
            },
            dismissButton = {
                TextButton(onClick = { showAddColumnDialog = false }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }
}

@Composable
private fun KanbanColumnStitch(
    column: KanbanColumn,
    notes: List<KanbanNote>,
    allColumns: List<KanbanColumn>,
    onEditColumn: () -> Unit,
    onDeleteColumn: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onEditNote: (KanbanNote) -> Unit,
    onMoveNote: (KanbanNote, Long) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
    ) {
        // Column header with Stitch look
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(CyanNeon)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = column.name.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1E2D4A))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = notes.size.toString(),
                        color = CyanNeon,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row {
                IconButton(onClick = onEditColumn, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDeleteColumn, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cards list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes) { note ->
                KanbanCardStitch(
                    note = note,
                    allColumns = allColumns,
                    onDelete = { onDeleteNote(note.id) },
                    onEdit = { onEditNote(note) },
                    onMove = { colId -> onMoveNote(note, colId) }
                )
            }
        }
    }
}

@Composable
private fun KanbanCardStitch(
    note: KanbanNote,
    allColumns: List<KanbanColumn>,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Long) -> Unit
) {
    var showMoveMenu by remember { mutableStateOf(false) }
    
    val markerColor = remember(note.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex))
        } catch (e: Exception) {
            CyanNeon
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(8.dp))
            .clickable { onEdit() }
            .padding(12.dp)
    ) {
        // Border accent marker
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .background(markerColor)
        )

        Column(modifier = Modifier.padding(start = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    Box {
                        IconButton(onClick = { showMoveMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Mover", tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showMoveMenu,
                            onDismissRequest = { showMoveMenu = false },
                            modifier = Modifier.background(DarkNavyVariant).border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        ) {
                            allColumns.forEach { col ->
                                if (col.id != note.columnId) {
                                    DropdownMenuItem(
                                        text = { Text(col.name, fontSize = 12.sp, color = Color.White) },
                                        onClick = {
                                            onMove(col.id)
                                            showMoveMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = Res.get(R.string.excluir), tint = DangerRed, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = note.content,
                color = OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Chips mock subtasks & tags to look identical to Stitch
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtasks chip
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1E2D4A))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(Res.get(R.string.n_2_5_subtasks), color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Tag chip
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CyanNeon.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("infra", color = CyanNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NoteGridItem(note: KanbanNote, onClick: () -> Unit) {
    val borderVal = remember(note.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(note.colorHex))
        } catch (e: Exception) {
            CyanNeon
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827))
            .border(1.dp, borderVal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = note.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.content,
                color = OnSurfaceMuted,
                fontSize = 12.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
