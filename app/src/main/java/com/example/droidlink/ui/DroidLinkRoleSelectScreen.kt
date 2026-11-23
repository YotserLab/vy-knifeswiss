package com.example.droidlink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.R
import com.example.Res

// ── Palette ──────────────────────────────────────────────────────────────────
private val DeepDark       = Color(0xFF0A0E1A)
private val DarkNavy       = Color(0xFF111827)
private val CyanNeon       = Color(0xFF00D4FF)
private val SuccessGreen   = Color(0xFF22C55E)
private val AmberGold      = Color(0xFFF59E0B)
private val OnSurfaceLight = Color(0xFFE8EAF6)
private val OnSurfaceMuted = Color(0xFF8B97C8)

// ── Screen ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidLinkRoleSelectScreen(navController: NavController) {
    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = Res.get(R.string.droidlink_remote),
                        color = OnSurfaceLight,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = OnSurfaceLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkNavy
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Welcome text
            Text(
                text = Res.get(R.string.transforme_seu_celular_em_servidor_de_su),
                color = OnSurfaceMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Server card
            RoleCard(
                icon = Icons.Default.Cast,
                accentColor = CyanNeon,
                title = Res.get(R.string.modo_servidor),
                subtitle = Res.get(R.string.expor_este_dispositivo_para_controle_rem),
                onClick = { navController.navigate("droidlink_server") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Client card
            RoleCard(
                icon = Icons.Default.Computer,
                accentColor = SuccessGreen,
                title = Res.get(R.string.modo_cliente),
                subtitle = Res.get(R.string.conectar_e_controlar_outro_dispositivo_n),
                onClick = { navController.navigate("droidlink_connect") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Wi-Fi warning card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkNavy),
                border = BorderStroke(1.dp, AmberGold)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = Res.get(R.string.ambos_os_dispositivos_devem_estar_na_mes),
                        color = AmberGold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Version badge
            Text(
                text = Res.get(R.string.droidlink_remote_v1_0_wi_fi_local_zero_c),
                color = OnSurfaceMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
    }
}

// ── Role Card ─────────────────────────────────────────────────────────────────
@Composable
private fun RoleCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = BorderStroke(1.5.dp, accentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = OnSurfaceLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
