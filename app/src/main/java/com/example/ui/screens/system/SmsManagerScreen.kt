package com.example.ui.screens.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import com.example.R
import com.example.Res

data class SmsMessage(
    val id: String,
    val address: String,
    val body: String,
    val date: Long = 0L,
    val type: Int = 1,  // 1=inbox, 2=sent
    val read: Int = 1
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmsManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val initialPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    var allSms by remember { mutableStateOf(listOf<SmsMessage>()) }
    var permissionGranted by remember { mutableStateOf(initialPermission) }
    var isLoading by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0=Caixa de entrada, 1=Enviados

    val selectedIds = remember { mutableStateListOf<String>() }
    val expandedIds = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDefaultSmsPrompt by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    fun reload() {
        isLoading = true
        loadSmsAll(context) { list ->
            allSms = list
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionGranted = perms[Manifest.permission.READ_SMS] == true
        if (permissionGranted) reload()
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            reload()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }
    }

    // Filter logic
    val filtered = remember(allSms, searchQuery, activeTab) {
        val typeFilter = if (activeTab == 0) 1 else 2
        allSms
            .filter { it.type == typeFilter }
            .filter {
                searchQuery.isBlank() ||
                it.address.contains(searchQuery, ignoreCase = true) ||
                it.body.contains(searchQuery, ignoreCase = true)
            }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun deleteSelected() {
        val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true // COM-R03
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            context.packageName == android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        } else {
            true
        }

        if (!isDefaultSmsApp) {
            showDefaultSmsPrompt = true
            return
        }

        selectedIds.forEach { id -> deleteSms(context, id) {} }
        Toast.makeText(context, Res.get(R.string.x_sms_removido_s, (selectedIds.size)), Toast.LENGTH_SHORT).show()
        exitSelection()
        reload()
    }

    // Confirm delete dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text(Res.get(R.string.remover_x_mensagem_ns, (selectedIds.size))) },
            text = { Text(Res.get(R.string.esta_acao_nao_pode_ser_desfeita_as_mensa)) },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; deleteSelected() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(Res.get(R.string.remover), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }

    // Prompt to set as Default SMS App dialog
    if (showDefaultSmsPrompt) {
        AlertDialog(
            onDismissRequest = { showDefaultSmsPrompt = false },
            icon = { Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = { Text(Res.get(R.string.definir_como_app_padrao)) },
            text = { Text(Res.get(R.string.por_seguranca_o_android_so_permite_exclu)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDefaultSmsPrompt = false
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS) // COM-R04
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                        putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            } else {
                                val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                    putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, Res.get(R.string.erro_ao_abrir_configuracoes_x, (e.localizedMessage)), Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text(Res.get(R.string.definir_como_padrao), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDefaultSmsPrompt = false }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(Res.get(R.string.x_selecionado_s, (selectedIds.size))) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, Res.get(R.string.cancelar_selecao))
                        }
                    },
                    actions = {
                        // Select all
                        IconButton(onClick = {
                            if (selectedIds.size == filtered.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(filtered.map { it.id })
                            }
                        }) {
                            Icon(
                                if (selectedIds.size == filtered.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                Res.get(R.string.selecionar_tudo)
                            )
                        }
                        // Delete selected
                        IconButton(
                            onClick = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, Res.get(R.string.deletar_selecionados), tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(Res.get(R.string.gestor_de_sms)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, Res.get(R.string.voltar))
                        }
                    },
                    actions = {
                        IconButton(onClick = { reload() }) {
                            Icon(Icons.Default.Refresh, "Recarregar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Search bar ────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text(Res.get(R.string.buscar_por_remetente_ou_conteudo)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, Res.get(R.string.limpar_busca))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ── Tabs inbox / sent ─────────────────────────────────────────
            val inboxCount = allSms.count { it.type == 1 }
            val sentCount  = allSms.count { it.type == 2 }
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0; exitSelection() },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Inbox, null, modifier = Modifier.size(16.dp))
                            Text("Recebidos")
                            if (inboxCount > 0) Badge { Text(Res.get(R.string.x_27, inboxCount)) }
                        }
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1; exitSelection() },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                            Text("Enviados")
                            if (sentCount > 0) Badge { Text(Res.get(R.string.x_28, sentCount)) }
                        }
                    }
                )
            }

            // ── Selection bar ─────────────────────────────────────────────
            AnimatedVisibility(visible = selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            Res.get(R.string.x_de_x_selecionado_s, (selectedIds.size), (filtered.size)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                selectedIds.clear()
                                selectedIds.addAll(filtered.map { it.id })
                            }) { Text(Res.get(R.string.todos)) }
                            TextButton(onClick = { selectedIds.clear() }) { Text(Res.get(R.string.nenhum)) }
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────
            when {
                !permissionGranted -> {
                    PermissionPlaceholder(onRequest = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    })
                }
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MarkEmailRead, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (searchQuery.isNotEmpty()) Res.get(R.string.nenhum_resultado_para_x, searchQuery)
                                else Res.get(R.string.nenhuma_mensagem_encontrada),
                                color = Color.Gray
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, sms -> sms.id }) { _, sms ->
                            val isSelected = sms.id in selectedIds
                            val isExpanded = sms.id in expandedIds
                            val isUnread = sms.read == 0

                            val cardColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isUnread   -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    else       -> MaterialTheme.colorScheme.surface
                                },
                                animationSpec = tween(200),
                                label = "cardColor"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isSelected) Modifier.border(
                                            1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                if (isSelected) selectedIds.remove(sms.id) else selectedIds.add(sms.id)
                                                if (selectedIds.isEmpty()) selectionMode = false
                                            } else {
                                                if (isExpanded) expandedIds.remove(sms.id) else expandedIds.add(sms.id)
                                            }
                                        },
                                        onLongClick = {
                                            selectionMode = true
                                            if (!isSelected) selectedIds.add(sms.id)
                                        }
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Checkbox or avatar
                                        if (selectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    if (it) selectedIds.add(sms.id) else selectedIds.remove(sms.id)
                                                    if (selectedIds.isEmpty()) selectionMode = false
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        } else {
                                            // Avatar com inicial
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (activeTab == 0) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.secondary
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = sms.address.filter { it.isLetter() }.firstOrNull()?.uppercase() ?: "#",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sms.address.ifEmpty { Res.get(R.string.desconhecido) },
                                                    fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = formatSmsDate(sms.date),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isUnread) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                }
                                                Text(
                                                    text = sms.body,
                                                    fontSize = 12.sp,
                                                    color = if (isUnread) MaterialTheme.colorScheme.onSurface
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                                                )
                                            }
                                            if (sms.date > 0L && !isExpanded) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.AccessTime, null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = formatSmsDateFull(sms.date),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }

                                        // Chevron expand + delete individual
                                        if (!selectionMode) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clickable {
                                                            if (isExpanded) expandedIds.remove(sms.id) else expandedIds.add(sms.id)
                                                        }
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = Res.get(R.string.excluir),
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clickable {
                                                            selectedIds.clear()
                                                            selectedIds.add(sms.id)
                                                            showDeleteConfirm = true
                                                        }
                                                )
                                            }
                                        }
                                    }

                                    // Corpo expandido completo
                                    AnimatedVisibility(
                                        visible = isExpanded && !selectionMode,
                                        enter = expandVertically(tween(200)),
                                        exit = shrinkVertically(tween(200))
                                    ) {
                                        Column {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                            Text(
                                                text = sms.body,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    formatSmsDateFull(sms.date),
                                                    fontSize = 10.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Sms, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(Res.get(R.string.permissao_necessaria), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                Res.get(R.string.para_visualizar_e_gerenciar_suas_mensage),
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onRequest) {
                Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Res.get(R.string.conceder_permissao))
            }
        }
    }
}

private fun formatSmsDate(ts: Long): String {
    if (ts == 0L) return ""
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        now.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE HH:mm", Locale("pt", "BR")).format(Date(ts))
        else ->
            SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(ts))
    }
}

private fun formatSmsDateFull(ts: Long): String {
    if (ts == 0L) return Res.get(R.string.data_desconhecida)
    return SimpleDateFormat("dd/MM/yyyy '${Res.get(R.string.word_at)}' HH:mm:ss", Locale.getDefault()).format(Date(ts))
}

fun loadSmsAll(context: Context, onLoaded: (List<SmsMessage>) -> Unit) {
    val list = mutableListOf<SmsMessage>()
    val uris = listOf(
        "content://sms/inbox" to 1,
        "content://sms/sent"  to 2
    )
    for ((uriStr, type) in uris) {
        try {
            val cursor = context.contentResolver.query(
                Uri.parse(uriStr), null, null, null, "date DESC"
            )
            cursor?.use {
                val idxId   = it.getColumnIndex("_id")
                val idxAddr = it.getColumnIndex("address")
                val idxBody = it.getColumnIndex("body")
                val idxDate = it.getColumnIndex("date")
                val idxRead = it.getColumnIndex("read")
                while (it.moveToNext()) {
                    list.add(
                        SmsMessage(
                            id      = it.getString(idxId) ?: continue,
                            address = it.getString(idxAddr) ?: "",
                            body    = it.getString(idxBody) ?: "",
                            date    = if (idxDate >= 0) it.getLong(idxDate) else 0L,
                            type    = type,
                            read    = if (idxRead >= 0) it.getInt(idxRead) else 1
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }
    onLoaded(list.sortedByDescending { it.date })
}

fun loadSms(context: Context, onLoaded: (List<SmsMessage>) -> Unit) {
    loadSmsAll(context) { onLoaded(it.filter { sms -> sms.type == 1 }) }
}

fun deleteSms(context: Context, id: String, onDeleted: () -> Unit) {
    try {
        context.contentResolver.delete(Uri.parse("content://sms/$id"), null, null)
    } catch (_: Exception) {}
    onDeleted()
}
