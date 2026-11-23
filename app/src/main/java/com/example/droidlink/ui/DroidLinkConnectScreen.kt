package com.example.droidlink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.droidlink.client.DroidLinkClientState
import com.example.droidlink.client.DroidLinkClientViewModel
import com.example.droidlink.client.SavedHostProfile
import com.example.ui.theme.*
import com.example.R
import com.example.Res

/**
 * Connection screen for the DroidLink client (technician side).
 *
 * Renders a form to enter IP, port, and password, plus a row of recently
 * used hosts. Navigates to [DroidLinkConsoleScreen] on successful auth.
 *
 * @param viewModel Caller-provided ViewModel (typically from viewModel()).
 * @param onBack    Navigate back to the tool list / home.
 * @param onConnected Called when [DroidLinkClientState.Connected] is reached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidLinkConnectScreen(
    viewModel: DroidLinkClientViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    val clientState by viewModel.clientState.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()

    // Navigate to console as soon as we reach Connected
    LaunchedEffect(clientState) {
        if (clientState is DroidLinkClientState.Connected) onConnected()
    }

    // Form state
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8989") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val isLoading = clientState is DroidLinkClientState.Connecting ||
        clientState is DroidLinkClientState.Authenticating
    val errorMessage = (clientState as? DroidLinkClientState.Error)?.message

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = Res.get(R.string.droidlink_cliente),
                        fontFamily = FontFamily.Monospace,
                        color = CyanNeon
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = OnSurfaceLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── IP field ──────────────────────────────────────────────────
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text(Res.get(R.string.ip_do_servidor), color = OnSurfaceMuted) },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceLight
                ),
                colors = outlinedFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = Res.get(R.string.suporta_ip_local_wi_fi_192_168_x_x_e_tai), // DLK-R10
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )

            // ── Port field ────────────────────────────────────────────────
            OutlinedTextField(
                value = port,
                onValueChange = { v -> if (v.length <= 5) port = v.filter { it.isDigit() } },
                label = { Text("Porta", color = OnSurfaceMuted) },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceLight
                ),
                colors = outlinedFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Password field ────────────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(Res.get(R.string.senha), color = OnSurfaceMuted) },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showPassword) Res.get(R.string.ocultar_senha) else Res.get(R.string.mostrar_senha),
                            tint = OnSurfaceMuted
                        )
                    }
                },
                colors = outlinedFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = Res.get(R.string.aceita_a_senha_aleatoria_da_sessao_ou_a),
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )

            // ── Error message ─────────────────────────────────────────────
            AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = DangerRed,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DangerRed.copy(alpha = 0.08f), MaterialTheme.shapes.small)
                            .padding(10.dp)
                    )
                }
            }

            // ── Connect button ────────────────────────────────────────────
            Button(
                onClick = {
                    val portNum = port.toIntOrNull() ?: 8989
                    viewModel.connect(host, portNum, password)
                },
                enabled = !isLoading && host.isNotBlank() && port.isNotBlank() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = DeepDark,
                    disabledContainerColor = SuccessGreen.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = DeepDark,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (clientState is DroidLinkClientState.Authenticating)
                            Res.get(R.string.autenticando) else Res.get(R.string.conectando),
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text("Conectar", fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                }
            }

            // ── Recent hosts ──────────────────────────────────────────────
            if (recentHosts.isNotEmpty()) {
                Text(
                    Res.get(R.string.hosts_recentes),
                    color = OnSurfaceMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentHosts) { profile ->
                        RecentHostChip(
                            profile = profile,
                            enabled = !isLoading,
                            onClick = {
                                host = profile.host
                                port = profile.port.toString()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun RecentHostChip(
    profile: SavedHostProfile,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label = profile.label?.let { Res.get(R.string.x_x_x, it, (profile.host), (profile.port)) }
        ?: Res.get(R.string.x_x_3, (profile.host), (profile.port))

    SuggestionChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = CyanNeon
            )
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = CyanNeon,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = CyanNeonAlpha,
            iconContentColor = CyanNeon,
            labelColor = CyanNeon
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = true,
            borderColor = CyanNeonDim
        )
    )
}

// ── Color helpers ────────────────────────────────────────────────────────────

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanNeon,
    unfocusedBorderColor = DarkBorder,
    focusedLabelColor = CyanNeon,
    unfocusedLabelColor = OnSurfaceMuted,
    cursorColor = CyanNeon,
    focusedContainerColor = DarkNavy,
    unfocusedContainerColor = DarkNavy,
)
