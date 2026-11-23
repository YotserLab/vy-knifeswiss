package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryHeader
import com.example.ui.components.VYDivider
import com.example.ui.theme.*
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshInfoScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("local_chat_prefs", android.content.Context.MODE_PRIVATE) } // EXT-R12
    
    var isMeshEnabled by remember { mutableStateOf(sharedPref.getBoolean("mesh_enabled", false)) }
    var forceCoordinator by remember { mutableStateOf(sharedPref.getBoolean("force_coordinator", false)) }

    fun updateMeshState(enabled: Boolean) {
        isMeshEnabled = enabled
        sharedPref.edit().putBoolean("mesh_enabled", enabled).apply()
        com.example.service.LocalChatService.setServiceEnabled(context, enabled)
        
        val intent = android.content.Intent(context, com.example.service.LocalChatService::class.java).apply {
            action = if (enabled) com.example.service.LocalChatService.ACTION_START_SERVICE else com.example.service.LocalChatService.ACTION_STOP_SERVICE
        }
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.startService(intent)
        }
    }

    fun updateCoordinatorState(forced: Boolean) {
        forceCoordinator = forced
        sharedPref.edit().putBoolean("force_coordinator", forced).apply()
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Res.get(R.string.whatsapp_local_mesh),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurfaceLight,
                        )
                        Text(
                            Res.get(R.string.configuracoes_e_ajuda_da_malha),
                            fontSize = 12.sp,
                            color = OnSurfaceMuted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
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
                CategoryHeader(label = Res.get(R.string.controles_da_rede), accentColor = CyanNeon)
                VYDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    modifier = Modifier.background(DeepDark),
                    leadingContent = {
                        Icon(Icons.Default.Share, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                    },
                    headlineContent = {
                        Text(Res.get(R.string.modo_mesh_wi_fi_direct), fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                    },
                    supportingContent = {
                        Text(Res.get(R.string.funcionalidade_em_desenvolvimento_o_rote), fontSize = 12.sp, color = OnSurfaceMuted)
                    },
                    trailingContent = {
                        Switch(
                            checked = isMeshEnabled,
                            onCheckedChange = { updateMeshState(it) },
                            enabled = false,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepDark,
                                checkedTrackColor = CyanNeon,
                                uncheckedThumbColor = OnSurfaceDim,
                                uncheckedTrackColor = DarkNavyVariant,
                            ),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = DeepDark),
                )

                VYDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp), color = DarkBorder)

                ListItem(
                    modifier = Modifier.background(DeepDark),
                    headlineContent = {
                        Text(Res.get(R.string.forcar_papel_de_coordenador), fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                    },
                    supportingContent = {
                        Text(Res.get(R.string.ideal_se_o_celular_for_ficar_parado_cone), fontSize = 12.sp, color = OnSurfaceMuted)
                    },
                    trailingContent = {
                        Switch(
                            checked = forceCoordinator,
                            onCheckedChange = { updateCoordinatorState(it) },
                            enabled = isMeshEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepDark,
                                checkedTrackColor = CyanNeon,
                                uncheckedThumbColor = OnSurfaceDim,
                                uncheckedTrackColor = DarkNavyVariant,
                            ),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = DeepDark),
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                CategoryHeader(label = Res.get(R.string.como_funciona), accentColor = ElectricViolet)
                VYDivider(modifier = Modifier.padding(horizontal = 16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            Res.get(R.string.a_rede_mesh_funciona_conectando_seu_celu),
                            color = OnSurfaceLight,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Res.get(R.string.o_caminho_da_mensagem),
                            color = CyanNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            Res.get(R.string.quando_voce_envia_uma_mensagem_para_algu),
                            color = OnSurfaceLight,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Res.get(R.string.densidade_e_poder),
                            color = ElectricViolet,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            Res.get(R.string.em_shows_acampamentos_ou_cenarios_de_des),
                            color = OnSurfaceLight,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
