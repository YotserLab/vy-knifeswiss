package com.example.ui.screens.files
import com.example.ui.screens.system.CloudProvider

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import com.example.R
import com.example.Res

@Composable
fun FileManagerConnectionsTab(
    connectionsList: List<NetworkConnection>,
    onToggleConnection: (NetworkConnection, Boolean) -> Unit,
    onDeleteConnection: (NetworkConnection) -> Unit,
    onEditConnection: (NetworkConnection) -> Unit,
    onAddConnection: () -> Unit,
    ftpServer: FTPServer?,
    onStartFtpServer: () -> Unit,
    onStopFtpServer: () -> Unit,
    webDavServer: WebDAVServer? = null,
    onStartWebDav: () -> Unit = {},
    onStopWebDav: () -> Unit = {},
    onConnectAndBrowse: (NetworkConnection) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Res.get(R.string.conexoes), fontSize = 12.sp)
                }
            }
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FTP", fontSize = 12.sp)
                }
            }
            Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Res.get(R.string.webdav), fontSize = 12.sp)
                }
            }
            Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuvem", fontSize = 12.sp)
                }
            }
        }

        when (selectedTabIndex) {
            0 -> ConnectionsListTab(
                connectionsList = connectionsList,
                onToggleConnection = onToggleConnection,
                onDeleteConnection = onDeleteConnection,
                onEditConnection = onEditConnection,
                onAddConnection = onAddConnection,
                onConnectAndBrowse = onConnectAndBrowse
            )
            1 -> FtpServerTab(ftpServer = ftpServer, onStart = onStartFtpServer, onStop = onStopFtpServer)
            2 -> WebDavServerTab(webDavServer = webDavServer, onStart = onStartWebDav, onStop = onStopWebDav)
            3 -> CloudStorageTab()
        }
    }
}

@Composable
private fun ConnectionsListTab(
    connectionsList: List<NetworkConnection>,
    onToggleConnection: (NetworkConnection, Boolean) -> Unit,
    onDeleteConnection: (NetworkConnection) -> Unit,
    onEditConnection: (NetworkConnection) -> Unit,
    onAddConnection: () -> Unit,
    onConnectAndBrowse: (NetworkConnection) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Res.get(R.string.protocolos_e_conexoes_x, (connectionsList.size)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onAddConnection) { Text(Res.get(R.string.adicionar)) }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (connectionsList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(Res.get(R.string.nenhuma_conexao_configurada), color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(connectionsList, key = { it.id }) { conn ->
                    ConnectionCard(
                        connection = conn,
                        onToggle = { onToggleConnection(conn, it) },
                        onDelete = { onDeleteConnection(conn) },
                        onEdit = { onEditConnection(conn) },
                        onBrowse = { onConnectAndBrowse(conn) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: NetworkConnection,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onBrowse: () -> Unit
) {
    val protoColor = when (connection.protocol) {
        "SFTP" -> Color(0xFF00838F)
        "FTP" -> Color(0xFFE65100)
        "FTPS" -> Color(0xFF2E7D32)
        Res.get(R.string.webdav) -> Color(0xFF1565C0)
        "SMB" -> Color(0xFF6A1B9A)
        else -> Color.Gray
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = protoColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                connection.protocol,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = protoColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(connection.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        Res.get(R.string.x_x_x_2, (connection.user), (connection.host), (connection.port)),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        Res.get(R.string.caminho_x, (connection.remotePath)),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = connection.isConnected,
                        onCheckedChange = onToggle,
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (connection.isConnected) Res.get(R.string.conectado) else "Desconectado",
                        fontSize = 12.sp,
                        color = if (connection.isConnected) Color(0xFF2E7D32) else Color.Gray
                    )
                }
                Row {
                    if (connection.isConnected) {
                        IconButton(onClick = onBrowse, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Navegar", tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = Res.get(R.string.editar), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.excluir), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpServerTab(
    ftpServer: FTPServer?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val wifiIp = remember {
        getLocalIpAddress()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (ftpServer?.isRunning == true) Color(0xFF2E7D32) else Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(Res.get(R.string.acesso_remoto_via_ftp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    Res.get(R.string.acesse_os_arquivos_do_seu_dispositivo_de),
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (ftpServer?.isRunning == true) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Res.get(R.string.servidor_ftp_ativo), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                Res.get(R.string.conecte_se_usando),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                Res.get(R.string.ftp_x_2221, wifiIp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            Text(
                                Res.get(R.string.usuario_android_senha_qualquer),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Res.get(R.string.parar_servidor))
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Res.get(R.string.iniciar_servidor_ftp))
                    }
                }

                if (ftpServer != null && ftpServer.log.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(Res.get(R.string.log_do_servidor), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.height(150.dp).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(ftpServer.log) { line ->
                                Text(
                                    line,
                                    color = Color(0xFF4CAF50),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getLocalIpAddress(): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (intf.isLoopback || !intf.isUp) continue
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        }
    } catch (_: Exception) {}
    return "127.0.0.1"
}

@Composable
private fun WebDavServerTab(
    webDavServer: WebDAVServer?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isRunning = webDavServer?.isRunning == true
    val ip = remember(isRunning) { webDavServer?.getIp() ?: getLocalIpAddress() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isRunning) Color(0xFF1565C0) else Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(Res.get(R.string.servidor_webdav), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    Res.get(R.string.acesse_arquivos_do_dispositivo_via_webda),
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isRunning) {
                    Surface(
                        color = Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Res.get(R.string.webdav_ativo), fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(Res.get(R.string.url_de_acesso), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "http://$ip:8080",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(Res.get(R.string.como_conectar), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(Res.get(R.string.windows_mapear_unidade_de_rede_http_x_80, ip), fontSize = 11.sp, color = Color.DarkGray)
                            Text(Res.get(R.string.macos_finder_ir_conectar_ao_servidor_htt, ip), fontSize = 11.sp, color = Color.DarkGray)
                            Text(Res.get(R.string.linux_nautilus_dolphin_webdav_dav_x_8080, ip), fontSize = 11.sp, color = Color.DarkGray)
                            Text(Res.get(R.string.android_solid_explorer_es_file_manager_x), fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Res.get(R.string.parar_webdav))
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Res.get(R.string.iniciar_servidor_webdav))
                    }
                }

                if (webDavServer != null && webDavServer.log.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(Res.get(R.string.log_do_servidor), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(6.dp)) {
                        LazyColumn(modifier = Modifier.height(120.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(webDavServer.log) { line ->
                                Text(line, color = Color(0xFF64B5F6), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudStorageTab() {
    val context = androidx.compose.ui.platform.LocalContext.current

    data class CloudProvider(
        val name: String,
        val description: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val color: Color,
        val webDavNote: String,
        val packageName: String?,
        val webUrl: String
    )

    val providers = listOf(
        CloudProvider(
            name = Res.get(R.string.google_drive),
            description = Res.get(R.string.acesse_o_google_drive_instalando_o_app_o),
            icon = Icons.Default.CloudQueue,
            color = Color(0xFF1A73E8),
            webDavNote = Res.get(R.string.o_google_drive_nao_suporta_webdav_nativo),
            packageName = "com.google.android.apps.docs",
            webUrl = "https://drive.google.com"
        ),
        CloudProvider(
            name = Res.get(R.string.onedrive),
            description = Res.get(R.string.acesse_o_onedrive_instalando_o_app_ofici),
            icon = Icons.Default.Cloud,
            color = Color(0xFF0078D4),
            webDavNote = Res.get(R.string.sharepoint_onedrive_for_business_suporta),
            packageName = "com.microsoft.skydrive",
            webUrl = "https://onedrive.live.com"
        ),
        CloudProvider(
            name = "Nextcloud",
            description = Res.get(R.string.nextcloud_suporta_webdav_nativo_adicione),
            icon = Icons.Default.Storage,
            color = Color(0xFF0082C9),
            webDavNote = Res.get(R.string.url_webdav_https_seu_servidor_com_remote),
            packageName = "com.nextcloud.client",
            webUrl = "https://nextcloud.com"
        ),
        CloudProvider(
            name = "Dropbox",
            description = Res.get(R.string.use_o_app_oficial_do_dropbox_o_acesso_we),
            icon = Icons.Default.FolderSpecial,
            color = Color(0xFF0061FF),
            webDavNote = Res.get(R.string.dropbox_removeu_o_suporte_webdav_use_o_a),
            packageName = "com.dropbox.android",
            webUrl = "https://www.dropbox.com"
        ),
        CloudProvider(
            name = "pCloud",
            description = Res.get(R.string.pcloud_suporta_webdav_adicione_como_cone),
            icon = Icons.Default.CloudDone,
            color = Color(0xFF20C0E8),
            webDavNote = Res.get(R.string.webdav_url_https_webdav_pcloud_com_porta),
            packageName = "com.pcloud.pcloud",
            webUrl = "https://www.pcloud.com"
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        Res.get(R.string.provedores_com_suporte_webdav_nativo_pod) +
                        Res.get(R.string.para_outros_use_o_app_oficial_ou_configu),
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037)
                    )
                }
            }
        }

        items(providers) { provider ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(provider.icon, contentDescription = null, tint = provider.color, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(provider.description, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(provider.webDavNote, fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (provider.packageName != null) {
                            val isInstalled = remember(provider.packageName) {
                                try {
                                    context.packageManager.getPackageInfo(provider.packageName, 0)
                                    true
                                } catch (_: Exception) { false }
                            }
                            if (isInstalled) {
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(context.packageManager.getLaunchIntentForPackage(provider.packageName))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Res.get(R.string.abrir_app), fontSize = 11.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("market://details?id=${provider.packageName}"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://play.google.com/store/apps/details?id=${provider.packageName}"))
                                            context.startActivity(intent)
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Instalar", fontSize = 11.sp)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(provider.webUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Web", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
