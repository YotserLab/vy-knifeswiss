package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityManualDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) } // 0=2FA Migration, 1=Vault Guide, 2=Security & Backup

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(Res.get(R.string.manual_do_cofre_2fa), color = OnSurfaceLight, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                // Tab Selection
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkNavy,
                    contentColor = CyanNeon,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(Res.get(R.string.migracao_2fa), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(Res.get(R.string.cofre_de_senhas_2), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(Res.get(R.string.backup_seguranca), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> MigrationGuideSection()
                        1 -> VaultGuideSection()
                        2 -> SecurityGuideSection()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepDark)
            ) {
                Text(Res.get(R.string.entendi), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun MigrationGuideSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Google Authenticator
        ManualCard(
            title = Res.get(R.string.n_1_migrar_do_google_authenticator),
            accentColor = Color(0xFF4285F4),
            icon = Icons.Default.Phonelink
        ) {
            Text(Res.get(R.string.no_google_authenticator_acesse_o_menu_e), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.o_google_exibira_um_qr_code_de_transfere), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.no_nosso_aplicativo_va_no_autenticador_2), color = OnSurfaceMuted, fontSize = 12.sp)
        }

        // Microsoft Authenticator
        ManualCard(
            title = Res.get(R.string.n_2_migrar_do_microsoft_authenticator),
            accentColor = Color(0xFF00A4EF),
            icon = Icons.Default.Security
        ) {
            Text(Res.get(R.string.o_microsoft_authenticator_nao_permite_ex), color = OnSurfaceMuted, fontSize = 12.sp)
            Text(Res.get(R.string.para_transferir_acesse_os_sites_cadastra), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.clique_em_reconfigurar_autenticador_ou_e), color = OnSurfaceLight, fontSize = 12.sp)
        }

        // Bitwarden & outros
        ManualCard(
            title = Res.get(R.string.n_3_outros_apps_bitwarden_aegis_authy),
            accentColor = Color(0xFF22C55E),
            icon = Icons.Default.SwapHoriz
        ) {
            Text(Res.get(R.string.exporte_o_arquivo_de_backup_no_seu_app_a), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.no_nosso_app_use_a_opcao_de_importar_csv), color = OnSurfaceMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VaultGuideSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ManualCard(
            title = Res.get(R.string.cofre_criptografado),
            accentColor = CyanNeon,
            icon = Icons.Default.Lock
        ) {
            Text(Res.get(R.string.todas_as_suas_senhas_sao_salvas_localmen), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.o_pin_mestre_de_4_a_16_digitos_gera_a_ch), color = OnSurfaceMuted, fontSize = 12.sp)
        }

        ManualCard(
            title = Res.get(R.string.recursos_do_cofre),
            accentColor = AmberGold,
            icon = Icons.Default.AutoFixHigh
        ) {
            Text(Res.get(R.string.gerador_de_senhas_crie_senhas_de_8_a_64), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.categorias_organize_em_logins_cartoes_no), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.auto_lock_o_cofre_bloqueia_automaticamen), color = OnSurfaceMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SecurityGuideSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ManualCard(
            title = Res.get(R.string.backup_criptografado_vyvault),
            accentColor = ElectricViolet,
            icon = Icons.Default.FileDownload
        ) {
            Text(Res.get(R.string.exportacao_segura_com_biometria_digital), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.o_arquivo_vyvault_gerado_fica_100_cripto), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.no_novo_aparelho_basta_informar_o_mesmo), color = OnSurfaceMuted, fontSize = 12.sp)
        }

        ManualCard(
            title = Res.get(R.string.o_que_fazer_se_esquecer_o_pin),
            accentColor = DangerRed,
            icon = Icons.Default.Warning
        ) {
            Text(Res.get(R.string.arquitetura_zero_knowledge_ninguem_tem_a), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.se_tiver_a_biometria_ativa_no_celular_at), color = OnSurfaceLight, fontSize = 12.sp)
            Text(Res.get(R.string.se_precisar_zerar_tudo_va_em_configuraco), color = OnSurfaceMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ManualCard(
    title: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeepDark)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            content()
        }
    }
}
