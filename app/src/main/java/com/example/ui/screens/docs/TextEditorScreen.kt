package com.example.ui.screens.docs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.Stack
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// DOC-R05
fun TextEditorScreen(encodedPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePath = remember(encodedPath) {
        if (encodedPath.isNullOrEmpty()) ""
        else runCatching { URLDecoder.decode(encodedPath, "UTF-8") }.getOrElse { encodedPath }
    }

    var showFilePicker by remember { mutableStateOf(filePath.isEmpty()) }
    var pickedFilePath by remember { mutableStateOf(filePath) }
    var isNewFile by remember { mutableStateOf(false) }

    val file = remember(pickedFilePath) { if (pickedFilePath.isNotEmpty()) File(pickedFilePath) else null }
    val extension = remember(pickedFilePath) {
        if (pickedFilePath.isEmpty()) "" else (file?.extension?.lowercase() ?: "")
    }

    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(14) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Undo/Redo stacks
    val undoStack = remember { Stack<TextFieldValue>() }
    val redoStack = remember { Stack<TextFieldValue>() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val text = inputStream?.bufferedReader()?.readText() ?: ""
                    textValue = TextFieldValue(text)
                    undoStack.clear()
                    redoStack.clear()
                    showFilePicker = false
                    isLoading = false
                    errorMsg = null
                } catch (e: Exception) {
                    errorMsg = Res.get(R.string.erro_ao_abrir_x, (e.message))
                    isLoading = false
                }
            }
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(textValue.text.toByteArray()) }
                    }
                    isNewFile = false
                    hasUnsavedChanges = false
                    Toast.makeText(context, Res.get(R.string.salvo), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, Res.get(R.string.erro_ao_salvar_x, (e.message)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(pickedFilePath) {
        if (pickedFilePath.isNotEmpty() && file != null && file.exists()) {
            runCatching {
                val content = withContext(Dispatchers.IO) { file.readText() }
                textValue = TextFieldValue(content)
                undoStack.clear()
                redoStack.clear()
            }.onFailure { e ->
                errorMsg = Res.get(R.string.erro_ao_abrir_x, (e.message))
            }
            isLoading = false
        }
    }

    val lineCount = remember(textValue.text) {
        textValue.text.count { it == '\n' } + 1
    }

    // Syntax highlighting applied via VisualTransformation

    fun pushUndo(value: TextFieldValue) {
        undoStack.push(value)
        if (undoStack.size > 100) undoStack.removeElementAt(0)
        redoStack.clear()
    }

    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text(Res.get(R.string.ir_para_linha)) },
            text = {
                OutlinedTextField(
                    value = goToLineInput,
                    onValueChange = { goToLineInput = it.filter { c -> c.isDigit() } },
                    label = { Text(Res.get(R.string.numero_da_linha_1_x, lineCount)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetLine = goToLineInput.toIntOrNull() ?: 1
                    val lines = textValue.text.split("\n")
                    val charPos = lines.take((targetLine - 1).coerceAtLeast(0)).sumOf { it.length + 1 }
                    textValue = textValue.copy(selection = androidx.compose.ui.text.TextRange(charPos.coerceAtMost(textValue.text.length)))
                    showGoToLineDialog = false
                }) { Text("Ir") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) { Text(Res.get(R.string.cancelar)) }
            }
        )
    }

    if (showFilePicker) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.EditNote, contentDescription = null,
                modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(Res.get(R.string.editor_de_texto), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(Res.get(R.string.crie_ou_edite_arquivos_de_texto_e_codigo), color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                isNewFile = true
                textValue = TextFieldValue("")
                undoStack.clear()
                redoStack.clear()
                showFilePicker = false
                isLoading = false
                hasUnsavedChanges = false
            }) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Res.get(R.string.novo_arquivo_2))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Res.get(R.string.abrir_arquivo))
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text(Res.get(R.string.voltar), color = Color.Gray) }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isNewFile) Res.get(R.string.sem_titulo) else (file?.name ?: Res.get(R.string.editor_de_texto)),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            fontSize = 16.sp
                        )
                        Text(
                            "$lineCount linhas \u2022 ${if (isNewFile) "TXT" else extension.uppercase().ifEmpty { "TXT" }}${if (hasUnsavedChanges) Res.get(R.string.u2022_nao_salvo) else ""}",
                            fontSize = 11.sp,
                            color = if (hasUnsavedChanges) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                actions = {
                    IconButton(onClick = { if (fontSize > 10) fontSize-- }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = Res.get(R.string.fonte_menor))
                    }
                    Text(Res.get(R.string.xsp, (fontSize)), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    IconButton(onClick = { if (fontSize < 24) fontSize++ }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = Res.get(R.string.fonte_maior))
                    }

                    // Undo
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack.push(textValue)
                                textValue = undoStack.pop()
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfazer",
                            tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray)
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack.push(textValue)
                                textValue = redoStack.pop()
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Refazer",
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray)
                    }

                    IconButton(onClick = { showGoToLineDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = Res.get(R.string.ir_para_linha))
                    }
                    IconButton(
                        onClick = {
                            if (isNewFile || file == null) {
                                saveAsLauncher.launch("${(textValue.text.take(20).takeWhile { it.isLetterOrDigit() || it == ' ' }).trim().ifEmpty { "novo" }.replace(" ", "_")}.txt")
                            } else {
                                isSaving = true
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { file.writeText(textValue.text) }
                                        hasUnsavedChanges = false
                                        Toast.makeText(context, Res.get(R.string.salvo), Toast.LENGTH_SHORT).show()
                                    }.onFailure { e ->
                                        Toast.makeText(context, Res.get(R.string.erro_ao_salvar_x, (e.message)), Toast.LENGTH_LONG).show()
                                    }
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && hasUnsavedChanges
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = Res.get(R.string.salvar),
                                tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else Color.Gray)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg!!, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBack) { Text(Res.get(R.string.voltar)) }
                }
            }
            return@Scaffold
        }

        val scrollState = rememberScrollState()
        val hScrollState = rememberScrollState()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        color = Color(0xFF555555),
                        lineHeight = (fontSize * 1.5).sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color(0xFF333333))

            BasicTextField(
                value = textValue,
                onValueChange = { new ->
                    pushUndo(textValue)
                    textValue = new
                    hasUnsavedChanges = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .horizontalScroll(hScrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = Color(0xFFD4D4D4),
                    lineHeight = (fontSize * 1.5).sp
                ),
                visualTransformation = { text ->
                    val annotatedString = applySyntaxHighlight(text.text, extension)
                    androidx.compose.ui.text.input.TransformedText(
                        annotatedString,
                        androidx.compose.ui.text.input.OffsetMapping.Identity
                    )
                },
                cursorBrush = SolidColor(Color(0xFF569CD6))
            )
        }
    }
}

fun applySyntaxHighlight(text: String, extension: String): AnnotatedString {
    return buildAnnotatedString {
        when (extension) {
            "kt", "java" -> applyKotlinJavaHighlight(text)
            "json" -> applyJsonHighlight(text)
            "xml" -> applyXmlHighlight(text)
            "sh", "bash" -> applyShellHighlight(text)
            "md" -> applyMarkdownHighlight(text)
            else -> append(text)
        }
    }
}

private fun AnnotatedString.Builder.applyKotlinJavaHighlight(text: String) {
    val keywords = setOf("fun", "val", "var", "class", "object", "import", "package", "return",
        "if", "else", "when", "for", "while", "do", "in", "is", "as", "by", "null", "true",
        "false", "override", "data", "sealed", "enum", "interface", "abstract", "private",
        "public", "protected", "internal", "companion", "suspend", "coroutine", "lateinit",
        "const", "open", "final", "void", "new", "this", "super", "static", "throws",
        "try", "catch", "finally", "extends", "implements")
    appendWithPatterns(text, keywords, Color(0xFF569CD6), Color(0xFFCE9178), Color(0xFF6A9955), "//")
}

private fun AnnotatedString.Builder.applyJsonHighlight(text: String) {
    val stringColor = Color(0xFFCE9178)
    val boolColor = Color(0xFF569CD6)
    val lines = text.lines()
    lines.forEachIndexed { idx, line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("//") || trimmed.startsWith("#") ->
                withStyle(SpanStyle(color = Color(0xFF6A9955))) { append(line) }
            else -> {
                var processed = line
                // Highlight keys
                processed = processed.replace(Regex("\"([^\"]+)\"\\s*:")) { match ->
                    // Can't really do inline styling in a string, so we approximate
                    match.value
                }
                appendWithPatterns(processed, setOf("true", "false", "null"), boolColor, stringColor, Color(0xFF6A9955), "//")
            }
        }
        if (idx < lines.size - 1) append("\n")
    }
}

private fun AnnotatedString.Builder.applyXmlHighlight(text: String) {
    val tagColor = Color(0xFF569CD6)
    val attrColor = Color(0xFF9CDCFE)
    val commentColor = Color(0xFF6A9955)
    val lines = text.lines()
    lines.forEachIndexed { idx, line ->
        when {
            line.trimStart().startsWith("<!--") ->
                withStyle(SpanStyle(color = commentColor)) { append(line) }
            line.contains("<") -> {
                val parts = line.split(Regex("(?=<)|(?<=>)"))
                for (part in parts) {
                    when {
                        part.startsWith("</") || part.startsWith("<") ->
                            withStyle(SpanStyle(color = tagColor)) { append(part) }
                        part.contains("=\"") ->
                            withStyle(SpanStyle(color = attrColor)) { append(part) }
                        else -> append(part)
                    }
                }
            }
            else -> append(line)
        }
        if (idx < lines.size - 1) append("\n")
    }
}

private fun AnnotatedString.Builder.applyShellHighlight(text: String) {
    val keywords = setOf("echo", "export", "if", "then", "else", "fi", "for", "do", "done",
        "while", "case", "esac", "function", "return", "exit", "source", "cd", "ls", "grep",
        "sed", "awk", "cat", "mkdir", "rm", "chmod", "sudo", "apt", "pip", "git")
    val cmdColor = Color(0xFF569CD6)
    val stringColor = Color(0xFFCE9178)
    val commentColor = Color(0xFF6A9955)
    appendWithPatterns(text, keywords, cmdColor, stringColor, commentColor, "#")
}

private fun AnnotatedString.Builder.applyMarkdownHighlight(text: String) {
    val headerColor = Color(0xFF569CD6)
    val boldColor = Color(0xFFDCDCAA)
    val linkColor = Color(0xFF3794FF)
    val lines = text.lines()
    lines.forEachIndexed { idx, line ->
        when {
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                withStyle(SpanStyle(color = headerColor, fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.Normal)) {
                    append(line)
                }
            }
            line.contains("**") || line.contains("__") -> {
                val parts = line.split(Regex("(?=\\*\\*)|(?<=\\*\\*)|(?=__)|(?<=__)"))
                var bold = false
                for (part in parts) {
                    if (part == "**" || part == "__") {
                        bold = !bold
                        append(part)
                    } else {
                        if (bold) withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.Bold)) { append(part) }
                        else append(part)
                    }
                }
            }
            line.contains("[") && line.contains("](") -> {
                val before = line.substringBefore("[")
                append(before)
                val linkText = line.substringAfter("[").substringBefore("]")
                val linkUrl = line.substringAfter("](").substringBefore(")")
                withStyle(SpanStyle(color = linkColor)) { append("[$linkText]($linkUrl)") }
                append(line.substringAfter(")"))
            }
            else -> append(line)
        }
        if (idx < lines.size - 1) append("\n")
    }
}

private fun AnnotatedString.Builder.appendWithPatterns(
    text: String,
    keywords: Set<String>,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    commentPrefix: String
) {
    val tokens = text.split(Regex("(?<=\\s)|(?=\\s)"))
    for (token in tokens) {
        when {
            token.trimStart().startsWith(commentPrefix) ->
                withStyle(SpanStyle(color = commentColor)) { append(token) }
            token.trim().let { t -> t.startsWith("\"") && t.endsWith("\"") && t.length > 1 } ->
                withStyle(SpanStyle(color = stringColor)) { append(token) }
            token.trim() in keywords ->
                withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) { append(token) }
            else -> append(token)
        }
    }
}
