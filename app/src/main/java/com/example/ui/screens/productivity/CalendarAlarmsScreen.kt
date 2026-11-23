package com.example.ui.screens.productivity

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.SmartAlarm
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.R
import com.example.Res

// Dynamic audio synthesizer for premium alarm sounds without requiring files
class SynthesizedAudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var playingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun playSound(soundName: String, initialVolume: Float, onVolumeUpdate: (Float) -> Unit, isGradual: Boolean) {
        stop()
        playingJob = scope.launch {
            val sampleRate = 44100
            val numSamples = sampleRate * 2 // 2-second buffer loop
            val buffer = ShortArray(numSamples)
            
            // Generate distinct audio frequencies depending on sound profile
            val freq1: Double
            val freq2: Double
            when (soundName) {
                Res.get(R.string.ocean_calm) -> { freq1 = 220.0; freq2 = 221.0 } // Calming low binaural beats
                Res.get(R.string.morning_birds) -> { freq1 = 880.0; freq2 = 1760.0 } // Bird-like high chirps
                Res.get(R.string.forest_rain) -> { freq1 = 150.0; freq2 = 4000.0 } // White-noise crackles
                else -> { freq1 = 440.0; freq2 = 880.0 } // Standard Loud alert
            }

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                var amplitude = 0.0
                
                when (soundName) {
                    Res.get(R.string.ocean_calm) -> {
                        // Slow modulated soft waves
                        val modulation = Math.sin(2.0 * Math.PI * 0.15 * t) * 0.5 + 0.5
                        amplitude = (Math.sin(2.0 * Math.PI * freq1 * t) + Math.sin(2.0 * Math.PI * freq2 * t)) * 0.4 * modulation
                    }
                    Res.get(R.string.morning_birds) -> {
                        // Rapid high-frequency chirping packets
                        val birdEnv = if ((t * 4).toInt() % 2 == 0) Math.sin(2.0 * Math.PI * 8.0 * t).coerceAtLeast(0.0) else 0.0
                        amplitude = Math.sin(2.0 * Math.PI * freq1 * t + (Math.sin(2.0 * Math.PI * 15.0 * t) * 100.0)) * 0.3 * birdEnv
                    }
                    Res.get(R.string.forest_rain) -> {
                        // Crackly soft noise mimicking raindrops on leaves
                        val rand = Math.random() * 2.0 - 1.0
                        amplitude = (Math.sin(2.0 * Math.PI * freq1 * t) + rand) * 0.15
                    }
                    else -> {
                        // Pure double-beep alarm pattern
                        val beepEnv = if ((t * 3).toInt() % 2 == 0) 1.0 else 0.0
                        amplitude = (Math.sin(2.0 * Math.PI * freq1 * t) + Math.sin(2.0 * Math.PI * freq2 * t)) * 0.4 * beepEnv
                    }
                }
                
                buffer[i] = (amplitude * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack?.let { track ->
                    track.write(buffer, 0, buffer.size)
                    track.setLoopPoints(0, buffer.size, -1)
                    track.play()
                }

                // Smooth gradual volume ramping from low up to 100% volume
                var currentVol = initialVolume
                val targetVol = 1.0f
                val rampStrength = if (isGradual) 0.05f else 1.0f
                
                while (isActive) {
                    delay(1500)
                    if (currentVol < targetVol) {
                        currentVol = (currentVol + rampStrength).coerceAtMost(targetVol)
                        audioTrack?.setVolume(currentVol)
                        withContext(Dispatchers.Main) {
                            onVolumeUpdate(currentVol)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        playingJob?.cancel()
        playingJob = null
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}

// In-App Event backup modeling 
data class LocalCalendarEvent(
    val id: Long = 0,
    val title: String,
    val description: String,
    val dateString: String, // "yyyy-MM-dd"
    val timeString: String, // "HH:MM"
    val calendarName: String = Res.get(R.string.calendario_local)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAlarmsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val alarmDao = remember { database.smartAlarmDao() }

    // State collections
    val alarms by alarmDao.getAllAlarms().collectAsState(initial = emptyList())
    var activeTab by remember { mutableStateOf(0) } // 0 -> Calendar, 1 -> Alarms, 2 -> Stopwatch & Timer

    // Alarm synthesis manager
    val audioSynth = remember { SynthesizedAudioPlayer() }

    // Alarm Trigger modal visual overlay simulation (highly interactive!)
    var ringingAlarm by remember { mutableStateOf<SmartAlarm?>(null) }
    var ringingSnoozeCount by remember { mutableStateOf(0) }
    var ringingCurrentVolume by remember { mutableStateOf(0.1f) }

    // Seed default smart alarms on startup
    LaunchedEffect(alarms) {
        if (alarms.isEmpty()) {
            coroutineScope.launch {
                alarmDao.insertAlarm(SmartAlarm(hour = 7, minute = 0, label = Res.get(R.string.despertar_disposicao), soundName = Res.get(R.string.ocean_calm), isGradualVolume = true))
                alarmDao.insertAlarm(SmartAlarm(hour = 8, minute = 30, label = Res.get(R.string.trabalho_inicio), soundName = Res.get(R.string.morning_birds), isGradualVolume = true))
            }
        }
    }

    // Terminate audio when exiting screen
    DisposableEffect(Unit) {
        onDispose {
            audioSynth.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.calendario_alarme_e_hora)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.CalendarMonth, Res.get(R.string.calendario)) },
                    label = { Text(Res.get(R.string.calendario)) }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.Alarm, "Despertador") },
                    label = { Text("Alarmes") }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Timer, Res.get(R.string.cronometro_temporizador)) },
                    label = { Text(Res.get(R.string.timer_relogio)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> CalendarSectionView()
                1 -> AlarmSectionView(
                    alarms = alarms,
                    onToggleEnabled = { alarm ->
                        coroutineScope.launch {
                            alarmDao.updateAlarm(alarm.copy(isEnabled = !alarm.isEnabled))
                        }
                    },
                    onDeleteAlarm = { id ->
                        coroutineScope.launch {
                            alarmDao.deleteAlarm(id)
                        }
                    },
                    onAddAlarm = { hour, minute, label, sound, gradual, snoozeMin, maxSnooze ->
                        coroutineScope.launch {
                            alarmDao.insertAlarm(
                                SmartAlarm(
                                    hour = hour,
                                    minute = minute,
                                    label = label,
                                    soundName = sound,
                                    isGradualVolume = gradual,
                                    snoozeMinutes = snoozeMin,
                                    maxSnoozeCount = maxSnooze
                                )
                            )
                        }
                    },
                    onTriggerAlarmPreset = { alarm ->
                        // Simulate an active smart alarm sounding instantly
                        ringingAlarm = alarm
                        ringingSnoozeCount = 0
                        ringingCurrentVolume = if (alarm.isGradualVolume) 0.1f else 1.0f
                        audioSynth.playSound(
                            soundName = alarm.soundName,
                            initialVolume = ringingCurrentVolume,
                            onVolumeUpdate = { ringingCurrentVolume = it },
                            isGradual = alarm.isGradualVolume
                        )
                        // Trigger soft vibration pulse
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        vibrator?.vibrate(400)
                    }
                )
                2 -> StopWatchTimerView()
            }

            // High priority Ringing Overlay overlay when alarm is simulated/triggered!
            ringingAlarm?.let { alarm ->
                AlertDialog(
                    onDismissRequest = { /* Force explicit interact */ },
                    icon = { Icon(Icons.Default.AlarmOn, contentDescription = Res.get(R.string.alarm_ringing), modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.error) },
                    title = {
                        Text(
                            text = Res.get(R.string.tocando_x, (alarm.label)),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                Res.get(R.string.perfil_de_som_x, (alarm.soundName)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Visual cue indicating the gradual waking volume rise
                            LinearProgressIndicator(
                                progress = ringingCurrentVolume,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            Text(
                                text = Res.get(R.string.intensidade_gradual_ringingcurrentvolume, ((ringingCurrentVolume * 100).toInt())),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                Res.get(R.string.sonecas_usadas_x_x, ringingSnoozeCount, (alarm.maxSnoozeCount)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ringingSnoozeCount >= alarm.maxSnoozeCount) Color.Red else Color.Black
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                audioSynth.stop()
                                ringingAlarm = null
                                Toast.makeText(context, Res.get(R.string.alarme_desligado_bom_dia), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("DESLIGAR")
                        }
                    },
                    dismissButton = {
                        if (ringingSnoozeCount < alarm.maxSnoozeCount) {
                            OutlinedButton(
                                onClick = {
                                    ringingSnoozeCount += 1
                                    audioSynth.stop()
                                    // Simulated delayed snooze trigger
                                    Toast.makeText(context, Res.get(R.string.soneca_programada_por_mais_x_minutos_sim, (alarm.snoozeMinutes)), Toast.LENGTH_LONG).show()
                                    ringingAlarm = null
                                }
                            ) {
                                Text(Res.get(R.string.soneca_xmin, (alarm.snoozeMinutes)))
                            }
                        } else {
                            Text(
                                Res.get(R.string.limite_de_sonecas_excedido),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Red,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 1: CALENDAR VIEW
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarSectionView() {
    val context = LocalContext.current

    var localCalendarEvents by remember { mutableStateOf<List<LocalCalendarEvent>>(emptyList()) }
    var calendarGranted by remember { mutableStateOf(false) }

    var activeOpenedMonth by remember { mutableStateOf<Calendar?>(null) }
    var activeOpenedDayString by remember { mutableStateOf<String?>(null) }
    var showEventEditorDialog by remember { mutableStateOf(false) }
    var newEventTitle by remember { mutableStateOf("") }
    var newEventDescription by remember { mutableStateOf("") }
    var newEventTimeHM by remember { mutableStateOf("09:00") }

    val nowCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH)) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readPerm = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writePerm = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        calendarGranted = readPerm && writePerm
        if (!calendarGranted) {
            Toast.makeText(context, Res.get(R.string.aviso_sem_permissao_de_agenda_rodando_em), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val readChecked = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeChecked = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readChecked && writeChecked) {
            calendarGranted = true
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    val sharedPrefs = remember { context.getSharedPreferences("calendar_backup_prefs", Context.MODE_PRIVATE) }

    fun loadOfflineAndSystemEvents() {
        val list = mutableListOf<LocalCalendarEvent>()
        val keys = sharedPrefs.all
        for ((key, value) in keys) {
            if (key.startsWith("evt_")) {
                val parts = (value as? String)?.split("||")
                if (parts != null && parts.size >= 4) {
                    list.add(
                        LocalCalendarEvent(
                            title = parts[0],
                            description = parts[1],
                            dateString = parts[2],
                            timeString = parts[3],
                            calendarName = parts.getOrNull(4) ?: Res.get(R.string.calendario_offline)
                        )
                    )
                }
            }
        }

        if (calendarGranted) {
            try {
                val uri = CalendarContract.Events.CONTENT_URI // PROD-R06
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART
                )
                val now = System.currentTimeMillis()
                val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
                val selectionArgs = arrayOf(
                    (now - 30 * 24 * 3600 * 1000L).toString(),
                    (now + 365 * 24 * 3600 * 1000L).toString()
                )
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(titleIdx) ?: Res.get(R.string.sem_titulo_2)
                        val desc = cursor.getString(descIdx) ?: ""
                        val startMillis = cursor.getLong(startIdx)
                        val dfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val dfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val calendarDate = dfDate.format(Date(startMillis))
                        val calendarTime = dfTime.format(Date(startMillis))
                        list.add(LocalCalendarEvent(title = title, description = desc, dateString = calendarDate, timeString = calendarTime, calendarName = Res.get(R.string.google_calendar_sync)))
                    }
                }
            } catch (_: SecurityException) {} catch (e: Exception) { e.printStackTrace() }
        }
        localCalendarEvents = list
    }

    LaunchedEffect(calendarGranted) { loadOfflineAndSystemEvents() }

    val monthsShort = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    val monthListState = rememberLazyListState()

    // Auto-scroll to selected month on first composition
    LaunchedEffect(selectedMonth) {
        monthListState.animateScrollToItem((selectedMonth - 1).coerceAtLeast(0))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Icon(Icons.Default.KeyboardArrowLeft, Res.get(R.string.ano_anterior))
                    }
                    Text(Res.get(R.string.x_8, selectedYear), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { selectedYear++ }) {
                        Icon(Icons.Default.KeyboardArrowRight, Res.get(R.string.proximo_ano))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    state = monthListState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    userScrollEnabled = true
                ) {
                    items(monthsShort.size) { idx ->
                        val name = monthsShort[idx]
                        val isSelected = selectedMonth == idx
                        val isCurrent = selectedYear == nowCal.get(Calendar.YEAR) && idx == nowCal.get(Calendar.MONTH)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMonth = idx },
                            label = { Text(name, fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (isCurrent && !isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderWidth = 0.dp
                            )
                        )
                    }
                }
            }
        }
        } // end item (header card)

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var accumulated = 0f
                        var pointerCount = 1
                        do {
                            val event = awaitPointerEvent()
                            pointerCount = event.changes.count { it.pressed }
                            val change = event.changes.firstOrNull() ?: break
                            val delta = change.position.x - change.previousPosition.x
                            if (delta != 0f) change.consume()
                            accumulated += delta
                        } while (event.changes.any { it.pressed })
                        val step = if (pointerCount >= 2) 2 else 1
                        if (kotlin.math.abs(accumulated) > 80f) {
                            if (accumulated > 0f) selectedMonth -= step else selectedMonth += step
                        }
                    }
                }
        ) {
            AnimatedContent(
                targetState = Pair(selectedYear, selectedMonth),
                transitionSpec = {
                    val dir = if (targetState.second > initialState.second || targetState.first > initialState.first) 1 else -1
                    slideInHorizontally(animationSpec = tween(250)) { width -> dir * width } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { width -> -dir * width }
                },
                label = "calendar"
            ) { (year, month) ->
                MonthCalendarGrid(
                    year = year,
                    month = month,
                    events = localCalendarEvents,
                    onDayClick = { dateStr ->
                        activeOpenedDayString = dateStr
                        showEventEditorDialog = true
                    },
                    onOpenMonth = {
                        val cal = Calendar.getInstance()
                        cal.set(year, month, 1)
                        activeOpenedMonth = cal
                    }
                )
            }
        }
        } // end item (calendar box)

        // ── Month events ───────────────────────────────────────────────────
        item { Spacer(modifier = Modifier.height(12.dp)) }

        val monthPrefix = String.format("%04d-%02d", selectedYear, selectedMonth + 1)
        val monthEvents = localCalendarEvents
            .filter { it.dateString.startsWith(monthPrefix) }
            .sortedBy { it.dateString + it.timeString }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.EventNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Res.get(R.string.eventos_de_x_selectedmonth_x_x, (monthsShort[selectedMonth]), selectedYear, (monthEvents.size)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (monthEvents.isEmpty()) {
            item {
                Text(
                    Res.get(R.string.nenhum_evento_neste_mes),
                    color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            items(monthEvents) { ev ->
                val p = ev.dateString.split("-")
                val shortDate = Res.get(R.string.x_2_x_1, (p[2]), (p[1]))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(shortDate, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ev.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ev.description.isNotEmpty())
                                Text(ev.description, fontSize = 11.sp, color = Color.Gray,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(ev.timeString, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    } // end LazyColumn

    if (showEventEditorDialog && activeOpenedDayString != null) {
        val selectedDay = activeOpenedDayString!!
        val parts = selectedDay.split("-")
        val friendlyDate = Res.get(R.string.x_2_x_1_x_0, (parts[2]), (parts[1]), (parts[0]))
        val daysEvents = localCalendarEvents.filter { it.dateString == selectedDay }

        AlertDialog(
            onDismissRequest = { showEventEditorDialog = false },
            title = { Text(Res.get(R.string.agenda_x, friendlyDate)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(Res.get(R.string.eventos_marcados), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (daysEvents.isEmpty()) {
                        Text(Res.get(R.string.nenhum_compromisso_agendado_para_este_di), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        daysEvents.forEach { ev ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(ev.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(ev.timeString, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(ev.description, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(Res.get(R.string.origem_x, (ev.calendarName)), fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(Res.get(R.string.adicionar_novo_evento), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(value = newEventTitle, onValueChange = { newEventTitle = it }, label = { Text(Res.get(R.string.titulo_do_evento)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(value = newEventDescription, onValueChange = { newEventDescription = it }, label = { Text(Res.get(R.string.descricao_detalhada)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(Res.get(R.string.horario_de_inicio_hh_mm), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newEventTimeHM, onValueChange = { newEventTimeHM = it }, singleLine = true, modifier = Modifier.width(80.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newEventTitle.trim().isNotEmpty()) {
                        val uniquePrefKey = "evt_${System.currentTimeMillis()}"
                        val payload = "${newEventTitle.trim()}||${newEventDescription.trim()}||$selectedDay||$newEventTimeHM||Offline App"
                        sharedPrefs.edit().putString(uniquePrefKey, payload).apply()
                        var systemWriteAttempt = false
                        if (calendarGranted) {
                            try {
                                val dfFull = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                val parsedDate = dfFull.parse(Res.get(R.string.x_x_9, selectedDay, newEventTimeHM))
                                val startMilli = parsedDate?.time ?: System.currentTimeMillis()
                                val eventValues = ContentValues().apply {
                                    put(CalendarContract.Events.DTSTART, startMilli)
                                    put(CalendarContract.Events.DTEND, startMilli + 60 * 60 * 1000L)
                                    put(CalendarContract.Events.TITLE, newEventTitle)
                                    put(CalendarContract.Events.DESCRIPTION, newEventDescription)
                                    put(CalendarContract.Events.CALENDAR_ID, 1)
                                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                                }
                                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
                                systemWriteAttempt = true
                            } catch (_: Exception) {}
                        }
                        Toast.makeText(context, if (systemWriteAttempt) Res.get(R.string.item_criado_no_calendario_e_no_app) else Res.get(R.string.item_salvo_na_agenda_local_offline), Toast.LENGTH_SHORT).show()
                        newEventTitle = ""; newEventDescription = ""; newEventTimeHM = "09:00"
                        showEventEditorDialog = false; loadOfflineAndSystemEvents()
                    } else {
                        Toast.makeText(context, Res.get(R.string.preencha_o_titulo_do_compromisso), Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Agendar") }
            },
            dismissButton = { TextButton(onClick = { showEventEditorDialog = false }) { Text(Res.get(R.string.fechar)) } }
        )
    }

    activeOpenedMonth?.let { monthCal ->
        val monthName = monthCal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("pt", "BR")) ?: ""
        val yearNum = monthCal.get(Calendar.YEAR)
        Dialog(onDismissRequest = { activeOpenedMonth = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("${monthName.replaceFirstChar { it.uppercase() }} $yearNum - Agenda Completa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    val monthPrefix = String.format("%04d-%02d", yearNum, monthCal.get(Calendar.MONTH) + 1)
                    val monthEvents = localCalendarEvents.filter { it.dateString.startsWith(monthPrefix) }
                    if (monthEvents.isEmpty()) {
                        Text(Res.get(R.string.nao_foram_localizados_compromissos_para), fontSize = 13.sp, color = Color.Gray)
                    } else {
                        monthEvents.sortedBy { it.dateString + it.timeString }.forEach { ev ->
                            val p = ev.dateString.split("-")
                            val shortDate = Res.get(R.string.x_2_x_1, (p[2]), (p[1]))
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(shortDate, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column { Text(ev.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(ev.description, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                                Text(ev.timeString, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { activeOpenedMonth = null }, modifier = Modifier.align(Alignment.End)) { Text(Res.get(R.string.fechar)) }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    year: Int,
    month: Int,
    events: List<LocalCalendarEvent>,
    onDayClick: (String) -> Unit,
    onOpenMonth: () -> Unit
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply { set(year, month, 1) }
    }
    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("pt", "BR")) ?: ""
    val startingDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val yearNum = cal.get(Calendar.YEAR)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${monthName.replaceFirstChar { it.uppercase() }} $yearNum",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onOpenMonth, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.OpenInNew, Res.get(R.string.abrir_mes), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                val abbreviations = listOf("D", "S", "T", "Q", "Q", "S", "S")
                abbreviations.forEach { d ->
                    Text(text = d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            var cellCount = 0
            for (row in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 1..7) {
                        cellCount++
                        val dayIndex = cellCount - (startingDayOfWeek - 1)
                        val isValidDayRange = dayIndex in 1..daysInMonth
                        val formattedDateStr = if (isValidDayRange) String.format("%04d-%02d-%02d", yearNum, month + 1, dayIndex) else ""
                        val hasEvent = isValidDayRange && events.any { it.dateString == formattedDateStr }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isValidDayRange) {
                                        if (hasEvent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    } else Color.Transparent
                                )
                                .clickable(enabled = isValidDayRange) { onDayClick(formattedDateStr) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isValidDayRange) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = Res.get(R.string.x_9, dayIndex), fontWeight = if (hasEvent) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 12.sp, color = if (hasEvent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (hasEvent) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 2: ALARMS VIEW
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSectionView(
    alarms: List<SmartAlarm>,
    onToggleEnabled: (SmartAlarm) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onAddAlarm: (Int, Int, String, String, Boolean, Int, Int) -> Unit,
    onTriggerAlarmPreset: (SmartAlarm) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var hourText by remember { mutableStateOf("07") }
    var minuteText by remember { mutableStateOf("00") }
    var labelText by remember { mutableStateOf(Res.get(R.string.alarme_personalizado)) }
    
    var soundSelected by remember { mutableStateOf(Res.get(R.string.ocean_calm)) }
    var gradualSelected by remember { mutableStateOf(true) }
    var snoozeMinInput by remember { mutableStateOf("5") }
    var maxSnoozeInput by remember { mutableStateOf("3") }

    val soundOptions = listOf(Res.get(R.string.default_buzzer), Res.get(R.string.ocean_calm), Res.get(R.string.morning_birds), Res.get(R.string.forest_rain))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Res.get(R.string.despertador_inteligente),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.AddAlarm, Res.get(R.string.novo_alarme), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Res.get(R.string.adicionar))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    Res.get(R.string.nenhum_alarme_salvo_clique_em_adicionar),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alarms) { alarm ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTriggerAlarmPreset(alarm) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                    Text(
                                        text = alarm.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Simulated Trigger (extremely fun!)
                                    IconButton(
                                        onClick = { onTriggerAlarmPreset(alarm) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, Res.get(R.string.testar_alarme), tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { onToggleEnabled(alarm) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(Res.get(R.string.som_x, (alarm.soundName)), fontSize = 11.sp, color = Color.Gray)
                                    Text(Res.get(R.string.rampa_gradual) + (if (alarm.isGradualVolume) Res.get(R.string.sim) else Res.get(R.string.nao)), fontSize = 11.sp, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Res.get(R.string.soneca_xm, (alarm.snoozeMinutes)), fontSize = 11.sp, color = Color.Gray)
                                    Text(Res.get(R.string.repetir_soneca_xx, (alarm.maxSnoozeCount)), fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { onDeleteAlarm(alarm.id) },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, Res.get(R.string.excluir_alarme), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(Res.get(R.string.configurar_alarme_inteligente)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Time setup elements
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.take(2) },
                            label = { Text("HH") },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.take(2) },
                            label = { Text("MM") },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text(Res.get(R.string.identificacao_nome)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(Res.get(R.string.melodia_para_despertar), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        soundOptions.forEach { sName ->
                            val active = soundSelected == sName
                            Button(
                                onClick = { soundSelected = sName },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Text(sName.replace(Res.get(R.string.default_res), ""), fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(Res.get(R.string.aumento_gradual_ativo), fontSize = 12.sp)
                        Switch(checked = gradualSelected, onCheckedChange = { gradualSelected = it })
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Res.get(R.string.tempo_soneca_min), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = snoozeMinInput,
                            onValueChange = { snoozeMinInput = it },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Res.get(R.string.limite_de_sonecas_multiplos), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = maxSnoozeInput,
                            onValueChange = { maxSnoozeInput = it },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = hourText.toIntOrNull() ?: 0
                        val m = minuteText.toIntOrNull() ?: 0
                        val zoomMin = snoozeMinInput.toIntOrNull() ?: 5
                        val zoomLimit = maxSnoozeInput.toIntOrNull() ?: 3
                        
                        onAddAlarm(
                            h.coerceIn(0, 23),
                            m.coerceIn(0, 59),
                            labelText.trim().ifEmpty { "Alarme" },
                            soundSelected,
                            gradualSelected,
                            zoomMin,
                            zoomLimit
                        )
                        showAddDialog = false
                    }
                ) {
                    Text(Res.get(R.string.salvar))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}

// -------------------------------------------------------------
// SECTION 3: STOPWATCH & TIMER VIEW
// -------------------------------------------------------------
@Composable
fun StopWatchTimerView() {
    var timerHoursInput by remember { mutableStateOf("00") }
    var timerMinutesInput by remember { mutableStateOf("05") }
    var timerSecondsInput by remember { mutableStateOf("00") }

    // Stopwatch State attributes 
    var isStopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchTime by remember { mutableStateOf(0L) } // in milliseconds
    val lapRecordList = remember { mutableStateListOf<String>() }

    // Timer countdown State attributes
    var isTimerActive by remember { mutableStateOf(false) }
    var timerTotalSecondsInput by remember { mutableStateOf(0L) }
    var timerRemainingSeconds by remember { mutableStateOf(0L) }

    // Background handler for Stopwatch and Timer
    val context = LocalContext.current
    var lastTickTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isStopwatchRunning) {
        if (isStopwatchRunning) {
            lastTickTime = System.currentTimeMillis()
            while (isStopwatchRunning) {
                val now = System.currentTimeMillis()
                val delta = now - lastTickTime
                stopwatchTime += delta
                lastTickTime = now
                delay(12) // Smooth 1-hundredth second updates!
            }
        }
    }

    LaunchedEffect(isTimerActive, timerRemainingSeconds) {
        if (isTimerActive && timerRemainingSeconds > 0) {
            delay(1000)
            if (timerRemainingSeconds > 0) {
                timerRemainingSeconds -= 1
            }
            if (timerRemainingSeconds == 0L) {
                isTimerActive = false
                // Play completion beep sounds using high-pitch Tone alert
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(1000)
                Toast.makeText(context, Res.get(R.string.o_tempo_do_temporizador_acabou), Toast.LENGTH_LONG).show()
                
                // Synthesize happy completion song
                CoroutineScope(Dispatchers.Default).launch {
                    val sampleRate = 44100
                    val numSamples = sampleRate * 1 // 1 second sound snippet
                    val buffer = ShortArray(numSamples)
                    for (i in 0 until numSamples) {
                        val t = i.toDouble() / sampleRate
                        // Arpeggio sound
                        val freq = if (t < 0.3) 523.25 else if (t < 0.6) 659.25 else 783.99 // C5 -> E5 -> G5
                        buffer[i] = (Math.sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.4).toInt().toShort()
                    }
                    try {
                        val track = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                            .setAudioFormat(AudioFormat.Builder().setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).build())
                            .setBufferSizeInBytes(buffer.size * 2)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()
                        track.write(buffer, 0, buffer.size)
                        track.play()
                        delay(1200)
                        track.release()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    // Centisecond conversion formatted String
    fun getStopwatchFormatted(): String {
        val totalHundredths = stopwatchTime / 10
        val hundredths = totalHundredths % 100
        val seconds = (totalHundredths / 100) % 60
        val minutes = (totalHundredths / 6000) % 60
        val hours = (totalHundredths / 360000)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
        } else {
            String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
        }
    }

    fun getTimerProgress(): Float {
        if (timerTotalSecondsInput == 0L) return 0f
        return timerRemainingSeconds.toFloat() / timerTotalSecondsInput.toFloat()
    }

    fun getTimerFormattedString(totalSecs: Long): String {
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d:%02d", hrs, mins, secs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 1. CRONOMETRO CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Res.get(R.string.cronometro_digital),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                // Giant stopwatch display
                Text(
                    text = getStopwatchFormatted(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Start / Stop Click
                    Button(
                        onClick = { isStopwatchRunning = !isStopwatchRunning },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStopwatchRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isStopwatchRunning) "PAUSAR" else "INICIAR")
                    }

                    // Lap split Click
                    OutlinedButton(
                        onClick = {
                            if (stopwatchTime > 0) {
                                val splitStr = Res.get(R.string.x_1, (lapRecordList.size + 1)) + getStopwatchFormatted()
                                lapRecordList.add(0, splitStr) // Add on top of history 
                            }
                        },
                        enabled = isStopwatchRunning
                    ) {
                        Text("VOLTA")
                    }

                    // Reset Click
                    OutlinedButton(
                        onClick = {
                            isStopwatchRunning = false
                            stopwatchTime = 0
                            lapRecordList.clear()
                        }
                    ) {
                        Text("ZERAR")
                    }
                }

                // Scrollable lap logs list
                if (lapRecordList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(lapRecordList) { record ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(record.substringBefore(" -"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(record.substringAfter("- "), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. TEMPORIZADOR CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Res.get(R.string.temporizador_timer),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (!isTimerActive && timerRemainingSeconds == 0L) {
                    // Set inputs mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = timerHoursInput,
                            onValueChange = { timerHoursInput = it.take(2) },
                            label = { Text("HH") },
                            modifier = Modifier.width(55.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(":", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = timerMinutesInput,
                            onValueChange = { timerMinutesInput = it.take(2) },
                            label = { Text("MM") },
                            modifier = Modifier.width(55.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(":", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = timerSecondsInput,
                            onValueChange = { timerSecondsInput = it.take(2) },
                            label = { Text("SS") },
                            modifier = Modifier.width(55.dp),
                            singleLine = true
                        )
                    }
                } else {
                    // Display Active circular countdown clock progress
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = getTimerProgress(),
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = getTimerFormattedString(timerRemainingSeconds),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                Res.get(R.string.total) + getTimerFormattedString(timerTotalSecondsInput),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (!isTimerActive && timerRemainingSeconds == 0L) {
                        Button(
                            onClick = {
                                val h = timerHoursInput.toLongOrNull() ?: 0L
                                val m = timerMinutesInput.toLongOrNull() ?: 0L
                                val s = timerSecondsInput.toLongOrNull() ?: 0L
                                val calculatedSec = (h * 3600) + (m * 60) + s
                                if (calculatedSec > 0) {
                                    timerTotalSecondsInput = calculatedSec
                                    timerRemainingSeconds = calculatedSec
                                    isTimerActive = true
                                } else {
                                    Toast.makeText(context, Res.get(R.string.insira_um_tempo_valido), Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(Res.get(R.string.iniciar_countdown))
                        }
                    } else {
                        Button(
                            onClick = { isTimerActive = !isTimerActive },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTimerActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isTimerActive) "PAUSAR" else "RETOMAR")
                        }

                        OutlinedButton(
                            onClick = {
                                isTimerActive = false
                                timerRemainingSeconds = 0
                                timerTotalSecondsInput = 0
                            }
                        ) {
                            Text(Res.get(R.string.zerar_voltar))
                        }
                    }
                }
            }
        }
    }
}
