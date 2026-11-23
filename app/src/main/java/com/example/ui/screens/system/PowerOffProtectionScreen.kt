package com.example.ui.screens.system

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppTopAppBar
import com.example.ui.components.GradientButton
import com.example.ui.components.InfoRow
import com.example.ui.components.NeonCard
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerOffProtectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER
    val brand = Build.BRAND
    val model = Build.MODEL
    val guide = remember(manufacturer, brand) { findPowerOffGuide(manufacturer, brand) } // EXT-R01
    val checks = remember { buildPowerOffChecks(context) }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            AppTopAppBar(
                title = Res.get(R.string.protecao_ao_desligar),
                onBack = onBack,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NeonCard(borderColor = CyanNeon) {
                Text(
                    Res.get(R.string.dispositivo_detectado),
                    color = OnSurfaceLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                InfoRow("Fabricante", manufacturer.capitalize())
                InfoRow("Marca", brand.capitalize())
                InfoRow("Modelo", model)
                InfoRow("Android", Res.get(R.string.android_x_api_x, (Build.VERSION.RELEASE), (Build.VERSION.SDK_INT)))
            }

            checks.forEach { check ->
                StatusCard(check)
            }

            NeonCard(borderColor = ElectricViolet) {
                Text(
                    guide.first,
                    color = OnSurfaceLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
                guide.second.split("\n").forEach { line ->
                    Text(
                        text = line,
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    Res.get(R.string.aviso_apps_comuns_nao_conseguem_ativar_e) +
                            Res.get(R.string.abra_as_configuracoes_de_seguranca_do_ap),
                    color = OnSurfaceDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }

            GradientButton(
                text = Res.get(R.string.abrir_configuracoes_de_seguranca),
                onClick = { openSecuritySettings(context) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = DeepDark,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

private enum class Status(val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String) {
    SAFE(SuccessGreen, Icons.Default.CheckCircle, "OK"),
    WARNING(WarningAmber, Icons.Default.Warning, Res.get(R.string.atencao)),
    DANGER(DangerRed, Icons.Default.Error, "Risco"),
    INFO(OnSurfaceMuted, Icons.Default.Info, "Info"),
}

private data class PowerOffCheck(
    val title: String,
    val status: Status,
    val value: String,
    val tip: String,
)

@Composable
private fun StatusCard(check: PowerOffCheck) {
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
                Text(
                    text = check.status.label,
                    color = check.status.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = check.tip,
                color = OnSurfaceMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

private fun buildPowerOffChecks(context: Context): List<PowerOffCheck> {
    val checks = mutableListOf<PowerOffCheck>()

    // 1. Screen lock
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
    val isSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        keyguard?.isDeviceSecure == true
    } else {
        keyguard?.isKeyguardSecure == true
    }
    checks += PowerOffCheck(
        title = Res.get(R.string.bloqueio_de_tela),
        status = if (isSecure) Status.SAFE else Status.DANGER,
        value = if (isSecure) Res.get(R.string.ativo) else Res.get(R.string.nao_configurado),
        tip = Res.get(R.string.configure_um_pin_senha_ou_padrao_sem_iss),
    )

    // 2. Find My Device (Google)
    val findMyDeviceActive = isFindMyDeviceActive(context)
    checks += PowerOffCheck(
        title = Res.get(R.string.encontrar_meu_dispositivo_google),
        status = when (findMyDeviceActive) {
            true -> Status.SAFE
            false -> Status.WARNING
        },
        value = if (findMyDeviceActive) Res.get(R.string.ativo_como_administrador) else Res.get(R.string.nao_detectado_como_ativo),
        tip = Res.get(R.string.ative_em_configuracoes_seguranca_encontr),
    )

    // 3. Shutdown protection availability
    val protectionInfo = evaluateShutdownProtection(context)
    checks += PowerOffCheck(
        title = protectionInfo.title,
        status = protectionInfo.status,
        value = protectionInfo.value,
        tip = protectionInfo.tip,
    )

    // 4. Theft Detection Lock (Android 15+)
    checks += if (Build.VERSION.SDK_INT >= 35) {
        PowerOffCheck(
            title = Res.get(R.string.bloqueio_por_deteccao_de_roubo),
            status = Status.INFO,
            value = Res.get(R.string.disponivel_no_android_15),
            tip = Res.get(R.string.o_android_15_pode_detectar_movimentos_de),
        )
    } else {
        PowerOffCheck(
            title = Res.get(R.string.bloqueio_por_deteccao_de_roubo),
            status = Status.INFO,
            value = Res.get(R.string.requer_android_15_ou_superior),
            tip = Res.get(R.string.essa_protecao_nativa_so_esta_disponivel),
        )
    }

    return checks
}

private data class ShutdownProtectionInfo(
    val title: String,
    val status: Status,
    val value: String,
    val tip: String,
)

private fun evaluateShutdownProtection(context: Context): ShutdownProtectionInfo {
    val brand = Build.BRAND.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isXiaomi = brand in listOf("xiaomi", "redmi", "poco", "blackshark") ||
            manufacturer in listOf("xiaomi")

    return when {
        isXiaomi -> evaluateXiaomiShutdownProtection(context)
        brand.contains("samsung") -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_samsung),
            status = Status.INFO,
            value = Res.get(R.string.verifique_manualmente),
            tip = Res.get(R.string.em_alguns_modelos_samsung_configuracoes),
        )
        brand.contains("google") || brand.contains("pixel") -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_pixel),
            status = Status.INFO,
            value = Res.get(R.string.nao_disponivel_nativamente),
            tip = Res.get(R.string.pixel_nao_oferece_exigir_pin_para_deslig),
        )
        brand.contains("oneplus") -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_oneplus),
            status = Status.INFO,
            value = Res.get(R.string.verifique_manualmente),
            tip = Res.get(R.string.configuracoes_seguranca_e_bloqueio_de_te),
        )
        brand.contains("motorola") -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_motorola),
            status = Status.INFO,
            value = Res.get(R.string.verifique_manualmente),
            tip = Res.get(R.string.alguns_modelos_tem_bloqueio_imediato_ao),
        )
        brand.contains("realme") || brand.contains("oppo") || brand.contains("oneplus") -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_coloros),
            status = Status.INFO,
            value = Res.get(R.string.verifique_manualmente),
            tip = Res.get(R.string.configuracoes_senha_e_seguranca_procure),
        )
        brand.contains("huawei") || brand.contains("honor") -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_emui_magicos),
            status = Status.INFO,
            value = Res.get(R.string.verifique_manualmente),
            tip = Res.get(R.string.configuracoes_seguranca_e_privacidade_ma),
        )
        else -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_2),
            status = Status.INFO,
            value = Res.get(R.string.depende_do_fabricante),
            tip = Res.get(R.string.procure_em_configuracoes_seguranca_por_e),
        )
    }
}

private fun evaluateXiaomiShutdownProtection(context: Context): ShutdownProtectionInfo {
    val versionName = getXiaomiSecurityVersion(context)
    val hasMinimumVersion = versionName?.let { isSecurityAppShutdownAware(it) } == true

    val settingsValue = try {
        Settings.Secure.getInt(context.contentResolver, "miui_shut_down_password_enabled", 0) == 1
    } catch (e: Exception) {
        false
    }

    return when {
        hasMinimumVersion -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_xiaomi),
            status = Status.SAFE,
            value = Res.get(R.string.app_seguranca_compativel_x, versionName),
            tip = Res.get(R.string.seu_app_seguranca_tem_a_funcao_verifique),
        )
        settingsValue -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_xiaomi),
            status = Status.WARNING,
            value = Res.get(R.string.chave_ativada_mas_app_incompativel),
            tip = Res.get(R.string.a_chave_miui_shut_down_password_enabled, versionName),
        )
        else -> ShutdownProtectionInfo(
            title = Res.get(R.string.protecao_ao_desligar_xiaomi),
            status = Status.INFO,
            value = Res.get(R.string.nao_disponivel_nesta_versao_x, versionName),
            tip = Res.get(R.string.o_recurso_confirmacao_de_desligamento_ch),
        )
    }
}

private fun getXiaomiSecurityVersion(context: Context): String? {
    return try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo("com.miui.securitycenter", PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo("com.miui.securitycenter", 0)
        }
        info.versionName
    } catch (e: Exception) {
        null
    }
}

private fun isSecurityAppShutdownAware(versionName: String?): Boolean {
    if (versionName.isNullOrBlank()) return false
    return try {
        val digits = versionName.split(".", "-", "_")[0].toIntOrNull() ?: 0
        // Shutdown confirmation appeared around v12.7.2 of the Security app.
        digits >= 12 && versionName.contains(Regex("""(12\.(7|8|9)|1[3-9]\.\d+)"""))
    } catch (e: Exception) {
        false
    }
}

private fun isFindMyDeviceActive(context: Context): Boolean {
    return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admins = dpm?.activeAdmins ?: emptyList()
        admins.any { it.packageName == "com.google.android.gms" || it.flattenToString().contains("mdm") }
    } catch (e: Exception) {
        false
    }
}

private fun openSecuritySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { it.uppercase() }
}

private fun findPowerOffGuide(manufacturer: String, brand: String): Pair<String, String> {
    val key = Res.get(R.string.x_x_15, manufacturer, brand).lowercase()
    return when {
        key.contains("samsung") -> Res.get(R.string.samsung_one_ui) to Res.get(R.string.guia_samsung)

        key.contains("xiaomi") || key.contains("redmi") || key.contains("poco") -> Res.get(R.string.xiaomi_redmi_poco_miui_hyperos) to Res.get(R.string.guia_xiaomi)

        key.contains("oneplus") -> Res.get(R.string.oneplus_oxygenos) to Res.get(R.string.guia_oneplus)

        key.contains("motorola") -> "Motorola" to Res.get(R.string.guia_motorola)

        key.contains("google") || key.contains("pixel") -> Res.get(R.string.google_pixel) to Res.get(R.string.guia_pixel)

        key.contains("realme") || key.contains("oppo") -> Res.get(R.string.realme_oppo_coloros) to Res.get(R.string.guia_coloros)

        key.contains("asus") -> Res.get(R.string.asus_zenui) to Res.get(R.string.guia_asus)

        key.contains("huawei") || key.contains("honor") -> Res.get(R.string.huawei_honor_emui_magicos) to Res.get(R.string.guia_huawei)

        key.contains("vivo") -> Res.get(R.string.vivo_funtouch_os_originos) to Res.get(R.string.guia_vivo)

        key.contains("sony") -> Res.get(R.string.sony_xperia_ui) to Res.get(R.string.guia_sony)

        else -> Res.get(R.string.configuracoes_gerais_do_android) to Res.get(R.string.guia_generico)
    }
}
