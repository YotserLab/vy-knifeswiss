package com.example.ui.screens.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cast.media.CastMedia
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.ui.screens.files.FileItem
import com.example.ui.screens.files.FileType
import java.io.File
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwlBrowserTab(
    currentPath: String,
    files: List<FileItem>,
    isLoading: Boolean,
    error: String?,
    isGridView: Boolean,
    onToggleGrid: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileAction: (FileItem, String) -> Unit,
    onNavigatePath: ((String) -> Unit)? = null
) {
    var selectedFileForAction by remember { mutableStateOf<FileItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(DeepDark)) {
        // Windows Explorer Style Toolbar & Breadcrumbs
        Surface(
            color = DarkNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Toolbar superior
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleGrid) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = Res.get(R.string.alternar_visualizacao),
                            tint = AmberGold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                HorizontalDivider(color = DarkBorder)

                // Breadcrumb path navigation
                BreadcrumbPath(path = currentPath, onNavigatePath = onNavigatePath)
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberGold)
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = DangerRed)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(error, color = DangerRed, fontSize = 14.sp)
                    }
                }
            }
            files.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(64.dp), tint = OnSurfaceDim)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(Res.get(R.string.pasta_vazia), color = OnSurfaceMuted, fontSize = 16.sp)
                    }
                }
            }
            else -> {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(files) { file ->
                            OwlFileGridCell(
                                file = file,
                                onClick = { onFileClick(file) },
                                onLongClick = { selectedFileForAction = file }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(files) { file ->
                            OwlFileRow(
                                file = file,
                                onClick = { onFileClick(file) },
                                onLongClick = { selectedFileForAction = file }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
                        }
                    }
                }
            }
        }
    }

    if (selectedFileForAction != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedFileForAction = null },
            containerColor = DarkNavy,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AmberGold) }
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(Res.get(R.string.acoes_x_name, (selectedFileForAction!!.name)), color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomSheetActionIcon(Icons.Default.DriveFileRenameOutline, "Renomear", AmberGold) {
                        selectedFileForAction?.let { onFileAction(it, "rename") }
                        selectedFileForAction = null
                    }
                    BottomSheetActionIcon(Icons.Default.Share, Res.get(R.string.compart), CyanNeon) {
                        selectedFileForAction?.let { onFileAction(it, "share") }
                        selectedFileForAction = null
                    }
                    // Remote files can't be served by the local HTTP server, and
                    // FileType misses some castable exts — mimeForPath covers those.
                    if (selectedFileForAction!!.isLocal &&
                        (selectedFileForAction!!.type == FileType.VIDEO ||
                            selectedFileForAction!!.type == FileType.AUDIO ||
                            selectedFileForAction!!.type == FileType.IMAGE ||
                            CastMedia.mimeForPath(selectedFileForAction!!.name) != "application/octet-stream")
                    ) {
                        BottomSheetActionIcon(Icons.Default.Cast, "Cast", CyanNeon) {
                            selectedFileForAction?.let { onFileAction(it, "cast") }
                            selectedFileForAction = null
                        }
                    }
                    BottomSheetActionIcon(Icons.Default.DriveFileMove, "Mover", ElectricViolet) {
                        selectedFileForAction?.let { onFileAction(it, "Move") }
                        selectedFileForAction = null
                    }
                    BottomSheetActionIcon(Icons.Default.Delete, "Apagar", DangerRed) {
                        selectedFileForAction?.let { onFileAction(it, "delete") }
                        selectedFileForAction = null
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun BottomSheetActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Box(Modifier.size(56.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = OnSurfaceLight, fontSize = 12.sp)
    }
}

@Composable
fun BreadcrumbPath(path: String, onNavigatePath: ((String) -> Unit)?) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Home, contentDescription = "Raiz", modifier = Modifier.size(18.dp), tint = AmberGold)
        Text(
            text = " > ",
            color = OnSurfaceMuted,
            fontSize = 14.sp
        )
        
        if (segments.isEmpty()) {
            Text("/", color = OnSurfaceLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else {
            var currentBuiltPath = ""
            segments.forEachIndexed { index, segment ->
                currentBuiltPath += "/$segment"
                val segmentPath = currentBuiltPath
                
                Text(
                    text = segment,
                    fontSize = 14.sp,
                    fontWeight = if (index == segments.size - 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == segments.size - 1) OnSurfaceLight else AmberGold,
                    modifier = Modifier
                        .clickable(enabled = onNavigatePath != null && index < segments.size - 1) {
                            onNavigatePath?.invoke(segmentPath)
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
                
                if (index < segments.size - 1) {
                    Text(" > ", color = OnSurfaceMuted, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun FilePreviewOrIcon(file: FileItem, size: androidx.compose.ui.unit.Dp) {
    if (file.isLocal && (file.type == FileType.IMAGE || file.type == FileType.VIDEO)) {
        AsyncImage(
            model = File(file.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Icon(
            imageVector = when (file.type) {
                FileType.DIRECTORY -> Icons.Default.Folder
                FileType.AUDIO -> Icons.Default.Audiotrack
                FileType.VIDEO -> Icons.Default.Videocam
                FileType.IMAGE -> Icons.Default.Image
                FileType.DOCUMENT, FileType.PDF -> Icons.Default.Description
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint = when (file.type) {
                FileType.DIRECTORY -> AmberGold
                FileType.IMAGE, FileType.VIDEO -> CyanNeon
                FileType.AUDIO -> ElectricViolet
                FileType.PDF -> DangerRed
                else -> OnSurfaceMuted
            },
            modifier = Modifier.size(size)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OwlFileRow(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilePreviewOrIcon(file, 40.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name, 
                fontWeight = FontWeight.Bold, 
                fontSize = 15.sp, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                color = OnSurfaceLight
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file.size.isNotEmpty()) {
                    Text(file.size, fontSize = 12.sp, color = OnSurfaceMuted)
                }
                if (file.lastModified > 0) {
                    val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified))
                    Text(date, fontSize = 12.sp, color = OnSurfaceMuted)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OwlFileGridCell(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkNavy)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            FilePreviewOrIcon(file, 48.dp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = file.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = OnSurfaceLight,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun FileDropdownMenu(
    file: FileItem,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onClick: () -> Unit,
    onAction: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded, 
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(DarkNavyVariant).border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
    ) {
        DropdownMenuItem(text = { Text(Res.get(R.string.abrir), color = OnSurfaceLight) }, onClick = { onDismissRequest(); onClick() })
        DropdownMenuItem(text = { Text("Renomear", color = OnSurfaceLight) }, onClick = { onDismissRequest(); onAction("rename") })
        if (file.type != FileType.DIRECTORY) {
            DropdownMenuItem(text = { Text(Res.get(R.string.adicionar_a_playlist), color = OnSurfaceLight) }, onClick = { onDismissRequest(); onAction("playlist") })
            if (file.isLocal) {
                DropdownMenuItem(text = { Text("Compartilhar", color = OnSurfaceLight) }, onClick = { onDismissRequest(); onAction("share") })
            }
        }
        HorizontalDivider(color = DarkBorder)
        DropdownMenuItem(
            text = { Text(Res.get(R.string.excluir), color = DangerRed) },
            onClick = { onDismissRequest(); onAction("delete") }
        )
    }
}
