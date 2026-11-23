package com.example.ui.screens.system
import com.example.ui.screens.security.VaultSession
import com.example.utils.crypto.SecurePrefs
import com.example.utils.crypto.VaultCrypto

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.theme.*
import com.example.ui.components.*
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Dynamically checks whether the Developer Options intent is available on the system
    val isDevOptionsAvailable = remember {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) // EXT-R10
            val resolveInfo = context.packageManager.queryIntentActivities(intent, 0)
            resolveInfo.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.configuracoes_do_desenvolvedor),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurfaceLight
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = AmberGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.DeveloperMode,
                contentDescription = Res.get(R.string.modo_dev),
                modifier = Modifier.size(64.dp),
                tint = AmberGold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!isDevOptionsAvailable) {
                // If developer options are not enabled or available on the system
                NeonCard(
                    borderColor = DangerRed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = Res.get(R.string.aviso), tint = DangerRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Res.get(R.string.opcoes_do_desenvolvedor_desabilitadas),
                                fontWeight = FontWeight.Bold,
                                color = DangerRed,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = Res.get(R.string.as_opcoes_de_desenvolvedor_do_sistema_an),
                            color = OnSurfaceLight,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        GradientButton(
                            text = Res.get(R.string.abrir_sobre_o_telefone),
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                                    context.startActivity(intent)
                                    Toast.makeText(context, Res.get(R.string.procure_por_numero_da_versao_ou_build_nu), Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, Res.get(R.string.nao_foi_possivel_abrir_as_configuracoes), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            startColor = DangerRed,
                            endColor = DangerRed.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Developer options already enabled on the system
                NeonCard(
                    borderColor = DarkBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = Res.get(R.string.opcoes_habilitadas_no_sistema),
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Res.get(R.string.acesso_direto_as_configuracoes_de_desenv),
                            color = OnSurfaceMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        GradientButton(
                            text = Res.get(R.string.abrir_opcoes_de_desenvolvedor),
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, Res.get(R.string.erro_ao_abrir_configuracoes), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            startColor = ElectricViolet,
                            endColor = ElectricViolet.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            
            Spacer(modifier = Modifier.height(20.dp))

            // Fixed button for "About Phone"
            NeonCard(
                borderColor = CyanNeon,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Res.get(R.string.sobre_o_telefone),
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Res.get(R.string.acesse_as_informacoes_do_dispositivo_aqu),
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GradientButton(
                        text = Res.get(R.string.abrir_sobre_o_telefone),
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                                context.startActivity(intent)
                                Toast.makeText(context, Res.get(R.string.procure_por_numero_da_versao_ou_build_nu_2), Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, Res.get(R.string.nao_foi_possivel_abrir_as_configuracoes_2), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        startColor = CyanNeon,
                        endColor = CyanNeon.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Wi-Fi Debugging button (always accessible once enabled)
            NeonCard(
                borderColor = DarkBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Res.get(R.string.depuracao_sem_fio),
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Res.get(R.string.acesse_as_opcoes_de_desenvolvimento_do_s),
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GradientButton(
                        text = Res.get(R.string.ir_para_depuracao_por_wi_fi),
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                context.startActivity(intent)
                                Toast.makeText(context, Res.get(R.string.procure_pela_opcao_depuracao_por_wi_fi), Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, Res.get(R.string.ative_as_opcoes_de_desenvolvedor_no_sist), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        startColor = AmberGold,
                        endColor = AmberGold.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Redefinir Cofre & Autenticador ─────────────────────────────
            var showResetConfirm by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            NeonCard(
                borderColor = DangerRed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = "Reset", tint = DangerRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Res.get(R.string.redefinir_cofre_autenticador),
                            fontWeight = FontWeight.Bold,
                            color = DangerRed,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Res.get(R.string.apaga_permanentemente_todas_as_senhas_sa),
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GradientButton(
                        text = Res.get(R.string.resetar_cofre_e_autenticador),
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        startColor = DangerRed,
                        endColor = DangerRed.copy(alpha = 0.7f)
                    )
                }
            }

            if (showResetConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetConfirm = false },
                    containerColor = DarkNavy,
                    title = { Text(Res.get(R.string.zerar_cofre_e_autenticador), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                    text = { Text(Res.get(R.string.atencao_esta_acao_e_irreversivel_todas_a), color = OnSurfaceMuted) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val db = com.example.data.AppDatabase.getDatabase(context)
                                    db.vaultDao().getAll()
                                    db.clearAllTables()
                                    com.example.utils.crypto.VaultCrypto.resetVault(context)
                                    com.example.utils.crypto.SecurePrefs.remove(context, "vault_bio_pin")
                                    VaultSession.lock()
                                    withContext(Dispatchers.Main) {
                                        showResetConfirm = false
                                        Toast.makeText(context, Res.get(R.string.cofre_e_autenticador_redefinidos_com_suc), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text(Res.get(R.string.confirmar_reset), color = DangerRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirm = false }) {
                            Text(Res.get(R.string.cancelar), color = OnSurfaceMuted)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = Res.get(R.string.nota_de_seguranca_o_android_restringe_al),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
