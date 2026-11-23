package com.example.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.Res

@Composable
fun FileManagerRemoteTab(
    connectionsList: List<NetworkConnection>,
    onManageConnections: () -> Unit,
    onAddConnection: () -> Unit
) {
    val activeConnected = connectionsList.filter { it.isConnected }

    if (connectionsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text(Res.get(R.string.nenhum_armazenamento_configurado), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Res.get(R.string.adicione_conexoes_ftp_sftp_ou_webdav), color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onAddConnection) { Text(Res.get(R.string.adicionar_conexao)) }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                Res.get(R.string.armazenamentos_conectados),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                Res.get(R.string.x_de_x_conexoes_ativas, (activeConnected.size), (connectionsList.size)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        items(connectionsList) { conn ->
            RemoteStorageCard(
                connection = conn,
                isActive = conn.isConnected,
                onClick = { /* future: browse files */ }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Res.get(R.string.adicionar_conexao))
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onManageConnections, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Res.get(R.string.gerenciar_conexoes), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun RemoteStorageCard(
    connection: NetworkConnection,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val (icon, tint) = when (connection.protocol) {
        "SFTP" -> Icons.Default.Terminal to Color(0xFF00838F)
        "FTP" -> Icons.Default.Computer to Color(0xFFE65100)
        "FTPS" -> Icons.Default.Lock to Color(0xFF2E7D32)
        Res.get(R.string.webdav) -> Icons.Default.Cloud to Color(0xFF1565C0)
        "SMB" -> Icons.Default.Lan to Color(0xFF6A1B9A)
        else -> Icons.Default.Folder to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isActive, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant
            else Color(0xFFF5F5F5)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(connection.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Res.get(R.string.x_x_x_4, (connection.protocol), (connection.host), (connection.port)),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Text(
                        Res.get(R.string.conectado_como_x_x, (connection.user), (connection.remotePath)),
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
            if (isActive) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
            }
        }
    }
}
