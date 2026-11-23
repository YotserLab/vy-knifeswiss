package com.example.droidlink.ui
import com.example.utils.net.NetworkUtils

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.navigation.NavController
import com.example.droidlink.client.QualityMode
import com.example.droidlink.model.DroidLinkSettings
import com.example.droidlink.server.ConnectedClientRecord
import com.example.droidlink.server.DroidLinkServerState
import com.example.droidlink.server.DroidLinkServerViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CyanNeonAlpha
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.DeepDark
import com.example.ui.theme.OnSurfaceLight
import com.example.ui.theme.OnSurfaceMuted
import com.example.ui.theme.SuccessGreen
import com.example.R
import com.example.Res

// ─────────────────────────────────────────────────────────────────────────────
// DroidLinkServerPanel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DroidLinkServerPanel(
    navController: NavController,
    viewModel: DroidLinkServerViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val serverState = uiState.serverState
    val connectionHistory by viewModel.connectionHistory.collectAsState()
    val sessionSettings by viewModel.settings.collectAsState()

    // Initial load of settings and permissions
    LaunchedEffect(Unit) {
        viewModel.refreshPermissionsAndPrivileges(context)
        viewModel.loadSavedSettings(context)
        viewModel.refreshConnectionHistory(context)
    }

    LaunchedEffect(serverState) {
        viewModel.refreshConnectionHistory(context)
    }

    // Local mutable UI state
    var port by rememberSaveable { mutableStateOf(uiState.port.toString()) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var auditLogExpanded by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // MediaProjection launcher — called after startServer()
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            // Forward the grant intent to the foreground service
            val serviceIntent = Intent(context, Class.forName("com.example.droidlink.server.DroidLinkServerService")).apply {
                action = "com.example.droidlink.ACTION_MEDIA_PROJECTION_GRANT"
                putExtra("resultCode", result.resultCode)
                putExtra("data", data)
            }
            context.startService(serviceIntent)
            viewModel.addAuditLog(Res.get(R.string.mediaprojection_concedida_pelo_usuario))
        } else {
            viewModel.addAuditLog(Res.get(R.string.mediaprojection_negada_pelo_usuario))
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Res.get(R.string.droidlink_servidor),
                            color = OnSurfaceLight,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = OnSurfaceLight
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = Res.get(R.string.ajuda_de_permissoes),
                            tint = CyanNeon
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkNavy,
                    scrolledContainerColor = DarkNavy
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {

            // ── 1. Readiness Checklist ─────────────────────────────────
            item {
                ReadinessChecklistCard(
                    uiState = uiState,
                    context = context,
                    viewModel = viewModel,
                    onOpenHelp = { showHelpDialog = true }
                )
            }

            // ── 2. Server Settings ──────────────────────────────
            item {
                ServerConfigCard(
                    port = port,
                    onPortChange = { port = it },
                    ephemeralPassword = uiState.displayPassword,
                    ephemeralVisible = passwordVisible,
                    onToggleEphemeralVisible = { passwordVisible = !passwordVisible },
                    onRegenerateEphemeral = { viewModel.regeneratePassword() },
                    fixedPassword = uiState.fixedPassword,
                    onSaveFixedPassword = { newPass -> viewModel.setFixedPassword(context, newPass) },
                    onClearFixedPassword = { viewModel.setFixedPassword(context, "") }
                )
            }

            // ── 3. Session Settings (AnyDesk Style) ───────────────
            item {
                SessionSettingsCard(
                    settings = sessionSettings,
                    onUpdateSettings = { newSettings -> viewModel.updateSettings(context, newSettings) }
                )
            }

            // ── 4. Connection IPs (Wi-Fi and Tailscale VPN) ─────────────────
            item {
                val (ips, tailscaleIp) = when (val s = serverState) {
                    is DroidLinkServerState.Listening -> Pair(s.localIps, s.tailscaleIp ?: uiState.tailscaleIp)
                    is DroidLinkServerState.ClientConnected -> Pair(listOf(s.clientIp), uiState.tailscaleIp)
                    else -> Pair(
                        com.example.droidlink.server.DroidLinkServerService.resolveLocalIps(context),
                        uiState.tailscaleIp ?: com.example.utils.net.NetworkUtils.getTailscaleIpAddressSync()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!tailscaleIp.isNullOrBlank()) {
                        TailscaleVpnCard(tailscaleIp = tailscaleIp, context = context)
                    }
                    if (ips.isNotEmpty()) {
                        WifiIpsRow(ips = ips, context = context)
                    }
                }
            }

            // ── 4. Start / Stop Button ──────────────────────────────────
            item {
                ServerToggleButton(
                    serverState = serverState,
                    onStart = {
                        val portInt = port.toIntOrNull() ?: 8989
                        viewModel.startServer(context, portInt, uiState.displayPassword)
                        // Request MediaProjection after service starts
                        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                as MediaProjectionManager
                        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                    },
                    onStop = { viewModel.stopServer(context) }
                )
            }

            // ── 5. Session Status Card (only when ClientConnected) ──
            item {
                val connectedState = serverState as? DroidLinkServerState.ClientConnected
                AnimatedVisibility(
                    visible = connectedState != null,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    connectedState?.let { state ->
                        SessionStatusCard(state = state)
                    }
                }
            }

            // ── 6. Connected Devices History ───────────────────
            item {
                ConnectionHistoryCard(
                    history = connectionHistory,
                    onClear = { viewModel.clearConnectionHistory(context) },
                    context = context
                )
            }

            // ── 7. Audit Log ───────────────────────────────────────
            item {
                AuditLogCard(
                    entries = uiState.auditLog.takeLast(20),
                    expanded = auditLogExpanded,
                    onToggleExpand = { auditLogExpanded = !auditLogExpanded }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showHelpDialog) {
        DroidLinkHelpDialog(
            onDismiss = { showHelpDialog = false },
            context = context
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Readiness Checklist
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadinessChecklistCard(
    uiState: com.example.droidlink.server.DroidLinkServerUiState,
    context: Context,
    viewModel: DroidLinkServerViewModel,
    onOpenHelp: () -> Unit
) {
    // Live-read permissions
    val accessibilityGranted = com.example.droidlink.server.RemoteInputAccessibilityService.isRunning
    val notificationsGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.areNotificationsEnabled()
        } else {
            true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = BorderStroke(1.dp, CyanNeon)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Res.get(R.string.prontidao_do_sistema),
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanNeon,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onOpenHelp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Res.get(R.string.como_liberar), style = MaterialTheme.typography.labelSmall, color = CyanNeon, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))

            // MediaProjection — always shows Grant (requires ActivityResult)
            PermissionRow(
                label = Res.get(R.string.captura_de_tela_mediaprojection),
                granted = false, // always requires explicit grant at start
                alwaysShowGrant = true,
                onGrant = null // handled by main button
            )

            Spacer(Modifier.height(8.dp))

            // Accessibility
            PermissionRow(
                label = Res.get(R.string.servico_de_acessibilidade),
                granted = accessibilityGranted,
                onGrant = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    viewModel.addAuditLog(Res.get(R.string.redirecionado_para_configuracoes_de_aces))
                }
            )

            Spacer(Modifier.height(8.dp))

            // Notifications
            PermissionRow(
                label = Res.get(R.string.notificacoes),
                granted = notificationsGranted,
                onGrant = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(intent)
                    viewModel.addAuditLog(Res.get(R.string.redirecionado_para_configuracoes_de_noti))
                }
            )

            Spacer(Modifier.height(12.dp))

            // Privilege badge
            PrivilegeBadge(level = uiState.privilegeLevel)
        }
    }
}

@Composable
private fun DroidLinkHelpDialog(onDismiss: () -> Unit, context: Context) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = CyanNeon)
                Spacer(Modifier.width(8.dp))
                Text(Res.get(R.string.como_liberar_o_controle_remoto), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    Res.get(R.string.para_que_outro_aparelho_controle_este_ce),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )

                // Box Samsung
                Surface(
                    color = DarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Res.get(R.string.no_samsung_one_ui_android_1316), fontWeight = FontWeight.Bold, color = CyanNeon, style = MaterialTheme.typography.labelMedium)
                        Text(
                            Res.get(R.string.n_1_se_o_botao_de_acessibilidade_estiver_c) +
                            Res.get(R.string.abra_configuracoes_aplicativos_vy_swiss) +
                            Res.get(R.string.toque_nos_3_pontinhos_no_canto_superior) +
                            Res.get(R.string.toque_em_permitir_configuracoes_restrita) +
                            Res.get(R.string.n_2_depois_va_em_acessibilidade_aplicativo),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceLight
                        )
                    }
                }

                // Box Xiaomi
                Surface(
                    color = DarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Res.get(R.string.na_xiaomi_miui_hyperos), fontWeight = FontWeight.Bold, color = AmberGold, style = MaterialTheme.typography.labelMedium)
                        Text(
                            Res.get(R.string.nas_opcoes_do_desenvolvedor_ative_depura) +
                            Res.get(R.string.em_acessibilidade_apps_baixados_ative_vy),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceLight
                        )
                    }
                }

                // Box Bateria
                Surface(
                    color = DarkNavy,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Res.get(R.string.bateria_em_segundo_plano), fontWeight = FontWeight.Bold, color = SuccessGreen, style = MaterialTheme.typography.labelMedium)
                        Text(
                            Res.get(R.string.defina_o_uso_de_bateria_do_app_como_sem),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceLight
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
            ) {
                Text(Res.get(R.string.abrir_acessibilidade), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onDismiss()
                }
            ) {
                Text(Res.get(R.string.info_do_app_restricoes), color = OnSurfaceLight)
            }
        },
        containerColor = DarkNavy
    )
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    alwaysShowGrant: Boolean = false,
    onGrant: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                granted -> Icons.Default.Check
                alwaysShowGrant -> Icons.Default.Info
                else -> Icons.Default.Warning
            },
            contentDescription = null,
            tint = when {
                granted -> SuccessGreen
                alwaysShowGrant -> AmberGold
                else -> Color(0xFFEF4444)
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = OnSurfaceLight,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!granted || alwaysShowGrant) {
            onGrant?.let { action ->
                TextButton(
                    onClick = action,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Conceder",
                        color = CyanNeon,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (alwaysShowGrant && onGrant == null) {
                Text(
                    text = Res.get(R.string.via_iniciar),
                    color = AmberGold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivilegeBadge(level: com.example.droidlink.server.PrivilegeLevel) {
    val (label, color) = when (level) {
        com.example.droidlink.server.PrivilegeLevel.STANDARD -> Res.get(R.string.padrao) to CyanNeon
        com.example.droidlink.server.PrivilegeLevel.SHIZUKU -> "SHIZUKU" to AmberGold
        com.example.droidlink.server.PrivilegeLevel.ROOT -> "ROOT" to SuccessGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = Res.get(R.string.privilegio),
            color = OnSurfaceMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Server Settings
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServerConfigCard(
    port: String,
    onPortChange: (String) -> Unit,
    ephemeralPassword: String,
    ephemeralVisible: Boolean,
    onToggleEphemeralVisible: () -> Unit,
    onRegenerateEphemeral: () -> Unit,
    fixedPassword: String,
    onSaveFixedPassword: (String) -> Unit,
    onClearFixedPassword: () -> Unit
) {
    var fixedInput by remember(fixedPassword) { mutableStateOf(fixedPassword) }
    var fixedVisible by rememberSaveable { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyanNeon,
        unfocusedBorderColor = OnSurfaceMuted.copy(alpha = 0.4f),
        focusedTextColor = OnSurfaceLight,
        unfocusedTextColor = OnSurfaceLight,
        cursorColor = CyanNeon,
        focusedLabelColor = CyanNeon,
        unfocusedLabelColor = OnSurfaceMuted
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Res.get(R.string.configuracoes_de_servidor),
                style = MaterialTheme.typography.titleSmall,
                color = OnSurfaceLight,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // Port field
            OutlinedTextField(
                value = port,
                onValueChange = { value ->
                    if (value.all { it.isDigit() } && value.length <= 5) {
                        onPortChange(value)
                    }
                },
                label = { Text(Res.get(R.string.porta_tcp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = textFieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ── 1. Random Password (Temporary) ───────────────────────────
            Text(
                text = Res.get(R.string.n_1_senha_aleatoria_sessao_atual),
                style = MaterialTheme.typography.labelMedium,
                color = CyanNeon,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ephemeralPassword,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(Res.get(R.string.senha_aleatoria)) },
                    singleLine = true,
                    visualTransformation = if (ephemeralVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleEphemeralVisible) {
                            Icon(
                                imageVector = if (ephemeralVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (ephemeralVisible) Res.get(R.string.ocultar_senha) else Res.get(R.string.mostrar_senha),
                                tint = OnSurfaceMuted
                            )
                        }
                    },
                    colors = textFieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onRegenerateEphemeral,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanNeonAlpha)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = Res.get(R.string.gerar_nova_senha_aleatoria),
                        tint = CyanNeon
                    )
                }
            }
            Text(
                text = Res.get(R.string.troca_automaticamente_a_cada_reinicio_id),
                color = OnSurfaceMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(16.dp))

            // ── 2. Senha Fixa (Permanente / Desassistido) ─────────────────
            Text(
                text = Res.get(R.string.n_2_senha_fixa_acesso_permanente_desassist),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = fixedInput,
                onValueChange = { fixedInput = it },
                label = { Text(Res.get(R.string.senha_fixa_persistente)) },
                singleLine = true,
                visualTransformation = if (fixedVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { fixedVisible = !fixedVisible }) {
                        Icon(
                            imageVector = if (fixedVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (fixedVisible) Res.get(R.string.ocultar_senha) else Res.get(R.string.mostrar_senha),
                            tint = OnSurfaceMuted
                        )
                    }
                },
                colors = textFieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onSaveFixedPassword(fixedInput) },
                    enabled = fixedInput.isNotBlank() && fixedInput != fixedPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DarkNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(Res.get(R.string.salvar_senha_fixa), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (fixedPassword.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            fixedInput = ""
                            onClearFixedPassword()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(Res.get(R.string.remover), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (fixedPassword.isNotBlank()) Res.get(R.string.senha_fixa_salva_com_criptografia_aes_25)
                       else Res.get(R.string.nenhuma_senha_fixa_salva_defina_uma_para),
                color = if (fixedPassword.isNotBlank()) SuccessGreen else OnSurfaceMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(14.dp))
            Surface(
                color = DarkNavy.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = Res.get(R.string.i_o_servidor_aceita_tanto_a_senha_tempor),
                    color = AmberGold,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session Settings (AnyDesk Style)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionSettingsCard(
    settings: DroidLinkSettings,
    onUpdateSettings: (DroidLinkSettings) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = Res.get(R.string.configuracoes_da_sessao_anydesk),
                            style = MaterialTheme.typography.titleSmall,
                            color = OnSurfaceLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Res.get(R.string.audio_video_fluidez_e_permissoes_de_cont),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        tint = OnSurfaceMuted
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Section 1: Audio & Sound (always visible, collapsed or expanded) ──
            Surface(
                color = DeepDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (settings.transmitAudio) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (settings.transmitAudio) CyanNeon else OnSurfaceMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = Res.get(R.string.transmitir_audio_do_sistema),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceLight
                                )
                                Text(
                                    text = Res.get(R.string.transmite_som_de_midias_videos_e_jogos_p),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = settings.transmitAudio,
                            onCheckedChange = { onUpdateSettings(settings.copy(transmitAudio = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyanNeon,
                                checkedTrackColor = CyanNeonAlpha
                            )
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = DarkNavy)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = if (settings.keepServerSpeakerActive) Color(0xFF38BDF8) else OnSurfaceMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = Res.get(R.string.manter_som_no_alto_falante_do_celular),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceLight
                                )
                                Text(
                                    text = if (settings.keepServerSpeakerActive)
                                        Res.get(R.string.o_som_continua_tocando_normalmente_neste)
                                    else
                                        Res.get(R.string.silencia_este_aparelho_durante_a_sessao),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (settings.keepServerSpeakerActive) SuccessGreen else AmberGold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = settings.keepServerSpeakerActive,
                            onCheckedChange = { onUpdateSettings(settings.copy(keepServerSpeakerActive = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF38BDF8),
                                checkedTrackColor = Color(0xFF0369A1).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ── Section 2: Video Quality & Performance ──
                    Surface(
                        color = DeepDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = Res.get(R.string.qualidade_de_video_fluidez),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceLight
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QualityMode.entries.forEach { mode ->
                                    val isSelected = settings.qualityMode == mode
                                    SuggestionChip(
                                        onClick = { onUpdateSettings(settings.copy(qualityMode = mode, targetFps = when(mode) {
                                            QualityMode.LOW_LATENCY -> 20
                                            QualityMode.BALANCED -> 30
                                            QualityMode.HIGH_DETAIL -> 60
                                        })) },
                                        label = {
                                            Text(
                                                text = when(mode) {
                                                    QualityMode.LOW_LATENCY -> "Velocidade"
                                                    QualityMode.BALANCED -> "Equilibrado"
                                                    QualityMode.HIGH_DETAIL -> Res.get(R.string.hd_60fps)
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) DarkNavy else OnSurfaceLight
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isSelected) CyanNeon else CyanNeonAlpha
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = if (isSelected) CyanNeon else CyanNeon.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // ── Section 3: Remote Control Permissions ──
                    Surface(
                        color = DeepDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = Res.get(R.string.permissoes_de_controle_do_cliente),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceLight
                                )
                            }
                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Res.get(R.string.permitir_toque_gestos_remotos), style = MaterialTheme.typography.bodySmall, color = OnSurfaceLight)
                                Switch(
                                    checked = settings.allowRemoteInput,
                                    onCheckedChange = { onUpdateSettings(settings.copy(allowRemoteInput = it)) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeonAlpha)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Res.get(R.string.permitir_botoes_do_sistema_home_voltar), style = MaterialTheme.typography.bodySmall, color = OnSurfaceLight)
                                Switch(
                                    checked = settings.allowSystemActions,
                                    onCheckedChange = { onUpdateSettings(settings.copy(allowSystemActions = it)) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeonAlpha)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Res.get(R.string.manter_tela_ligada_durante_a_sessao), style = MaterialTheme.typography.bodySmall, color = OnSurfaceLight)
                                Switch(
                                    checked = settings.keepScreenAwake,
                                    onCheckedChange = { onUpdateSettings(settings.copy(keepScreenAwake = it)) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeonAlpha)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Network IPs (Tailscale and Wi-Fi)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TailscaleVpnCard(tailscaleIp: String, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF38BDF8))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = Res.get(R.string.tailscale_vpn),
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Res.get(R.string.tailscale_vpn_acesso_remoto),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tailscaleIp,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceLight
                )
                Text(
                    text = Res.get(R.string.toque_para_copiar_conecte_de_redes_exter),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(Res.get(R.string.tailscale_ip), tailscaleIp))
                    Toast.makeText(context, Res.get(R.string.ip_tailscale_copiado), Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = Res.get(R.string.copiar_ip_tailscale),
                    tint = Color(0xFF38BDF8)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiIpsRow(ips: List<String>, context: Context) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = Res.get(R.string.ips_wi_fi_rede_local),
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceMuted,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ips.forEach { ip ->
                SuggestionChip(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(Res.get(R.string.ip_wi_fi), ip))
                        Toast.makeText(context, Res.get(R.string.ip_x_copiado, ip), Toast.LENGTH_SHORT).show()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = ip,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = CyanNeon
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = CyanNeonAlpha
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = CyanNeon.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Start / Stop Server Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServerToggleButton(
    serverState: DroidLinkServerState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isRunning = serverState is DroidLinkServerState.ClientConnected
            || serverState is DroidLinkServerState.Listening
    val isStarting = serverState is DroidLinkServerState.Starting

    val buttonColor = when {
        isRunning -> DangerRed
        isStarting -> AmberGold
        else -> SuccessGreen
    }
    val buttonText = when {
        isRunning -> Res.get(R.string.parar_servidor_kill_switch)
        isStarting -> Res.get(R.string.iniciando)
        else -> Res.get(R.string.iniciar_servidor)
    }
    val buttonIcon = when {
        isRunning -> Icons.Default.Close
        else -> Icons.Default.Cast
    }

    Button(
        onClick = { if (isRunning) onStop() else if (!isStarting) onStart() },
        enabled = !isStarting,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = buttonColor.copy(alpha = 0.5f),
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        )
    ) {
        Icon(
            imageVector = buttonIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }

    // Error state hint
    if (serverState is DroidLinkServerState.Error) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = Res.get(R.string.erro_x, (serverState.message)),
            color = DangerRed,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session Status Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionStatusCard(state: DroidLinkServerState.ClientConnected) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = BorderStroke(1.dp, CyanNeon)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SuccessGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Res.get(R.string.sessao_ativa),
                    color = CyanNeon,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))

            SessionMetricRow(label = "Cliente", value = state.clientIp)
            Spacer(Modifier.height(4.dp))
            SessionMetricRow(label = "FPS", value = Res.get(R.string.x_4, (state.fps)))
            Spacer(Modifier.height(4.dp))
            SessionMetricRow(label = Res.get(R.string.latencia), value = Res.get(R.string.x_ms, (state.latencyMs)))
        }
    }
}

@Composable
private fun SessionMetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = Res.get(R.string.x_5, label),
            color = OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            color = CyanNeon,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client Connection History
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionHistoryCard(
    history: List<ConnectedClientRecord>,
    onClear: () -> Unit,
    context: Context
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Res.get(R.string.historico_de_conexoes_x, (history.size)),
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurfaceLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = Res.get(R.string.limpar_historico),
                                tint = DangerRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Recolher" else "Expandir",
                            tint = OnSurfaceMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Res.get(R.string.nenhum_dispositivo_conectou_a_este_servi),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
            } else {
                val displayList = if (expanded) history else history.take(3)
                Spacer(Modifier.height(8.dp))
                displayList.forEachIndexed { index, record ->
                    if (index > 0) {
                        Spacer(Modifier.height(6.dp))
                    }
                    Surface(
                        color = DeepDark.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = record.ip,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (record.ip.startsWith("100.")) Color(0xFF38BDF8) else CyanNeon
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = if (record.authType == Res.get(R.string.senha_fixa)) Color(0xFF1E3A8A) else Color(0xFF451A03),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = record.authType,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (record.authType == Res.get(R.string.senha_fixa)) Color(0xFF93C5FD) else Color(0xFFFDE68A),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(record.connectedAt))
                                val durationText = if (record.disconnectedAt != null) {
                                    val secs = record.durationSeconds
                                    val m = secs / 60
                                    val s = secs % 60
                                    Res.get(R.string.x_duracao, if (m > 0) "${m}m ${s}s" else Res.get(R.string.xs, (s)))
                                } else {
                                    Res.get(R.string.em_andamento)
                                }
                                Text(
                                    text = Res.get(R.string.xx, dateStr, durationText),
                                    fontSize = 10.sp,
                                    color = OnSurfaceMuted
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(Res.get(R.string.ip_conectado), record.ip))
                                    Toast.makeText(context, Res.get(R.string.ip_x_copiado_2, (record.ip)), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = Res.get(R.string.copiar_ip),
                                    tint = OnSurfaceMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (!expanded && history.size > 3) {
                    TextButton(
                        onClick = { expanded = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                    ) {
                        Text(
                            text = Res.get(R.string.ver_mais_x_3_conexoes, (history.size - 3)),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanNeon
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audit Log
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuditLogCard(
    entries: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkNavy)
    ) {
        Column {
            // Header row — tap to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Res.get(R.string.log_de_auditoria),
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceLight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Res.get(R.string.x_entradas, (entries.size)),
                    color = OnSurfaceMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepDark)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            text = Res.get(R.string.nenhuma_entrada_de_log_ainda),
                            color = OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            fontSize = 11.sp
                        )
                    } else {
                        entries.forEachIndexed { idx, entry ->
                            Text(
                                text = entry,
                                color = if (idx % 2 == 0) OnSurfaceLight else OnSurfaceMuted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
