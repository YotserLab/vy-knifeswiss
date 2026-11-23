package com.example.ui.screens.system

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppTopAppBar
import com.example.ui.components.CategoryHeader
import com.example.ui.components.VYDivider
import com.example.ui.theme.*
import com.example.R
import com.example.Res

data class ToolSettingItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: String,
    val categoryColor: Color = CyanNeon,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tool_settings", Context.MODE_PRIVATE) } // EXT-R09

    val toolsByCategory = remember {
        listOf(
            // Rede
            ToolSettingItem(Res.get(R.string.monitor_wi_fi),  Res.get(R.string.analise_de_sinal_e_redes_wi_fi),    Icons.Default.Wifi,          Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.escaner_de_portas), Res.get(R.string.detecta_portas_abertas_em_hosts), Icons.Default.Search,        Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.testes_de_rede), Res.get(R.string.ping_e_testes_de_latencia),         Icons.Default.NetworkCheck,  Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.varredura_de_sub_rede), Res.get(R.string.varre_dispositivos_na_sub_rede), Icons.Default.DeviceHub,  Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.teste_de_velocidade), Res.get(R.string.mede_velocidade_de_internet),  Icons.Default.Speed,         Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.informacoes_de_rede), Res.get(R.string.info_de_ip_gateway_e_dns),     Icons.Default.Info,          Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.chat_local),     Res.get(R.string.chat_p2p_na_rede_local),            Icons.AutoMirrored.Filled.Chat,          Res.get(R.string.rede_conectividade),        CyanNeon),
            ToolSettingItem(Res.get(R.string.whatsapp_local_mesh), Res.get(R.string.chat_100_descentralizado),    Icons.Default.Share,         Res.get(R.string.rede_conectividade),        CyanNeon),
            // Utilities
            ToolSettingItem(Res.get(R.string.nivel_de_bolha),  Res.get(R.string.nivel_de_bolha_digital),            Icons.Default.ScreenRotation, Res.get(R.string.utilitarios_ferramentas), ElectricViolet),
            ToolSettingItem(Res.get(R.string.bussola),        Res.get(R.string.bussola_com_rosa_dos_ventos),       Icons.Default.Explore,       Res.get(R.string.utilitarios_ferramentas),   ElectricViolet),
            ToolSettingItem(Res.get(R.string.scanner_e_geracao_qr), Res.get(R.string.scan_e_geracao_de_qr_codes),   Icons.Default.SettingsApplications, Res.get(R.string.utilitarios_ferramentas), ElectricViolet),
            ToolSettingItem(Res.get(R.string.device_info_hardware), Res.get(R.string.informacoes_completas_do_dispositivo_e_h), Icons.Default.Memory, Res.get(R.string.utilitarios_ferramentas), ElectricViolet),
            ToolSettingItem(Res.get(R.string.calculadora_hp_48g), Res.get(R.string.calculadora_cientifica_hp_48g), Icons.Default.Calculate,    Res.get(R.string.utilitarios_ferramentas),   ElectricViolet),
            // Management
            ToolSettingItem(Res.get(R.string.gerenciador_de_arquivos), Res.get(R.string.gerenciador_de_arquivos_completo), Icons.Default.Folder,   Res.get(R.string.produtividade_gestao),      AmberGold),
            ToolSettingItem(Res.get(R.string.terminal_ssh), Res.get(R.string.shell_local_e_cliente_ssh),          Icons.Default.DeveloperMode, Res.get(R.string.produtividade_gestao),      AmberGold),
            ToolSettingItem(Res.get(R.string.historico_e_exportar_pdf), Res.get(R.string.historico_e_exportacao_pdf), Icons.Default.History,      Res.get(R.string.produtividade_gestao),      AmberGold),
            ToolSettingItem(Res.get(R.string.notas_e_kanban), Res.get(R.string.notas_e_quadro_kanban),             Icons.AutoMirrored.Filled.Assignment,    Res.get(R.string.produtividade_gestao),      AmberGold),
            ToolSettingItem(Res.get(R.string.calendario_e_alarmes), Res.get(R.string.calendario_e_alarmes_2),         Icons.Default.Schedule,      Res.get(R.string.produtividade_gestao),      AmberGold),
            ToolSettingItem(Res.get(R.string.backup_e_nuvem), Res.get(R.string.backup_e_sincronizacao),            Icons.Default.Backup,        Res.get(R.string.produtividade_gestao),      AmberGold),
            // Security
            ToolSettingItem(Res.get(R.string.protecao_ao_desligar), Res.get(R.string.identifica_o_aparelho_e_mostra_o_caminho), Icons.Default.Security, Res.get(R.string.seguranca_privacidade), SuccessGreen),
            ToolSettingItem(Res.get(R.string.verificacao_de_seguranca), Res.get(R.string.analisa_bloqueio_biometria_adb_patch_vpn), Icons.Default.Security, Res.get(R.string.seguranca_privacidade), SuccessGreen),
        )
    }

    var toolStates by remember {
        mutableStateOf(toolsByCategory.associate { it.title to prefs.getBoolean(it.title, true) })
    }

    var totpBioEnabled by remember {
        mutableStateOf(prefs.getBoolean("totp_require_biometric", true))
    }

    val categories = remember { toolsByCategory.map { it.category }.distinct() }
    val categoryColors = mapOf(
        Res.get(R.string.rede_conectividade)    to CyanNeon,
        Res.get(R.string.utilitarios_ferramentas) to ElectricViolet,
        Res.get(R.string.produtividade_gestao)  to AmberGold,
        Res.get(R.string.seguranca_privacidade) to SuccessGreen,
    )

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Res.get(R.string.configuracoes),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurfaceLight,
                        )
                        Text(
                            Res.get(R.string.ferramentas_visiveis_no_dashboard),
                            fontSize = 12.sp,
                            color    = OnSurfaceMuted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("OK", color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                CategoryHeader(
                    label       = Res.get(R.string.seguranca_autenticacao),
                    accentColor = ElectricViolet,
                )
                VYDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    modifier = Modifier.background(DeepDark),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = ElectricViolet,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    headlineContent = {
                        Text(
                            Res.get(R.string.biometria_no_autenticador_totp),
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceLight,
                        )
                    },
                    supportingContent = {
                        Text(
                            Res.get(R.string.solicitar_biometria_ou_senha_do_celular),
                            fontSize = 12.sp,
                            color    = OnSurfaceMuted,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = totpBioEnabled,
                            onCheckedChange = { newValue ->
                                prefs.edit().putBoolean("totp_require_biometric", newValue).apply()
                                totpBioEnabled = newValue
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor  = DeepDark,
                                checkedTrackColor  = ElectricViolet,
                                uncheckedThumbColor = OnSurfaceDim,
                                uncheckedTrackColor = DarkNavyVariant,
                            ),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = DeepDark),
                )
                VYDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color    = DarkBorder,
                )
            }

            categories.forEach { category ->
                item {
                    CategoryHeader(
                        label       = category,
                        accentColor = categoryColors[category] ?: CyanNeon,
                    )
                    VYDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                val tools = toolsByCategory.filter { it.category == category }
                tools.forEachIndexed { i, tool ->
                    item {
                        val isEnabled = toolStates[tool.title] ?: true
                        ListItem(
                            modifier = Modifier.background(DeepDark),
                            leadingContent = {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = null,
                                    tint = if (isEnabled) tool.categoryColor
                                           else OnSurfaceDim,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            headlineContent = {
                                Text(
                                    tool.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEnabled) OnSurfaceLight else OnSurfaceDim,
                                )
                            },
                            supportingContent = {
                                Text(
                                    tool.description,
                                    fontSize = 12.sp,
                                    color    = if (isEnabled) OnSurfaceMuted else OnSurfaceDim,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { newValue ->
                                        prefs.edit().putBoolean(tool.title, newValue).apply()
                                        toolStates = toolStates.toMutableMap().apply { put(tool.title, newValue) }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor  = DeepDark,
                                        checkedTrackColor  = tool.categoryColor,
                                        uncheckedThumbColor = OnSurfaceDim,
                                        uncheckedTrackColor = DarkNavyVariant,
                                    ),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = DeepDark),
                        )
                        if (i < tools.size - 1) {
                            VYDivider(
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                color    = DarkBorder,
                            )
                        }
                    }
                }
            }
        }
    }
}

