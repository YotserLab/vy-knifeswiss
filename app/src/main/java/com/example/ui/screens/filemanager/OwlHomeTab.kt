package com.example.ui.screens.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.ui.screens.files.FileType
import com.example.ui.screens.files.NetworkConnection
import com.example.R
import com.example.Res

@Composable
fun OwlHomeTab(
    sdCardPath: String?,
    localFilesCount: Int,
    imgCount: Int,
    vidCount: Int,
    audCount: Int,
    docCount: Int,
    connectionsList: List<NetworkConnection>,
    playlistsCount: Int,
    isFtpRunning: Boolean,
    ftpAddress: String,
    onToggleFtp: () -> Unit,
    onNavigateLocal: (path: String) -> Unit,
    onNavigateFilter: (FileType) -> Unit,
    onNavigateRemote: (NetworkConnection) -> Unit,
    onAddConnection: () -> Unit,
    onManageConnections: () -> Unit,
    onManagePlaylists: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Armazenamento
        item {
            CategoryHeader(label = "Armazenamento", accentColor = AmberGold, modifier = Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageCard(Modifier.weight(1f), "Interno", Icons.Default.PhoneAndroid, AmberGold) { onNavigateLocal("/sdcard") }
                if (sdCardPath != null) {
                    StorageCard(Modifier.weight(1f), Res.get(R.string.cartao_sd), Icons.Default.SdStorage, ElectricViolet) { onNavigateLocal(sdCardPath) }
                }
            }
        }

        // Categorias Inteligentes (Grid)
        item {
            CategoryHeader(label = Res.get(R.string.categorias_inteligentes), accentColor = CyanNeon, modifier = Modifier.padding(bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryTile(Modifier.weight(1f), "Imagens", imgCount, Icons.Default.Image, CyanNeon) { onNavigateFilter(FileType.IMAGE) }
                    CategoryTile(Modifier.weight(1f), Res.get(R.string.videos), vidCount, Icons.Default.Videocam, ElectricViolet) { onNavigateFilter(FileType.VIDEO) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryTile(Modifier.weight(1f), Res.get(R.string.audios), audCount, Icons.Default.Audiotrack, AmberGold) { onNavigateFilter(FileType.AUDIO) }
                    CategoryTile(Modifier.weight(1f), "Documentos", docCount, Icons.Default.Description, SuccessGreen) { onNavigateFilter(FileType.DOCUMENT) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryTile(Modifier.weight(1f), "Downloads", 0, Icons.Default.Download, Color.White) { onNavigateLocal(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath) }
                    CategoryTile(Modifier.weight(1f), "Recentes", 0, Icons.Default.History, CyanNeon) { /* TODO: Implementar Recentes */ }
                }
            }
        }

        // Cloud and Network
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryHeader(label = Res.get(R.string.nuvem_rede), accentColor = ElectricViolet, modifier = Modifier.padding(0.dp))
                IconButton(onClick = onAddConnection, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = Res.get(R.string.adicionar), tint = ElectricViolet)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NeonCard(borderColor = DarkBorder, modifier = Modifier.fillMaxWidth()) {
                Column {
                    OwlHomeRow(
                        icon = Icons.Default.Computer,
                        title = if (isFtpRunning) Res.get(R.string.servidor_ftp_rodando) else Res.get(R.string.iniciar_servidor_ftp),
                        subtitle = if (isFtpRunning) Res.get(R.string.acesse_x_no_seu_pc, ftpAddress) else Res.get(R.string.acesse_os_arquivos_pelo_pc_via_wi_fi),
                        iconTint = if (isFtpRunning) SuccessGreen else OnSurfaceDim,
                        onClick = onToggleFtp
                    )
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(start = 56.dp))
                    
                    // Google Drive
                    OwlHomeRow(
                        icon = Icons.Default.Cloud,
                        title = Res.get(R.string.google_drive),
                        subtitle = Res.get(R.string.acessar_arquivos_da_nuvem),
                        iconTint = AmberGold,
                        onClick = { onNavigateRemote(NetworkConnection(name = Res.get(R.string.google_drive), protocol = Res.get(R.string.googledrive), host = "drive.google.com")) }
                    )
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(start = 56.dp))
                    
                    if (connectionsList.isEmpty()) {
                        Text(
                            text = Res.get(R.string.nenhuma_conexao_configurada_2),
                            modifier = Modifier.padding(16.dp),
                            color = OnSurfaceMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        connectionsList.forEachIndexed { index, conn ->
                            OwlHomeRow(
                                icon = if (conn.protocol.contains("FTP")) Icons.Default.Dns else Icons.Default.CloudQueue,
                                title = conn.name,
                                iconTint = ElectricViolet,
                                subtitle = Res.get(R.string.x_x_38, (conn.protocol), (conn.host)),
                                onClick = { onNavigateRemote(conn) }
                            )
                            if (index < connectionsList.size - 1) {
                                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = DarkBorder)
                    OwlHomeRow(Icons.Default.Settings, Res.get(R.string.gerenciar_conexoes_e_servidores), OnSurfaceMuted, onClick = onManageConnections)
                }
            }
        }

        // Media & Playlists section
        item {
            CategoryHeader(label = Res.get(R.string.favoritos_e_midia), accentColor = AmberGold, modifier = Modifier.padding(bottom = 8.dp))
            NeonCard(
                borderColor = DarkBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    OwlHomeRow(Icons.AutoMirrored.Filled.QueueMusic, Res.get(R.string.playlists_x, playlistsCount), AmberGold, onClick = onManagePlaylists)
                }
            }
        }
    }
}

@Composable
private fun OwlHomeRow(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = OnSurfaceMuted)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceMuted.copy(alpha = 0.6f))
    }
}

@Composable
fun StorageCard(modifier: Modifier = Modifier, title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    NeonCard(borderColor = color.copy(alpha = 0.5f), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun CategoryTile(modifier: Modifier = Modifier, title: String, count: Int, icon: ImageVector, color: Color, onClick: () -> Unit) {
    NeonCard(borderColor = DarkBorder, modifier = modifier.clickable { onClick() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (count > 0) {
                    Text(Res.get(R.string.x_itens_2, count), color = OnSurfaceMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
