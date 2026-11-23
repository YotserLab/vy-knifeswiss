package com.example.ui.screens.system

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.SensorUtils
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleLevelScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val sensorUtils = remember { SensorUtils(context) }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else
            @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
    }

    val gravityData by sensorUtils.getGravityData().collectAsState(initial = Triple(0f, 0f, false))

    val maxG = 9.81f
    val rawX = (gravityData.first / maxG).coerceIn(-1f, 1f)
    val rawY = (gravityData.second / maxG).coerceIn(-1f, 1f)

    val angleX = remember(gravityData) { Math.toDegrees(Math.asin(rawX.toDouble())).toFloat() }
    val angleY = remember(gravityData) { Math.toDegrees(Math.asin(rawY.toDouble())).toFloat() }

    val isLevel = remember(angleX, angleY) {
        kotlin.math.abs(angleX) < 1.5f && kotlin.math.abs(angleY) < 1.5f
    }

    // Smooth bubble animation
    val animX by animateFloatAsState(targetValue = rawX, animationSpec = spring(stiffness = 200f), label = "bubbleX")
    val animY by animateFloatAsState(targetValue = rawY, animationSpec = spring(stiffness = 200f), label = "bubbleY")

    // Vibrate when leveled
    var wasLevel by remember { mutableStateOf(false) }
    LaunchedEffect(isLevel) {
        if (isLevel && !wasLevel) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        wasLevel = isLevel
    }

    val bubbleColor = if (isLevel) Color(0xFF00E676) else Color(0xFF29B6F6)
    val ringColor = if (isLevel) Color(0xFF00C853) else Color(0xFF546E7A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.nivel_de_bolha), fontWeight = FontWeight.Bold) },
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
            // Level indicator badge
            Surface(
                color = if (isLevel) Color(0xFF00C853).copy(alpha = 0.15f) else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (gravityData.third) Res.get(R.string.dados_simulados) else if (isLevel) Res.get(R.string.nivelado) else Res.get(R.string.ajustando),
                    color = if (gravityData.third) Color(0xFFFFA000) else if (isLevel) Color(0xFF00E676) else Color(0xFF546E7A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main bubble canvas
            Canvas(modifier = Modifier.size(280.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val outerRadius = size.width / 2 - 4f
                val innerRadius = outerRadius * 0.22f
                val bubbleRadius = outerRadius * 0.14f

                drawBubbleLevelBackground(center, outerRadius, innerRadius, ringColor)

                // Bubble position
                val maxOffset = outerRadius - bubbleRadius - 4f
                val bx = center.x + animX * maxOffset
                val by = center.y + animY * maxOffset

                // Bubble shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f),
                    radius = bubbleRadius + 4f,
                    center = Offset(bx + 3f, by + 3f)
                )

                // Bubble gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bubbleColor.copy(alpha = 0.9f),
                            bubbleColor.copy(alpha = 0.6f),
                            bubbleColor.copy(alpha = 0.3f)
                        ),
                        center = Offset(bx - bubbleRadius * 0.3f, by - bubbleRadius * 0.3f),
                        radius = bubbleRadius * 1.2f
                    ),
                    radius = bubbleRadius,
                    center = Offset(bx, by)
                )

                // Bubble highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = bubbleRadius * 0.35f,
                    center = Offset(bx - bubbleRadius * 0.25f, by - bubbleRadius * 0.25f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Digital readouts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LevelReadout(label = "X", value = angleX)
                LevelReadout(label = "Y", value = angleY)
            }
        }
    }
}

private fun DrawScope.drawBubbleLevelBackground(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    ringColor: Color
) {
    // Dark background circle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A2535), Color(0xFF0D1520)),
            center = center,
            radius = outerRadius
        ),
        radius = outerRadius,
        center = center
    )

    // Outer border
    drawCircle(
        color = ringColor,
        radius = outerRadius,
        center = center,
        style = Stroke(width = 6f)
    )

    // Crosshair lines
    val lineColor = Color(0xFF2A3A4A)
    drawLine(lineColor, Offset(center.x, center.y - outerRadius + 12f), Offset(center.x, center.y + outerRadius - 12f), strokeWidth = 1.5f)
    drawLine(lineColor, Offset(center.x - outerRadius + 12f, center.y), Offset(center.x + outerRadius - 12f, center.y), strokeWidth = 1.5f)

    // Concentric rings (guidance circles)
    for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
        drawCircle(
            color = Color(0xFF243040),
            radius = outerRadius * fraction,
            center = center,
            style = Stroke(width = 1f)
        )
    }

    // Center target zone (green)
    drawCircle(
        color = ringColor.copy(alpha = 0.25f),
        radius = innerRadius,
        center = center
    )
    drawCircle(
        color = ringColor,
        radius = innerRadius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Center dot
    drawCircle(
        color = Color(0xFF546E7A),
        radius = 4f,
        center = center
    )
}

@Composable
private fun LevelReadout(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFF546E7A),
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${if (value >= 0) "+" else ""}${"%.1f".format(value)}°",
            color = if (kotlin.math.abs(value) < 1.5f) Color(0xFF00E676) else Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
