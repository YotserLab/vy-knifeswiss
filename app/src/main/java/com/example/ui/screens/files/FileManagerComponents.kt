package com.example.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.R
import com.example.Res

@Composable
fun FileRowItem(
    file: FileItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCast: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val dateStr = remember(file.lastModified) {
        if (file.lastModified > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified))
        } else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
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
                tint = when (file.type) {
                    FileType.DIRECTORY -> Color(0xFFFFA726)
                    FileType.AUDIO -> Color(0xFF42A5F5)
                    FileType.VIDEO -> Color(0xFFEF5350)
                    FileType.IMAGE -> Color(0xFFAB47BC)
                    FileType.DOCUMENT -> Color(0xFF66BB6A)
                    else -> Color.Gray
                },
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(file.size, fontSize = 11.sp, color = Color.Gray)
                    if (dateStr.isNotEmpty()) {
                        Text(dateStr, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // 3-dot context menu
            Box {
                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = Res.get(R.string.opcoes), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(Res.get(R.string.abrir)) },
                        onClick = { showContextMenu = false; onOpen() },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        onClick = { showContextMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (file.type != FileType.DIRECTORY) {
                        DropdownMenuItem(
                            text = { Text("Compartilhar") },
                            onClick = { showContextMenu = false; onShare() },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    if (file.type == FileType.AUDIO || file.type == FileType.VIDEO || file.type == FileType.IMAGE) {
                        DropdownMenuItem(
                            text = { Text(Res.get(R.string.adicionar_a_playlist)) },
                            onClick = { showContextMenu = false; onAddToPlaylist() },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(Res.get(R.string.transmitir_cast)) },
                            onClick = { showContextMenu = false; onCast() },
                            leadingIcon = { Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(Res.get(R.string.excluir), color = MaterialTheme.colorScheme.error) },
                        onClick = { showContextMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteFileCard(
    item: FileItem,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCast: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (item.type) {
                        FileType.DIRECTORY -> Icons.Default.Folder
                        FileType.AUDIO -> Icons.Default.Audiotrack
                        FileType.VIDEO -> Icons.Default.CloudQueue
                        FileType.IMAGE -> Icons.Default.Image
                        FileType.DOCUMENT -> Icons.Default.Description
                        else -> Icons.Default.Dns
                    },
                    contentDescription = item.name,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                    Text(Res.get(R.string.x_remote_storage_path, (item.size)), style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
                }
            }

            Row {
                if (item.type == FileType.AUDIO || item.type == FileType.VIDEO || item.type == FileType.IMAGE) {
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = Res.get(R.string.add_to_playlist), tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onCast) {
                        Icon(Icons.Default.Cast, contentDescription = Res.get(R.string.cast_stream), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                IconButton(onClick = onView) {
                    Icon(Icons.Default.RemoveRedEye, contentDescription = Res.get(R.string.view_simulated_file), tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.FileDownload, contentDescription = Res.get(R.string.sync_download))
                }
            }
        }
    }
}

@Composable
fun FileManagerHomeGrid(
    onNavigate: (Int) -> Unit,
    onFilterClick: (FileType?) -> Unit,
    localFiles: List<FileItem>,
    connectionsList: List<NetworkConnection>,
    imgCount: Int = localFiles.count { it.type == FileType.IMAGE },
    audCount: Int = localFiles.count { it.type == FileType.AUDIO },
    vidCount: Int = localFiles.count { it.type == FileType.VIDEO },
    docCount: Int = localFiles.count { it.type == FileType.DOCUMENT }
) {
    val remoteCount = connectionsList.count { it.isConnected }
    val dirCount = localFiles.count { it.type == FileType.DIRECTORY }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { HomeGridItem(Icons.Default.Storage, Res.get(R.string.armaz_principal), Res.get(R.string.x_arquivos, (localFiles.size)), Color.Gray, onClick = { onNavigate(0) }) }
            item { HomeGridItem(Icons.Default.Download, "Downloads", Res.get(R.string.x_arquivos, (localFiles.size)), Color(0xFF2C2C2C), onClick = { onNavigate(0) }) }
            item { HomeGridItem(Icons.Default.DonutLarge, Res.get(R.string.analise), Res.get(R.string.diretorio_atual), Color.Gray, onClick = { onNavigate(0) }) }

            item { HomeGridItem(Icons.Default.Image, "Imagens", Res.get(R.string.x_arquivos_2, imgCount), Color(0xFF6A1B9A), onClick = { onFilterClick(FileType.IMAGE) }) }
            item { HomeGridItem(Icons.Default.LibraryMusic, Res.get(R.string.audio_2), Res.get(R.string.x_arquivos_3, audCount), Color(0xFF00838F), onClick = { onFilterClick(FileType.AUDIO) }) }
            item { HomeGridItem(Icons.Default.Movie, Res.get(R.string.videos), Res.get(R.string.x_arquivos_4, vidCount), Color(0xFFC62828), onClick = { onFilterClick(FileType.VIDEO) }) }

            item { HomeGridItem(Icons.Default.Description, "Documentos", Res.get(R.string.x_arquivos_5, docCount), Color(0xFF1565C0), onClick = { onFilterClick(FileType.DOCUMENT) }) }
            item { HomeGridItem(Icons.Default.Folder, "Pastas", Res.get(R.string.x_pastas, dirCount), Color(0xFF9CCC65), onClick = { onNavigate(0) }) }
            item { HomeGridItem(Icons.Default.AccessTime, "Arquivos", Res.get(R.string.x_itens, (localFiles.size)), Color(0xFF455A64), onClick = { onNavigate(0) }) }

            item { HomeGridItem(Icons.Default.CloudQueue, Res.get(R.string.remoto_nuvem), if (remoteCount > 0) Res.get(R.string.x_ativo, remoteCount) else "", Color(0xFF2196F3), onClick = { onNavigate(1) }) }
            item { HomeGridItem(Icons.Default.Lan, Res.get(R.string.conexoes), Res.get(R.string.x_config, (connectionsList.size)), Color(0xFF37474F), onClick = { onNavigate(2) }) }
            item { HomeGridItem(Icons.AutoMirrored.Filled.QueueMusic, Res.get(R.string.playlists_cast), Res.get(R.string.midia), Color(0xFFE91E63), onClick = { onNavigate(3) }) }
        }
    }
}

@Composable
fun HomeGridItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(0.5.dp, Color.LightGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            maxLines = 1,
            lineHeight = 16.sp
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                lineHeight = 14.sp
            )
        }
    }
}
