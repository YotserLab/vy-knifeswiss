package com.example.ui.screens.security

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.biometric.BiometricManager as AndroidXBiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val checks = remember { buildSecurityChecks(context) }

    Scaffold(
        containerColor = DeepDark,
        topBar = { AppTopAppBar(title = Res.get(R.string.verificacao_de_seguranca), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        ) {
            item {
                HeaderScoreCard(checks)
            }

            checks.forEach { check ->
                item {
                    SecurityCheckCard(check, context)
                }
            }

            item {
                TipsCard()
            }
        }
    }
}

private data class SecurityCheck(
    val title: String,
    val status: CheckStatus,
    val value: String,
    val tip: String,
    val intent: Intent?,
)

private enum class CheckStatus(val color: Color, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SAFE(SuccessGreen, "OK", Icons.Default.CheckCircle),
    WARNING(WarningAmber, Res.get(R.string.atencao), Icons.Default.Warning),
    DANGER(DangerRed, "Risco", Icons.Default.Error),
    INFO(OnSurfaceMuted, "Info", Icons.Default.Info),
}

private data class BiometricCheck(
    val status: CheckStatus,
    val value: String,
    val tip: String,
    val intent: Intent?,
)

private fun buildSecurityChecks(context: Context): List<SecurityCheck> {
    val checks = mutableListOf<SecurityCheck>()

    // 1. Screen lock
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
    val isSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        keyguard?.isDeviceSecure == true
    } else {
        keyguard?.isKeyguardSecure == true
    }
    checks += SecurityCheck(
        title = Res.get(R.string.bloqueio_de_tela),
        status = if (isSecure) CheckStatus.SAFE else CheckStatus.DANGER,
        value = if (isSecure) Res.get(R.string.ativo_e_seguro) else Res.get(R.string.nao_configurado_ou_inseguro),
        tip = Res.get(R.string.defina_um_pin_padrao_ou_senha_forte_evit),
        intent = Intent(Settings.ACTION_SECURITY_SETTINGS),
    )

    // 2. Biometry
    val biometric = AndroidXBiometricManager.from(context)
    val canAuth = biometric.canAuthenticate(Authenticators.BIOMETRIC_WEAK)
    val bio = when (canAuth) {
        AndroidXBiometricManager.BIOMETRIC_SUCCESS ->
            BiometricCheck(CheckStatus.SAFE, Res.get(R.string.biometria_cadastrada), Res.get(R.string.digital_ou_face_configurada), null)
        AndroidXBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            BiometricCheck(
                CheckStatus.WARNING,
                Res.get(R.string.sensor_disponivel_mas_sem_digital_face_c),
                Res.get(R.string.cadastre_uma_digital_ou_reconhecimento_f),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                else
                    Intent(Settings.ACTION_SECURITY_SETTINGS),
            )
        AndroidXBiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            BiometricCheck(CheckStatus.INFO, Res.get(R.string.sem_sensor_biometrico), Res.get(R.string.use_um_pin_ou_senha_forte), null) // COF-R11
        else ->
            BiometricCheck(CheckStatus.INFO, Res.get(R.string.biometria_indisponivel), Res.get(R.string.verifique_se_ha_um_metodo_de_desbloqueio), null)
    }
    checks += SecurityCheck(
        title = "Biometria",
        status = bio.status,
        value = bio.value,
        tip = bio.tip,
        intent = bio.intent,
    )

    // 3. ADB / USB debugging
    val adbEnabled = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (e: Exception) {
        false
    }
    checks += SecurityCheck(
        title = Res.get(R.string.depuracao_usb_adb),
        status = if (adbEnabled) CheckStatus.WARNING else CheckStatus.SAFE,
        value = if (adbEnabled) "Ativada" else "Desativada",
        tip = Res.get(R.string.deixe_a_depuracao_usb_desativada_no_dia),
        intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    // 4. Security patch
    val patch = Build.VERSION.SECURITY_PATCH
    val patchStatus = evaluatePatchAge(patch)
    checks += SecurityCheck(
        title = Res.get(R.string.patch_de_seguranca),
        status = patchStatus.status,
        value = patch ?: Res.get(R.string.desconhecido),
        tip = patchStatus.tip,
        intent = null,
    )

    // 5. VPN
    val isVpn = isVpnActive(context)
    checks += SecurityCheck(
        title = "VPN",
        status = if (isVpn) CheckStatus.SAFE else CheckStatus.INFO,
        value = if (isVpn) Res.get(R.string.conexao_vpn_ativa) else Res.get(R.string.nenhuma_vpn_ativa),
        tip = Res.get(R.string.use_uma_vpn_em_redes_publicas_para_prote),
        intent = Intent(Settings.ACTION_VPN_SETTINGS),
    )

    // 6. Root
    val rooted = isDeviceRooted()
    checks += SecurityCheck(
        title = Res.get(R.string.acesso_root_2),
        status = if (rooted) CheckStatus.DANGER else CheckStatus.SAFE,
        value = if (rooted) "Detectado" else Res.get(R.string.nao_detectado),
        tip = Res.get(R.string.celulares_com_root_perdem_camadas_de_seg),
        intent = null,
    )

    // 7. Accessibility services
    val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val services = a11yManager?.getEnabledAccessibilityServiceList(-1) ?: emptyList()
    val a11yNames = services.map { it.resolveInfo?.loadLabel(context.packageManager)?.toString() ?: it.id }.take(4)
    checks += SecurityCheck(
        title = Res.get(R.string.servicos_de_acessibilidade),
        status = if (services.isEmpty()) CheckStatus.SAFE else CheckStatus.WARNING,
        value = if (services.isEmpty()) Res.get(R.string.nenhum_ativo) else a11yNames.joinToString(", ").ifEmpty { Res.get(R.string.x_ativo_s, (services.size)) },
        tip = Res.get(R.string.revise_os_apps_com_acessibilidade_eles_p),
        intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    )

    // 8. Screen off timeout
    val timeout = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
    } catch (e: Exception) {
        -1
    }
    val timeoutSec = timeout / 1000
    val (timeoutStatus, timeoutValue, timeoutTip) = when {
        timeout <= 0 -> Triple(CheckStatus.INFO, Res.get(R.string.desconhecido), Res.get(R.string.nao_foi_possivel_ler_o_tempo_de_bloqueio))
        timeout <= 60_000 -> Triple(CheckStatus.SAFE, Res.get(R.string.xs_2, (timeoutSec)), Res.get(R.string.bloqueio_automatico_rapido))
        timeout <= 300_000 -> Triple(CheckStatus.WARNING, Res.get(R.string.xs_2, (timeoutSec)), Res.get(R.string.considere_reduzir_para_30s_1min_se_estiv))
        else -> Triple(CheckStatus.DANGER, Res.get(R.string.xs_2, (timeoutSec)), Res.get(R.string.bloqueio_muito_lento_reduza_para_evitar))
    }
    checks += SecurityCheck(
        title = Res.get(R.string.bloqueio_automatico),
        status = timeoutStatus,
        value = timeoutValue,
        tip = timeoutTip,
        intent = Intent(Settings.ACTION_DISPLAY_SETTINGS),
    )

    // 9. Find My Device (tip only)
    checks += SecurityCheck(
        title = Res.get(R.string.encontrar_meu_dispositivo),
        status = CheckStatus.INFO,
        value = Res.get(R.string.verifique_manualmente),
        tip = Res.get(R.string.ative_encontrar_meu_dispositivo_do_googl),
        intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    )

    // 10. Play Protect (tip only)
    checks += SecurityCheck(
        title = Res.get(R.string.google_play_protect),
        status = CheckStatus.INFO,
        value = Res.get(R.string.verifique_manualmente),
        tip = Res.get(R.string.mantenha_o_play_protect_ativo_play_store),
        intent = Intent(Intent.ACTION_VIEW).apply {
            `package` = "com.android.vending"
            data = android.net.Uri.parse("market://details?id=com.google.android.gms")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )

    return checks
}

private data class PatchStatus(val status: CheckStatus, val tip: String)

private fun evaluatePatchAge(patch: String?): PatchStatus {
    if (patch.isNullOrBlank()) return PatchStatus(CheckStatus.INFO, Res.get(R.string.nao_foi_possivel_identificar_o_patch_atu))
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val patchDate = sdf.parse(patch) ?: return PatchStatus(CheckStatus.INFO, Res.get(R.string.formato_de_patch_desconhecido_busque_uma))
        val now = System.currentTimeMillis()
        val diffMonths = TimeUnit.MILLISECONDS.toDays(now - patchDate.time) / 30
        when {
            diffMonths <= 3 -> PatchStatus(CheckStatus.SAFE, Res.get(R.string.patch_recente_mantenha_as_atualizacoes_a))
            diffMonths <= 6 -> PatchStatus(CheckStatus.WARNING, Res.get(R.string.patch_com_x_meses_verifique_se_ha_atuali, diffMonths))
            else -> PatchStatus(CheckStatus.DANGER, Res.get(R.string.patch_desatualizado_x_meses_atualize_par, diffMonths))
        }
    } catch (e: Exception) {
        PatchStatus(CheckStatus.INFO, Res.get(R.string.nao_foi_possivel_calcular_a_idade_do_pat))
    }
}

private fun isDeviceRooted(): Boolean {
    val paths = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
    )
    return paths.any { File(it).exists() }
}

private fun isVpnActive(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.let { null } // fallback not needed for API < 23
            return false
        } ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } catch (e: Exception) {
        false
    }
}

@Composable
private fun HeaderScoreCard(checks: List<SecurityCheck>) {
    val safe = checks.count { it.status == CheckStatus.SAFE }
    val total = checks.size
    val scoreColor = when {
        safe == total -> SuccessGreen
        safe >= total / 2 -> WarningAmber
        else -> DangerRed
    }

    NeonCard(borderColor = scoreColor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = Res.get(R.string.x_x_10, safe, total),
                color = scoreColor,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )
            Text(
                text = Res.get(R.string.itens_seguros),
                color = OnSurfaceMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (safe == total) Res.get(R.string.boa_postura_de_seguranca) else Res.get(R.string.corrija_os_itens_em_vermelho_e_amarelo),
                color = OnSurfaceLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityCheckCard(check: SecurityCheck, context: Context) {
    NeonCard(borderColor = check.status.color) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = check.status.icon,
                    contentDescription = null,
                    tint = check.status.color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = check.title,
                        color = OnSurfaceLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = check.value,
                        color = OnSurfaceMuted,
                        fontSize = 12.sp,
                    )
                }
                VYBadge(text = check.status.label, color = check.status.color)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = check.tip,
                color = OnSurfaceMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            if (check.intent != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { openSecurityIntent(context, check.intent) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = Res.get(R.string.abrir_configuracao),
                        color = CyanNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TipsCard() {
    NeonCard(borderColor = CyanNeon) {
        Column {
            Text(
                text = Res.get(R.string.dicas_contra_roubo_furto_e_acesso_forcad),
                color = OnSurfaceLight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            TipRow(Res.get(R.string.use_pin_ou_senha_nao_padrao_simples))
            TipRow(Res.get(R.string.ative_o_pin_do_chip_sim_lock_para_evitar))
            TipRow(Res.get(R.string.mantenha_encontrar_meu_dispositivo_ativo))
            TipRow(Res.get(R.string.anote_o_imei_do_seu_celular_digite_06_no))
            TipRow(Res.get(R.string.reduza_o_tempo_de_bloqueio_automatico))
            TipRow(Res.get(R.string.evite_notificacoes_sensiveis_na_tela_de))
            TipRow(Res.get(R.string.nao_deixe_o_e_mail_de_recuperacao_de_sen))
            TipRow(Res.get(R.string.oculte_apps_financeiros_usando_a_pasta_s))
            TipRow(Res.get(R.string.ative_a_verificacao_em_duas_etapas_2fa_u))
            TipRow(Res.get(R.string.evite_acessar_bancos_ou_digitar_senhas_a))
            TipRow(Res.get(R.string.nao_salve_senhas_de_banco_em_apps_sem_pr))
            TipRow(Res.get(R.string.em_situacao_de_risco_prefira_pin_em_vez))
            TipRow(Res.get(R.string.nao_deixe_adb_ligado_em_aparelho_que_nao))
            
            Spacer(Modifier.height(16.dp))
            
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.material3.TextButton(
                onClick = { 
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://yotserlab.com/pt/seguranca/"))
                    context.startActivity(intent)
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Security, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Res.get(R.string.mais_consultas_sobre_seguranca_digital),
                    color = CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun TipRow(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "•",
            color = CyanNeon,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun openSecurityIntent(context: Context, intent: Intent) {
    try {
        val resolved = intent.resolveActivity(context.packageManager)
        if (resolved != null) {
            context.startActivity(intent)
        } else {
            val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
