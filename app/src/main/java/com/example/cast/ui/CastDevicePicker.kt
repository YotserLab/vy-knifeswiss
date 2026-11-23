package com.example.cast.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import com.example.cast.discovery.CastDiscovery
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.DeepDark
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceLight
import com.example.ui.theme.OnSurfaceMuted
import com.example.R
import com.example.Res

/** Icon that best represents a discovered device's protocol. */
internal fun castDeviceIcon(type: CastDeviceType): ImageVector = when (type) {
    CastDeviceType.DLNA -> Icons.Default.Tv
    CastDeviceType.ROKU -> Icons.Default.Router
    CastDeviceType.CHROMECAST -> Icons.Default.Cast
    CastDeviceType.DIAL -> Icons.Default.DevicesOther
}

/** Small amber chip marking targets that cannot play local media. */
@Composable
internal fun LimitedChip() {
    Surface(
        color = AmberGold.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f))
    ) {
        Text(
            text = "limitado",
            color = AmberGold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * One discovered cast target. Shared between the picker sheet and the
 * devices card on CastScreen — [trailing] lets each caller decide what
 * sits at the end (chip, spinner, both…).
 */
@Composable
internal fun CastDeviceRow(
    device: CastDevice,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = castDeviceIcon(device.type),
            contentDescription = null,
            tint = if (device.canCastLocalMedia) CyanNeon else OnSurfaceDim,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    color = OnSurfaceLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!device.canCastLocalMedia) {
                    Spacer(Modifier.width(8.dp))
                    LimitedChip()
                }
            }
            val subtitle = listOf(device.manufacturer, device.modelName, device.ip)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = OnSurfaceMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Bottom sheet listing cast targets discovered on the LAN. Scanning
 * starts automatically when the sheet opens; [onDeviceSelected] is
 * invoked on row click and the caller decides whether to connect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePicker(
    discovery: CastDiscovery,
    onDeviceSelected: (CastDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val devices by discovery.devices.collectAsState()
    val scanning by discovery.scanning.collectAsState()
    var showManualIp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { discovery.startScan() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AmberGold) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = Res.get(R.string.transmitir_para_tv),
                    color = OnSurfaceLight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (scanning) {
                    CircularProgressIndicator(
                        color = CyanNeon,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = {
                        // stale entries linger otherwise — clear before rescanning
                        discovery.clear()
                        discovery.startScan()
                    },
                    enabled = !scanning
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = Res.get(R.string.escanear_novamente),
                        tint = if (scanning) OnSurfaceDim else CyanNeon
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (scanning) {
                                Res.get(R.string.procurando_dispositivos)
                            } else {
                                Res.get(R.string.nenhum_dispositivo_encontrado_na_rede)
                            },
                            color = OnSurfaceMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (!scanning) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = Res.get(R.string.verifique_se_a_tv_esta_ligada_e_conectad),
                                color = OnSurfaceDim,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(devices, key = { it.id + it.ip }) { device ->
                        CastDeviceRow(device = device, onClick = { onDeviceSelected(device) })
                    }
                }
            }

            // Manual entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showManualIp = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = Res.get(R.string.adicionar_por_ip),
                    color = AmberGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (showManualIp) {
        ManualIpDialog(
            onConfirm = { ip ->
                discovery.addManualDevice(ip)
                showManualIp = false
            },
            onDismiss = { showManualIp = false }
        )
    }
}

/** Loose IPv4 shape check — enough to reject typos before probing. */
private val IPV4_PATTERN = Regex("""\d{1,3}(\.\d{1,3}){3}""")

/**
 * Shared Res.get(R.string.adicionar_por_ip) dialog used by both the picker sheet and
 * the CastScreen devices card. Field state lives inside the dialog so
 * cancel/dismiss always leaves it clean; confirm stays disabled until
 * the input loosely matches an IPv4 address.
 */
@Composable
internal fun ManualIpDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var ip by remember { mutableStateOf("") }
    val trimmed = ip.trim()
    val valid = trimmed.matches(IPV4_PATTERN)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = { Text(Res.get(R.string.adicionar_dispositivo), color = OnSurfaceLight) },
        text = {
            Column {
                Text(
                    text = Res.get(R.string.informe_o_endereco_ip_do_dispositivo_na),
                    color = OnSurfaceMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    singleLine = true,
                    label = { Text(Res.get(R.string.endereco_ip), color = OnSurfaceMuted) },
                    placeholder = { Text("192.168.0.10", color = OnSurfaceDim) },
                    isError = ip.isNotBlank() && !valid,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = OnSurfaceLight,
                        unfocusedTextColor = OnSurfaceLight,
                        cursorColor = CyanNeon
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = valid) {
                Text(Res.get(R.string.adicionar), color = if (valid) AmberGold else OnSurfaceDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Res.get(R.string.cancelar), color = OnSurfaceMuted)
            }
        }
    )
}
