package com.example.ui.screens.network

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.AppDatabase
import com.example.data.HistoryLog
import com.example.data.HistoryRepository
import com.example.ui.components.*
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.utils.net.SpeedTestUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).historyDao() }
    val repository = remember { HistoryRepository(dao) }

    // Test states
    var isRunning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var testPhase by remember { mutableStateOf("PRONTO") } // "PRONTO", "PING", "DOWNLOAD", "UPLOAD", "CONCLUIDO"
    var statusText by remember { mutableStateOf(Res.get(R.string.iniciar_teste)) }
    
    var pingResult by remember { mutableStateOf(0f) }
    var downloadResult by remember { mutableStateOf(0f) }
    var uploadResult by remember { mutableStateOf(0f) }
    var currentGaugeValue by remember { mutableStateOf(0f) }
    
    // Technical network info
    var testDate by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf(Res.get(R.string.sao_paulo_sp)) }
    var providerName by remember { mutableStateOf(Res.get(R.string.vivo_s_a)) }
    var connectionType by remember { mutableStateOf(Res.get(R.string.wifi_5g)) }

    // History data
    val historyLogs by repository.allLogs.collectAsState(initial = emptyList())
    val speedHistory = remember(historyLogs) {
        historyLogs.filter { it.toolName == Res.get(R.string.speed_test) }
    }

    // Gauge needle animation
    val animatedSpeed by animateFloatAsState(
        targetValue = currentGaugeValue,
        animationSpec = tween(durationMillis = 300),
        label = "gaugeNeedle"
    )

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.speed_test),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = CyanNeon)
                    }
                },
                actions = {
                    IconButton(onClick = { navController?.navigate(Screen.History.route) }) {
                        Icon(Icons.Default.History, contentDescription = Res.get(R.string.historico), tint = CyanNeon)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ambient Decorative Glow
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Speedometer Gauge ────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gauge Background and Active arcs
                    Canvas(modifier = Modifier.size(210.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        
                        // Background circle
                        drawArc(
                            color = Color(0xFF1E2D4A).copy(alpha = 0.5f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Gradiente Cyan to Green
                        val maxExpected = 150f
                        val sweepAngle = (animatedSpeed / maxExpected).coerceIn(0f, 1f) * 270f
                        if (sweepAngle > 0f) {
                            drawArc(
                                brush = Brush.linearGradient(
                                    colors = listOf(CyanNeon, SuccessGreen)
                                ),
                                startAngle = 135f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Content Inside Gauge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = when (testPhase) {
                                "PING" -> Res.get(R.string.ping_latency)
                                "DOWNLOAD" -> "Download"
                                "UPLOAD" -> "Upload"
                                "CONCLUIDO" -> Res.get(R.string.teste_concluido)
                                else -> "Download"
                            }.uppercase(),
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = String.format(Locale.US, "%.1f", animatedSpeed),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        Text(
                            text = "Mbps",
                            color = CyanNeon.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = Res.get(R.string.servidor_x, serverName),
                            color = OnSurfaceMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Metrics Card Row (Ping, Download, Upload) ────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // PING CARD
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .border(
                                1.dp,
                                if (testPhase == "PING") CyanNeon else Color(0xFF1E2D4A),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PING", color = OnSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (pingResult > 0) Res.get(R.string.x_ms_2, (pingResult.toInt())) else "—",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // DOWNLOAD CARD
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .border(
                                1.dp,
                                if (testPhase == "DOWNLOAD") CyanNeon else Color(0xFF1E2D4A),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp)) // arrow_downward equivalent
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DOWNLOAD", color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (downloadResult > 0) String.format(Locale.US, "%.1f M", downloadResult) else "—",
                                color = CyanNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // UPLOAD CARD
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .border(
                                1.dp,
                                if (testPhase == "UPLOAD") CyanNeon else Color(0xFF1E2D4A),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp)) // arrow_upward equivalent
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("UPLOAD", color = OnSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uploadResult > 0) String.format(Locale.US, "%.1f M", uploadResult) else "—",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // ── Main Action Button ───────────────────────────────────
            item {
                if (!isRunning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                isRunning = true
                                testPhase = "PING"
                                statusText = Res.get(R.string.conectando_2)
                                pingResult = 0f
                                downloadResult = 0f
                                uploadResult = 0f
                                currentGaugeValue = 0f
                                testDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                                coroutineScope.launch {
                                    val result = SpeedTestUtils.runFullSpeedTest { progress, logMsg ->
                                        statusText = logMsg
                                        if (logMsg.startsWith("Download")) {
                                            testPhase = "DOWNLOAD"
                                            val speedVal = logMsg.substringAfter(Res.get(R.string.download)).substringBefore("Mbps").trim().toFloatOrNull() ?: 0f
                                            currentGaugeValue = speedVal
                                        } else if (logMsg.startsWith("Upload")) {
                                            testPhase = "UPLOAD"
                                            val speedVal = logMsg.substringAfter(Res.get(R.string.upload)).substringBefore("Mbps").trim().toFloatOrNull() ?: 0f
                                            currentGaugeValue = speedVal
                                        }
                                    }

                                    pingResult = result.pingMs
                                    downloadResult = result.downloadMbps
                                    uploadResult = result.uploadMbps
                                    currentGaugeValue = result.downloadMbps

                                    // 4. Concluding
                                    testPhase = "CONCLUIDO"
                                    statusText = "Finalizado"
                                    hasScanned = true
                                    isRunning = false

                                    // Save to SQLite History
                                    repository.insert(
                                        HistoryLog(
                                            toolName = Res.get(R.string.speed_test),
                                            resultSummary = "Down: ${String.format(Locale.US, "%.1f", downloadResult)} Mbps | Up: ${String.format(Locale.US, "%.1f", uploadResult)} Mbps | Ping: ${pingResult.toInt()} ms",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(CyanNeon, Color(0xFF0099FF))
                                    )
                                )
                        ) {
                            Text(
                                Res.get(R.string.iniciar_teste_2),
                                color = DeepDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    // Running state text banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CyanNeon,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText.uppercase(),
                            color = CyanNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }
                }
            }

            // ── Results Details Card ─────────────────────────────────
            if (hasScanned || downloadResult > 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        // Decorative accent line on the left side
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(CyanNeon, ElectricViolet)
                                    )
                                )
                        )
                        
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SERVIDOR", color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(serverName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Res.get(R.string.provedor_isp), color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(providerName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Res.get(R.string.data_hora), color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(testDate, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Res.get(R.string.conexao), color = OnSurfaceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp)) // wifi equivalent
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(connectionType, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    val shareText = "Speed Test - Download: ${String.format(Locale.US, "%.1f", downloadResult)} Mbps | Upload: ${String.format(Locale.US, "%.1f", uploadResult)} Mbps | Ping: ${pingResult.toInt()} ms | Provedor: $providerName"
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_resultado))
                                    context.startActivity(shareIntent)
                                },
                                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Res.get(R.string.compartilhar_resultado_2), color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Recent History Section ───────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.dp)
                            .background(CyanNeon)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Res.get(R.string.historico_recente),
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            if (speedHistory.isEmpty()) {
                item {
                    Text(
                        text = Res.get(R.string.nenhum_teste_de_velocidade_recente_encon),
                        color = OnSurfaceMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
            } else {
                items(speedHistory.take(5)) { log ->
                    // Parse values
                    val dateStr = remember(log.timestamp) {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                    }
                    val values = remember(log.resultSummary) {
                        // Summary format: "Down: 124.5 Mbps | Up: 45.2 Mbps | Ping: 12 ms"
                        val parts = log.resultSummary.split("|")
                        val down = parts.getOrNull(0)?.substringAfter(Res.get(R.string.down))?.trim() ?: "—"
                        val up = parts.getOrNull(1)?.substringAfter(Res.get(R.string.up))?.trim() ?: "—"
                        val ping = parts.getOrNull(2)?.substringAfter(Res.get(R.string.ping))?.trim() ?: "—"
                        Triple(down, up, ping)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A2035).copy(alpha = 0.5f))
                            .border(1.dp, Color(0xFF1E2D4A).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(dateStr, color = OnSurfaceMuted, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(values.first, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(values.second, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Ping", color = OnSurfaceMuted, fontSize = 11.sp)
                                Text(values.third, color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
