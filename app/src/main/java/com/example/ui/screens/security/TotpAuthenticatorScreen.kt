package com.example.ui.screens.security
import com.example.ui.screens.chat.LocalChatRepository
import com.example.utils.crypto.TotpSecretCrypto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.AppDatabase
import com.example.data.TotpAccount
import com.example.data.VaultBackupPayload
import com.example.data.VaultEntry
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.example.ui.theme.*
import com.example.utils.TotpGenerator
import com.example.utils.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.utils.findFragmentActivity
import com.example.R
import com.example.Res

// Predefined icon colors for accounts
private val totpColors = listOf(
    Color(0xFF00D4FF), Color(0xFF7C3AED), Color(0xFFF59E0B),
    Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFF97316)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpAuthenticatorScreen(
    onBackClick: () -> Unit,
    onNavigateToLocalChat: (() -> Unit)? = null,
    onNavigateToQrScanner: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val totpDao = remember { db.totpDao() }
    val accounts by totpDao.getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var showQrScanHint by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<TotpAccount?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<TotpAccount?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showQrDialogAccount by remember { mutableStateOf<TotpAccount?>(null) }
    var showSingleExportAccount by remember { mutableStateOf<TotpAccount?>(null) }
    val vaultDao = remember { db.vaultDao() }
    val vaultEntries by vaultDao.getAll().collectAsState(initial = emptyList())

    val prefs = remember { context.getSharedPreferences("tool_settings", Context.MODE_PRIVATE) }
    var requireBiometric by remember { mutableStateOf(prefs.getBoolean("totp_require_biometric", true)) }
    var isUnlocked by remember { mutableStateOf(!requireBiometric || TotpSession.isUnlocked()) }

    LaunchedEffect(isUnlocked) {
        while (isUnlocked) {
            kotlinx.coroutines.delay(1000)
            if (!TotpSession.isUnlocked() && requireBiometric) {
                isUnlocked = false
            }
        }
    }
    var bioError by remember { mutableStateOf<String?>(null) }

    fun triggerBiometricUnlock() {
        bioError = null
        val activity = context.findFragmentActivity() ?: run {
            isUnlocked = true
            return
        }
        val bioManager = BiometricManager.from(context)
        val executor = ContextCompat.getMainExecutor(context)

        val canBioDevice = bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        val canBioWeak = bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        val canBioStrong = bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        if (canBioDevice && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val promptInfo = if (canBioStrong) {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(Res.get(R.string.autenticador_2fa))
                        .setSubtitle(Res.get(R.string.use_a_biometria_ou_senha_para_acessar_os))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()
                } else if (canBioWeak) {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(Res.get(R.string.autenticador_2fa))
                        .setSubtitle(Res.get(R.string.use_a_biometria_ou_senha_para_acessar_os))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()
                } else {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(Res.get(R.string.autenticador_2fa))
                        .setSubtitle(Res.get(R.string.use_a_senha_do_celular_para_acessar_os_c))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()
                }
                val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() { // COF-R04
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(350)
                            TotpSession.unlock()
                            isUnlocked = true
                            bioError = null
                        }
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            bioError = Res.get(R.string.erro_x_5, msg)
                        } else {
                            bioError = Res.get(R.string.autenticacao_cancelada)
                        }
                    }
                    override fun onAuthenticationFailed() {
                        bioError = Res.get(R.string.biometria_nao_reconhecida)
                    }
                })
                prompt.authenticate(promptInfo)
                return
            } catch (e: Exception) {
                // fallback
            }
        }

        if (canBioStrong) {
            try {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(Res.get(R.string.autenticador_2fa))
                    .setSubtitle(Res.get(R.string.use_a_biometria_ou_senha_para_acessar_os))
                    // Pre-Android 11 compatibility
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(350)
                            TotpSession.unlock()
                            isUnlocked = true
                            bioError = null
                        }
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            bioError = Res.get(R.string.erro_x_5, msg)
                        } else {
                            bioError = Res.get(R.string.autenticacao_cancelada)
                        }
                    }
                    override fun onAuthenticationFailed() {
                        bioError = Res.get(R.string.biometria_nao_reconhecida)
                    }
                })
                prompt.authenticate(promptInfo)
                return
            } catch (e: Exception) {
                // fallback
            }
        }

        isUnlocked = true
    }

    LaunchedEffect(requireBiometric) {
        if (requireBiometric && !isUnlocked) {
            delay(150)
            triggerBiometricUnlock()
        }
    }

    // Tick every second to update codes
    var currentMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentMillis = System.currentTimeMillis()
        }
    }

    var showManualDialog by remember { mutableStateOf(false) }

    // Gallery QR Code / Snapshot Picker
    val galleryQrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                com.example.utils.QrSnapshotDecoder.decodeSnapshotUri(context, uri) { raw ->
                    if (!raw.isNullOrBlank()) {
                        processScannedQrCode(raw, scope, totpDao, context)
                    } else {
                        Toast.makeText(context, Res.get(R.string.nenhum_qr_code_encontrado_no_print_foto), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (requireBiometric && !isUnlocked) {
        Scaffold(
            containerColor = DeepDark,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhonelinkLock, null, tint = ElectricViolet, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Res.get(R.string.autenticador_2fa), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ElectricViolet)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = ElectricViolet.copy(alpha = 0.15f),
                        border = BorderStroke(2.dp, ElectricViolet)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        Res.get(R.string.autenticacao_requerida),
                        color = OnSurfaceLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Res.get(R.string.o_autenticador_totp_esta_protegido_use_a),
                        color = OnSurfaceMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (bioError != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            bioError!!,
                            color = DangerRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { triggerBiometricUnlock() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(Modifier.width(8.dp))
                        Text(Res.get(R.string.desbloquear_com_biometria), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhonelinkLock, null, tint = ElectricViolet, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Res.get(R.string.autenticador_2fa), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ElectricViolet)
                    }
                },
                actions = {
                    IconButton(onClick = { galleryQrLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, Res.get(R.string.importar_print_screenshot), tint = ElectricViolet)
                    }
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.MoreVert, Res.get(R.string.configuracoes_e_opcoes), tint = OnSurfaceLight)
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                            containerColor = DarkNavy
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(Res.get(R.string.pedir_biometria_ao_abrir), color = OnSurfaceLight, fontSize = 14.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Switch(
                                            checked = requireBiometric,
                                            onCheckedChange = { newValue ->
                                                requireBiometric = newValue
                                                prefs.edit().putBoolean("totp_require_biometric", newValue).apply()
                                                if (!newValue) isUnlocked = true
                                                Toast.makeText(
                                                    context,
                                                    if (newValue) Res.get(R.string.biometria_ao_abrir_ativada) else Res.get(R.string.biometria_ao_abrir_desativada),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor  = DeepDark,
                                                checkedTrackColor  = ElectricViolet,
                                                uncheckedThumbColor = OnSurfaceDim,
                                                uncheckedTrackColor = DarkNavyVariant
                                            )
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Fingerprint, null, tint = ElectricViolet) },
                                onClick = {
                                    val newValue = !requireBiometric
                                    requireBiometric = newValue
                                    prefs.edit().putBoolean("totp_require_biometric", newValue).apply()
                                    if (!newValue) isUnlocked = true
                                    Toast.makeText(
                                        context,
                                        if (newValue) Res.get(R.string.biometria_ao_abrir_ativada) else Res.get(R.string.biometria_ao_abrir_desativada),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            HorizontalDivider(color = DarkBorder)
                            DropdownMenuItem(
                                text = { Text(Res.get(R.string.exportar_backup_criptografado), color = OnSurfaceLight) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = CyanNeon) },
                                onClick = {
                                    showSettingsMenu = false
                                    showExportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Res.get(R.string.restaurar_backup_criptografado), color = OnSurfaceLight) },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = ElectricViolet) },
                                onClick = {
                                    showSettingsMenu = false
                                    showImportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Res.get(R.string.manual_e_instrucoes), color = OnSurfaceLight) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = OnSurfaceMuted) },
                                onClick = {
                                    showSettingsMenu = false
                                    showManualDialog = true
                                }
                            )
                            if (accounts.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(Res.get(R.string.apagar_todas_as_contas_2fa), color = DangerRed, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = DangerRed) },
                                    onClick = {
                                        showSettingsMenu = false
                                        showDeleteAllConfirm = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ElectricViolet,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(Res.get(R.string.adicionar_conta), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.PhonelinkLock, null, tint = OnSurfaceDim, modifier = Modifier.size(72.dp))
                    Text(Res.get(R.string.nenhuma_conta_2fa_cadastrada), color = OnSurfaceMuted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    // Import snapshot direct banner
                    Button(
                        onClick = { showQrScanHint = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(Res.get(R.string.adicionar_conta_google_auth_qr), fontWeight = FontWeight.Bold)
                    }

                    Text(Res.get(R.string.se_voce_esta_no_mesmo_celular_tire_um_pr), color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(DeepDark),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Quick Snapshot import header card
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { galleryQrLauncher.launch("image/*") },
                        colors = CardDefaults.cardColors(containerColor = DarkNavy),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = ElectricViolet, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Res.get(R.string.importar_screenshot_do_google_auth), color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(Res.get(R.string.no_mesmo_celular_escolha_a_foto_print_do), color = OnSurfaceMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = ElectricViolet, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                itemsIndexed(accounts, key = { _, a -> a.id }) { idx, account ->
                    TotpCard(
                        account = account,
                        currentMillis = currentMillis,
                        colorIndex = idx % totpColors.size,
                        onCopy = { code ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("totp", code))
                            Toast.makeText(context, Res.get(R.string.codigo_copiado), Toast.LENGTH_SHORT).show()
                        },
                        onEdit = { editAccount = account },
                        onShowQr = {
                            promptBio(
                                context = context,
                                title = Res.get(R.string.exibir_qr_code),
                                subtitle = Res.get(R.string.autentique_para_visualizar_o_qr_code_des),
                                onSuccess = { showQrDialogAccount = account },
                                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        onShare = {
                            promptBio(
                                context = context,
                                title = Res.get(R.string.exportar_conta),
                                subtitle = Res.get(R.string.autentique_para_exportar_esta_conta),
                                onSuccess = { showSingleExportAccount = account },
                                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        onDelete = { showDeleteConfirm = account }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }

    // ── Add Dialog ────────────────────────────────────────────────────
    if (showAddDialog || editAccount != null) {
        TotpAddDialog(
            existingAccount = editAccount,
            onSave = { account ->
                scope.launch(Dispatchers.IO) {
                    if (editAccount != null) totpDao.update(account)
                    else totpDao.insert(account.copy(position = accounts.size))
                }
                showAddDialog = false
                editAccount = null
            },
            onScanQr = {
                showAddDialog = false
                showQrScanHint = true
            },
            onDismiss = { showAddDialog = false; editAccount = null }
        )
    }

    // ── QR Scanner & Migration Dialog ─────────────────────────────────
    if (showQrScanHint) {
        AlertDialog(
            onDismissRequest = { showQrScanHint = false },
            containerColor = DarkNavy,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = ElectricViolet)
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.importar_qr_code_google_auth), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Res.get(R.string.escolha_como_importar_seu_qr_code_ou_lot), color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)

                    // Option 1: Gallery Screenshot
                    Button(
                        onClick = {
                            showQrScanHint = false
                            galleryQrLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Res.get(R.string.selecionar_foto_screenshot_da_galeria), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Option 1.5: QR Scanner App
                    Button(
                        onClick = {
                            showQrScanHint = false
                            onNavigateToQrScanner?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                        border = BorderStroke(1.dp, ElectricViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp), tint = ElectricViolet)
                        Spacer(Modifier.width(8.dp))
                        Text(Res.get(R.string.escanear_com_a_camera_leitor_do_app), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElectricViolet)
                    }

                    // Option 2: Paste Clipboard
                    OutlinedButton(
                        onClick = {
                            try {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    showQrScanHint = false
                                    processScannedQrCode(clip, scope, totpDao, context)
                                } else {
                                    Toast.makeText(context, Res.get(R.string.area_de_transferencia_vazia), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, Res.get(R.string.erro_ao_ler_area_de_transferencia), Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Res.get(R.string.colar_da_area_de_transferencia), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }

                    // Option 3: Manual Entry
                    OutlinedButton(
                        onClick = {
                            showQrScanHint = false
                            showAddDialog = true
                        },
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Res.get(R.string.digitar_chave_secreta_manualmente), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showQrScanHint = false }) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) } }
        )
    }

    // ── Delete Confirm ────────────────────────────────────────────────
    showDeleteConfirm?.let { account ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = DarkNavy,
            title = { Text(Res.get(R.string.remover_conta), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
            text = { Text(Res.get(R.string.x_x_sera_removida_n_ncertifique_se_de_te, (account.issuer), (account.accountName)), color = OnSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { totpDao.delete(account.id) }
                    showDeleteConfirm = null
                }) { Text(Res.get(R.string.remover), color = DangerRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) } }
        )
    }
    // ── Delete All Confirm ─────────────────────────────────────────────
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            containerColor = DarkNavy,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = DangerRed)
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.apagar_todas_as_contas_2fa_2), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    Res.get(R.string.esta_acao_removera_permanentemente_todas, (accounts.size)),
                    color = OnSurfaceMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            totpDao.deleteAll()
                        }
                        showDeleteAllConfirm = false
                        Toast.makeText(context, Res.get(R.string.todas_as_contas_2fa_foram_removidas), Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(Res.get(R.string.sim_apagar_tudo), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(Res.get(R.string.cancelar), color = OnSurfaceMuted)
                }
            }
        )
    }

    // ── Export Dialog ─────────────────────────────────────────────────
    if (showExportDialog) {
        TotpExportDialog(
            accounts = accounts,
            vaultEntries = vaultEntries,
            context = context,
            onDismiss = { showExportDialog = false },
            onNavigateToLocalChat = onNavigateToLocalChat
        )
    }

    // ── Import Dialog ─────────────────────────────────────────────────
    if (showImportDialog) {
        TotpImportDialog(
            context = context,
            onDismiss = { showImportDialog = false },
            onImportPayload = { payload ->
                scope.launch(Dispatchers.IO) {
                    val existingTotp = totpDao.getAllList()
                    val existingSecrets = existingTotp.map { it.encryptedSecret.uppercase().replace(" ", "").replace("-", "") }.toSet()
                    var insertedTotp = 0
                    var skippedTotp = 0
                    payload.totpAccounts.forEach { acc ->
                        val cleanSecret = acc.encryptedSecret.uppercase().replace(" ", "").replace("-", "")
                        if (!existingSecrets.contains(cleanSecret)) {
                            totpDao.insert(acc.copy(position = existingTotp.size + insertedTotp))
                            insertedTotp++
                        } else {
                            skippedTotp++
                        }
                    }
                    payload.vaultEntries.forEach { entry -> vaultDao.insert(entry) }
                    withContext(Dispatchers.Main) {
                        val msg = Res.get(R.string.x_contas_2fa_e_x_senhas_importadas, insertedTotp, (payload.vaultEntries.size)) +
                                if (skippedTotp > 0) Res.get(R.string.x_2fa_duplicadas_ignoradas, skippedTotp) else ""
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
                showImportDialog = false
            }
        )
    }

    // ── Manual Dialog ─────────────────────────────────────────────────
    if (showManualDialog) {
        com.example.ui.components.SecurityManualDialog(onDismiss = { showManualDialog = false })
    }

    // ── Single QR Code Dialog ──────────────────────────────────────────
    showQrDialogAccount?.let { acc ->
        TotpSingleQrDialog(
            account = acc,
            context = context,
            onDismiss = { showQrDialogAccount = null }
        )
    }

    // ── Single Export Dialog ──────────────────────────────────────────
    showSingleExportAccount?.let { acc ->
        TotpSingleExportDialog(
            account = acc,
            context = context,
            onDismiss = { showSingleExportAccount = null },
            onNavigateToLocalChat = onNavigateToLocalChat
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TOTP Import / Export Dialogs with Biometric Authentication
// ═══════════════════════════════════════════════════════════════════════
private fun promptBio(context: Context, title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val activity = context.findFragmentActivity()
    val canBio = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    if (activity != null && canBio) {
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(350)
                    onSuccess()
                }
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) onError(Res.get(R.string.erro_x_5, msg))
            }
            override fun onAuthenticationFailed() { onError(Res.get(R.string.biometria_nao_reconhecida)) }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setNegativeButtonText(Res.get(R.string.cancelar)).build())
    } else {
        onSuccess()
    }
}

@Composable
private fun TotpExportDialog(
    accounts: List<TotpAccount>,
    vaultEntries: List<VaultEntry>,
    context: Context,
    onDismiss: () -> Unit,
    onNavigateToLocalChat: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=Encrypted (.vyvault), 1=JSON (.json)
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }

    var selectedAccountIds by remember { mutableStateOf(accounts.map { it.id }.toSet()) }
    var selectedVaultIds by remember { mutableStateOf(vaultEntries.map { it.id }.toSet()) }
    var showSelectFilter by remember { mutableStateOf(false) }

    fun getExportPayload(): VaultBackupPayload {
        val selectedTotp = accounts.filter { selectedAccountIds.contains(it.id) }
        val selectedVault = vaultEntries.filter { selectedVaultIds.contains(it.id) }
        return VaultBackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            vaultEntries = selectedVault,
            totpAccounts = selectedTotp
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = { Text(Res.get(R.string.exportar_backup_2fa_e_cofre), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkNavy,
                    contentColor = ElectricViolet
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; statusMsg = null },
                        text = { Text(Res.get(R.string.criptografado_vyvault), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; statusMsg = null },
                        text = { Text(Res.get(R.string.json_json), fontSize = 11.sp) }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        Res.get(R.string.exportar_x_x_contas_2fa_e_x_x_senhas_sal, (selectedAccountIds.size), (accounts.size), (selectedVaultIds.size), (vaultEntries.size)),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text(Res.get(R.string.pin_senha_para_protecao_do_backup_portat)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder
                        )
                    )
                } else {
                    Text(
                        Res.get(R.string.exportar_x_x_contas_2fa_e_x_x_senhas_sal_2, (selectedAccountIds.size), (accounts.size), (selectedVaultIds.size), (vaultEntries.size)),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    Text(Res.get(R.string.o_arquivo_json_contera_dados_em_texto_si), color = WarningAmber, style = MaterialTheme.typography.bodySmall)
                }

                if (accounts.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showSelectFilter = !showSelectFilter },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                    ) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (showSelectFilter) Res.get(R.string.ocultar_selecao_x_x_2fa, (selectedAccountIds.size), (accounts.size))
                            else Res.get(R.string.selecionar_contas_especificas_x_x, (selectedAccountIds.size), (accounts.size)),
                            fontSize = 11.sp
                        )
                    }

                    if (showSelectFilter) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(Res.get(R.string.selecione_quais_contas_incluir), color = OnSurfaceMuted, fontSize = 11.sp)
                            TextButton(
                                onClick = {
                                    selectedAccountIds = if (selectedAccountIds.size == accounts.size) emptySet() else accounts.map { it.id }.toSet()
                                }
                            ) {
                                Text(
                                    if (selectedAccountIds.size == accounts.size) Res.get(R.string.desmarcar_todas) else Res.get(R.string.marcar_todas),
                                    fontSize = 11.sp, color = CyanNeon
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(accounts) { _, acc ->
                                val isSelected = selectedAccountIds.contains(acc.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedAccountIds = if (isSelected) selectedAccountIds - acc.id else selectedAccountIds + acc.id
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedAccountIds = if (checked) selectedAccountIds + acc.id else selectedAccountIds - acc.id
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ElectricViolet)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        Res.get(R.string.x_x_26, (acc.issuer), (acc.accountName)),
                                        color = if (isSelected) OnSurfaceLight else OnSurfaceDim,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                statusMsg?.let { Text(it, color = if (it.startsWith("✓")) SuccessGreen else DangerRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (pin.length < 4) {
                                    statusMsg = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos)
                                    return@OutlinedButton
                                }
                                val payload = getExportPayload()
                                if (payload.totpAccounts.isEmpty() && payload.vaultEntries.isEmpty()) {
                                    statusMsg = Res.get(R.string.selecione_pelo_menos_1_conta_para_export)
                                    return@OutlinedButton
                                }
                                promptBio(
                                    context = context,
                                    title = Res.get(R.string.enviar_via_chat_local_wi_fi),
                                    subtitle = Res.get(R.string.autentique_para_transmitir_o_backup_para),
                                    onSuccess = {
                                        val encrypted = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                        LocalChatRepository.pendingSharedText.value = encrypted
                                        Toast.makeText(context, Res.get(R.string.backup_pronto_selecione_o_aparelho_no_ch), Toast.LENGTH_LONG).show()
                                        onDismiss()
                                        onNavigateToLocalChat?.invoke()
                                    },
                                    onError = { err -> statusMsg = err }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ElectricViolet),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                        ) {
                            Icon(Icons.Default.Dns, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(Res.get(R.string.chat_local_wi_fi), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (pin.length < 4) {
                                    statusMsg = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos)
                                    return@OutlinedButton
                                }
                                val payload = getExportPayload()
                                if (payload.totpAccounts.isEmpty() && payload.vaultEntries.isEmpty()) {
                                    statusMsg = Res.get(R.string.selecione_pelo_menos_1_conta_para_export)
                                    return@OutlinedButton
                                }
                                promptBio(
                                    context = context,
                                    title = Res.get(R.string.compartilhar_backup_criptografado),
                                    subtitle = Res.get(R.string.autentique_para_autorizar_o_compartilham),
                                    onSuccess = {
                                        val encrypted = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, encrypted)
                                            putExtra(Intent.EXTRA_TITLE, Res.get(R.string.backup_2fa_criptografado_vyvault))
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_backup_2fa)))
                                    },
                                    onError = { err -> statusMsg = err }
                                )
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ElectricViolet),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartilhar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (pin.length < 4) {
                                statusMsg = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos)
                                return@Button
                            }
                            val payload = getExportPayload()
                            if (payload.totpAccounts.isEmpty() && payload.vaultEntries.isEmpty()) {
                                statusMsg = Res.get(R.string.selecione_pelo_menos_1_conta_para_export)
                                return@Button
                            }
                            promptBio(
                                context = context,
                                title = Res.get(R.string.salvar_backup_2fa),
                                subtitle = Res.get(R.string.autentique_para_salvar_o_backup_criptogr),
                                onSuccess = {
                                    val encrypted = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                    try {
                                        val path = saveToVyFolder(context, encrypted, "vyvault")
                                        statusMsg = Res.get(R.string.salvo_em_x, path)
                                    } catch (e: Exception) {
                                        statusMsg = e.message
                                    }
                                },
                                onError = { err -> statusMsg = err }
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
                            if (pin.length < 4) {
                                statusMsg = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos)
                                return@Button
                            }
                            val payload = getExportPayload()
                            if (payload.totpAccounts.isEmpty() && payload.vaultEntries.isEmpty()) {
                                statusMsg = Res.get(R.string.selecione_pelo_menos_1_conta_para_export)
                                return@Button
                            }
                            promptBio(
                                context = context,
                                title = Res.get(R.string.copiar_backup_2fa),
                                subtitle = Res.get(R.string.autentique_para_copiar_o_backup_criptogr),
                                onSuccess = {
                                    val encrypted = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("vyvault", encrypted))
                                    statusMsg = Res.get(R.string.backup_criptografado_copiado)
                                },
                                onError = { err -> statusMsg = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Text(Res.get(R.string.copiar_vyvault_area_de_transferencia), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val payload = getExportPayload()
                            if (payload.totpAccounts.isEmpty() && payload.vaultEntries.isEmpty()) {
                                statusMsg = Res.get(R.string.selecione_pelo_menos_1_conta_para_export)
                                return@Button
                            }
                            promptBio(
                                context = context,
                                title = Res.get(R.string.salvar_backup_json),
                                subtitle = Res.get(R.string.autentique_para_salvar_o_arquivo_json_de),
                                onSuccess = {
                                    try {
                                        val path = saveToVyFolder(context, payload.toJson(), "json")
                                        statusMsg = Res.get(R.string.salvo_em_x, path)
                                    } catch (e: Exception) {
                                        statusMsg = e.message
                                    }
                                },
                                onError = { err -> statusMsg = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Res.get(R.string.salvar_no_aparelho_pasta_vy), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val payload = getExportPayload()
                            if (payload.totpAccounts.isEmpty() && payload.vaultEntries.isEmpty()) {
                                statusMsg = Res.get(R.string.selecione_pelo_menos_1_conta_para_export)
                                return@Button
                            }
                            promptBio(
                                context = context,
                                title = Res.get(R.string.copiar_backup_json),
                                subtitle = Res.get(R.string.autentique_para_copiar_o_arquivo_json_de),
                                onSuccess = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("json", payload.toJson()))
                                    statusMsg = Res.get(R.string.backup_json_copiado)
                                },
                                onError = { err -> statusMsg = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Text(Res.get(R.string.copiar_json_area_de_transferencia), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Res.get(R.string.fechar), color = OnSurfaceMuted) } }
    )
}

@Composable
private fun TotpImportDialog(
    context: Context,
    onDismiss: () -> Unit,
    onImportPayload: (VaultBackupPayload) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0=Encrypted (.vyvault), 1=JSON (.json)
    var encryptedInput by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

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
                        jsonInput = trimmed
                        statusMsg = Res.get(R.string.arquivo_json_carregado)
                    } else {
                        selectedTab = 0
                        encryptedInput = trimmed
                        statusMsg = Res.get(R.string.arquivo_vyvault_carregado)
                    }
                }
            } catch (e: Exception) {
                statusMsg = Res.get(R.string.erro_ao_ler_arquivo_x, (e.message))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = { Text(Res.get(R.string.restaurar_importar_backup), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, ElectricViolet),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                ) {
                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.selecionar_arquivo_json_ou_vyvault), fontWeight = FontWeight.Bold)
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkNavy,
                    contentColor = ElectricViolet
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; statusMsg = null },
                        text = { Text(Res.get(R.string.criptografado_vyvault), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; statusMsg = null },
                        text = { Text(Res.get(R.string.json_json), fontSize = 11.sp) }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        Res.get(R.string.cole_o_codigo_criptografado_vyvault_para),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = encryptedInput,
                        onValueChange = { encryptedInput = it },
                        label = { Text(Res.get(R.string.codigo_de_backup_vyvault)) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder
                        )
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text(Res.get(R.string.pin_mestre_usado_na_criacao_do_backup)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder
                        )
                    )
                } else {
                    Text(
                        Res.get(R.string.cole_o_codigo_do_backup_em_formato_json),
                        color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text(Res.get(R.string.conteudo_json_json)) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder
                        )
                    )
                }

                statusMsg?.let { Text(it, color = if (it.startsWith("✓")) SuccessGreen else DangerRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        if (encryptedInput.isBlank() || pin.isBlank()) {
                            statusMsg = Res.get(R.string.preencha_o_codigo_do_backup_e_o_pin)
                            return@Button
                        }
                        promptBio(
                            context = context,
                            title = Res.get(R.string.restaurar_backup_2fa),
                            subtitle = Res.get(R.string.autentique_com_biometria_para_autorizar),
                            onSuccess = {
                                val decrypted = VaultCrypto.decryptBackupPayload(encryptedInput.trim(), pin)
                                if (decrypted != null) {
                                    val payload = VaultBackupPayload.fromJson(decrypted)
                                    if (payload != null) {
                                        onImportPayload(payload)
                                    } else { statusMsg = Res.get(R.string.formato_de_backup_invalido) }
                                } else {
                                    statusMsg = Res.get(R.string.pin_incorreto_ou_dados_corrompidos)
                                }
                            },
                            onError = { err -> statusMsg = err }
                        )
                    } else {
                        if (jsonInput.isBlank()) {
                            statusMsg = Res.get(R.string.insira_o_conteudo_json_ou_escolha_um_arq)
                            return@Button
                        }
                        val payload = VaultBackupPayload.fromJson(jsonInput.trim())
                        if (payload != null) {
                            onImportPayload(payload)
                        } else {
                            statusMsg = Res.get(R.string.formato_json_invalido_2)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) { Text(Res.get(R.string.autorizar_restaurar), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) } }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// TOTP Card
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun TotpCard(
    account: TotpAccount,
    currentMillis: Long,
    colorIndex: Int,
    onCopy: (String) -> Unit,
    onEdit: () -> Unit,
    onShowQr: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = totpColors[colorIndex]
    val rawSecret = remember(account.encryptedSecret) {
        com.example.utils.crypto.TotpSecretCrypto.decryptSecret(account.encryptedSecret)
    }
    val code = remember(rawSecret, currentMillis / 1000 / account.period) {
        try {
            TotpGenerator.generate(
                secret = rawSecret,
                digits = account.digits,
                period = account.period,
                algorithm = account.algorithm,
                epochMillis = currentMillis
            )
        } catch (e: Exception) { "------" }
    }
    val timeRemaining = TotpGenerator.timeRemaining(account.period, currentMillis)
    val progress = TotpGenerator.timeProgress(account.period, currentMillis)
    val isExpiring = timeRemaining <= 5

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "totp_progress"
    )

    val initial = account.issuer.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (isExpiring) 0.7f else 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with circular progress
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressRing(progress = animatedProgress, color = accentColor, isExpiring = isExpiring)
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initial, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(account.issuer, color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account.accountName, color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                // Code display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (firstHalf, secondHalf) = if (code.length == 6) {
                        code.substring(0, 3) to code.substring(3)
                    } else {
                        code.substring(0, code.length / 2) to code.substring(code.length / 2)
                    }
                    Text(
                        text = Res.get(R.string.x_x_27, firstHalf, secondHalf),
                        color = if (isExpiring) DangerRed else accentColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Res.get(R.string.xs_4, (timeRemaining)),
                        color = if (isExpiring) DangerRed else OnSurfaceMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // Actions
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onCopy(code.replace(" ", "")) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = DarkNavy
                    ) {
                        DropdownMenuItem(
                            text = { Text(Res.get(R.string.exibir_qr_code), color = OnSurfaceLight) },
                            leadingIcon = { Icon(Icons.Default.QrCode, null, tint = ElectricViolet) },
                            onClick = { showMenu = false; onShowQr() }
                        )
                        DropdownMenuItem(
                            text = { Text(Res.get(R.string.exportar_esta_conta), color = OnSurfaceLight) },
                            leadingIcon = { Icon(Icons.Default.Share, null, tint = CyanNeon) },
                            onClick = { showMenu = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text(Res.get(R.string.editar), color = OnSurfaceLight) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = OnSurfaceMuted) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        HorizontalDivider(color = DarkBorder)
                        DropdownMenuItem(
                            text = { Text(Res.get(R.string.remover), color = DangerRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerRed) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Circular Progress Ring
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun CircularProgressRing(progress: Float, color: Color, isExpiring: Boolean) {
    val animColor by animateColorAsState(
        targetValue = if (isExpiring) DangerRed else color,
        animationSpec = tween(500),
        label = "ring_color"
    )
    Box(
        modifier = Modifier
            .size(54.dp)
            .drawWithContent {
                drawContent()
                val strokeWidth = 3.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                // Background ring
                drawCircle(
                    color = animColor.copy(alpha = 0.15f),
                    radius = radius,
                    style = Stroke(strokeWidth)
                )
                // Foreground arc
                drawArc(
                    color = animColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (1f - progress),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Add/Edit Account Dialog
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotpAddDialog(
    existingAccount: TotpAccount?,
    onSave: (TotpAccount) -> Unit,
    onScanQr: () -> Unit,
    onDismiss: () -> Unit
) {
    var issuer by remember { mutableStateOf(existingAccount?.issuer ?: "") }
    var accountName by remember { mutableStateOf(existingAccount?.accountName ?: "") }
    var secret by remember { mutableStateOf(existingAccount?.let { com.example.utils.crypto.TotpSecretCrypto.decryptSecret(it.encryptedSecret) } ?: "") }
    var algorithm by remember { mutableStateOf(existingAccount?.algorithm ?: "SHA1") }
    var digits by remember { mutableStateOf(existingAccount?.digits?.toString() ?: "6") }
    var period by remember { mutableStateOf(existingAccount?.period?.toString() ?: "30") }
    var showSecret by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var otpUri by remember { mutableStateOf("") }
    var uriError by remember { mutableStateOf<String?>(null) }
    var previewCode by remember { mutableStateOf("") }

    // Parse URI helper
    fun parseUri() {
        val result = TotpGenerator.parseOtpAuthUri(otpUri.trim())
        if (result != null) {
            issuer = result.issuer
            accountName = result.account
            secret = result.secret
            algorithm = result.algorithm
            digits = result.digits.toString()
            period = result.period.toString()
            uriError = null
        } else {
            uriError = Res.get(R.string.uri_invalida_formato_otpauth_totp)
        }
    }

    // Preview code when secret changes
    LaunchedEffect(secret, algorithm, digits, period) {
        if (secret.isNotBlank()) {
            previewCode = try {
                TotpGenerator.generate(
                    secret = secret,
                    digits = digits.toIntOrNull() ?: 6,
                    period = period.toIntOrNull() ?: 30,
                    algorithm = algorithm
                )
            } catch (e: Exception) { Res.get(R.string.chave_invalida) }
        } else {
            previewCode = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Text(
                if (existingAccount == null) Res.get(R.string.adicionar_conta_2fa) else Res.get(R.string.editar_conta),
                color = OnSurfaceLight, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── QR Code URI input ──────────────────────────────────
                Text(Res.get(R.string.cole_uri_do_qr_code_otpauth), color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = otpUri,
                        onValueChange = { otpUri = it; uriError = null },
                        modifier = Modifier.weight(1f),
                        label = { Text(Res.get(R.string.uri_otpauth)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = ElectricViolet, unfocusedLabelColor = OnSurfaceMuted, cursorColor = ElectricViolet
                        )
                    )
                    IconButton(
                        onClick = { parseUri() },
                        modifier = Modifier.clip(CircleShape).background(ElectricViolet.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = ElectricViolet)
                    }
                }
                uriError?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }

                OutlinedButton(
                    onClick = onScanQr,
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.escanear_qr_code), fontWeight = FontWeight.Medium)
                }

                HorizontalDivider(color = DarkBorder)

                // ── Manual input ───────────────────────────────────────
                Text(Res.get(R.string.inserir_manualmente), color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                TotpTextField(Res.get(R.string.emissor_ex_github), issuer, { issuer = it }, Icons.Default.Business)
                TotpTextField(Res.get(R.string.conta_ex_user_email_com), accountName, { accountName = it }, Icons.Default.AlternateEmail)
                // Secret key
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it.uppercase().replace(" ", "") },
                        modifier = Modifier.weight(1f),
                        label = { Text(Res.get(R.string.chave_secreta_base32)) },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Key, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = ElectricViolet, unfocusedLabelColor = OnSurfaceMuted, cursorColor = ElectricViolet
                        )
                    )
                }

                // Preview code
                if (previewCode.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricViolet.copy(alpha = 0.1f))
                            .border(1.dp, ElectricViolet.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Res.get(R.string.codigo_de_verificacao), color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                            val (f, s) = if (previewCode.length == 6) previewCode.substring(0, 3) to previewCode.substring(3)
                                         else previewCode to ""
                            Text(Res.get(R.string.x_x_28, f, s), color = ElectricViolet, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 3.sp)
                        }
                    }
                }

                // Advanced options
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceMuted)
                ) {
                    Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAdvanced) Res.get(R.string.ocultar_avancado) else Res.get(R.string.opcoes_avancadas), style = MaterialTheme.typography.bodySmall)
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Algoritmo", color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SHA1", "SHA256", "SHA512").forEach { alg ->
                                FilterChip(
                                    selected = algorithm == alg,
                                    onClick = { algorithm = alg },
                                    label = { Text(alg, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricViolet.copy(alpha = 0.2f),
                                        selectedLabelColor = VioletLight
                                    )
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Res.get(R.string.digitos), color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("6", "8").forEach { d ->
                                        FilterChip(selected = digits == d, onClick = { digits = d }, label = { Text(d) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet.copy(alpha = 0.2f), selectedLabelColor = VioletLight))
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Res.get(R.string.periodo_s), color = OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("30", "60").forEach { p ->
                                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet.copy(alpha = 0.2f), selectedLabelColor = VioletLight))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (issuer.isBlank() || secret.isBlank()) return@Button
                    onSave(
                        (existingAccount ?: TotpAccount(issuer = "", accountName = "", encryptedSecret = "")).copy(
                            issuer = issuer.trim(),
                            accountName = accountName.trim(),
                            encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(secret.trim()),
                            algorithm = algorithm,
                            digits = digits.toIntOrNull() ?: 6,
                            period = period.toIntOrNull() ?: 30
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) { Text(if (existingAccount == null) Res.get(R.string.adicionar) else Res.get(R.string.salvar), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Res.get(R.string.cancelar), color = OnSurfaceMuted) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotpTextField(label: String, value: String, onChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(icon, null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
            focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder,
            focusedLabelColor = ElectricViolet, unfocusedLabelColor = OnSurfaceMuted, cursorColor = ElectricViolet
        )
    )
}

private fun processScannedQrCode(
    qrText: String,
    scope: kotlinx.coroutines.CoroutineScope,
    totpDao: com.example.data.TotpDao,
    context: Context
) {
    val clean = qrText.trim()
    if (clean.startsWith("otpauth-migration://")) {
        val accounts = TotpGenerator.parseGoogleAuthMigrationUri(clean)
        if (accounts.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val existing = totpDao.getAllList()
                val existingSecrets = existing.map { com.example.utils.crypto.TotpSecretCrypto.decryptSecret(it.encryptedSecret).uppercase().replace(" ", "").replace("-", "") }.toSet()
                var inserted = 0
                var skipped = 0

                accounts.forEach { params ->
                    val cleanSecret = params.secret.uppercase().replace(" ", "").replace("-", "")
                    if (!existingSecrets.contains(cleanSecret)) {
                        totpDao.insert(
                            TotpAccount(
                                issuer = params.issuer,
                                accountName = params.account,
                                encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(params.secret),
                                algorithm = params.algorithm,
                                digits = params.digits,
                                period = params.period,
                                position = existing.size + inserted
                            )
                        )
                        inserted++
                    } else {
                        skipped++
                    }
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (inserted > 0 && skipped > 0) {
                        Toast.makeText(context, Res.get(R.string.x_conta_s_2fa_importada_s_x_ja_existente, inserted, skipped), Toast.LENGTH_LONG).show()
                    } else if (inserted > 0) {
                        Toast.makeText(context, Res.get(R.string.x_conta_s_2fa_importada_s_do_google_auth, inserted), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, Res.get(R.string.i_todas_as_x_conta_s_deste_lote_ja_estav, skipped), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, Res.get(R.string.falha_ao_ler_lote_do_google_authenticato), Toast.LENGTH_SHORT).show()
        }
    } else if (clean.startsWith("otpauth://")) {
        val params = TotpGenerator.parseOtpAuthUri(clean)
        if (params != null) {
            scope.launch(Dispatchers.IO) {
                val existing = totpDao.getAllList()
                val cleanSecret = params.secret.uppercase().replace(" ", "").replace("-", "")
                val exists = existing.any { com.example.utils.crypto.TotpSecretCrypto.decryptSecret(it.encryptedSecret).uppercase().replace(" ", "").replace("-", "") == cleanSecret }
                
                if (!exists) {
                    totpDao.insert(
                        TotpAccount(
                            issuer = params.issuer,
                            accountName = params.account,
                            encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(params.secret),
                            algorithm = params.algorithm,
                            digits = params.digits,
                            period = params.period,
                            position = existing.size
                        )
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.get(R.string.conta_2fa_x_adicionada_2, (params.issuer)), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.get(R.string.i_a_conta_x_ja_esta_cadastrada_2, (params.issuer)), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, Res.get(R.string.uri_otpauth_invalida), Toast.LENGTH_SHORT).show()
        }
    } else {
        val cleanSecret = clean.replace(" ", "").replace("-", "").uppercase()
        if (cleanSecret.length >= 8) {
            scope.launch(Dispatchers.IO) {
                val existing = totpDao.getAllList()
                val exists = existing.any { com.example.utils.crypto.TotpSecretCrypto.decryptSecret(it.encryptedSecret).uppercase().replace(" ", "").replace("-", "") == cleanSecret }
                
                if (!exists) {
                    totpDao.insert(
                        TotpAccount(
                            issuer = Res.get(R.string.n_2fa_importado),
                            accountName = Res.get(R.string.conta_2fa),
                            encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(cleanSecret),
                            position = existing.size
                        )
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.get(R.string.chave_2fa_importada), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.get(R.string.i_esta_chave_2fa_ja_esta_cadastrada), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, Res.get(R.string.conteudo_do_qr_code_invalido), Toast.LENGTH_SHORT).show()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Single Account QR Code Display Dialog
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotpSingleQrDialog(
    account: TotpAccount,
    context: Context,
    onDismiss: () -> Unit
) {
    val rawSecret = remember(account.encryptedSecret) {
        com.example.utils.crypto.TotpSecretCrypto.decryptSecret(account.encryptedSecret)
    }
    val otpauthUri = remember(account, rawSecret) {
        "otpauth://totp/${Uri.encode(account.issuer)}:${Uri.encode(account.accountName)}?secret=$rawSecret&issuer=${Uri.encode(account.issuer)}&algorithm=${account.algorithm}&digits=${account.digits}&period=${account.period}"
    }

    val qrBitmap = remember(otpauthUri) {
        try {
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                otpauthUri,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512,
                512
            )
            val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, null, tint = ElectricViolet, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(account.issuer, color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(account.accountName, color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = Res.get(R.string.qr_code_2fa),
                            modifier = Modifier.size(220.dp).padding(12.dp)
                        )
                    }
                } else {
                    Text(Res.get(R.string.erro_ao_gerar_qr_code), color = DangerRed)
                }

                Text(
                    Res.get(R.string.escaneie_este_qr_code_com_o_aplicativo_2),
                    color = OnSurfaceMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("otpauth", otpauthUri))
                    Toast.makeText(context, Res.get(R.string.uri_otpauth_copiada), Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Res.get(R.string.copiar_uri), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Res.get(R.string.fechar), color = OnSurfaceMuted)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Single Account Export Dialog (.vyvault)
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotpSingleExportDialog(
    account: TotpAccount,
    context: Context,
    onDismiss: () -> Unit,
    onNavigateToLocalChat: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = {
            Text(Res.get(R.string.compartilhar_conta_2fa), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Res.get(R.string.exportando_conta_x_x, (account.issuer), (account.accountName)),
                    color = OnSurfaceLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    Res.get(R.string.defina_uma_senha_pin_de_4_digitos_para_p),
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text(Res.get(R.string.pin_senha_de_protecao)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                        focusedBorderColor = ElectricViolet, unfocusedBorderColor = DarkBorder
                    )
                )
                statusMsg?.let {
                    Text(it, color = if (it.startsWith("✓")) SuccessGreen else DangerRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        if (pin.length < 4) {
                            statusMsg = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos)
                            return@OutlinedButton
                        }
                        val payload = VaultBackupPayload(
                            version = 1,
                            exportedAt = System.currentTimeMillis(),
                            totpAccounts = listOf(account)
                        )
                        val encrypted = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                        LocalChatRepository.pendingSharedText.value = encrypted
                        Toast.makeText(context, Res.get(R.string.conta_pronta_selecione_o_destino_no_chat), Toast.LENGTH_LONG).show()
                        onDismiss()
                        onNavigateToLocalChat?.invoke()
                    },
                    border = BorderStroke(1.dp, ElectricViolet),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                ) {
                    Text(Res.get(R.string.chat_local), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (pin.length < 4) {
                            statusMsg = Res.get(R.string.pin_deve_ter_pelo_menos_4_digitos)
                            return@Button
                        }
                        val payload = VaultBackupPayload(
                            version = 1,
                            exportedAt = System.currentTimeMillis(),
                            totpAccounts = listOf(account)
                        )
                        val encrypted = VaultCrypto.encryptBackupPayload(payload.toJson(), pin)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, encrypted)
                            putExtra(Intent.EXTRA_TITLE, Res.get(R.string.conta_2fa_criptografada_x, (account.issuer)))
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_conta_2fa)))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                ) {
                    Text("Compartilhar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Res.get(R.string.cancelar), color = OnSurfaceMuted)
            }
        }
    )
}

fun saveToVyFolder(context: Context, content: String, extension: String): String {
    val fileName = "vy_backup_${System.currentTimeMillis()}.$extension"
    return try {
        val publicDocs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val vyFolder = java.io.File(publicDocs, Res.get(R.string.vyknifeswiss))
        if (!vyFolder.exists()) vyFolder.mkdirs()
        val file = java.io.File(vyFolder, fileName)
        file.writeText(content)
        file.absolutePath
    } catch (e: Exception) {
        try {
            val appDocs = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            val vyFolder = java.io.File(appDocs, Res.get(R.string.vyknifeswiss))
            if (!vyFolder.exists()) vyFolder.mkdirs()
            val file = java.io.File(vyFolder, fileName)
            file.writeText(content)
            file.absolutePath
        } catch (e2: Exception) {
            throw Exception(Res.get(R.string.erro_ao_salvar_x_2, (e2.message)))
        }
    }
}
