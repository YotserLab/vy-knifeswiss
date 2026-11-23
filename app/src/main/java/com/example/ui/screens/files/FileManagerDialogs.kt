package com.example.ui.screens.files

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.MediaPlaylist
import java.io.File
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionDialog(
    connectionsList: List<NetworkConnection>,
    onSaveConnections: (List<NetworkConnection>) -> Unit,
    onDismiss: () -> Unit
) {
    var editConn by remember { mutableStateOf<NetworkConnection?>(null) }
    var showForm by remember { mutableStateOf(false) }

    if (showForm) {
        ConnectionFormDialog(
            editConn = editConn,
            onSave = { conn ->
                val newList = if (editConn != null) {
                    connectionsList.map { if (it.id == editConn!!.id) conn else it }
                } else {
                    connectionsList + conn
                }
                onSaveConnections(newList)
                showForm = false
                editConn = null
            },
            onDismiss = { showForm = false; editConn = null }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(Res.get(R.string.conexoes_de_rede)) },
            text = {
                Column {
                    Text(
                        Res.get(R.string.configure_conexoes_ftp_sftp_webdav_ou_sm),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (connectionsList.isEmpty()) {
                        Text(Res.get(R.string.nenhuma_conexao_configurada_2), color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(connectionsList) { conn ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(conn.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(Res.get(R.string.x_x_x_3, (conn.protocol), (conn.host), (conn.port)), fontSize = 11.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            editConn = conn
                                            showForm = true
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = Res.get(R.string.editar), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showForm = true }) { Text(Res.get(R.string.nova_conexao)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(Res.get(R.string.fechar)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionFormDialog(
    editConn: NetworkConnection?,
    onSave: (NetworkConnection) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(editConn?.name ?: "") }
    var protocol by remember { mutableStateOf(editConn?.protocol ?: "FTP") }
    var host by remember { mutableStateOf(editConn?.host ?: "") }
    var port by remember { mutableStateOf(editConn?.port?.toString() ?: "21") }
    var user by remember { mutableStateOf(editConn?.user ?: "") }
    var password by remember { mutableStateOf(editConn?.password ?: "") }
    var remotePath by remember { mutableStateOf(editConn?.remotePath ?: "/") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editConn != null) Res.get(R.string.editar_conexao) else Res.get(R.string.nova_conexao)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(Res.get(R.string.nome)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Text(Res.get(R.string.protocolo), style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FTP", "FTPS", "SFTP", Res.get(R.string.webdav), "SMB").forEach { p -> // ARQ-R06
                        FilterChip(selected = protocol == p, onClick = {
                            protocol = p
                            port = when (p) { "SFTP" -> "22"; "FTPS" -> "990"; Res.get(R.string.webdav) -> "443"; else -> "21" }
                        }, label = { Text(p, fontSize = 11.sp) })
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Porta") }, modifier = Modifier.width(90.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text(Res.get(R.string.usuario)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(Res.get(R.string.senha)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    }
                )

                OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, label = { Text(Res.get(R.string.caminho_remoto)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || host.isBlank()) {
                    Toast.makeText(context, Res.get(R.string.nome_e_host_sao_obrigatorios), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val p = port.toIntOrNull() ?: 21
                onSave(NetworkConnection(
                    id = editConn?.id ?: System.currentTimeMillis(),
                    name = name, protocol = protocol, host = host, port = p,
                    user = user, password = password, remotePath = remotePath,
                    isConnected = editConn?.isConnected ?: false
                ))
                onDismiss()
            }) { Text(Res.get(R.string.salvar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar)) }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onCreatePlaylist: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playlistFormName by remember { mutableStateOf("") }
    var playlistFormDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Res.get(R.string.create_media_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = playlistFormName,
                    onValueChange = { playlistFormName = it },
                    label = { Text(Res.get(R.string.playlist_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = playlistFormDesc,
                    onValueChange = { playlistFormDesc = it },
                    label = { Text(Res.get(R.string.description_tag)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistFormName.isNotEmpty()) {
                        onCreatePlaylist(playlistFormName, playlistFormDesc)
                        onDismiss()
                    } else {
                        Toast.makeText(context, Res.get(R.string.name_is_required), Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    itemToAddToPlaylist: FileItem,
    playlistsState: List<MediaPlaylist>,
    onAddFileToPlaylist: (FileItem, Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Res.get(R.string.select_target_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Res.get(R.string.add_x_to, (itemToAddToPlaylist.name)))
                if (playlistsState.isEmpty()) {
                    Text(Res.get(R.string.no_playlists_configured_create_one_first), color = Color.Gray, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlistsState) { pl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddFileToPlaylist(itemToAddToPlaylist, pl.id)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = pl.name,
                                    modifier = Modifier.padding(12.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameFileDialog(
    fileItem: FileItem,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf(fileItem.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Res.get(R.string.renomear_arquivo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Res.get(R.string.digite_o_novo_nome_para_o_arquivo))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(Res.get(R.string.nome_do_arquivo)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.trim().isNotEmpty()) {
                        try {
                            val file = File(fileItem.path)
                            val parent = file.parentFile
                            val newFile = File(parent, newName.trim())
                            if (file.renameTo(newFile)) {
                                Toast.makeText(context, Res.get(R.string.renomeado_com_sucesso), Toast.LENGTH_SHORT).show()
                                onSuccess()
                                onDismiss()
                            } else {
                                Toast.makeText(context, Res.get(R.string.erro_ao_renomear_o_arquivo), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, Res.get(R.string.erro_x_4, (e.message)), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, Res.get(R.string.o_nome_nao_pode_ser_vazio), Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Renomear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Res.get(R.string.cancelar))
            }
        }
    )
}

fun shareFile(context: Context, fileItem: FileItem) {
    try {
        val file = File(fileItem.path)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (fileItem.type) {
                FileType.AUDIO -> "audio/*"
                FileType.VIDEO -> "video/*"
                FileType.IMAGE -> "image/*"
                FileType.DOCUMENT -> "text/plain"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, Res.get(R.string.compartilhar_arquivo)))
    } catch (e: Exception) {
        Toast.makeText(context, Res.get(R.string.erro_ao_compartilhar_x, (e.message)), Toast.LENGTH_SHORT).show()
    }
}
