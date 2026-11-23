package com.example.ui.screens.productivity

import androidx.compose.foundation.clickable
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.ShoppingItem
import com.example.data.ShoppingList
import com.example.ui.components.AppTopAppBar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.R
import com.example.Res

// ViewModel
class ShoppingViewModel : ViewModel() {
    private var dao: com.example.data.ShoppingDao? = null
    
    val allLists = MutableStateFlow<List<ShoppingList>>(emptyList())
    val topItems = MutableStateFlow<List<String>>(emptyList())
    
    fun initDao(context: android.content.Context) {
        if (dao == null) {
            dao = AppDatabase.getDatabase(context).shoppingDao()
            viewModelScope.launch {
                dao?.getAllLists()?.collect { allLists.value = it }
            }
            viewModelScope.launch {
                dao?.getTopSuggestedItems()?.collect { topItems.value = it }
            }
        }
    }
    
    fun createList(title: String, onCreate: (Long) -> Unit) {
        viewModelScope.launch {
            val id = dao?.insertList(ShoppingList(title = title)) ?: 0
            onCreate(id)
        }
    }
    
    fun deleteList(list: ShoppingList) {
        viewModelScope.launch { dao?.deleteList(list) }
    }
    
    fun duplicateList(oldListId: Long, newTitle: String, selectedItemNames: List<String>, onCreate: (Long) -> Unit) {
        viewModelScope.launch {
            val newListId = dao?.insertList(ShoppingList(title = newTitle)) ?: 0
            val newItems = selectedItemNames.map { ShoppingItem(listId = newListId, name = it) }
            dao?.insertItems(newItems)
            onCreate(newListId)
        }
    }
    
    fun getItemsForList(listId: Long): Flow<List<ShoppingItem>> {
        return dao?.getItemsForList(listId) ?: flowOf(emptyList())
    }
    
    fun addItem(listId: Long, name: String) {
        viewModelScope.launch {
            dao?.insertItem(ShoppingItem(listId = listId, name = name))
        }
    }
    
    fun toggleItem(item: ShoppingItem) {
        viewModelScope.launch {
            dao?.updateItem(item.copy(isChecked = !item.isChecked))
        }
    }
    
    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch { dao?.deleteItem(item) }
    }
}

// UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ShoppingViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.initDao(context) }
    
    var currentListId by remember { mutableStateOf<Long?>(null) }
    
    if (currentListId == null) {
        ShoppingHistoryScreen(viewModel, onBack, onListSelected = { currentListId = it })
    } else {
        ShoppingDetailScreen(viewModel, listId = currentListId!!, onBack = { currentListId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingHistoryScreen(
    viewModel: ShoppingViewModel, 
    onBack: () -> Unit,
    onListSelected: (Long) -> Unit
) {
    val lists by viewModel.allLists.collectAsState()
    var showNewListDialog by remember { mutableStateOf(false) }
    var listToDuplicate by remember { mutableStateOf<ShoppingList?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.listas_de_compras)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewListDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = Res.get(R.string.nova_lista))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(lists) { list ->
                ListItem(
                    headlineContent = { Text(list.title) },
                    supportingContent = { 
                        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        Text(df.format(Date(list.timestamp))) 
                    },
                    modifier = Modifier.clickable { onListSelected(list.id) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { listToDuplicate = list }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar")
                            }
                            IconButton(onClick = { viewModel.deleteList(list) }) {
                                Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.excluir))
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
        
        if (showNewListDialog) {
            NewListDialog(
                onDismiss = { showNewListDialog = false },
                onCreate = { title ->
                    viewModel.createList(title) { onListSelected(it) }
                    showNewListDialog = false
                }
            )
        }
        
        if (listToDuplicate != null) {
            DuplicateListDialog(
                viewModel = viewModel,
                oldList = listToDuplicate!!,
                onDismiss = { listToDuplicate = null },
                onCreate = { title, selectedItems ->
                    viewModel.duplicateList(listToDuplicate!!.id, title, selectedItems) { onListSelected(it) }
                    listToDuplicate = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingDetailScreen(viewModel: ShoppingViewModel, listId: Long, onBack: () -> Unit) {
    val itemsFlow = remember(listId) { viewModel.getItemsForList(listId) }
    val items by itemsFlow.collectAsState(initial = emptyList())
    val topItems by viewModel.topItems.collectAsState()
    
    var newItemName by remember { mutableStateOf("") }
    
    val listName = viewModel.allLists.value.find { it.id == listId }?.title ?: "Lista"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val sb = StringBuilder()
                        sb.appendLine(Res.get(R.string.lista_de_compras_x, listName))
                        sb.appendLine()
                        items.forEach { item ->
                            val check = if (item.isChecked) Res.get(R.string.x_26) else "[ ]"
                            sb.appendLine(Res.get(R.string.x_x_21, check, (item.name)))
                        }
                        
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, sb.toString())
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_via))
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                    }
                }
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Input area
            Column(modifier = Modifier.padding(16.dp)) {
                var expanded by remember { mutableStateOf(false) }
                val filteredItems = if (newItemName.isNotBlank()) {
                    com.example.data.defaultShoppingItems.filter { it.contains(newItemName, ignoreCase = true) }.take(5)
                } else emptyList()

                ExposedDropdownMenuBox(
                    expanded = expanded && filteredItems.isNotEmpty(),
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { 
                            newItemName = it
                            expanded = true
                        },
                        label = { Text(Res.get(R.string.adicionar_item)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (newItemName.isNotBlank()) {
                                    viewModel.addItem(listId, newItemName.trim())
                                    newItemName = ""
                                    expanded = false
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = Res.get(R.string.adicionar))
                            }
                        }
                    )
                    
                    if (filteredItems.isNotEmpty() && expanded) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            filteredItems.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        viewModel.addItem(listId, selectionOption)
                                        newItemName = ""
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (topItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(Res.get(R.string.sugestoes), style = MaterialTheme.typography.bodySmall)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(topItems) { itemName ->
                            InputChip(
                                selected = false,
                                onClick = { 
                                    viewModel.addItem(listId, itemName)
                                },
                                label = { Text(itemName) }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // List area
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = item.name, 
                                style = androidx.compose.ui.text.TextStyle(
                                    textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                            ) 
                        },
                        leadingContent = {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = { viewModel.toggleItem(item) }
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteItem(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.remover))
                            }
                        },
                        modifier = Modifier.clickable { viewModel.toggleItem(item) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun NewListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { 
        mutableStateOf(java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())) 
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Res.get(R.string.nova_lista)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(Res.get(R.string.nome_da_lista)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onCreate(title) }) {
                Text("Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar)) }
        }
    )
}

@Composable
fun DuplicateListDialog(
    viewModel: ShoppingViewModel,
    oldList: ShoppingList,
    onDismiss: () -> Unit,
    onCreate: (title: String, selectedItems: List<String>) -> Unit
) {
    var title by remember { mutableStateOf(Res.get(R.string.x_copia, (oldList.title))) }
    val itemsFlow = remember(oldList.id) { viewModel.getItemsForList(oldList.id) }
    val items by itemsFlow.collectAsState(initial = emptyList())
    
    // Set of selected item names
    val selectedItems = remember { mutableStateListOf<String>() }
    
    // Auto select all initially when items load
    LaunchedEffect(items) {
        if (items.isNotEmpty() && selectedItems.isEmpty()) {
            selectedItems.addAll(items.map { it.name })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Res.get(R.string.duplicar_lista)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(Res.get(R.string.novo_nome_2)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(Res.get(R.string.selecione_os_itens_para_copiar))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(items) { item ->
                        val isSelected = selectedItems.contains(item.name)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isSelected) selectedItems.remove(item.name)
                                else selectedItems.add(item.name)
                            }
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedItems.add(item.name)
                                    else selectedItems.remove(item.name)
                                }
                            )
                            Text(item.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onCreate(title, selectedItems) }) {
                Text("Duplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar)) }
        }
    )
}
