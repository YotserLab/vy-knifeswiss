package com.example.ui.screens.deviceinfo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import com.example.R
import com.example.Res

@Composable
// DEV-R05
fun HardwareTestsContent(
    testResults: Map<HardwareTestId, TestStatus>,
    onUpdateResult: (HardwareTestId, TestStatus) -> Unit
) {
    val context = LocalContext.current
    var activeTest by remember { mutableStateOf<HardwareTestId?>(null) }

    val passedCount = testResults.values.count { it == TestStatus.PASSED }
    val failedCount = testResults.values.count { it == TestStatus.FAILED }
    val totalTests = HardwareTestId.values().size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header Stats Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            Res.get(R.string.suite_de_testes_de_hardware),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            Res.get(R.string.validacao_interativa_de_sensores_e_atuad),
                            color = Color(0xFF8E99B0),
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BadgePill(Res.get(R.string.x_ok, passedCount), AccentGreen)
                        if (failedCount > 0) {
                            BadgePill(Res.get(R.string.x_falhas, failedCount), AccentPink)
                        }
                    }
                }
            }
        }

        item {
            Text(
                Res.get(R.string.testes_disponiveis_x, totalTests),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }

        items(HardwareTestId.values()) { test ->
            val status = testResults[test] ?: TestStatus.UNTESTED

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBackground)
                    .border(
                        1.dp,
                        when (status) {
                            TestStatus.PASSED -> AccentGreen.copy(alpha = 0.5f)
                            TestStatus.FAILED -> AccentPink.copy(alpha = 0.5f)
                            TestStatus.UNTESTED -> CardBorder
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { activeTest = test }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when (status) {
                                        TestStatus.PASSED -> AccentGreen.copy(alpha = 0.15f)
                                        TestStatus.FAILED -> AccentPink.copy(alpha = 0.15f)
                                        TestStatus.UNTESTED -> AccentCyan.copy(alpha = 0.15f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconForTest(test),
                                contentDescription = null,
                                tint = when (status) {
                                    TestStatus.PASSED -> AccentGreen
                                    TestStatus.FAILED -> AccentPink
                                    TestStatus.UNTESTED -> AccentCyan
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = test.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = test.description,
                                color = Color(0xFF8E99B0),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (status) {
                            TestStatus.PASSED -> {
                                Text("Aprovado", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            }
                            TestStatus.FAILED -> {
                                Text("Falhou", color = AccentPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = AccentPink, modifier = Modifier.size(16.dp))
                            }
                            TestStatus.UNTESTED -> {
                                Text("Iniciar", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    activeTest?.let { test ->
        ActiveTestModal(
            test = test,
            context = context,
            onDismiss = { activeTest = null },
            onFinish = { res ->
                onUpdateResult(test, res)
                activeTest = null
            }
        )
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun getIconForTest(test: HardwareTestId): ImageVector {
    return when (test) {
        HardwareTestId.DISPLAY_DEAD_PIXELS -> Icons.Default.Screenshot
        HardwareTestId.MULTI_TOUCH -> Icons.Default.TouchApp
        HardwareTestId.FLASHLIGHT -> Icons.Default.FlashlightOn
        HardwareTestId.SPEAKER -> Icons.Default.VolumeUp
        HardwareTestId.EARPIECE -> Icons.Default.PhoneInTalk
        HardwareTestId.MICROPHONE -> Icons.Default.Mic
        HardwareTestId.PROXIMITY_SENSOR -> Icons.Default.Sensors
        HardwareTestId.LIGHT_SENSOR -> Icons.Default.LightMode
        HardwareTestId.ACCELEROMETER -> Icons.Default.ScreenRotation
        HardwareTestId.VIBRATION -> Icons.Default.Vibration
    }
}

@Composable
private fun ActiveTestModal(
    test: HardwareTestId,
    context: Context,
    onDismiss: () -> Unit,
    onFinish: (TestStatus) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1117))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar of Modal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(test.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(test.description, color = Color(0xFF8E99B0), fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = Res.get(R.string.fechar), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBackground)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (test) {
                        HardwareTestId.DISPLAY_DEAD_PIXELS -> DisplayDeadPixelsTest()
                        HardwareTestId.MULTI_TOUCH -> MultiTouchTest()
                        HardwareTestId.FLASHLIGHT -> FlashlightTest(context)
                        HardwareTestId.SPEAKER -> SpeakerToneTest()
                        HardwareTestId.EARPIECE -> EarpieceToneTest()
                        HardwareTestId.MICROPHONE -> MicrophoneVisualTest(context)
                        HardwareTestId.PROXIMITY_SENSOR -> ProximitySensorTest(context)
                        HardwareTestId.LIGHT_SENSOR -> LightSensorTest(context)
                        HardwareTestId.ACCELEROMETER -> AccelerometerBallTest(context)
                        HardwareTestId.VIBRATION -> VibrationMotorTest(context)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Confirmation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onFinish(TestStatus.FAILED) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381523)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = AccentPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Falhou", color = AccentPink, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onFinish(TestStatus.PASSED) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3930)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aprovado", color = AccentGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// INDIVIDUAL TEST RUNNERS
// -------------------------------------------------------------

@Composable
private fun DisplayDeadPixelsTest() {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.White, Color.Black)
    var colorIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors[colorIndex])
            .clickable {
                colorIndex = (colorIndex + 1) % colors.size
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                Res.get(R.string.toque_na_tela_para_alternar_cores_x_1_x, (colorIndex + 1), (colors.size)),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MultiTouchTest() {
    var touchCount by remember { mutableStateOf(0) }
    var maxTouches by remember { mutableStateOf(0) }
    var touchPoints by remember { mutableStateOf(listOf<Offset>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        touchCount = pointers.size
                        if (touchCount > maxTouches) maxTouches = touchCount
                        touchPoints = pointers.map { it.position }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            touchPoints.forEachIndexed { idx, point ->
                drawCircle(color = AccentCyan, radius = 60f, center = point)
                drawCircle(color = Color.White, radius = 10f, center = point)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Res.get(R.string.x_toques_detectados, touchCount),
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                Res.get(R.string.maximo_simultaneo_nesta_sessao_x, maxTouches),
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                Res.get(R.string.toque_na_tela_com_multiplos_dedos_simult),
                color = Color(0xFF8E99B0),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FlashlightTest(context: Context) {
    var isFlashOn by remember { mutableStateOf(false) }
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraId = remember {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isFlashOn && cameraId != null) {
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = if (isFlashOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
            contentDescription = null,
            tint = if (isFlashOn) AccentOrange else Color(0xFF8E99B0),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (isFlashOn) Res.get(R.string.lanterna_ativada) else Res.get(R.string.lanterna_desligada),
            color = if (isFlashOn) AccentOrange else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (cameraId != null) {
                    try {
                        val next = !isFlashOn
                        cameraManager.setTorchMode(cameraId, next)
                        isFlashOn = next
                    } catch (_: Exception) {}
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isFlashOn) AccentOrange else AccentBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (isFlashOn) Res.get(R.string.desligar_lanterna) else Res.get(R.string.ligar_lanterna), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpeakerToneTest() {
    var isPlaying by remember { mutableStateOf(false) }
    var toneGen by remember { mutableStateOf<ToneGenerator?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            toneGen?.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = null,
            tint = if (isPlaying) AccentGreen else Color(0xFF8E99B0),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            Res.get(R.string.teste_de_saida_de_som_alto_falante),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            Res.get(R.string.emite_um_tom_de_frequencia_pura_de_teste),
            color = Color(0xFF8E99B0),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                if (isPlaying) {
                    toneGen?.stopTone()
                    toneGen?.release()
                    toneGen = null
                    isPlaying = false
                } else {
                    try {
                        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        toneGen?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 3000)
                        isPlaying = true
                    } catch (_: Exception) {}
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) AccentPink else AccentGreen),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (isPlaying) Res.get(R.string.parar_som) else Res.get(R.string.emitir_tom_de_teste), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EarpieceToneTest() {
    var isPlaying by remember { mutableStateOf(false) }
    var toneGen by remember { mutableStateOf<ToneGenerator?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            toneGen?.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PhoneInTalk,
            contentDescription = null,
            tint = if (isPlaying) AccentBlue else Color(0xFF8E99B0),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            Res.get(R.string.teste_de_auricular_de_chamadas),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            Res.get(R.string.aproxime_o_ouvido_da_parte_superior_do_a),
            color = Color(0xFF8E99B0),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                if (isPlaying) {
                    toneGen?.stopTone()
                    toneGen?.release()
                    toneGen = null
                    isPlaying = false
                } else {
                    try {
                        toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                        toneGen?.startTone(ToneGenerator.TONE_SUP_DIAL, 4000)
                        isPlaying = true
                    } catch (_: Exception) {}
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) AccentPink else AccentBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (isPlaying) Res.get(R.string.parar_tom) else Res.get(R.string.testar_auricular), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MicrophoneVisualTest(context: Context) {
    var isRecording by remember { mutableStateOf(false) }
    var amplitudeDb by remember { mutableStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            try {
                @Suppress("MissingPermission")
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                record.startRecording()
                val buffer = ShortArray(bufferSize)

                while (isActive && isRecording) {
                    val read = record.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        var maxAmp = 0
                        for (i in 0 until read) {
                            val sample = Math.abs(buffer[i].toInt())
                            if (sample > maxAmp) maxAmp = sample
                        }
                        amplitudeDb = ((maxAmp.toFloat() / 32767f) * 100).toInt().coerceIn(0, 100)
                    }
                    delay(100)
                }
                record.stop()
                record.release()
            } catch (_: Exception) {
                isRecording = false
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = if (isRecording) AccentPink else Color(0xFF8E99B0),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            Res.get(R.string.medidor_de_decibeis_ao_vivo),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { amplitudeDb / 100f },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = AccentPink,
            trackColor = CardBorder
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            Res.get(R.string.volume_captado_x, amplitudeDb),
            color = AccentPink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { isRecording = !isRecording },
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) AccentPink else AccentCyan),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (isRecording) Res.get(R.string.parar_captura) else Res.get(R.string.falar_no_microfone), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProximitySensorTest(context: Context) {
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val proxSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    var isNear by remember { mutableStateOf(false) }
    var distanceVal by remember { mutableStateOf(0f) }

    DisposableEffect(proxSensor) {
        if (proxSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null) {
                        val d = event.values[0]
                        distanceVal = d
                        isNear = d < (proxSensor.maximumRange.coerceAtMost(5f))
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, proxSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = if (isNear) Icons.Default.PanTool else Icons.Default.Sensors,
            contentDescription = null,
            tint = if (isNear) AccentGreen else AccentCyan,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (isNear) Res.get(R.string.objeto_proximo_near) else Res.get(R.string.sensor_livre_far),
            color = if (isNear) AccentGreen else AccentCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${Res.get(R.string.distancia)}: ${String.format(Locale.US, "%.1f", distanceVal)} cm",
            color = Color.White,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            Res.get(R.string.cubra_o_sensor_de_proximidade_no_topo_do),
            color = Color(0xFF8E99B0),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LightSensorTest(context: Context) {
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val lightSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    var luxValue by remember { mutableStateOf(0f) }

    DisposableEffect(lightSensor) {
        if (lightSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null) {
                        luxValue = event.values[0]
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LightMode,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            Res.get(R.string.intensidade_de_luz_ambiental),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${String.format(Locale.US, "%.1f", luxValue)} Lux",
            color = AccentOrange,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            Res.get(R.string.aproxime_o_aparelho_de_uma_lampada_ou_cu),
            color = Color(0xFF8E99B0),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccelerometerBallTest(context: Context) {
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var xTilt by remember { mutableStateOf(0f) }
    var yTilt by remember { mutableStateOf(0f) }

    DisposableEffect(accel) {
        if (accel != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null) {
                        xTilt = -event.values[0]
                        yTilt = event.values[1]
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(color = CardBorder, radius = 180f, center = Offset(cx, cy))
            drawCircle(color = CardBorder.copy(alpha = 0.5f), radius = 90f, center = Offset(cx, cy))

            val bx = (cx + xTilt * 25f).coerceIn(40f, size.width - 40f)
            val by = (cy + yTilt * 25f).coerceIn(40f, size.height - 40f)

            drawCircle(color = AccentCyan, radius = 28f, center = Offset(bx, by))
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            Text(
                Res.get(R.string.incline_o_telefone_para_mover_a_esfera),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "X: ${String.format(Locale.US, "%.1f", xTilt)} | Y: ${String.format(Locale.US, "%.1f", yTilt)}",
                color = AccentCyan,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VibrationMotorTest(context: Context) {
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Vibration,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            Res.get(R.string.teste_de_motor_haptico_vibracao),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            Res.get(R.string.dispara_impulsos_de_vibracao_para_valida),
            color = Color(0xFF8E99B0),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(250)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(Res.get(R.string.pulso_curto), color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val waveform = longArrayOf(0, 200, 150, 200, 150, 400)
                        vibrator.vibrate(VibrationEffect.createWaveform(waveform, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 200, 150, 200, 150, 400), -1)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(Res.get(R.string.onda_dupla), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
