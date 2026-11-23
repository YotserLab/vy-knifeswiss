package com.example.ui.screens.security
import com.example.ui.screens.chat.LocalChatRepository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.AppDatabase
import com.example.data.TotpAccount
import com.example.data.VaultBackupPayload
import com.example.data.VaultEntry
import com.example.ui.theme.*
import com.example.utils.crypto.SecurePrefs
import com.example.utils.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import com.example.utils.findFragmentActivity
import com.example.R
import com.example.Res

// ── Session state (survives recompose, cleared on process kill) ─────────
object VaultSession {
    var unlockedPin: String? = null
    var unlockedAt: Long = 0L
    private const val TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    fun isUnlocked(): Boolean {
        val pin = unlockedPin ?: return false
        if (System.currentTimeMillis() - unlockedAt > TIMEOUT_MS) {
            lock()
            return false
        }
        return true
    }

    fun unlock(pin: String) {
        unlockedPin = pin
        unlockedAt = System.currentTimeMillis()
    }

    fun lock() {
        unlockedPin = null
        unlockedAt = 0L
    }

    fun touch() {
        if (unlockedPin != null) unlockedAt = System.currentTimeMillis()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordVaultScreen(
    onBackClick: () -> Unit,
    onNavigateToLocalChat: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val vaultDao = remember { db.vaultDao() }

    // Lock state — recheck every second
    var isUnlocked by remember { mutableStateOf(VaultSession.isUnlocked()) }

    LaunchedEffect(isUnlocked) {
        while (isUnlocked) {
            delay(5_000)
            if (!VaultSession.isUnlocked()) {
                isUnlocked = false
            }
        }
    }

    if (!VaultCrypto.isVaultSetup(context)) {
        VaultSetupScreen(
            onSetupComplete = { pin ->
                VaultCrypto.setupPin(context, pin)
                VaultSession.unlock(pin)
                isUnlocked = true
            },
            onBackClick = onBackClick
        )
    } else if (!isUnlocked) {
        VaultUnlockScreen(
            context = context,
            onUnlocked = { pin ->
                VaultSession.unlock(pin)
                isUnlocked = true
            },
            onBackClick = onBackClick
        )
    } else {
        VaultMainScreen(
            context = context,
            vaultDao = vaultDao,
            pin = VaultSession.unlockedPin ?: "",
            onLock = {
                VaultSession.lock()
                isUnlocked = false
            },
            onBackClick = onBackClick,
            onNavigateToLocalChat = onNavigateToLocalChat
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Setup Screen — create PIN
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSetupScreen(onSetupComplete: (String) -> Unit, onBackClick: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(0) } // 0=enter, 1=confirm

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.configurar_cofre), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(CyanNeon.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = CyanNeon, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (step == 0) Res.get(R.string.criar_pin_mestre) else Res.get(R.string.confirmar_pin),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceLight
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (step == 0) Res.get(R.string.crie_um_pin_de_416_digitos_para_proteger)
                else Res.get(R.string.digite_o_pin_novamente_para_confirmar),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            PinDots(value = if (step == 0) pin else confirmPin, maxLen = 16)
            Spacer(Modifier.height(16.dp))
            PinKeyboard(
                value = if (step == 0) pin else confirmPin,
                onChange = { v ->
                    if (step == 0) pin = v else confirmPin = v
                    error = null
                },
                onConfirm = {
                    if (step == 0) {
                        if (pin.length < 4) { error = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos) }
                        else step = 1
                    } else {
                        if (confirmPin == pin) {
                            onSetupComplete(pin)
                        } else {
                            error = Res.get(R.string.pins_nao_coincidem)
                            confirmPin = ""
                        }
                    }
                }
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Unlock Screen — enter PIN or biometric
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultUnlockScreen(
    context: Context,
    onUnlocked: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }

    val canUseBiometric = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun tryBiometric() {
        val activity = context.findFragmentActivity() ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Retrieve stored PIN from encrypted preferences
                val bioPin = SecurePrefs.getString(context, "vault_bio_pin", null)
                if (bioPin != null) {
                    // Delay slightly to allow BiometricPrompt to fully dismiss before changing UI tree
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(350)
                        onUnlocked(bioPin)
                    }
                }
                else error = Res.get(R.string.configure_o_pin_primeiro)
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) // COF-R05
                    error = Res.get(R.string.erro_biometrico_x, msg)
            }
            override fun onAuthenticationFailed() {
                error = Res.get(R.string.biometria_nao_reconhecida)
            }
        }
        BiometricPrompt(activity, executor, callback).authenticate( // COF-R03
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(Res.get(R.string.desbloquear_cofre_de_senhas))
                .setSubtitle(Res.get(R.string.use_sua_biometria_para_acessar_o_cofre))
                .setNegativeButtonText(Res.get(R.string.usar_pin))
                .build()
        )
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.cofre_de_senhas), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(CyanNeon.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockOpen, null, tint = CyanNeon, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(Res.get(R.string.desbloquear_cofre), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
            Spacer(Modifier.height(8.dp))
            Text(Res.get(R.string.digite_seu_pin_mestre), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            Spacer(Modifier.height(32.dp))
            PinDots(value = pin, maxLen = 16)
            Spacer(Modifier.height(16.dp))
            PinKeyboard(
                value = pin,
                onChange = { pin = it; error = null },
                onConfirm = {
                    if (VaultCrypto.verifyPin(context, pin)) {
                        // Store for biometric reuse in encrypted preferences
                        SecurePrefs.putString(context, "vault_bio_pin", pin)
                        onUnlocked(pin)
                    } else {
                        attempts++
                        error = if (attempts >= 5) Res.get(R.string.muitas_tentativas_incorretas)
                                else Res.get(R.string.pin_incorreto_x_5, (attempts))
                        pin = ""
                    }
                }
            )
            if (canUseBiometric) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { tryBiometric() },
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                ) {
                    Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.usar_biometria))
                }
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Main Vault Screen
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultMainScreen(
    context: Context,
    vaultDao: com.example.data.VaultDao,
    pin: String,
    onLock: () -> Unit,
    onBackClick: () -> Unit,
    onNavigateToLocalChat: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val entries by vaultDao.getAll().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var showEntryMenu by remember { mutableStateOf<VaultEntry?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<VaultEntry?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    val categories = listOf("all" to "Todas", "login" to "Logins", "card" to Res.get(R.string.cartoes), "note" to "Notas", "identity" to "Identidades")

    val filteredEntries = entries.filter { entry ->
        val matchCat = when (selectedCategory) {
            "all" -> true
            "favorites" -> entry.isFavorite
            else -> entry.category == selectedCategory
        }
        val matchSearch = searchQuery.isBlank() ||
            entry.title.contains(searchQuery, true) ||
            entry.username.contains(searchQuery, true) ||
            entry.url.contains(searchQuery, true)
        matchCat && matchSearch
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.cofre_de_senhas), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CyanNeon)
                    }
                },
                actions = {
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(Icons.Default.HelpOutline, Res.get(R.string.manual_e_migracao), tint = CyanNeon)
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.FileUpload, null, tint = OnSurfaceMuted)
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.FileDownload, null, tint = OnSurfaceMuted)
                    }
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, null, tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyanNeon,
                contentColor = DeepDark
            ) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(DeepDark)) {
            // Search bar
            VaultSearchBar(query = searchQuery, onQuery = { searchQuery = it; VaultSession.touch() })
            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = categories.indexOfFirst { it.first == selectedCategory }.coerceAtLeast(0),
                containerColor = DeepDark,
                contentColor = CyanNeon,
                edgePadding = 16.dp
            ) {
                categories.forEachIndexed { idx, (key, label) ->
                    Tab(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key; VaultSession.touch() },
                        text = { Text(label, fontSize = 12.sp) }
                    )
                }
                Tab(
                    selected = selectedCategory == "favorites",
                    onClick = { selectedCategory = "favorites"; VaultSession.touch() },
                    text = { Text(Res.get(R.string.favoritos), fontSize = 12.sp) }
                )
            }
            // Entry count
            Text(
                "${filteredEntries.size} ite${if (filteredEntries.size == 1) "m" else "ns"}",
                color = OnSurfaceMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (filteredEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = OnSurfaceDim, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(Res.get(R.string.nenhuma_entrada_encontrada), color = OnSurfaceMuted)
                        if (entries.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(Res.get(R.string.toque_em_para_adicionar_sua_primeira_sen), color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        VaultEntryCard(
                            entry = entry,
                            onCopyPassword = {
                                val pw = VaultCrypto.decrypt(entry.encryptedPassword, pin, context) ?: ""
                                copyToClipboard(context, pw, Res.get(R.string.senha_copiada))
                                VaultSession.touch()
                            },
                            onCopyUsername = {
                                copyToClipboard(context, entry.username, Res.get(R.string.usuario_copiado))
                                VaultSession.touch()
                            },
                            onClick = { editEntry = entry; VaultSession.touch() },
                            onMenuClick = { showEntryMenu = entry; VaultSession.touch() }
                        )
                    }
                }
            }
        }
    }

    // ── Add/Edit Dialog ───────────────────────────────────────────────
    if (showAddDialog || editEntry != null) {
        VaultEntryDialog(
            entry = editEntry,
            pin = pin,
            context = context,
            onSave = { newEntry ->
                scope.launch(Dispatchers.IO) {
                    if (editEntry != null) vaultDao.update(newEntry)
                    else vaultDao.insert(newEntry)
                }
                showAddDialog = false
                editEntry = null
            },
            onDismiss = { showAddDialog = false; editEntry = null }
        )
    }

    // ── Entry context menu ────────────────────────────────────────────
    showEntryMenu?.let { entry ->
        AlertDialog(
            onDismissRequest = { showEntryMenu = null },
            containerColor = DarkNavy,
            title = { Text(entry.title, color = OnSurfaceLight) },
            text = {
                Column {
                    listOf(
                        Icons.Default.ContentCopy to Res.get(R.string.copiar_senha) to {
                            val pw = VaultCrypto.decrypt(entry.encryptedPassword, pin, context) ?: ""
                            copyToClipboard(context, pw, Res.get(R.string.senha_copiada))
                        },
                        Icons.Default.Person to Res.get(R.string.copiar_usuario) to {
                            copyToClipboard(context, entry.username, Res.get(R.string.usuario_copiado))
                        },
                        Icons.Default.Edit to Res.get(R.string.editar) to { editEntry = entry },
                        Icons.Default.Delete to Res.get(R.string.excluir) to { showDeleteConfirm = entry }
                    ).forEach { (iconLabel, action) ->
                        val (icon, label) = iconLabel
                        TextButton(
                            onClick = { action(); showEntryMenu = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(icon, null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = OnSurfaceLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ── Delete confirm ────────────────────────────────────────────────
    showDeleteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = DarkNavy,
            title = { Text(Res.get(R.string.excluir_entrada), color = OnSurfaceLight) },
            text = { Text(Res.get(R.string.x_sera_removido_permanentemente, (entry.title)), color = OnSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { vaultDao.delete(entry.id) }
                    showDeleteConfirm = null
                }) { Text(Res.get(R.string.excluir), color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) }
            }
        )
    }

    val totpDao = remember { AppDatabase.getDatabase(context).totpDao() }
    val totpAccounts by totpDao.getAll().collectAsState(initial = emptyList())

    // ── Import / Restore Dialog ───────────────────────────────────────
    if (showImportDialog) {
        VaultImportDialog(
            context = context,
            pin = pin,
            onDismiss = { showImportDialog = false },
            onImportCsv = { csvText ->
                scope.launch(Dispatchers.IO) {
                    val entriesToInsert = parseBitwardenCsv(csvText).map { entry ->
                        val encrypted = if (entry.encryptedPassword.isNotBlank())
                            VaultCrypto.encrypt(entry.encryptedPassword, pin, context)
                        else ""
                        entry.copy(encryptedPassword = encrypted)
                    }
                    vaultDao.insertAll(entriesToInsert)
                }
                showImportDialog = false
            },
            onImportPayload = { payload ->
                scope.launch(Dispatchers.IO) {
                    vaultDao.insertAll(payload.vaultEntries)
                    
                    val existingTotp = totpDao.getAllList()
                    val existingSecrets = existingTotp.map { it.encryptedSecret.uppercase().replace(" ", "").replace("-", "") }.toSet()
                    var insertedTotp = 0
                    var skippedTotp = 0
                    
                    val totpToInsert = mutableListOf<TotpAccount>()
                    payload.totpAccounts.forEach { account ->
                        val cleanSecret = account.encryptedSecret.uppercase().replace(" ", "").replace("-", "")
                        if (!existingSecrets.contains(cleanSecret)) {
                            totpToInsert.add(account.copy(position = existingTotp.size + insertedTotp))
                            insertedTotp++
                        } else {
                            skippedTotp++
                        }
                    }
                    if (totpToInsert.isNotEmpty()) {
                        totpDao.insertAll(totpToInsert)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val msg = Res.get(R.string.x_senhas_e_x_contas_2fa_importadas, (payload.vaultEntries.size), insertedTotp) +
                                if (skippedTotp > 0) Res.get(R.string.x_2fa_duplicadas_ignoradas, skippedTotp) else ""
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
                showImportDialog = false
            }
        )
    }

    // ── Export Dialog ─────────────────────────────────────────────────
    if (showExportDialog) {
        VaultExportDialog(
            entries = entries,
            totpAccounts = totpAccounts,
            pin = pin,
            context = context,
            onDismiss = { showExportDialog = false },
            onNavigateToLocalChat = onNavigateToLocalChat
        )
    }

    // ── Manual / Guia Dialog ──────────────────────────────────────────
    if (showManualDialog) {
        com.example.ui.components.SecurityManualDialog(onDismiss = { showManualDialog = false })
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Vault Entry Card
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun VaultEntryCard(
    entry: VaultEntry,
    onCopyPassword: () -> Unit,
    onCopyUsername: () -> Unit,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val categoryColor = when (entry.category) {
        "card"     -> Color(0xFFF59E0B)
        "note"     -> Color(0xFF22C55E)
        "identity" -> Color(0xFF7C3AED)
        else       -> CyanNeon
    }
    val initial = entry.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = categoryColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (entry.isFavorite) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                    }
                }
                if (entry.username.isNotBlank()) {
                    Text(entry.username, color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (entry.url.isNotBlank()) {
                    Text(entry.url.removePrefix("https://").removePrefix("http://").take(40), color = OnSurfaceDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Quick copy password
            IconButton(onClick = onCopyPassword, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Key, null, tint = categoryColor, modifier = Modifier.size(20.dp))
            }
            // Menu
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = OnSurfaceMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Add/Edit Dialog
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultEntryDialog(
    entry: VaultEntry?,
    pin: String,
    context: Context,
    onSave: (VaultEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(
        entry?.let { VaultCrypto.decrypt(it.encryptedPassword, pin, context) ?: "" } ?: ""
    )}
    var url by remember { mutableStateOf(entry?.url ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: "login") }
    var isFavorite by remember { mutableStateOf(entry?.isFavorite ?: false) }
    var showPassword by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var generatedLen by remember { mutableStateOf(20) }
    var genUppercase by remember { mutableStateOf(true) }
    var genNumbers by remember { mutableStateOf(true) }
    var genSymbols by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (entry == null) Res.get(R.string.nova_entrada) else Res.get(R.string.editar_entrada), color = OnSurfaceLight, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (isFavorite) Color(0xFFF59E0B) else OnSurfaceMuted)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Category selector
                Text("Categoria", color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("login" to "Login", "card" to Res.get(R.string.cartao), "note" to "Nota", "identity" to "Identidade").forEach { (key, label) ->
                        FilterChip(
                            selected = category == key,
                            onClick = { category = key },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                                selectedLabelColor = CyanNeon
                            )
                        )
                    }
                }
                VaultTextField(Res.get(R.string.titulo), title, { title = it }, Icons.Default.Label)
                VaultTextField(Res.get(R.string.usuario_e_mail), username, { username = it }, Icons.Default.Person)
                // Password field with generator
                VaultTextField(
                    label = Res.get(R.string.senha),
                    value = password,
                    onChange = { password = it },
                    icon = Icons.Default.Key,
                    isPassword = !showPassword,
                    trailingActions = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showGenerator = !showGenerator }) {
                            Icon(Icons.Default.AutoFixHigh, null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                        }
                    }
                )
                // Password generator
                AnimatedVisibility(visible = showGenerator) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepDark)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(Res.get(R.string.gerador_de_senha), color = CyanNeon, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Res.get(R.string.tamanho_x, generatedLen), color = OnSurfaceLight, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                            Slider(value = generatedLen.toFloat(), onValueChange = { generatedLen = it.toInt() }, valueRange = 8f..64f, steps = 55, colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon), modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Res.get(R.string.a_z) to genUppercase to { genUppercase = !genUppercase },
                                   "0-9" to genNumbers to { genNumbers = !genNumbers },
                                   "!@#" to genSymbols to { genSymbols = !genSymbols })
                                .forEach { (pair, action) ->
                                    val (label, checked) = pair
                                    FilterChip(selected = checked, onClick = action, label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanNeon.copy(alpha = 0.2f), selectedLabelColor = CyanNeon))
                                }
                        }
                        Button(
                            onClick = { password = generatePassword(generatedLen, genUppercase, genNumbers, genSymbols) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Res.get(R.string.gerar_senha), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                VaultTextField(Res.get(R.string.url_site), url, { url = it }, Icons.Default.Link)
                VaultTextField("Notas", notes, { notes = it }, Icons.Default.Notes, singleLine = false)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val encrypted = if (password.isNotBlank()) VaultCrypto.encrypt(password, pin, context) else ""
                    val now = System.currentTimeMillis()
                    onSave(
                        (entry ?: VaultEntry(title = "")).copy(
                            title = title.trim(),
                            username = username.trim(),
                            encryptedPassword = encrypted,
                            url = url.trim(),
                            notes = notes.trim(),
                            category = category,
                            isFavorite = isFavorite,
                            updatedAt = now
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
            ) { Text(if (entry == null) Res.get(R.string.salvar) else "Atualizar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Import / Export
// ═══════════════════════════════════════════════════════════════════════
// ═══════════════════════════════════════════════════════════════════════
// Biometric Helper for Import/Export
// ═══════════════════════════════════════════════════════════════════════
private fun promptBiometricForAction(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context as? FragmentActivity
    val canBio = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    ) == BiometricManager.BIOMETRIC_SUCCESS

    if (activity != null && canBio) {
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(Res.get(R.string.erro_biometrico_x, msg))
                }
            }
            override fun onAuthenticationFailed() {
                onError(Res.get(R.string.biometria_nao_reconhecida))
            }
        }
        BiometricPrompt(activity, executor, callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(Res.get(R.string.cancelar))
                .build()
        )
    } else {
        // Device doesn't support biometrics or fallback
        onSuccess()
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Import / Export
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultImportDialog(
    context: Context,
    pin: String,
    onDismiss: () -> Unit,
    onImportCsv: (String) -> Unit,
    onImportPayload: (VaultBackupPayload) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=Encrypted (.vyvault), 1=JSON (.json), 2=CSV (Bitwarden)
    var payloadText by remember { mutableStateOf("") }
    var jsonText by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val trimmed = content.trim()
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        selectedTab = 1
                        jsonText = trimmed
                        statusMessage = Res.get(R.string.arquivo_json_carregado)
                    } else if (trimmed.contains(",")) {
                        selectedTab = 2
                        csvText = trimmed
                        statusMessage = Res.get(R.string.arquivo_csv_carregado)
                    } else {
                        selectedTab = 0
                        payloadText = trimmed
                        statusMessage = Res.get(R.string.arquivo_de_backup_vyvault_carregado)
                    }
                }
            } catch (e: Exception) {
                statusMessage = Res.get(R.string.erro_ao_ler_arquivo_x, (e.message))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = { Text(Res.get(R.string.importar_restaurar_backup), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CyanNeon),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                ) {
                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.selecionar_arquivo_json_vyvault_csv), fontWeight = FontWeight.Bold)
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkNavy,
                    contentColor = CyanNeon,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; statusMessage = null },
                        text = { Text(Res.get(R.string.criptografado_vyvault), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; statusMessage = null },
                        text = { Text(Res.get(R.string.json_json), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; statusMessage = null },
                        text = { Text(Res.get(R.string.csv_bitwarden), fontSize = 11.sp) }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        Res.get(R.string.cole_a_chave_de_backup_criptografada_vyv),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = payloadText,
                        onValueChange = { payloadText = it; statusMessage = null },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text(Res.get(R.string.conteudo_do_backup_vyvault)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = CyanNeon, unfocusedBorderColor = DarkBorder
                        )
                    )
                } else if (selectedTab == 1) {
                    Text(
                        Res.get(R.string.cole_o_conteudo_do_backup_em_formato_jso),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it; statusMessage = null },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text(Res.get(R.string.conteudo_json_json)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = CyanNeon, unfocusedBorderColor = DarkBorder
                        )
                    )
                } else {
                    Text(Res.get(R.string.cole_o_conteudo_do_csv_exportado_do_bitw), color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it; statusMessage = null },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text(Res.get(R.string.csv_texto_simples)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = CyanNeon, unfocusedBorderColor = DarkBorder
                        )
                    )
                }

                statusMessage?.let {
                    Text(it, color = if (it.startsWith("✓")) SuccessGreen else DangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            if (payloadText.isBlank()) return@Button
                            promptBiometricForAction(
                                context = context,
                                title = Res.get(R.string.restaurar_backup_criptografado),
                                subtitle = Res.get(R.string.confirme_sua_digital_faceid_para_descrip),
                                onSuccess = {
                                    val decryptedJson = VaultCrypto.decryptBackupPayload(payloadText.trim(), pin)
                                    if (decryptedJson != null) {
                                        val payload = VaultBackupPayload.fromJson(decryptedJson)
                                        if (payload != null) {
                                            onImportPayload(payload)
                                        } else {
                                            statusMessage = Res.get(R.string.formato_de_backup_invalido)
                                        }
                                    } else {
                                        statusMessage = Res.get(R.string.falha_ao_descriptografar_pin_incorreto_o)
                                    }
                                },
                                onError = { err -> statusMessage = err }
                            )
                        }
                        1 -> {
                            if (jsonText.isBlank()) return@Button
                            val payload = VaultBackupPayload.fromJson(jsonText.trim())
                            if (payload != null) {
                                val processedEntries = payload.vaultEntries.map { entry ->
                                    if (entry.encryptedPassword.isNotBlank() && VaultCrypto.decrypt(entry.encryptedPassword, pin, context) == null) {
                                        entry.copy(encryptedPassword = VaultCrypto.encrypt(entry.encryptedPassword, pin, context))
                                    } else entry
                                }
                                onImportPayload(payload.copy(vaultEntries = processedEntries))
                            } else {
                                statusMessage = Res.get(R.string.formato_json_invalido)
                            }
                        }
                        2 -> {
                            if (csvText.isNotBlank()) onImportCsv(csvText)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
            ) { Text(Res.get(R.string.restaurar_importar), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) } }
    )
}

@Composable
private fun VaultExportDialog(
    entries: List<VaultEntry>,
    totpAccounts: List<TotpAccount>,
    pin: String,
    context: Context,
    onDismiss: () -> Unit,
    onNavigateToLocalChat: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=Encrypted (.vyvault), 1=JSON (.json), 2=CSV
    var statusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = { Text(Res.get(R.string.exportar_backup_do_cofre), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkNavy,
                    contentColor = CyanNeon,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; statusMessage = null },
                        text = { Text(Res.get(R.string.criptografado_vyvault), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; statusMessage = null },
                        text = { Text(Res.get(R.string.json_json), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; statusMessage = null },
                        text = { Text(Res.get(R.string.csv_bitwarden_2), fontSize = 11.sp) }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        Res.get(R.string.exporta_x_senhas_e_x_contas_2fa_criptogr, (entries.size), (totpAccounts.size)),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    Text(Res.get(R.string.a_biometria_autoriza_o_envio_criptografi), color = CyanNeon, style = MaterialTheme.typography.labelSmall)
                } else if (selectedTab == 1) {
                    Text(
                        Res.get(R.string.exporta_x_senhas_e_x_contas_2fa_em_forma, (entries.size), (totpAccounts.size)),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    Text(Res.get(R.string.o_arquivo_json_contera_dados_em_texto_es), color = WarningAmber, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(Res.get(R.string.exportar_x_entradas_como_csv_descriptogr, (entries.size)), color = OnSurfaceMuted)
                    Text(Res.get(R.string.o_arquivo_csv_contera_senhas_em_texto_si), color = WarningAmber, style = MaterialTheme.typography.bodySmall)
                }

                statusMessage?.let {
                    Text(it, color = if (it.startsWith("✓")) SuccessGreen else DangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                promptBiometricForAction(
                                    context = context,
                                    title = Res.get(R.string.enviar_via_chat_local_wi_fi),
                                    subtitle = Res.get(R.string.confirme_biometria_para_transmitir_o_bac),
                                    onSuccess = {
                                        val payload = VaultBackupPayload(
                                            version = 1,
                                            exportedAt = System.currentTimeMillis(),
                                            vaultEntries = entries,
                                            totpAccounts = totpAccounts
                                        )
                                        val encryptedPayload = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                        LocalChatRepository.pendingSharedText.value = encryptedPayload
                                        Toast.makeText(context, Res.get(R.string.backup_pronto_selecione_o_aparelho_no_ch), Toast.LENGTH_LONG).show()
                                        onDismiss()
                                        onNavigateToLocalChat?.invoke()
                                    },
                                    onError = { err -> statusMessage = err }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyanNeon),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                        ) {
                            Icon(Icons.Default.Dns, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(Res.get(R.string.chat_local_wi_fi), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                promptBiometricForAction(
                                    context = context,
                                    title = Res.get(R.string.compartilhar_backup_criptografado),
                                    subtitle = Res.get(R.string.confirme_sua_digital_faceid_para_autoriz),
                                    onSuccess = {
                                        val payload = VaultBackupPayload(
                                            version = 1,
                                            exportedAt = System.currentTimeMillis(),
                                            vaultEntries = entries,
                                            totpAccounts = totpAccounts
                                        )
                                        val encryptedPayload = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, encryptedPayload)
                                            putExtra(Intent.EXTRA_TITLE, Res.get(R.string.backup_criptografado_vyvault_2))
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_backup_criptografado)))
                                    },
                                    onError = { err -> statusMessage = err }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyanNeon),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartilhar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            promptBiometricForAction(
                                context = context,
                                title = Res.get(R.string.salvar_backup_criptografado),
                                subtitle = Res.get(R.string.confirme_sua_digital_faceid_para_salvar),
                                onSuccess = {
                                    val payload = VaultBackupPayload(
                                        version = 1,
                                        exportedAt = System.currentTimeMillis(),
                                        vaultEntries = entries,
                                        totpAccounts = totpAccounts
                                    )
                                    val encryptedPayload = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                    try {
                                        val path = saveToVyFolder(context, encryptedPayload, "vyvault")
                                        statusMessage = Res.get(R.string.salvo_em_x, path)
                                    } catch (e: Exception) {
                                        statusMessage = e.message
                                    }
                                },
                                onError = { err -> statusMessage = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Res.get(R.string.salvar_no_aparelho_pasta_vy), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            promptBiometricForAction(
                                context = context,
                                title = Res.get(R.string.exportar_backup_criptografado),
                                subtitle = Res.get(R.string.confirme_sua_digital_faceid_para_autoriz_2),
                                onSuccess = {
                                    val payload = VaultBackupPayload(
                                        version = 1,
                                        exportedAt = System.currentTimeMillis(),
                                        vaultEntries = entries,
                                        totpAccounts = totpAccounts
                                    )
                                    val encryptedPayload = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                    copyToClipboard(context, encryptedPayload, Res.get(R.string.backup_vyvault_copiado))
                                    statusMessage = Res.get(R.string.backup_criptografado_copiado)
                                },
                                onError = { err -> statusMessage = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
                    ) {
                        Text(Res.get(R.string.copiar_vyvault_para_transferencia), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (selectedTab == 1) {
                                promptBiometricForAction(
                                    context = context,
                                    title = Res.get(R.string.salvar_backup_json),
                                    subtitle = Res.get(R.string.confirme_sua_digital_faceid_para_salvar_2),
                                    onSuccess = {
                                        val decryptedEntries = entries.map { entry ->
                                            val pw = VaultCrypto.decrypt(entry.encryptedPassword, pin, context) ?: ""
                                            entry.copy(encryptedPassword = pw)
                                        }
                                        val payload = VaultBackupPayload(
                                            version = 1,
                                            exportedAt = System.currentTimeMillis(),
                                            vaultEntries = decryptedEntries,
                                            totpAccounts = totpAccounts
                                        )
                                        try {
                                            val path = saveToVyFolder(context, payload.toJson(), "json")
                                            statusMessage = Res.get(R.string.salvo_em_x, path)
                                        } catch (e: Exception) {
                                            statusMessage = e.message
                                        }
                                    },
                                    onError = { err -> statusMessage = err }
                                )
                            } else {
                                val csv = buildString {
                                    appendLine("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp")
                                    entries.forEach { e ->
                                        val pw = VaultCrypto.decrypt(e.encryptedPassword, pin, context) ?: ""
                                        appendLine(",${if (e.isFavorite) "1" else "0"},${e.category},\"${e.title}\",\"${e.notes}\",,0,\"${e.url}\",\"${e.username}\",\"${pw}\",")
                                    }
                                }
                                try {
                                    val path = saveToVyFolder(context, csv, "csv")
                                    statusMessage = Res.get(R.string.salvo_em_x, path)
                                } catch (e: Exception) {
                                    statusMessage = e.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanNeon,
                            contentColor = DeepDark
                        )
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (selectedTab == 1) Res.get(R.string.salvar_no_aparelho_pasta_vy) else Res.get(R.string.salvar_csv_pasta_vy),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (selectedTab == 1) {
                                promptBiometricForAction(
                                    context = context,
                                    title = Res.get(R.string.exportar_backup_json),
                                    subtitle = Res.get(R.string.confirme_sua_digital_faceid_para_autoriz_3),
                                    onSuccess = {
                                        val decryptedEntries = entries.map { entry ->
                                            val pw = VaultCrypto.decrypt(entry.encryptedPassword, pin, context) ?: ""
                                            entry.copy(encryptedPassword = pw)
                                        }
                                        val payload = VaultBackupPayload(
                                            version = 1,
                                            exportedAt = System.currentTimeMillis(),
                                            vaultEntries = decryptedEntries,
                                            totpAccounts = totpAccounts
                                        )
                                        copyToClipboard(context, payload.toJson(), Res.get(R.string.backup_json_copiado_para_a_area_de_trans))
                                        statusMessage = Res.get(R.string.backup_json_copiado)
                                    },
                                    onError = { err -> statusMessage = err }
                                )
                            } else {
                                val csv = buildString {
                                    appendLine("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp")
                                    entries.forEach { e ->
                                        val pw = VaultCrypto.decrypt(e.encryptedPassword, pin, context) ?: ""
                                        appendLine(",${if (e.isFavorite) "1" else "0"},${e.category},\"${e.title}\",\"${e.notes}\",,0,\"${e.url}\",\"${e.username}\",\"${pw}\",")
                                    }
                                }
                                copyToClipboard(context, csv, Res.get(R.string.csv_copiado_para_area_de_transferencia))
                                statusMessage = Res.get(R.string.csv_copiado)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) ElectricViolet else WarningAmber,
                            contentColor = if (selectedTab == 1) Color.White else DeepDark
                        )
                    ) {
                        Text(
                            text = if (selectedTab == 1) Res.get(R.string.autorizar_copiar_json) else Res.get(R.string.copiar_csv),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Res.get(R.string.fechar), color = OnSurfaceMuted) } }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Composable helpers
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    icon: ImageVector,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    trailingActions: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        leadingIcon = { Icon(icon, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp)) },
        trailingIcon = trailingActions?.let { { Row { it() } } },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OnSurfaceLight,
            unfocusedTextColor = OnSurfaceLight,
            focusedBorderColor = CyanNeon,
            unfocusedBorderColor = DarkBorder,
            focusedLabelColor = CyanNeon,
            unfocusedLabelColor = OnSurfaceMuted,
            cursorColor = CyanNeon
        )
    )
}

@Composable
private fun VaultSearchBar(query: String, onQuery: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(DarkNavy)
            .border(1.dp, DarkBorder, RoundedCornerShape(23.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceLight),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(Res.get(R.string.buscar_no_cofre), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDim)
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PinDots(value: String, maxLen: Int) {
    Row(horizontalArrangement = Arrangement.Center) {
        val displayLen = minOf(maxLen, 12)
        (0 until displayLen).forEach { i ->
            val filled = i < value.length
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(if (filled) 14.dp else 12.dp)
                    .clip(CircleShape)
                    .background(if (filled) CyanNeon else OnSurfaceDim)
            )
        }
        if (value.length > displayLen) {
            Text(Res.get(R.string.x_displaylen, (value.length - displayLen)), color = CyanNeon, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PinKeyboard(value: String, onChange: (String) -> Unit, onConfirm: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("⌫","0","✓"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    val isAction = key == "⌫" || key == "✓"
                    val bgColor = when (key) {
                        "✓" -> CyanNeon
                        "⌫" -> DarkNavy
                        else -> DarkNavyVariant
                    }
                    val textColor = when (key) {
                        "✓" -> DeepDark
                        else -> OnSurfaceLight
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(1.dp, if (key == "✓") CyanNeon else DarkBorder, CircleShape)
                            .clickable {
                                when (key) {
                                    "⌫" -> if (value.isNotEmpty()) onChange(value.dropLast(1))
                                    "✓" -> onConfirm()
                                    else -> if (value.length < 16) onChange(value + key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Utility functions
// ═══════════════════════════════════════════════════════════════════════
private fun generatePassword(len: Int, uppercase: Boolean, numbers: Boolean, symbols: Boolean): String {
    val lower = "abcdefghijklmnopqrstuvwxyz"
    val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val digits = "0123456789"
    val sym = "!@#\$%^&*()-_=+[]{}|;:,.<>?"
    var pool = lower
    if (uppercase) pool += upper
    if (numbers) pool += digits
    if (symbols) pool += sym
    val rng = SecureRandom()
    return (1..len).map { pool[rng.nextInt(pool.length)] }.joinToString("")
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("vault", text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun parseBitwardenCsv(csv: String): List<VaultEntry> {
    val lines = csv.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    // Try to detect header
    val headerLine = lines[0].lowercase()
    val startIdx = if (headerLine.contains("name") || headerLine.contains("folder")) 1 else 0
    return lines.drop(startIdx).mapNotNull { line ->
        try {
            val cols = splitCsvLine(line)
            // Bitwarden format: folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
            if (cols.size >= 10) {
                VaultEntry(
                    title = cols[3].trim().trim('"'),
                    notes = cols[4].trim().trim('"'),
                    url = cols.getOrNull(7)?.trim()?.trim('"') ?: "",
                    username = cols.getOrNull(8)?.trim()?.trim('"') ?: "",
                    encryptedPassword = cols.getOrNull(9)?.trim()?.trim('"') ?: "", // Will be encrypted before insert
                    isFavorite = cols.getOrNull(1)?.trim() == "1",
                    category = when (cols.getOrNull(2)?.trim()?.lowercase()) {
                        "card" -> "card"
                        "identity" -> "identity"
                        "securenote" -> "note"
                        else -> "login"
                    }
                )
            } else null
        } catch (e: Exception) { null }
    }
}

private fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val current = StringBuilder()
    for (ch in line) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
            else -> current.append(ch)
        }
    }
    result.add(current.toString())
    return result
}
