package com.example.ui.screens.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.components.CategoryHeader
import com.example.ui.components.VYDivider
import com.example.ui.theme.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    data class AppTool(val name: String, val desc: String, val category: String)

    val apps = listOf(
        AppTool(Res.get(R.string.monitor_wi_fi),      Res.get(R.string.analise_de_sinal_e_redes_wi_fi),           Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.escaner_de_portas),  Res.get(R.string.detecta_portas_abertas_em_hosts),           Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.testes_de_rede),     Res.get(R.string.ping_e_testes_de_latencia),                 Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.varredura_de_sub_rede), Res.get(R.string.varre_dispositivos_na_sub_rede),            Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.teste_de_velocidade), Res.get(R.string.mede_velocidade_de_internet),               Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.informacoes_de_rede), Res.get(R.string.info_de_ip_gateway_e_dns),                 Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.chat_local),         Res.get(R.string.chat_p2p_na_rede_local),                    Res.get(R.string.rede_conectividade)),
        AppTool(Res.get(R.string.nivel_de_bolha),     Res.get(R.string.nivel_de_bolha_digital),                    Res.get(R.string.utilitarios_ferramentas)),
        AppTool(Res.get(R.string.bussola),            Res.get(R.string.bussola_com_rosa_dos_ventos),               Res.get(R.string.utilitarios_ferramentas)),
        AppTool(Res.get(R.string.scanner_e_geracao_qr), Res.get(R.string.scan_e_geracao_de_qr_codes),                Res.get(R.string.utilitarios_ferramentas)),
        AppTool(Res.get(R.string.device_info_hardware), Res.get(R.string.clone_device_info_hardware_sistema_e_cpu), Res.get(R.string.utilitarios_ferramentas)),
        AppTool(Res.get(R.string.calculadora_hp_48g), Res.get(R.string.calculadora_cientifica_hp_48g),             Res.get(R.string.utilitarios_ferramentas)),
        AppTool(Res.get(R.string.calculadora_de_precos), Res.get(R.string.calculadora_de_precos_e_margens),           Res.get(R.string.utilitarios_ferramentas)),
        AppTool(Res.get(R.string.gerenciador_de_arquivos), Res.get(R.string.gerenciador_de_arquivos_completo),          Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.terminal_ssh),     Res.get(R.string.shell_local_e_cliente_ssh),                 Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.historico_e_exportar_pdf), Res.get(R.string.historico_e_exportacao_pdf),                Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.notas_e_kanban),     Res.get(R.string.notas_e_quadro_kanban),                     Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.lista_de_compras),   Res.get(R.string.listas_com_checklist_e_historico),          Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.calendario_e_alarmes), Res.get(R.string.calendario_e_alarmes_2),                      Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.backup_e_nuvem),     Res.get(R.string.backup_e_sincronizacao),                    Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.gravador_de_audio),   Res.get(R.string.gravacao_de_voz_local),                     Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.transcricao_offline), Res.get(R.string.voz_para_texto_sem_internet),               Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.gestor_de_sms),       Res.get(R.string.gerenciamento_de_mensagens_sms),            Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.limpador_de_cache),   Res.get(R.string.limpeza_rapida_do_aplicativo),              Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.sincronizador_de_arquivos), Res.get(R.string.sincronia_avancada_de_arquivos),            Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.gravador_de_tela),   Res.get(R.string.gravacao_da_tela_do_dispositivo),           Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.gestor_de_midia),    Res.get(R.string.gerenciador_de_fotos_e_videos),             Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.launcher_de_atividades), Res.get(R.string.abre_atividades_ocultas_do_sistema),        Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.quadro_branco),       Res.get(R.string.quadro_branco_para_desenhos),               Res.get(R.string.produtividade_gestao)),
        AppTool(Res.get(R.string.media_player),       Res.get(R.string.reprodutor_de_audio_e_video),               Res.get(R.string.modulos_integrados)),
        AppTool(Res.get(R.string.editor_de_texto),    Res.get(R.string.editor_com_syntax_highlight),               Res.get(R.string.modulos_integrados)),
        AppTool(Res.get(R.string.dev_settings),       Res.get(R.string.configuracoes_avancadas_do_app),            Res.get(R.string.modulos_integrados)),
    )

    val categories = apps.map { it.category }.distinct()
    val categoryColors = mapOf(
        Res.get(R.string.rede_conectividade)      to CyanNeon,
        Res.get(R.string.utilitarios_ferramentas) to ElectricViolet,
        Res.get(R.string.produtividade_gestao)    to AmberGold,
        Res.get(R.string.modulos_integrados)        to OnSurfaceMuted,
    )

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.sobre_o_vy_swiss_knife),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (!navController.popBackStack()) {
                            navController.navigate(com.example.ui.navigation.Screen.Dashboard.route)
                        }
                    }) {
                        Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = CyanNeon,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ── Hero section ─────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Gradient logo
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(listOf(CyanNeon, ElectricViolet))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "VY",
                            color      = Color.White,
                            fontSize   = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        Res.get(R.string.vy_swiss_knife),
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color      = OnSurfaceLight,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Res.get(R.string.versao_x_build_x, (com.example.BuildConfig.VERSION_NAME), (com.example.BuildConfig.VERSION_CODE) /* EXT-R08 */),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyanNeon,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        Res.get(R.string.uma_ferramenta_completa_de_diagnostico_u),
                        textAlign = TextAlign.Center,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = OnSurfaceMuted,
                    )
                    Spacer(Modifier.height(20.dp))
                    // Decorative divider
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(2.dp)
                            .background(Brush.horizontalGradient(listOf(CyanNeon, ElectricViolet)))
                    )
                }
            }

            // ── Tool categories ──────────────────────────────────────
            categories.forEach { category ->
                item {
                    CategoryHeader(
                        label       = category,
                        accentColor = categoryColors[category] ?: OnSurfaceMuted,
                    )
                }
                val tools = apps.filter { it.category == category }
                items(tools) { tool ->
                    val catColor = categoryColors[tool.category] ?: OnSurfaceMuted
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavy)
                            .border(1.dp, catColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(catColor.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(tool.name, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight, style = MaterialTheme.typography.bodyMedium)
                            Text(tool.desc, fontSize = 12.sp, color = OnSurfaceMuted)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── Footer ───────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VYDivider(modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Res.get(R.string.desenvolvido_para_android), fontSize = 11.sp, color = OnSurfaceDim)
                    }
                }
            }
        }
    }
}

