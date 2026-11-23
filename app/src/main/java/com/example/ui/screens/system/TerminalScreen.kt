package com.example.ui.screens.system

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.example.ui.theme.*
import com.example.ui.components.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import com.example.utils.net.SshSessionHelper
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.ChannelExec
import com.example.R
import com.example.Res

object SshManager {
    var session: Session? = null
    var debugLog = mutableListOf<String>()

    fun connect(profile: SshConnectionProfile, timeout: Int = 15000): String? {
        debugLog.clear()
        debugLog.add(Res.get(R.string.connecting_to_x_x, (profile.host), (profile.port)))
        return try {
            val jsch = JSch()
            if (profile.authType == "KEY") {
                jsch.addIdentity("key", profile.secret.toByteArray(), null, null)
                debugLog.add(Res.get(R.string.using_key_authentication))
            } else {
                debugLog.add(Res.get(R.string.using_password_authentication))
            }
            session = jsch.getSession(profile.user, profile.host, profile.port)
            if (profile.authType == "PASSWORD") {
                session?.setPassword(profile.secret)
            }
            session?.let { SshSessionHelper.applyConfig(it) }
            session?.connect(timeout)

            // Critical check: confirm the session is actually connected
            if (session?.isConnected == true) {
                debugLog.add(Res.get(R.string.connected_successfully_session_verified))
                null
            } else {
                debugLog.add(Res.get(R.string.session_connect_returned_but_isconnected))
                Res.get(R.string.connection_failed_session_not_establishe)
            }
        } catch (e: JSchException) {
            debugLog.add(Res.get(R.string.jschexception_x, (e.message)))
            Res.get(R.string.ssh_protocol_error_x, (e.message))
        } catch (e: SocketTimeoutException) {
            debugLog.add(Res.get(R.string.sockettimeoutexception_x, (e.message)))
            Res.get(R.string.connection_timeout_x, (e.message))
        } catch (e: UnknownHostException) {
            debugLog.add(Res.get(R.string.unknownhostexception_x, (e.message)))
            Res.get(R.string.unknown_host_x, (e.message))
        } catch (e: Exception) {
            debugLog.add(Res.get(R.string.connection_failed_x, (e.message)))
            e.message
        }
    }

    fun exec(command: String): String {
        return try {
            val channel = session?.openChannel("exec") as? ChannelExec ?: return Res.get(R.string.error_not_connected)
            channel.setCommand(command)
            channel.inputStream = null

            val input = channel.inputStream
            channel.connect()

            val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            channel.disconnect()
            if (output.toString().isEmpty()) return Res.get(R.string.command_completed_successfully)
            output.toString()
        } catch (e: Exception) {
            Res.get(R.string.ssh_error_x, (e.message))
        }
    }

    fun disconnect() {
        session?.disconnect()
        session = null
    }
}

data class SshConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val authType: String,
    val secret: String
)

data class ConsoleLine(
    val text: String,
    val type: LineType
)

enum class LineType {
    INPUT, OUTPUT, ERROR, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) }
    var terminalMode by remember { mutableStateOf(0) }

    val localLog = remember { mutableStateListOf<ConsoleLine>() }
    var localInput by remember { mutableStateOf("") }
    var localHistory = remember { mutableStateListOf<String>() }
    var localHistoryIndex by remember { mutableStateOf(-1) }
    val localScrollState = rememberLazyListState()

    val sshLog = remember { mutableStateListOf<ConsoleLine>() }
    var sshInput by remember { mutableStateOf("") }
    var sshHistory = remember { mutableStateListOf<String>() }
    var sshHistoryIndex by remember { mutableStateOf(-1) }
    var activeSshProfile by remember { mutableStateOf<SshConnectionProfile?>(null) }
    var isSshConnected by remember { mutableStateOf(false) }
    var isSshConnecting by remember { mutableStateOf(false) }
    val sshScrollState = rememberLazyListState()

    val sharedPrefs = remember { context.getSharedPreferences("ssh_profiles_pref", Context.MODE_PRIVATE) }
    var profilesList by remember { mutableStateOf<List<SshConnectionProfile>>(emptyList()) }

    var showAddProfileDialog by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    var formName by remember { mutableStateOf("") }
    var formHost by remember { mutableStateOf("") }
    var formPort by remember { mutableStateOf("22") }
    var formUser by remember { mutableStateOf("") }
    var formSecret by remember { mutableStateOf("") }
    var formAuthType by remember { mutableStateOf("PASSWORD") }

    // Debug mode toggle
    var debugMode by remember { mutableStateOf(false) }

    fun loadProfiles() {
        val raw = sharedPrefs.getString("profiles_raw", "") ?: ""
        if (raw.isNotEmpty()) {
            val list = raw.split("|||").mapNotNull { line ->
                val tokens = line.split(";;")
                if (tokens.size >= 7) {
                    SshConnectionProfile(
                        id = tokens[0],
                        name = tokens[1],
                        host = tokens[2],
                        port = tokens[3].toIntOrNull() ?: 22,
                        user = tokens[4],
                        authType = tokens[5],
                        secret = tokens[6]
                    )
                } else null
            }
            profilesList = list
        } else {
            val sample = listOf(
                SshConnectionProfile(name = Res.get(R.string.production_server), host = "192.168.1.50", port = 22, user = "admin", authType = "PASSWORD", secret = "******"),
                SshConnectionProfile(name = Res.get(R.string.gateway_router), host = "10.0.0.1", port = 22, user = "root", authType = "PASSWORD", secret = "******"),
                SshConnectionProfile(name = Res.get(R.string.development_vps), host = "159.203.4.15", port = 2212, user = "ubuntu", authType = "KEY", secret = "/keys/dev.pem")
            )
            profilesList = sample
        }
    }

    fun saveProfiles(list: List<SshConnectionProfile>) {
        val serialized = list.joinToString("|||") {
            "${it.id};;${it.name};;${it.host};;${it.port};;${it.user};;${it.authType};;${it.secret}"
        }
        sharedPrefs.edit().putString("profiles_raw", serialized).apply()
        profilesList = list
    }

    fun initLocalLogs() {
        if (localLog.isEmpty()) {
            localLog.add(ConsoleLine("==========================================", LineType.SYSTEM))
            localLog.add(ConsoleLine(Res.get(R.string.local_native_android_shell), LineType.SYSTEM))
            localLog.add(ConsoleLine("==========================================", LineType.SYSTEM))
            localLog.add(ConsoleLine(Res.get(R.string.os_linux_kernel_android_sdk_safe_executi), LineType.SYSTEM))
            localLog.add(ConsoleLine(Res.get(R.string.type_help_to_check_standard_diagnostic_t), LineType.SYSTEM))
            localLog.add(ConsoleLine(Res.get(R.string.try_running_commands_like_ls_pwd_id_date), LineType.SYSTEM))
            localLog.add(ConsoleLine("", LineType.OUTPUT))
        }
    }

    fun performPingTest(host: String): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, 22), 3000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(Unit) {
        loadProfiles()
        initLocalLogs()
    }

    fun performLocalExec(cmdStr: String) {
        val trimmed = cmdStr.trim()
        if (trimmed.isEmpty()) return

        localLog.add(ConsoleLine(Res.get(R.string.android_localhost_x, trimmed), LineType.INPUT))
        localHistory.add(trimmed)
        localHistoryIndex = -1

        val command = trimmed.lowercase(Locale.ROOT)
        if (command == "clear" || command == "cls") {
            localLog.clear()
            initLocalLogs()
            localInput = ""
            return
        }

        if (command == "help") {
            localLog.add(ConsoleLine(Res.get(R.string.swiss_knife_available_predefined_local_u), LineType.SYSTEM))
            localLog.add(ConsoleLine(Res.get(R.string.getprop_list_system_diagnostic_flags), LineType.OUTPUT))
            localLog.add(ConsoleLine(Res.get(R.string.date_show_device_timestamp_clock), LineType.OUTPUT))
            localLog.add(ConsoleLine(Res.get(R.string.id_display_running_process_uid_gid), LineType.OUTPUT))
            localLog.add(ConsoleLine(Res.get(R.string.ls_show_local_environment_folders), LineType.OUTPUT))
            localLog.add(ConsoleLine(Res.get(R.string.pwd_reveal_active_sandboxed_location), LineType.OUTPUT))
            localLog.add(ConsoleLine(Res.get(R.string.clear_clean_screen_history_backlog), LineType.OUTPUT))
            localLog.add(ConsoleLine(Res.get(R.string.other_standard_linux_utilities_on_this_h), LineType.SYSTEM))
            localInput = ""
            coroutineScope.launch {
                delay(100)
                localScrollState.animateScrollToItem(localLog.size - 1)
            }
            return
        }

        coroutineScope.launch {
            localLog.add(ConsoleLine(Res.get(R.string.searching_executing_process_x, trimmed), LineType.SYSTEM))
            val result = withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec(trimmed)
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    var line: String?
                    var count = 0
                    while (reader.readLine().also { line = it } != null && count < 150) {
                        output.append(line).append("\n")
                        count++
                    }
                    while (errorReader.readLine().also { line = it } != null && count < 150) {
                        output.append(line).append("\n")
                        count++
                    }
                    process.waitFor()
                    if (output.isEmpty()) {
                        Res.get(R.string.process_execution_completed_safely_retur, (process.exitValue()))
                    } else {
                        output.toString()
                    }
                } catch (e: Exception) {
                    Res.get(R.string.execution_error_x_ncheck_permissions_or, (e.message))
                }
            }

            localLog.add(ConsoleLine(result, LineType.OUTPUT))
            delay(100)
            localScrollState.animateScrollToItem(localLog.size - 1)
        }
        localInput = ""
    }

    fun performSshExec(cmdStr: String) {
        val trimmed = cmdStr.trim()
        if (trimmed.isEmpty()) return

        val profile = activeSshProfile
        val promptPrefix = "${profile?.user ?: "user"}@${profile?.host ?: "ssh-vps"}:~$ "
        sshLog.add(ConsoleLine("$promptPrefix$trimmed", LineType.INPUT))
        sshHistory.add(trimmed)
        sshHistoryIndex = -1

        val commandLowercase = trimmed.lowercase(Locale.ROOT)
        if (commandLowercase == "clear") {
            sshLog.clear()
            sshInput = ""
            return
        }

        if (commandLowercase == "exit") {
            coroutineScope.launch {
                sshLog.add(ConsoleLine(Res.get(R.string.ssh_channel_close_requested), LineType.SYSTEM))
                withContext(Dispatchers.IO) { SshManager.disconnect() }
                isSshConnected = false
                activeSshProfile = null
                sshLog.add(ConsoleLine(Res.get(R.string.connection_closed), LineType.SYSTEM))
                sshScrollState.animateScrollToItem(sshLog.size - 1)
            }
            sshInput = ""
            return
        }

        coroutineScope.launch {
            sshLog.add(ConsoleLine(Res.get(R.string.executing_over_ssh), LineType.SYSTEM))
            val result = withContext(Dispatchers.IO) {
                SshManager.exec(trimmed)
            }

            sshLog.add(ConsoleLine(result, LineType.OUTPUT))
            sshScrollState.animateScrollToItem(sshLog.size - 1)
        }
        sshInput = ""
    }

    fun triggerSshConnection(profile: SshConnectionProfile) {
        selectedTab = 0
        terminalMode = 1
        sshLog.clear()
        isSshConnecting = true
        isSshConnected = false
        activeSshProfile = profile

        sshLog.add(ConsoleLine(Res.get(R.string.initializing_ssh_connection_request), LineType.SYSTEM))
        sshLog.add(ConsoleLine(Res.get(R.string.target_host_x_x_x, (profile.user), (profile.host), (profile.port)), LineType.SYSTEM))

        // Ping test before SSH
        sshLog.add(ConsoleLine(Res.get(R.string.testing_connectivity_to_x, (profile.host)), LineType.SYSTEM))
        coroutineScope.launch {
            val pingOk = withContext(Dispatchers.IO) { performPingTest(profile.host) }
            if (pingOk) {
                sshLog.add(ConsoleLine(Res.get(R.string.connectivity_check_host_reachable), LineType.SYSTEM))
            } else {
                sshLog.add(ConsoleLine(Res.get(R.string.warning_host_did_not_respond_to_ping_por), LineType.SYSTEM))
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    SshManager.connect(profile)
                    null
                } catch (e: Exception) {
                    e.message
                }
            }

            isSshConnecting = false
            if (result == null && SshManager.session?.isConnected == true) {
                isSshConnected = true
                sshLog.add(ConsoleLine("==========================================", LineType.SYSTEM))
                sshLog.add(ConsoleLine(Res.get(R.string.successfully_connected_via_ssh_protocol), LineType.SYSTEM))
                sshLog.add(ConsoleLine("==========================================", LineType.SYSTEM))
                sshLog.add(ConsoleLine(Res.get(R.string.host_os_properties_loaded_via_jsch_multi), LineType.SYSTEM))
                sshLog.add(ConsoleLine(Res.get(R.string.ready_for_remote_command_execution), LineType.SYSTEM))
            } else {
                activeSshProfile = null
                isSshConnected = false
                sshLog.add(ConsoleLine("Connection failed: ${result ?: "Unknown error"}", LineType.ERROR))
            }
            sshScrollState.animateScrollToItem(sshLog.size - 1)
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Shell & Terminal SSH",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = AmberGold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (terminalMode == 0) {
                            localLog.clear()
                            initLocalLogs()
                        } else {
                            sshLog.clear()
                            if (isSshConnected) {
                                sshLog.add(ConsoleLine(Res.get(R.string.terminal_buffer_cleared), LineType.SYSTEM))
                            }
                        }
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = Res.get(R.string.limpar_console), tint = AmberGold)
                    }
                    // Debug mode toggle
                    IconButton(onClick = { debugMode = !debugMode }) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = Res.get(R.string.debug_mode),
                            tint = if (debugMode) AmberGold else OnSurfaceMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DeepDark,
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyanNeon
                    )
                },
                divider = { HorizontalDivider(color = DarkBorder) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(Res.get(R.string.active_terminal), color = if (selectedTab == 0) OnSurfaceLight else OnSurfaceMuted, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(Res.get(R.string.ssh_profiles), color = if (selectedTab == 1) OnSurfaceLight else OnSurfaceMuted, fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.weight(1f)) {
                     Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0E1A))
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Local Shell Tab
                            val isLocalActive = terminalMode == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(if (isLocalActive) Color(0xFF111827) else Color.Transparent)
                                    .clickable { terminalMode = 0 }
                                    .border(
                                        1.dp,
                                        if (isLocalActive) Color(0xFF1E2D4A) else Color.Transparent,
                                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = if (isLocalActive) CyanNeon else OnSurfaceMuted, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        Res.get(R.string.local_shell),
                                        color = if (isLocalActive) Color.White else OnSurfaceMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { localLog.clear(); initLocalLogs() },
                                        modifier = Modifier.size(14.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(10.dp))
                                    }
                                }
                            }

                            // SSH Tab (if connected or active)
                            if (activeSshProfile != null) {
                                val isSshActive = terminalMode == 1
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(if (isSshActive) Color(0xFF111827) else Color.Transparent)
                                        .clickable { terminalMode = 1 }
                                        .border(
                                            1.dp,
                                            if (isSshActive) Color(0xFF1E2D4A) else Color.Transparent,
                                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = if (isSshActive) CyanNeon else OnSurfaceMuted, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            Res.get(R.string.x_user_x_host, (activeSshProfile?.user), (activeSshProfile?.host)),
                                            color = if (isSshActive) Color.White else OnSurfaceMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 100.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                SshManager.disconnect()
                                                isSshConnected = false
                                                activeSshProfile = null
                                                terminalMode = 0
                                            },
                                            modifier = Modifier.size(14.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(10.dp))
                                        }
                                    }
                                }
                            }

                            // Add Tab "+" Button
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { selectedTab = 1 },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = Res.get(R.string.nova_conexao), tint = CyanNeon, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Debug Mode Panel
                    AnimatedVisibility(visible = debugMode && terminalMode == 1) {
                        NeonCard(
                            borderColor = AmberGold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(Res.get(R.string.debug_log), color = AmberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val debugLines = SshManager.debugLog.toList()
                                if (debugLines.isEmpty()) {
                                    Text(Res.get(R.string.no_debug_entries_yet), color = OnSurfaceMuted, fontSize = 9.sp)
                                } else {
                                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                                        items(debugLines) { line ->
                                            Text(
                                                text = line,
                                                color = OnSurfaceMuted,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (terminalMode == 0) {
                        TermuxStyleTerminal(
                            log = localLog,
                            input = localInput,
                            onInputChange = { localInput = it },
                            onSend = {
                                performLocalExec(localInput)
                                focusManager.clearFocus()
                            },
                            isEnabled = true,
                            scrollState = localScrollState,
                            history = localHistory,
                            historyIndex = localHistoryIndex,
                            onHistoryIndexChange = { localHistoryIndex = it }
                        )
                    } else {
                        if (!isSshConnected && !isSshConnecting) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.VpnKey,
                                        contentDescription = "Keys",
                                        modifier = Modifier.size(64.dp),
                                        tint = OnSurfaceDim
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        Res.get(R.string.nenhum_tunel_ssh_ativo),
                                        color = OnSurfaceLight,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        Res.get(R.string.para_iniciar_uma_sessao_shell_remota_e_s),
                                        color = OnSurfaceMuted,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    GradientButton(
                                        text = Res.get(R.string.abrir_perfis_ssh),
                                        onClick = { selectedTab = 1 },
                                        modifier = Modifier.wrapContentWidth(),
                                        startColor = ElectricViolet,
                                        endColor = ElectricViolet.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else {
                            TermuxStyleTerminal(
                                log = sshLog,
                                input = sshInput,
                                onInputChange = { sshInput = it },
                                onSend = {
                                    performSshExec(sshInput)
                                    focusManager.clearFocus()
                                },
                                isEnabled = isSshConnected,
                                scrollState = sshScrollState,
                                history = sshHistory,
                                historyIndex = sshHistoryIndex,
                                onHistoryIndexChange = { sshHistoryIndex = it }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            Res.get(R.string.servidores_ssh_configurados),
                            color = OnSurfaceLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        GradientButton(
                            text = Res.get(R.string.novo_host),
                            onClick = {
                                editingProfileId = null
                                formName = ""
                                formHost = ""
                                formPort = "22"
                                formUser = ""
                                formSecret = ""
                                formAuthType = "PASSWORD"
                                showAddProfileDialog = true
                            },
                            modifier = Modifier.wrapContentSize(),
                            startColor = AmberGold,
                            endColor = AmberGold.copy(alpha = 0.7f),
                            icon = {
                                Icon(Icons.Default.Add, contentDescription = Res.get(R.string.add_node), tint = DeepDark, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (profilesList.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(Res.get(R.string.nenhum_perfil_ssh_configurado_ainda), color = OnSurfaceMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(profilesList) { prof ->
                                NeonCard(
                                    borderColor = DarkBorder,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(prof.name, fontWeight = FontWeight.Bold, color = OnSurfaceLight, fontSize = 15.sp)
                                                Text(
                                                    Res.get(R.string.x_x_x_5, (prof.user), (prof.host), (prof.port)),
                                                    fontFamily = FontFamily.Monospace,
                                                    color = OnSurfaceMuted,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Button(
                                                    onClick = { triggerSshConnection(prof) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Connect", tint = DeepDark, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(Res.get(R.string.ssh_run), fontSize = 11.sp, color = DeepDark, fontWeight = FontWeight.Bold)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            val intent = android.content.Intent().apply {
                                                                setClassName("com.termux", "com.termux.app.RunCommandService") // DEV-R10
                                                                action = "com.termux.RUN_COMMAND"
                                                                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/ssh")
                                                                val args = arrayOf(Res.get(R.string.x_x_24, (prof.user), (prof.host)), "-p", prof.port.toString())
                                                                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
                                                                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                                                                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                                                            }
                                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                context.startForegroundService(intent)
                                                            } else {
                                                                context.startService(intent)
                                                            }
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, Res.get(R.string.erro_ao_abrir_no_termux_certifique_se_de), Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Terminal, contentDescription = "Termux", tint = CyanNeon, modifier = Modifier.size(18.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        editingProfileId = prof.id
                                                        formName = prof.name
                                                        formHost = prof.host
                                                        formPort = prof.port.toString()
                                                        formUser = prof.user
                                                        formSecret = prof.secret
                                                        formAuthType = prof.authType
                                                        showAddProfileDialog = true
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = Res.get(R.string.edit_profile), tint = AmberGold, modifier = Modifier.size(18.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val filtered = profilesList.filter { it.id != prof.id }
                                                        saveProfiles(filtered)
                                                        Toast.makeText(context, Res.get(R.string.ssh_profile_deleted), Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.delete_profile), tint = DangerRed, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Security, contentDescription = Res.get(R.string.security_status), tint = OnSurfaceMuted, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Auth-Mode: ${prof.authType} (${if (prof.authType == "PASSWORD") Res.get(R.string.encrypted_passcode) else Res.get(R.string.private_pem_key)})",
                                                color = OnSurfaceMuted,
                                                fontSize = 11.sp
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

    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddProfileDialog = false
                editingProfileId = null
            },
            title = { Text(if (editingProfileId != null) "Edit SSH Client Remote Node" else Res.get(R.string.configure_ssh_client_remote_node)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text(Res.get(R.string.configuration_profile_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = formHost,
                            onValueChange = { formHost = it },
                            label = { Text(Res.get(R.string.host_address_ip_domain)) },
                            modifier = Modifier.weight(2f)
                        )

                        OutlinedTextField(
                            value = formPort,
                            onValueChange = { formPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    OutlinedTextField(
                        value = formUser,
                        onValueChange = { formUser = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(Res.get(R.string.authentication_protocol), style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = formAuthType == "PASSWORD",
                            onClick = { formAuthType = "PASSWORD" },
                            label = { Text(Res.get(R.string.password_authentication)) }
                        )

                        FilterChip(
                            selected = formAuthType == "KEY",
                            onClick = { formAuthType = "KEY" },
                            label = { Text(Res.get(R.string.private_pem_key)) }
                        )
                    }

                    OutlinedTextField(
                        value = formSecret,
                        onValueChange = { formSecret = it },
                        label = { Text(if (formAuthType == "PASSWORD") Res.get(R.string.secure_password) else Res.get(R.string.pem_key_contents)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (formAuthType == "PASSWORD") PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        trailingIcon = {
                            Icon(
                                imageVector = if (formAuthType == "PASSWORD") Icons.Default.VpnKey else Icons.Default.Code,
                                contentDescription = null
                            )
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formName.isNotEmpty() && formHost.isNotEmpty() && formUser.isNotEmpty()) {
                            val portInt = formPort.toIntOrNull() ?: 22
                            val updated = if (editingProfileId != null) {
                                profilesList.map {
                                    if (it.id == editingProfileId) {
                                        it.copy(
                                            name = formName,
                                            host = formHost,
                                            port = portInt,
                                            user = formUser,
                                            authType = formAuthType,
                                            secret = formSecret
                                        )
                                    } else it
                                }
                            } else {
                                profilesList + SshConnectionProfile(
                                    name = formName,
                                    host = formHost,
                                    port = portInt,
                                    user = formUser,
                                    authType = formAuthType,
                                    secret = formSecret
                                )
                            }
                            saveProfiles(updated)

                            formName = ""
                            formHost = ""
                            formPort = "22"
                            formUser = ""
                            formSecret = ""
                            formAuthType = "PASSWORD"
                            editingProfileId = null

                            showAddProfileDialog = false
                            Toast.makeText(context, Res.get(R.string.secure_host_configured), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, Res.get(R.string.profile_name_ip_address_and_user_name_ar), Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text(if (editingProfileId != null) Res.get(R.string.save_changes_2) else Res.get(R.string.save_ssh_profile))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddProfileDialog = false
                        editingProfileId = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TerminalHotkeysBar(
    onKeyTap: (String) -> Unit
) {
    val keys = listOf("ESC", "TAB", "CTRL", "ALT", "-", "/", "|", "↑", "↓", "←", "→")
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(keys) { key ->
            Box(
                modifier = Modifier
                    .background(Color(0xFF2C2C2C))
                    .clickable { onKeyTap(key) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = key,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

fun handleHotkey(
    key: String,
    currentInput: String,
    onInputChange: (String) -> Unit,
    history: List<String>,
    historyIndex: Int,
    onHistoryIndexChange: (Int) -> Unit
) {
    when (key) {
        "TAB" -> onInputChange(currentInput + "\t")
        "ESC", "CTRL" -> onInputChange("")
        "↑" -> {
            if (history.isNotEmpty()) {
                val newIndex = if (historyIndex == -1) history.lastIndex else maxOf(0, historyIndex - 1)
                onHistoryIndexChange(newIndex)
                onInputChange(history[newIndex])
            }
        }
        "↓" -> {
             if (historyIndex != -1 && historyIndex < history.size - 1) {
                 val newIndex = historyIndex + 1
                 onHistoryIndexChange(newIndex)
                 onInputChange(history[newIndex])
             } else if (historyIndex == history.lastIndex) {
                 onHistoryIndexChange(-1)
                 onInputChange("")
             }
        }
        "←", "→" -> {} // Arrow keys move cursor (handled automatically on mobile keyboard or ignored here)
        else -> onInputChange(currentInput + key)
    }
}

@Composable
// DEV-R09
fun TermuxStyleTerminal(
    log: List<ConsoleLine>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    history: List<String>,
    historyIndex: Int,
    onHistoryIndexChange: (Int) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isEnabled) focusRequester.requestFocus()
                }
                .padding(4.dp)
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(log) { logLine ->
                    Text(
                        text = logLine.text,
                        color = when (logLine.type) {
                            LineType.INPUT -> Color.White
                            LineType.ERROR -> Color(0xFFEF5350)
                            LineType.SYSTEM -> Color(0xFF4CAF50)
                            LineType.OUTPUT -> Color(0xFFE0E0E0)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isEnabled) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$ $input${if (cursorVisible) "█" else ""}",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Hidden BasicTextField just to capture keyboard input
            if (isEnabled) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { onSend() }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent)
                )
            }
        }

        if (isEnabled) {
            TerminalHotkeysBar(
                onKeyTap = { key ->
                    handleHotkey(key, input, onInputChange, history, historyIndex, onHistoryIndexChange)
                }
            )
        }
    }
}
