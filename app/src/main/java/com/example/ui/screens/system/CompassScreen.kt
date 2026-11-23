package com.example.ui.screens.system

import android.content.Context
import android.location.LocationManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.SensorUtils
import java.util.Date
import android.location.Location
import android.hardware.GeomagneticField
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val sensorUtils = remember { SensorUtils(context) }

    val compassData by sensorUtils.getCompassData().collectAsState(initial = Pair(0f, false))
    val azimuth = compassData.first
    val isSimulated = compassData.second

    val animAzimuth by animateFloatAsState(
        targetValue = azimuth,
        animationSpec = tween(durationMillis = 200),
        label = "compass"
    )

    val direction = when (azimuth) {
        in 337.5..360.0, in 0.0..22.5 -> "N"
        in 22.5..67.5 -> "NE"
        in 67.5..112.5 -> "E"
        in 112.5..157.5 -> "SE"
        in 157.5..202.5 -> "S"
        in 202.5..247.5 -> "SW"
        in 247.5..292.5 -> "W"
        in 292.5..337.5 -> "NW"
        else -> "N"
    }

    // GPS coordinates
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var magneticDeclination by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        try {
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val hasGps = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (hasGps) {
                val loc = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                           ?: locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                loc?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                    val geoField = GeomagneticField(
                        it.latitude.toFloat(),
                        it.longitude.toFloat(),
                        it.altitude.toFloat(),
                        System.currentTimeMillis()
                    )
                    magneticDeclination = geoField.declination
                }
            }
        } catch (_: Exception) {}
    }

    val textMeasurer = rememberTextMeasurer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.bussola), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = Color(0xFF101820)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF101820)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = Res.get(R.string.x_12, (azimuth.toInt())), // DEV-R07
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = direction,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = when (direction) {
                    "N", "NE", "NW" -> Color(0xFFFF5252)
                    "S", "SE", "SW" -> Color(0xFF40C4FF)
                    else -> Color(0xFFFFD740)
                }
            )

            // GPS coordinates
            if (latitude != null && longitude != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}",
                    color = Color(0xFF78909C),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (magneticDeclination != 0f) {
                    Text(
                        text = Res.get(R.string.declinio_x, "%.1f".format(magneticDeclination)),
                        color = Color(0xFF546E7A),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Res.get(R.string.gps_indisponivel),
                    color = Color(0xFF546E7A),
                    fontSize = 12.sp
                )
            }

            if (isSimulated) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Res.get(R.string.bussola_dados_simulados),
                    color = Color(0xFFFFA000),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Canvas(modifier = Modifier.size(300.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2 - 12f

                drawCompassBackground(center, radius)

                rotate(degrees = -animAzimuth + magneticDeclination, pivot = center) {
                    drawCompassRose(center, radius, textMeasurer)
                    drawCompassNeedle(center, radius)
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFCCCCCC), Color(0xFF666666)),
                        center = center,
                        radius = 10f
                    ),
                    radius = 10f,
                    center = center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompassStat(label = "Azimute", value = Res.get(R.string.x_12, (azimuth.toInt())))
                CompassStat(label = Res.get(R.string.direcao), value = direction)
                CompassStat(label = "Invertido", value = Res.get(R.string.azimuth_toint_180_360, ((azimuth.toInt() + 180) % 360)))
            }
        }
    }
}

private fun DrawScope.drawCompassBackground(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A2535), Color(0xFF0D1520)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )

    drawCircle(
        color = Color(0xFF2A3A4A),
        radius = radius,
        center = center,
        style = Stroke(width = 8f)
    )

    drawCircle(
        color = Color(0xFF1E2E3E),
        radius = radius * 0.88f,
        center = center,
        style = Stroke(width = 1f)
    )

    for (deg in 0 until 360 step 5) {
        val angle = Math.toRadians(deg.toDouble())
        val isMajor = deg % 45 == 0
        val isMinor = deg % 15 == 0
        val outerR = radius - 4f
        val innerR = when {
            isMajor -> radius * 0.78f
            isMinor -> radius * 0.84f
            else -> radius * 0.89f
        }
        val ox = (center.x + outerR * Math.sin(angle)).toFloat()
        val oy = (center.y - outerR * Math.cos(angle)).toFloat()
        val ix = (center.x + innerR * Math.sin(angle)).toFloat()
        val iy = (center.y - innerR * Math.cos(angle)).toFloat()

        drawLine(
            color = if (isMajor) Color(0xFF546E7A) else Color(0xFF2A3A4A),
            start = Offset(ox, oy),
            end = Offset(ix, iy),
            strokeWidth = if (isMajor) 2.5f else 1f
        )
    }
}

private fun DrawScope.drawCompassRose(
    center: Offset,
    radius: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val cardinals = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
    val intercardinals = mapOf(45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW")

    for ((deg, label) in cardinals) {
        val angle = Math.toRadians(deg.toDouble())
        val isNorth = deg == 0
        val textR = radius * 0.66f
        val tx = (center.x + textR * Math.sin(angle)).toFloat()
        val ty = (center.y - textR * Math.cos(angle)).toFloat()

        val measured = textMeasurer.measure(
            label,
            style = TextStyle(
                color = if (isNorth) Color(0xFFFF5252) else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(measured, topLeft = Offset(tx - measured.size.width / 2f, ty - measured.size.height / 2f))
    }

    for ((deg, label) in intercardinals) {
        val angle = Math.toRadians(deg.toDouble())
        val textR = radius * 0.68f
        val tx = (center.x + textR * Math.sin(angle)).toFloat()
        val ty = (center.y - textR * Math.cos(angle)).toFloat()

        val measured = textMeasurer.measure(
            label,
            style = TextStyle(
                color = Color(0xFF78909C),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        drawText(measured, topLeft = Offset(tx - measured.size.width / 2f, ty - measured.size.height / 2f))
    }
}

private fun DrawScope.drawCompassNeedle(center: Offset, radius: Float) {
    val needleLen = radius * 0.5f
    val needleTailLen = radius * 0.28f

    val northPath = Path().apply {
        moveTo(center.x, center.y - needleLen)
        lineTo(center.x - 8f, center.y)
        lineTo(center.x, center.y - needleTailLen * 0.3f)
        lineTo(center.x + 8f, center.y)
        close()
    }
    drawPath(northPath, Color(0xFFFF5252))

    val southPath = Path().apply {
        moveTo(center.x, center.y + needleTailLen)
        lineTo(center.x - 8f, center.y)
        lineTo(center.x, center.y - needleTailLen * 0.3f)
        lineTo(center.x + 8f, center.y)
        close()
    }
    drawPath(southPath, Color(0xFF40C4FF))
}

@Composable
private fun CompassStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF546E7A), fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
    }
}
