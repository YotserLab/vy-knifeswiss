package com.example.plugin.registry
import com.example.ui.screens.chat.LocalChatScreen
import com.example.ui.screens.chat.MeshInfoScreen
import com.example.ui.screens.docs.EpubViewerScreen
import com.example.ui.screens.docs.PdfViewerScreen
import com.example.ui.screens.docs.TextEditorScreen
import com.example.ui.screens.docs.WhiteboardScreen
import com.example.ui.screens.files.BackupSyncScreen
import com.example.ui.screens.files.FileManagerScreen
import com.example.ui.screens.files.FileSynchronizerScreen
import com.example.ui.screens.media.AudioRecorderScreen
import com.example.ui.screens.media.MediaManagerScreen
import com.example.ui.screens.media.MediaPlayerScreen
import com.example.ui.screens.media.ScreenRecorderScreen
import com.example.ui.screens.media.TranscriptionScreen
import com.example.ui.screens.network.NetworkInfoScreen
import com.example.ui.screens.network.PingScreen
import com.example.ui.screens.network.PortScannerScreen
import com.example.ui.screens.network.SpeedTestScreen
import com.example.ui.screens.network.SubnetScannerScreen
import com.example.ui.screens.network.WifiMonitorScreen
import com.example.ui.screens.obd.Obd2ScannerScreen
import com.example.ui.screens.productivity.CalendarAlarmsScreen
import com.example.ui.screens.productivity.CalorieCalculatorScreen
import com.example.ui.screens.productivity.Hp48gCalculatorScreen
import com.example.ui.screens.productivity.NotesScreen
import com.example.ui.screens.productivity.PriceCalculatorScreen
import com.example.ui.screens.productivity.QuickNotesKanbanScreen
import com.example.ui.screens.productivity.ShoppingListScreen
import com.example.ui.screens.security.DeviceSecurityScreen
import com.example.ui.screens.security.PasswordVaultScreen
import com.example.ui.screens.security.TotpAuthenticatorScreen
import com.example.ui.screens.system.ActivityLauncherScreen
import com.example.ui.screens.system.BubbleLevelScreen
import com.example.ui.screens.system.CacheCleanerScreen
import com.example.ui.screens.system.CompassScreen
import com.example.ui.screens.system.DevSettingsScreen
import com.example.ui.screens.system.DeviceInfoScreen
import com.example.ui.screens.system.HistoryScreen
import com.example.ui.screens.system.PowerOffProtectionScreen
import com.example.ui.screens.system.QrCodeScreen
import com.example.ui.screens.system.TerminalScreen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cast.ui.CastScreen
import com.example.plugin.core.ToolPlugin
import com.example.plugin.manager.PluginManager
import com.example.plugin.model.PluginCategory
import com.example.plugin.model.PluginManifest
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.R
import com.example.Res

abstract class BaseNativePlugin(
    id: String,
    title: String,
    description: String,
    category: PluginCategory,
    override val icon: ImageVector,
    route: String,
    permissions: List<String> = emptyList()
) : ToolPlugin {
    override val isBuiltIn: Boolean = true
    override val manifest = PluginManifest(
        id = id,
        name = title,
        description = description,
        author = Res.get(R.string.vyknifeswiss_built_in),
        category = category,
        iconName = id,
        entryPoint = route,
        type = "native",
        permissions = permissions
    )
    override val route: String = route
}

object NativePluginRegistry {
    fun registerAll() {
        // --- NETWORK & CONNECTIVITY ---
        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "wifi_monitor",
            title = Res.get(R.string.monitor_wi_fi),
            description = Res.get(R.string.informacoes_detalhadas_sobre_a_conexao_w),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Wifi,
            route = Screen.WifiMonitor.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { WifiMonitorScreen(navController = navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "port_scanner",
            title = Res.get(R.string.escaner_de_portas),
            description = Res.get(R.string.verificador_de_portas_tcp_abertas_em_ips),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Search,
            route = Screen.PortScanner.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { PortScannerScreen(navController = navController) }
                navGraphBuilder.composable(
                    route = "port_scanner_with_host?host={host}", // RED-R12
                    arguments = listOf(navArgument("host") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { backStackEntry ->
                    PortScannerScreen(initialHost = backStackEntry.arguments?.getString("host"), navController = navController)
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "network_test",
            title = Res.get(R.string.testes_de_rede),
            description = Res.get(R.string.diagnostico_icmp_ping_resolucao_dns_e_te),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.NetworkCheck,
            route = Screen.NetworkTest.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { PingScreen(navController = navController) }
                navGraphBuilder.composable(
                    route = "network_test_with_host?host={host}",
                    arguments = listOf(navArgument("host") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { backStackEntry ->
                    PingScreen(initialHost = backStackEntry.arguments?.getString("host"), navController = navController)
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "subnet_scanner",
            title = Res.get(R.string.varredura_de_sub_rede),
            description = Res.get(R.string.descoberta_de_dispositivos_ativos_na_red),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.DeviceHub,
            route = Screen.SubnetScanner.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { SubnetScannerScreen(navController = navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "speed_test",
            title = Res.get(R.string.teste_de_velocidade),
            description = Res.get(R.string.mede_velocidades_de_download_upload_e_ji),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Speed,
            route = Screen.SpeedTest.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { SpeedTestScreen(navController = navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "network_info",
            title = Res.get(R.string.informacoes_de_rede),
            description = Res.get(R.string.visao_geral_de_interfaces_de_rede_ipv4_i),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Info,
            route = Screen.NetworkInfo.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { NetworkInfoScreen(navController = navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "local_chat",
            title = Res.get(R.string.chat_local),
            description = Res.get(R.string.chat_p2p_seguro_e_compartilhamento_de_ar),
            category = PluginCategory.NETWORK,
            icon = Icons.AutoMirrored.Filled.Chat,
            route = Screen.LocalChat.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) {
                    LocalChatScreen { targetRoute ->
                        if (targetRoute == Screen.Dashboard.route) {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Dashboard.route)
                            }
                        } else {
                            navController.navigate(targetRoute)
                        }
                    }
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "mesh_info",
            title = Res.get(R.string.whatsapp_local_mesh),
            description = Res.get(R.string.configuracao_e_status_de_rede_mesh_desce),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Share,
            route = Screen.MeshInfo.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { MeshInfoScreen(onBack = { navController.popBackStack() }) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "cast_to_tv",
            title = Res.get(R.string.cast_to_tv),
            description = Res.get(R.string.transmita_videos_fotos_e_musicas_para_tv),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Cast,
            route = Screen.CastToTV.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { CastScreen(navController = navController) }
            }
        })
        // --- UTILITIES & SENSORS ---
        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "bubble_level",
            title = Res.get(R.string.nivel_de_bolha),
            description = Res.get(R.string.medidor_de_nivelamento_e_angulos_de_supe),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.ScreenRotation,
            route = Screen.BubbleLevel.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { BubbleLevelScreen() }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "compass",
            title = Res.get(R.string.bussola),
            description = Res.get(R.string.bussola_magnetica_com_indicacao_de_graus),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.Explore,
            route = Screen.Compass.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { CompassScreen() }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "qr_code",
            title = Res.get(R.string.scanner_e_geracao_qr),
            description = Res.get(R.string.leitor_de_qr_codes_e_gerador_de_codigos),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.SettingsApplications,
            route = Screen.QrCode.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { QrCodeScreen(navController = navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "device_info",
            title = Res.get(R.string.device_info_hardware),
            description = Res.get(R.string.especificacoes_completas_de_cpu_gpu_memo),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.Memory,
            route = Screen.DeviceInfo.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { DeviceInfoScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "hp48g_calculator",
            title = Res.get(R.string.calculadora_hp_48g),
            description = Res.get(R.string.emulador_de_calculadora_cientifica_rpn_e),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.Calculate,
            route = Screen.Hp48gCalculator.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { Hp48gCalculatorScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "price_calculator",
            title = Res.get(R.string.calculadora_de_precos),
            description = Res.get(R.string.comparador_de_custo_beneficio_de_produto),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.Calculate,
            route = Screen.PriceCalculator.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { PriceCalculatorScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "calorie_calculator",
            title = Res.get(R.string.calc_calorias_veg),
            description = Res.get(R.string.calculadora_nutricional_de_calorias_e_ma),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.Calculate,
            route = Screen.CalorieCalculator.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { CalorieCalculatorScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "obd2_scanner",
            title = Res.get(R.string.scanner_obd_ii),
            description = Res.get(R.string.diagnostico_veicular_via_bluetooth_elm32),
            category = PluginCategory.UTILITIES,
            icon = Icons.Default.DirectionsCar,
            route = Screen.Obd2Scanner.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { Obd2ScannerScreen { navController.popBackStack() } }
            }
        })

        // --- SECURITY & PRIVACY ---
        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "password_vault",
            title = Res.get(R.string.cofre_de_senhas),
            description = Res.get(R.string.gerenciador_de_senhas_e_credenciais_com),
            category = PluginCategory.SECURITY,
            icon = Icons.Default.Lock,
            route = Screen.PasswordVault.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) {
                    PasswordVaultScreen(
                        onBackClick = { navController.popBackStack() },
                        onNavigateToLocalChat = { navController.navigate(Screen.LocalChat.route) }
                    )
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "totp_authenticator",
            title = Res.get(R.string.autenticador_2fa),
            description = Res.get(R.string.gerador_de_codigos_de_autenticacao_de_do),
            category = PluginCategory.SECURITY,
            icon = Icons.Default.PhonelinkLock,
            route = Screen.TotpAuthenticator.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) {
                    TotpAuthenticatorScreen(
                        onBackClick = { navController.popBackStack() },
                        onNavigateToLocalChat = { navController.navigate(Screen.LocalChat.route) },
                        onNavigateToQrScanner = { navController.navigate(Screen.QrCode.route) }
                    )
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "power_off_protection",
            title = Res.get(R.string.protecao_ao_desligar),
            description = Res.get(R.string.bloqueio_e_alarme_ao_tentar_desligar_o_a),
            category = PluginCategory.SECURITY,
            icon = Icons.Default.Security,
            route = Screen.PowerOffProtection.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { PowerOffProtectionScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "device_security",
            title = Res.get(R.string.verificacao_de_seguranca),
            description = Res.get(R.string.auditoria_de_integridade_verificacao_de),
            category = PluginCategory.SECURITY,
            icon = Icons.Default.Security,
            route = Screen.DeviceSecurity.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { DeviceSecurityScreen { navController.popBackStack() } }
            }
        })

        // --- PRODUCTIVITY & MANAGEMENT ---
        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "file_manager",
            title = Res.get(R.string.gerenciador_de_arquivos),
            description = Res.get(R.string.explorador_de_arquivos_locais_cliente_ft),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Folder,
            route = Screen.FileManager.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { FileManagerScreen(navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "terminal",
            title = Res.get(R.string.terminal_ssh),
            description = Res.get(R.string.console_de_comandos_shell_e_cliente_ssh),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.DeveloperMode,
            route = Screen.Terminal.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { TerminalScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "history",
            title = Res.get(R.string.historico_e_exportar_pdf),
            description = Res.get(R.string.registro_de_atividades_e_testes_com_expo),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.History,
            route = Screen.History.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) {
                    HistoryScreen(onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Dashboard.route)
                        }
                    })
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "quick_notes_kanban",
            title = Res.get(R.string.notas_e_kanban),
            description = Res.get(R.string.organizador_de_tarefas_estilo_quadro_kan),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.AutoMirrored.Filled.Assignment,
            route = Screen.QuickNotesKanban.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { QuickNotesKanbanScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "notes",
            title = Res.get(R.string.bloco_de_notas),
            description = Res.get(R.string.editor_de_anotacoes_com_persistencia_rap),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.AutoMirrored.Filled.Assignment,
            route = Screen.Notes.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { NotesScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "shopping_list",
            title = Res.get(R.string.lista_de_compras),
            description = Res.get(R.string.organizador_de_compras_com_calculo_total),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.ShoppingCart,
            route = Screen.ShoppingList.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { ShoppingListScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "calendar_alarms",
            title = Res.get(R.string.calendario_e_alarmes),
            description = Res.get(R.string.agendamento_inteligente_de_alarmes_e_eve),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Schedule,
            route = Screen.CalendarAlarms.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { CalendarAlarmsScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "backup_sync",
            title = Res.get(R.string.backup_e_nuvem),
            description = Res.get(R.string.sincronizacao_de_dados_e_backup_em_nuvem),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Backup,
            route = Screen.BackupSync.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { BackupSyncScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "audio_recorder",
            title = Res.get(R.string.gravador_de_audio),
            description = Res.get(R.string.gravador_de_voz_e_audio_em_alta_fidelida),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Mic,
            route = Screen.AudioRecorder.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { AudioRecorderScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "offline_transcription",
            title = Res.get(R.string.transcricao_offline),
            description = Res.get(R.string.reconhecimento_de_fala_e_transcricao_de),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Hearing,
            route = Screen.OfflineTranscription.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { TranscriptionScreen { navController.popBackStack() } }
            }
        })



        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "cache_cleaner",
            title = Res.get(R.string.limpador_de_cache),
            description = Res.get(R.string.limpeza_de_arquivos_temporarios_e_libera),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.CleaningServices,
            route = Screen.CacheCleaner.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { CacheCleanerScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "file_synchronizer",
            title = Res.get(R.string.sincronizador_de_arquivos),
            description = Res.get(R.string.espelhamento_e_sincronizacao_de_diretori),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Sync,
            route = Screen.FileSynchronizer.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { FileSynchronizerScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "screen_recorder",
            title = Res.get(R.string.gravador_de_tela),
            description = Res.get(R.string.captura_de_video_da_tela_do_android_com),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Videocam,
            route = Screen.ScreenRecorder.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { ScreenRecorderScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "media_manager",
            title = Res.get(R.string.gestor_de_midia),
            description = Res.get(R.string.galeria_rapida_e_organizador_de_fotos_mu),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.PermMedia,
            route = Screen.MediaManager.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { MediaManagerScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "dev_settings",
            title = Res.get(R.string.opcoes_do_desenvolvedor),
            description = Res.get(R.string.atalhos_rapidos_para_configuracoes_de_de),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.DeveloperMode,
            route = Screen.DevSettings.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { DevSettingsScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "activity_launcher",
            title = Res.get(R.string.launcher_de_atividades),
            description = Res.get(R.string.inicie_qualquer_activity_oculta_ou_inter),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Launch,
            route = Screen.ActivityLauncher.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { ActivityLauncherScreen { navController.popBackStack() } }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "whiteboard",
            title = Res.get(R.string.quadro_branco),
            description = Res.get(R.string.tela_de_desenho_livre_e_anotacoes_visuai),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Brush,
            route = Screen.Whiteboard.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(route) { WhiteboardScreen(navController) }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "media_player",
            title = Res.get(R.string.media_player),
            description = Res.get(R.string.reprodutor_de_audio_e_video_com_suporte),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.PlayCircle,
            route = Screen.MediaPlayer.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(
                    route = "media_player?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path") ?: ""
                    MediaPlayerScreen(encodedPath = path, onBack = { navController.popBackStack() })
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "text_editor",
            title = Res.get(R.string.editor_de_texto),
            description = Res.get(R.string.editor_de_arquivos_de_texto_e_codigo_fon),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.EditNote,
            route = Screen.TextEditor.route
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(
                    route = "text_editor?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path") ?: ""
                    TextEditorScreen(encodedPath = path, onBack = { navController.popBackStack() })
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "epub_viewer",
            title = Res.get(R.string.leitor_epub),
            description = Res.get(R.string.leitor_de_livros_digitais_em_formato_epu),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.Book,
            route = "epub_viewer"
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(
                    route = "epub_viewer?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path") ?: ""
                    EpubViewerScreen(encodedPath = path, onBack = { navController.popBackStack() }, onPickFile = { navController.navigate("file_browser") })
                }
            }
        })

        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "pdf_viewer",
            title = Res.get(R.string.visualizador_pdf),
            description = Res.get(R.string.visualizador_de_documentos_pdf_locais),
            category = PluginCategory.PRODUCTIVITY,
            icon = Icons.Default.PictureAsPdf,
            route = "pdf_viewer"
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(
                    route = "pdf_viewer?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path") ?: ""
                    PdfViewerScreen(encodedPath = path, onBack = { navController.popBackStack() }, onPickFile = { navController.navigate("file_browser") })
                }
            }
        })

        // --- DROIDLINK REMOTE ---
        PluginManager.registerNativePlugin(object : BaseNativePlugin(
            id = "droidlink_remote",
            title = Res.get(R.string.droidlink_remote),
            description = Res.get(R.string.controle_remoto_android_via_wi_fi_local),
            category = PluginCategory.NETWORK,
            icon = Icons.Default.Cast,
            route = Screen.DroidLinkRemote.route,
            permissions = listOf(
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
            )
        ) {
            override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
                navGraphBuilder.composable(Screen.DroidLinkRemote.route) {
                    com.example.droidlink.ui.DroidLinkRoleSelectScreen(navController)
                }
                navGraphBuilder.composable(Screen.DroidLinkServer.route) {
                    com.example.droidlink.ui.DroidLinkServerPanel(
                        navController,
                        androidx.lifecycle.viewmodel.compose.viewModel()
                    )
                }
                navGraphBuilder.composable(Screen.DroidLinkConnect.route) {
                    val clientViewModel: com.example.droidlink.client.DroidLinkClientViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.example.droidlink.ui.DroidLinkConnectScreen(
                        viewModel = clientViewModel,
                        onBack = { navController.popBackStack() },
                        onConnected = { navController.navigate(Screen.DroidLinkConsole.route) }
                    )
                }
                navGraphBuilder.composable(Screen.DroidLinkConsole.route) {
                    val clientViewModel: com.example.droidlink.client.DroidLinkClientViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.example.droidlink.ui.DroidLinkConsoleScreen(
                        viewModel = clientViewModel,
                        onDisconnected = { navController.popBackStack() }
                    )
                }
            }
        })
    }
}
