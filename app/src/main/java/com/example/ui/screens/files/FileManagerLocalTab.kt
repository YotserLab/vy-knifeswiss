package com.example.ui.screens.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.io.File
import java.util.Locale
import com.example.R
import com.example.Res

enum class SortType {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerLocalTab(
    currentDirectory: File,
    localFiles: List<FileItem>,
    localFileFilter: FileType?,
    navController: NavController,
    onNavigateDirectory: (File) -> Unit,
    onRefresh: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onAddToPlaylist: (FileItem) -> Unit,
    onCastFile: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortType.NAME_ASC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("list") }

    var hasStoragePermission by remember { mutableStateOf(false) }

    fun checkPermissionState(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) {
        hasStoragePermission = checkPermissionState()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val readGranted = result[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        hasStoragePermission = readGranted
        onRefresh()
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val filteredList = remember(localFiles, searchQuery, localFileFilter) {
        localFiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) &&
            (localFileFilter == null || it.type == localFileFilter)
        }
    }

    val sortedList = remember(filteredList, currentSort) {
        filteredList.sortedWith(
            compareBy<FileItem> { it.type != FileType.DIRECTORY }
                .thenComparator { a, b ->
                    when (currentSort) {
                        SortType.NAME_ASC -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                        SortType.NAME_DESC -> b.name.lowercase(Locale.ROOT).compareTo(a.name.lowercase(Locale.ROOT))
                        SortType.DATE_ASC -> a.lastModified.compareTo(b.lastModified)
                        SortType.DATE_DESC -> b.lastModified.compareTo(a.lastModified)
                        SortType.SIZE_ASC -> a.rawSize.compareTo(b.rawSize)
                        SortType.SIZE_DESC -> b.rawSize.compareTo(a.rawSize)
                    }
                }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (!hasStoragePermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(Res.get(R.string.permissao_necessaria_2), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    Text(Res.get(R.string.acesso_ao_armazenamento_necessario), fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { requestPermissions() }, modifier = Modifier.height(30.dp)) { Text("Conceder", fontSize = 12.sp) }
                        TextButton(onClick = { hasStoragePermission = true; onRefresh() }, modifier = Modifier.height(30.dp)) { Text(Res.get(R.string.usar_app), fontSize = 12.sp) }
                    }
                }
            }
        }

        val pathSegments = remember(currentDirectory) {
            buildBreadcrumb(currentDirectory)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pathSegments.forEachIndexed { index, (name, file) ->
                Text(
                    text = name,
                    fontSize = 12.sp,
                    color = if (index == pathSegments.size - 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == pathSegments.size - 1) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { if (file.exists()) onNavigateDirectory(file) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    maxLines = 1
                )
                if (index < pathSegments.size - 1) {
                    Text(" > ", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentDirectory.parentFile?.let(onNavigateDirectory) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar", modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { viewMode = if (viewMode == "list") "grid" else "list" }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (viewMode == "list") Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = Res.get(R.string.modo_visualizacao),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = {
                try {
                    val rNum = (100..999).random()
                    val testFile = File(currentDirectory, Res.get(R.string.nota_x, rNum))
                    testFile.writeText(Res.get(R.string.swiss_note_x_ncriada_diretamente_do_disp, rNum))
                    onRefresh()
                    Toast.makeText(context, Res.get(R.string.criado_nota_x, rNum), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, Res.get(R.string.falha_ao_criar_x, (e.message)), Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = Res.get(R.string.novo_arquivo), modifier = Modifier.size(18.dp))
            }

            var showNewFolderDialog by remember { mutableStateOf(false) }
            IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = Res.get(R.string.nova_pasta), modifier = Modifier.size(18.dp))
            }

            if (showNewFolderDialog) {
                var folderName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showNewFolderDialog = false },
                    title = { Text(Res.get(R.string.nova_pasta_2)) },
                    text = {
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            label = { Text(Res.get(R.string.nome_da_pasta)) },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (folderName.isNotBlank()) {
                                val newDir = File(currentDirectory, folderName.trim())
                                if (newDir.mkdir()) onRefresh()
                                else Toast.makeText(context, Res.get(R.string.erro_ao_criar_pasta), Toast.LENGTH_SHORT).show()
                            }
                            showNewFolderDialog = false
                        }) { Text("Criar") }
                    },
                    dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text(Res.get(R.string.cancelar)) } }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(Res.get(R.string.buscar)) },
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )

            Box {
                IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Sort, contentDescription = "Ordenar", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text(Res.get(R.string.nome_a_z)) }, onClick = { currentSort = SortType.NAME_ASC; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(Res.get(R.string.nome_z_a)) }, onClick = { currentSort = SortType.NAME_DESC; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(Res.get(R.string.mais_recentes)) }, onClick = { currentSort = SortType.DATE_DESC; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(Res.get(R.string.mais_antigos)) }, onClick = { currentSort = SortType.DATE_ASC; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(Res.get(R.string.maior_tamanho)) }, onClick = { currentSort = SortType.SIZE_DESC; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(Res.get(R.string.menor_tamanho)) }, onClick = { currentSort = SortType.SIZE_ASC; sortMenuExpanded = false })
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (sortedList.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(Res.get(R.string.nenhum_arquivo_encontrado_2), color = Color.Gray, fontSize = 13.sp)
            }
        } else if (viewMode == "grid") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sortedList) { file ->
                    FileGridCell(
                        file = file,
                        onClick = { onFileClick(file) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sortedList) { file ->
                    FileRowItem(
                        file = file,
                        onOpen = { onFileClick(file) },
                        onDelete = { onDeleteFile(file) },
                        onAddToPlaylist = { onAddToPlaylist(file) },
                        onCast = { onCastFile(file) },
                        onRename = { onRenameFile(file) },
                        onShare = { onShareFile(file) }
                    )
                }
            }
        }
    }
}

private fun buildBreadcrumb(directory: File): List<Pair<String, File>> {
    val segments = mutableListOf<Pair<String, File>>()
    var current: File? = directory
    while (current != null) {
        segments.add(0, current.name.ifEmpty { "/" } to current)
        current = current.parentFile
    }
    return segments
}

@Composable
fun FileGridCell(file: FileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = when (file.type) {
                    FileType.DIRECTORY -> Icons.Default.Folder
                    FileType.AUDIO -> Icons.Default.Audiotrack
                    FileType.VIDEO -> Icons.Default.Videocam
                    FileType.IMAGE -> Icons.Default.Image
                    FileType.DOCUMENT -> Icons.Default.Description
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = file.name,
                fontSize = 11.sp,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
