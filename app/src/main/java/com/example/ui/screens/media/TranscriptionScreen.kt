package com.example.ui.screens.media

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(context, Res.get(R.string.permissao_de_audio_e_necessaria_para_tra), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Text(Res.get(R.string.permissao_de_audio_necessaria), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(Res.get(R.string.este_recurso_precisa_do_microfone_para_f), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text(Res.get(R.string.conceder_permissao))
                }
            }
        }
    } else {
        TranscriptionContent(onBack = onBack)
    }
}

private data class LanguageOption(val label: String, val code: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptionContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val languages = listOf(
        LanguageOption(Res.get(R.string.portugues_brasil), "pt-BR"),
        LanguageOption(Res.get(R.string.ingles_eua), "en-US"),
        LanguageOption(Res.get(R.string.espanhol_espanha), "es-ES"),
        LanguageOption(Res.get(R.string.frances_franca), "fr-FR"),
        LanguageOption(Res.get(R.string.alemao_alemanha), "de-DE")
    )
    
    var selectedLanguage by remember { mutableStateOf(languages[0]) }
    var langDropdownExpanded by remember { mutableStateOf(false) }
    
    var mainText by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf(Res.get(R.string.pronto_para_transcrever_offline)) }
    var isListening by remember { mutableStateOf(false) }
    var rmsDb by remember { mutableStateOf(0f) }
    
    var savedFiles by remember { mutableStateOf(listOf<File>()) }
    var fileToSaveDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    
    var isListeningDesired by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    
    LaunchedEffect(mainText, partialText) {
        if (mainText.isNotEmpty() || partialText.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    // Setup directory
    val transcriptsDir = remember {
        val dir = File(context.getExternalFilesDir(null), "transcripts")
        if (!dir.exists()) dir.mkdirs()
        dir
    }
    
    fun refreshSavedFiles() {
        savedFiles = transcriptsDir.listFiles { f -> f.extension == "txt" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    LaunchedEffect(Unit) {
        refreshSavedFiles()
    }
    
    val scope = rememberCoroutineScope()
    
    // Speech Recognizer reference
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    
    val recognizerIntent = remember(selectedLanguage) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.code)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // MED-R06
        }
    }
    
    // We define recognitionListener lazily so it can refer to helper functions
    var startRecognizer: (() -> Unit)? by remember { mutableStateOf(null) }
    
    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText = Res.get(R.string.ouvindo_fale_agora)
            }
            override fun onBeginningOfSpeech() {
                statusText = Res.get(R.string.processando_voz)
            }
            override fun onRmsChanged(rmsdB: Float) {
                rmsDb = rmsdB
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusText = Res.get(R.string.processando_audio)
            }
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> Res.get(R.string.erro_de_audio)
                    SpeechRecognizer.ERROR_CLIENT -> Res.get(R.string.erro_no_cliente)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Res.get(R.string.permissoes_insuficientes)
                    SpeechRecognizer.ERROR_NETWORK -> Res.get(R.string.erro_de_rede_requerido_para_setup_inicia)
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Res.get(R.string.tempo_de_rede_esgotado)
                    SpeechRecognizer.ERROR_NO_MATCH -> Res.get(R.string.nenhuma_fala_detectada)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Res.get(R.string.servico_ocupado_reiniciando)
                    SpeechRecognizer.ERROR_SERVER -> Res.get(R.string.erro_do_servidor)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Res.get(R.string.silencio_prolongado)
                    else -> Res.get(R.string.erro_desconhecido_x, error)
                }
                
                // Commit any remaining partial text so we never lose spoken words on timeouts/errors!
                if (partialText.isNotEmpty()) {
                    val trimmed = partialText.trim()
                    if (trimmed.isNotEmpty()) {
                        mainText = if (mainText.isEmpty()) trimmed else Res.get(R.string.x_x_29, mainText, trimmed)
                    }
                    partialText = ""
                }
                
                statusText = message
                rmsDb = 0f
                isListening = false
                
                // If it was just a timeout, no-match, or busy state, and we still want to listen, restart with a delay
                if (isListeningDesired) {
                    scope.launch {
                        kotlinx.coroutines.delay(250)
                        startRecognizer?.invoke()
                    }
                } else {
                    isListeningDesired = false
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalResult = if (!matches.isNullOrEmpty()) matches[0] else partialText
                
                if (finalResult.isNotEmpty()) {
                    val trimmed = finalResult.trim()
                    if (trimmed.isNotEmpty()) {
                        mainText = if (mainText.isEmpty()) trimmed else Res.get(R.string.x_x_29, mainText, trimmed)
                    }
                }
                partialText = ""
                rmsDb = 0f
                isListening = false
                
                if (isListeningDesired) {
                    scope.launch {
                        kotlinx.coroutines.delay(150)
                        startRecognizer?.invoke()
                    }
                } else {
                    statusText = Res.get(R.string.transcrevido_com_sucesso)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    partialText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // Helper to start the recognizer, destroying old sessions to prevent freezes
    startRecognizer = {
        if (!isListening) {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            
            try {
                val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
                recognizer.setRecognitionListener(recognitionListener)
                speechRecognizer = recognizer
                
                recognizer.startListening(recognizerIntent)
                isListening = true
                statusText = Res.get(R.string.ouvindo)
            } catch (e: Exception) {
                statusText = Res.get(R.string.erro_ao_iniciar_x, (e.localizedMessage))
                isListening = false
                isListeningDesired = false
            }
        }
    }
    
    fun stopRecognizer() {
        isListeningDesired = false
        isListening = false
        rmsDb = 0f
        try {
            speechRecognizer?.stopListening()
            statusText = Res.get(R.string.ditado_parado)
        } catch (_: Exception) {}
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.transcricao_offline)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                actions = {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { langDropdownExpanded = true }) {
                            Icon(Icons.Default.Translate, contentDescription = "Idioma")
                        }
                        DropdownMenu(
                            expanded = langDropdownExpanded,
                            onDismissRequest = { langDropdownExpanded = false }
                        ) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.label) },
                                    onClick = {
                                        selectedLanguage = lang
                                        langDropdownExpanded = false
                                        Toast.makeText(context, Res.get(R.string.idioma_alterado_para_x, (lang.label)), Toast.LENGTH_SHORT).show()
                                        if (isListening) {
                                            stopRecognizer()
                                        }
                                    },
                                    trailingIcon = {
                                        if (selectedLanguage.code == lang.code) {
                                            Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status and Language Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Res.get(R.string.idioma_x, (selectedLanguage.label)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = if (isListening) Res.get(R.string.gravando_offline) else Res.get(R.string.totalmente_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isListening) MaterialTheme.colorScheme.error else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Text Editor Area Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(scrollState)
                ) {
                    OutlinedTextField(
                        value = mainText,
                        onValueChange = { mainText = it },
                        modifier = Modifier
                            .fillMaxWidth(),
                        placeholder = { Text(Res.get(R.string.a_transcricao_ou_suas_notas_digitadas_ap), color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    
                    if (partialText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = partialText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isListening) MaterialTheme.colorScheme.error else Color.Gray,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "Palavras: ${if (mainText.isEmpty()) 0 else mainText.split("\\s+".toRegex()).size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Pulsing volume or wave meter during transcription
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
                        initialValue = 1f,
                        targetValue = (1f + (rmsDb.coerceAtLeast(0f) / 10f)).coerceAtMost(2.5f),
                        animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse),
                        label = "scale"
                    )
                    Canvas(modifier = Modifier.size(24.dp).graphicsLayer(scaleX = scale, scaleY = scale)) {
                        drawCircle(color = Color.Red.copy(alpha = 0.25f))
                    }
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = Color.Red)
                    }
                } else {
                    Text(Res.get(R.string.toque_no_microfone_para_ditar), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Buttons Row (Copy, Share, Save, Clear)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear button
                IconButton(
                    onClick = {
                        mainText = ""
                        partialText = ""
                        statusText = Res.get(R.string.texto_limpo)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.limpar), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // Copy button
                IconButton(
                    onClick = {
                        if (mainText.isNotEmpty()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("transcription", mainText))
                            Toast.makeText(context, Res.get(R.string.texto_copiado_para_a_area_de_transferenc), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, Res.get(R.string.nada_para_copiar), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // MIC MAIN BUTTON
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (isListening) {
                                stopRecognizer()
                            } else {
                                isListeningDesired = true
                                startRecognizer?.invoke()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Microfone",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                // Share button
                IconButton(
                    onClick = {
                        if (mainText.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, mainText)
                            }
                            context.startActivity(Intent.createChooser(intent, Res.get(R.string.compartilhar_transcricao)))
                        } else {
                            Toast.makeText(context, Res.get(R.string.nada_para_compartilhar), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // Save Button
                IconButton(
                    onClick = {
                        if (mainText.isNotEmpty()) {
                            newFileName = "Transcricao_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                            fileToSaveDialog = true
                        } else {
                            Toast.makeText(context, Res.get(R.string.nada_para_salvar), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Save, contentDescription = Res.get(R.string.salvar), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            // Saved Transcripts Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Res.get(R.string.transcricoes_salvas_x, (savedFiles.size)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Saved Transcripts List
            if (savedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(Res.get(R.string.nenhuma_transcricao_salva_localmente), color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(savedFiles) { file ->
                        SavedFileItem(
                            file = file,
                            onLoad = {
                                mainText = file.readText()
                                Toast.makeText(context, Res.get(R.string.transcricao_carregada), Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                file.delete()
                                refreshSavedFiles()
                                Toast.makeText(context, Res.get(R.string.transcricao_removida), Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, file.readText())
                                }
                                context.startActivity(Intent.createChooser(intent, Res.get(R.string.compartilhar_transcricao)))
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Save confirmation / filename dialog
    if (fileToSaveDialog) {
        AlertDialog(
            onDismissRequest = { fileToSaveDialog = false },
            title = { Text(Res.get(R.string.salvar_transcricao)) },
            text = {
                Column {
                    Text(Res.get(R.string.digite_um_nome_para_o_arquivo))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFileName.isNotEmpty()) {
                            try {
                                val file = File(transcriptsDir, "$newFileName.txt")
                                file.writeText(mainText)
                                fileToSaveDialog = false
                                refreshSavedFiles()
                                Toast.makeText(context, Res.get(R.string.arquivo_salvo_com_sucesso), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, Res.get(R.string.erro_ao_salvar_arquivo_x, (e.localizedMessage)), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text(Res.get(R.string.salvar))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToSaveDialog = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun SavedFileItem(
    file: File,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val date = remember(file) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }
    val contentSnippet = remember(file) {
        val text = file.readText()
        if (text.length > 80) text.take(80) + "..." else text
    }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLoad() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contentSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = date,
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = Res.get(R.string.mais_opcoes), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(Res.get(R.string.carregar_no_editor)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onLoad()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartilhar") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Deletar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(Res.get(R.string.excluir_transcricao)) },
            text = { Text(Res.get(R.string.tem_certeza_que_deseja_excluir_permanent, (file.nameWithoutExtension))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Res.get(R.string.excluir))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}
