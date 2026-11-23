package com.example.ui.screens.docs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.navigation.Screen
import java.util.UUID
import com.example.R
import com.example.Res

// ── Data Classes ─────────────────────────────────────────────────────────────

sealed class WhiteboardElement {
    abstract val id: String
    abstract val position: Offset
    abstract val color: Color
    
    data class PathElement(
        override val id: String = UUID.randomUUID().toString(),
        val points: List<Offset>,
        override val color: Color = Color.Black,
        val strokeWidth: Float = 4f
    ) : WhiteboardElement() {
        override val position: Offset get() = points.firstOrNull() ?: Offset.Zero
    }
    
    data class RectangleElement(
        override val id: String = UUID.randomUUID().toString(),
        override val position: Offset,
        val size: Size,
        override val color: Color = Color.Black,
        val strokeWidth: Float = 2f
    ) : WhiteboardElement()
    
    data class CircleElement(
        override val id: String = UUID.randomUUID().toString(),
        override val position: Offset,
        val radius: Float,
        override val color: Color = Color.Black,
        val strokeWidth: Float = 2f
    ) : WhiteboardElement()
    
    data class LineElement(
        override val id: String = UUID.randomUUID().toString(),
        override val position: Offset,
        val end: Offset,
        override val color: Color = Color.Black,
        val strokeWidth: Float = 2f
    ) : WhiteboardElement()
    
    data class TextElement(
        override val id: String = UUID.randomUUID().toString(),
        override val position: Offset,
        val text: String,
        override val color: Color = Color.Black,
        val fontSize: Float = 24f
    ) : WhiteboardElement()
}

// ── Repository ───────────────────────────────────────────────────────────────

object WhiteboardRepository {
    val elements = mutableStateOf<List<WhiteboardElement>>(emptyList())
    val selectedElementId = mutableStateOf<String?>(null)
}

enum class WhiteboardTool {
    SELECT, PAN, PEN, RECTANGLE, CIRCLE, LINE, TEXT, ERASER
}

// ── Helper Functions ─────────────────────────────────────────────────────────

fun findElementAt(pos: Offset, elements: List<WhiteboardElement>): WhiteboardElement? {
    return elements.findLast { element ->
        when (element) {
            is WhiteboardElement.PathElement -> {
                element.points.any { (it - pos).getDistance() < 20f }
            }
            is WhiteboardElement.RectangleElement -> {
                val minX = minOf(element.position.x, element.position.x + element.size.width)
                val maxX = maxOf(element.position.x, element.position.x + element.size.width)
                val minY = minOf(element.position.y, element.position.y + element.size.height)
                val maxY = maxOf(element.position.y, element.position.y + element.size.height)
                pos.x in minX..maxX && pos.y in minY..maxY
            }
            is WhiteboardElement.CircleElement -> {
                (pos - element.position).getDistance() <= element.radius + 10f
            }
            is WhiteboardElement.LineElement -> {
                val dx = element.end.x - element.position.x
                val dy = element.end.y - element.position.y
                val l2 = dx * dx + dy * dy
                if (l2 == 0f) (pos - element.position).getDistance() < 20f
                else {
                    val t = ((pos.x - element.position.x) * dx + (pos.y - element.position.y) * dy) / l2
                    val clampedT = t.coerceIn(0f, 1f)
                    val projection = element.position + (element.end - element.position) * clampedT
                    (pos - projection).getDistance() < 20f
                }
            }
            is WhiteboardElement.TextElement -> {
                (pos - element.position).getDistance() < 40f
            }
        }
    }
}

// ── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// DOC-R06
fun WhiteboardScreen(navController: NavController) {
    
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    
    var currentTool by remember { mutableStateOf(WhiteboardTool.PEN) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentStrokeWidth by remember { mutableStateOf(4f) }
    
    var isDrawing by remember { mutableStateOf(false) }
    val currentPath = remember { mutableStateListOf<Offset>() }
    var startPoint by remember { mutableStateOf(Offset.Zero) }
    var currentDragPoint by remember { mutableStateOf(Offset.Zero) }
    
    var dragStartElementPos by remember { mutableStateOf(Offset.Zero) }
    var dragStartPointerPos by remember { mutableStateOf(Offset.Zero) }
    
    var showTextInput by remember { mutableStateOf(false) }
    var textInputPosition by remember { mutableStateOf(Offset.Zero) }
    var textInputValue by remember { mutableStateOf("") }
    
    val elements by WhiteboardRepository.elements
    val selectedElementId by WhiteboardRepository.selectedElementId
    
    val undoStack = remember { mutableStateOf<List<List<WhiteboardElement>>>(emptyList()) }
    val redoStack = remember { mutableStateOf<List<List<WhiteboardElement>>>(emptyList()) }
    
    fun saveState() {
        undoStack.value = undoStack.value + listOf(elements)
        redoStack.value = emptyList()
    }
    
    fun undo() {
        if (undoStack.value.isNotEmpty()) {
            redoStack.value = redoStack.value + listOf(elements)
            WhiteboardRepository.elements.value = undoStack.value.last()
            undoStack.value = undoStack.value.dropLast(1)
        }
    }
    
    fun redo() {
        if (redoStack.value.isNotEmpty()) {
            undoStack.value = undoStack.value + listOf(elements)
            WhiteboardRepository.elements.value = redoStack.value.last()
            redoStack.value = redoStack.value.dropLast(1)
        }
    }
    
    fun clearCanvas() {
        saveState()
        WhiteboardRepository.elements.value = emptyList()
        WhiteboardRepository.selectedElementId.value = null
    }
    
    fun eraseAt(pos: Offset) {
        val target = findElementAt(pos, elements)
        if (target != null) {
            saveState()
            WhiteboardRepository.elements.value = elements.filter { it.id != target.id }
            if (selectedElementId == target.id) {
                WhiteboardRepository.selectedElementId.value = null
            }
        }
    }
    
    fun moveElement(id: String, newPos: Offset) {
        WhiteboardRepository.elements.value = WhiteboardRepository.elements.value.map { element ->
            if (element.id == id) {
                when (element) {
                    is WhiteboardElement.PathElement -> {
                        val delta = newPos - (element.points.firstOrNull() ?: Offset.Zero)
                        element.copy(points = element.points.map { it + delta })
                    }
                    is WhiteboardElement.RectangleElement -> element.copy(position = newPos)
                    is WhiteboardElement.CircleElement -> element.copy(position = newPos)
                    is WhiteboardElement.LineElement -> {
                        val delta = newPos - element.position
                        element.copy(position = newPos, end = element.end + delta)
                    }
                    is WhiteboardElement.TextElement -> element.copy(position = newPos)
                }
            } else {
                element
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whiteboard") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                },
                actions = {
                    if (selectedElementId != null) {
                        IconButton(onClick = {
                            saveState()
                            WhiteboardRepository.elements.value = elements.filter { it.id != selectedElementId }
                            WhiteboardRepository.selectedElementId.value = null
                        }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = Res.get(R.string.excluir_selecionado), tint = Color.Red)
                        }
                    }
                    IconButton(onClick = { undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfazer")
                    }
                    IconButton(onClick = { redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Refazer")
                    }
                    IconButton(onClick = { clearCanvas() }) {
                        Icon(Icons.Default.Delete, contentDescription = Res.get(R.string.limpar))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WhiteboardToolbar(
                currentTool = currentTool,
                currentColor = currentColor,
                currentStrokeWidth = currentStrokeWidth,
                onToolChange = { 
                    currentTool = it 
                    WhiteboardRepository.selectedElementId.value = null
                },
                onColorChange = { currentColor = it },
                onStrokeWidthChange = { currentStrokeWidth = it }
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color(0xFFFCFCFC))
                    .pointerInput(currentTool, currentColor, currentStrokeWidth, offset, scale, selectedElementId, elements) {
                        awaitPointerEventScope {
                            var activeId: Long? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                val downPointers = changes.filter { it.pressed }
                                
                                if (downPointers.isEmpty()) {
                                    if (isDrawing) {
                                        isDrawing = false
                                        when (currentTool) {
                                            WhiteboardTool.PEN -> {
                                                if (currentPath.size > 1) {
                                                    saveState()
                                                    WhiteboardRepository.elements.value = elements + 
                                                        WhiteboardElement.PathElement(
                                                            points = currentPath.toList(),
                                                            color = currentColor,
                                                            strokeWidth = currentStrokeWidth
                                                        )
                                                }
                                                currentPath.clear()
                                            }
                                            WhiteboardTool.RECTANGLE -> {
                                                saveState()
                                                WhiteboardRepository.elements.value = elements +
                                                    WhiteboardElement.RectangleElement(
                                                        position = startPoint,
                                                        size = Size(currentDragPoint.x - startPoint.x, currentDragPoint.y - startPoint.y),
                                                        color = currentColor,
                                                        strokeWidth = currentStrokeWidth
                                                    )
                                            }
                                            WhiteboardTool.CIRCLE -> {
                                                saveState()
                                                val radius = (currentDragPoint - startPoint).getDistance()
                                                WhiteboardRepository.elements.value = elements +
                                                    WhiteboardElement.CircleElement(
                                                        position = startPoint,
                                                        radius = radius,
                                                        color = currentColor,
                                                        strokeWidth = currentStrokeWidth
                                                    )
                                            }
                                            WhiteboardTool.LINE -> {
                                                saveState()
                                                WhiteboardRepository.elements.value = elements +
                                                    WhiteboardElement.LineElement(
                                                        position = startPoint,
                                                        end = currentDragPoint,
                                                        color = currentColor,
                                                        strokeWidth = currentStrokeWidth
                                                    )
                                            }
                                            else -> {}
                                        }
                                    }
                                    activeId = null
                                    continue
                                }
                                
                                if (downPointers.size == 1) {
                                    val pointer = downPointers.first()
                                    val screenPos = pointer.position
                                    val canvasPos = Offset(
                                        (screenPos.x - offset.x) / scale,
                                        (screenPos.y - offset.y) / scale
                                    )
                                    
                                    if (pointer.changedToDown()) {
                                        activeId = pointer.id.value
                                        when (currentTool) {
                                            WhiteboardTool.PEN -> {
                                                isDrawing = true
                                                currentPath.clear()
                                                currentPath.add(canvasPos)
                                            }
                                            WhiteboardTool.RECTANGLE,
                                            WhiteboardTool.CIRCLE,
                                            WhiteboardTool.LINE -> {
                                                isDrawing = true
                                                startPoint = canvasPos
                                                currentDragPoint = canvasPos
                                            }
                                            WhiteboardTool.SELECT -> {
                                                val clicked = findElementAt(canvasPos, elements)
                                                WhiteboardRepository.selectedElementId.value = clicked?.id
                                                if (clicked != null) {
                                                    dragStartElementPos = clicked.position
                                                    dragStartPointerPos = canvasPos
                                                }
                                            }
                                            WhiteboardTool.ERASER -> {
                                                eraseAt(canvasPos)
                                            }
                                            WhiteboardTool.TEXT -> {
                                                textInputPosition = canvasPos
                                                textInputValue = ""
                                                showTextInput = true
                                            }
                                            else -> {}
                                        }
                                    } else if (pointer.id.value == activeId) {
                                        when (currentTool) {
                                            WhiteboardTool.PAN -> {
                                                val prevPos = pointer.previousPosition
                                                val delta = screenPos - prevPos
                                                offset += delta
                                            }
                                            WhiteboardTool.PEN -> {
                                                if (isDrawing) {
                                                    currentPath.add(canvasPos)
                                                }
                                            }
                                            WhiteboardTool.RECTANGLE,
                                            WhiteboardTool.CIRCLE,
                                            WhiteboardTool.LINE -> {
                                                if (isDrawing) {
                                                    currentDragPoint = canvasPos
                                                }
                                            }
                                            WhiteboardTool.SELECT -> {
                                                val selectedId = selectedElementId
                                                if (selectedId != null && dragStartPointerPos != Offset.Zero) {
                                                    val delta = canvasPos - dragStartPointerPos
                                                    moveElement(selectedId, dragStartElementPos + delta)
                                                }
                                            }
                                            WhiteboardTool.ERASER -> {
                                                eraseAt(canvasPos)
                                            }
                                            else -> {}
                                        }
                                    }
                                    pointer.consume()
                                } else if (downPointers.size >= 2) {
                                    isDrawing = false
                                    currentPath.clear()
                                    
                                    val p1 = downPointers[0]
                                    val p2 = downPointers[1]
                                    
                                    val p1Pos = p1.position
                                    val p2Pos = p2.position
                                    val p1Prev = p1.previousPosition
                                    val p2Prev = p2.previousPosition
                                    
                                    val currentDist = (p1Pos - p2Pos).getDistance()
                                    val prevDist = (p1Prev - p2Prev).getDistance()
                                    
                                    val currentCentroid = (p1Pos + p2Pos) / 2f
                                    val prevCentroid = (p1Prev + p2Prev) / 2f
                                    
                                    offset += currentCentroid - prevCentroid
                                    
                                    if (prevDist > 0f) {
                                        val zoomFactor = currentDist / prevDist
                                        val newScale = (scale * zoomFactor).coerceIn(0.1f, 10f)
                                        offset = currentCentroid - (currentCentroid - offset) * (newScale / scale)
                                        scale = newScale
                                    }
                                    
                                    p1.consume()
                                    p2.consume()
                                }
                            }
                        }
                    }
            ) {
                WhiteboardCanvas(
                    elements = elements,
                    currentPath = currentPath,
                    isDrawing = isDrawing,
                    currentTool = currentTool,
                    startPoint = startPoint,
                    currentDragPoint = currentDragPoint,
                    currentColor = currentColor,
                    currentStrokeWidth = currentStrokeWidth,
                    offset = offset,
                    scale = scale,
                    selectedElementId = selectedElementId
                )
            }
        }
    }
    
    if (showTextInput) {
        AlertDialog(
            onDismissRequest = { showTextInput = false },
            title = { Text(Res.get(R.string.adicionar_texto)) },
            text = {
                OutlinedTextField(
                    value = textInputValue,
                    onValueChange = { textInputValue = it },
                    label = { Text("Texto") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textInputValue.isNotBlank()) {
                            saveState()
                            WhiteboardRepository.elements.value = elements +
                                WhiteboardElement.TextElement(
                                    position = textInputPosition,
                                    text = textInputValue,
                                    color = currentColor,
                                    fontSize = currentStrokeWidth * 6f
                                )
                        }
                        showTextInput = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInput = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}

// ── Canvas Component ─────────────────────────────────────────────────────────

@Composable
fun WhiteboardCanvas(
    elements: List<WhiteboardElement>,
    currentPath: List<Offset>,
    isDrawing: Boolean,
    currentTool: WhiteboardTool,
    startPoint: Offset,
    currentDragPoint: Offset,
    currentColor: Color,
    currentStrokeWidth: Float,
    offset: Offset,
    scale: Float,
    selectedElementId: String?
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        
        // ── Infinite dotted grid ──
        val gridStep = 40f
        val startX = -offset.x / scale
        val endX = (size.width - offset.x) / scale
        val startY = -offset.y / scale
        val endY = (size.height - offset.y) / scale
        
        val firstX = startX - (startX % gridStep) - gridStep
        val firstY = startY - (startY % gridStep) - gridStep
        
        var gx = firstX
        while (startX < endX && gx < endX + gridStep) {
            var gy = firstY
            while (gy < endY + gridStep) {
                drawCircle(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    radius = 1.5f * scale,
                    center = Offset(gx * scale + offset.x, gy * scale + offset.y)
                )
                gy += gridStep
            }
            gx += gridStep
        }
        
        // ── Draw main elements ──
        canvas.save()
        canvas.translate(offset.x, offset.y)
        canvas.scale(scale, scale)
        
        elements.forEach { element ->
            when (element) {
                is WhiteboardElement.PathElement -> {
                    val path = Path().apply {
                        element.points.forEachIndexed { index, point ->
                            if (index == 0) moveTo(point.x, point.y)
                            else lineTo(point.x, point.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = element.color,
                        style = Stroke(width = element.strokeWidth)
                    )
                }
                is WhiteboardElement.RectangleElement -> {
                    val minX = minOf(element.position.x, element.position.x + element.size.width)
                    val minY = minOf(element.position.y, element.position.y + element.size.height)
                    val w = kotlin.math.abs(element.size.width)
                    val h = kotlin.math.abs(element.size.height)
                    drawRect(
                        color = element.color,
                        topLeft = Offset(minX, minY),
                        size = Size(w, h),
                        style = Stroke(width = element.strokeWidth)
                    )
                }
                is WhiteboardElement.CircleElement -> {
                    drawCircle(
                        color = element.color,
                        radius = element.radius,
                        center = element.position,
                        style = Stroke(width = element.strokeWidth)
                    )
                }
                is WhiteboardElement.LineElement -> {
                    drawLine(
                        color = element.color,
                        start = element.position,
                        end = element.end,
                        strokeWidth = element.strokeWidth
                    )
                }
                is WhiteboardElement.TextElement -> {
                    val paint = android.graphics.Paint().apply {
                        color = element.color.toArgb()
                        textSize = element.fontSize
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawText(element.text, element.position.x, element.position.y, paint)
                }
            }
            
            // ── Draw selection highlight ──
            if (element.id == selectedElementId) {
                when (element) {
                    is WhiteboardElement.RectangleElement -> {
                        val minX = minOf(element.position.x, element.position.x + element.size.width)
                        val minY = minOf(element.position.y, element.position.y + element.size.height)
                        val w = kotlin.math.abs(element.size.width)
                        val h = kotlin.math.abs(element.size.height)
                        drawRect(
                            color = Color(0xFF2196F3),
                            topLeft = Offset(minX - 4f, minY - 4f),
                            size = Size(w + 8f, h + 8f),
                            style = Stroke(width = 2f)
                        )
                    }
                    is WhiteboardElement.CircleElement -> {
                        drawCircle(
                            color = Color(0xFF2196F3),
                            radius = element.radius + 4f,
                            center = element.position,
                            style = Stroke(width = 2f)
                        )
                    }
                    is WhiteboardElement.LineElement -> {
                        drawLine(
                            color = Color(0xFF2196F3),
                            start = element.position,
                            end = element.end,
                            strokeWidth = element.strokeWidth + 4f
                        )
                    }
                    is WhiteboardElement.PathElement -> {
                        // Highlight first and last point
                        element.points.firstOrNull()?.let {
                            drawCircle(color = Color(0xFF2196F3), radius = 6f, center = it)
                        }
                        element.points.lastOrNull()?.let {
                            drawCircle(color = Color(0xFF2196F3), radius = 6f, center = it)
                        }
                    }
                    is WhiteboardElement.TextElement -> {
                        val paint = android.graphics.Paint().apply {
                            color = Color(0xFF2196F3).toArgb()
                            textSize = element.fontSize
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2f
                            isAntiAlias = true
                        }
                        canvas.drawRect(
                            element.position.x - 4f,
                            element.position.y - element.fontSize - 4f,
                            element.position.x + (element.text.length * element.fontSize * 0.6f) + 4f,
                            element.position.y + 4f,
                            paint
                        )
                    }
                }
            }
        }
        
        // ── Draw active drawing preview ──
        if (isDrawing) {
            when (currentTool) {
                WhiteboardTool.PEN -> {
                    if (currentPath.isNotEmpty()) {
                        val path = Path().apply {
                            currentPath.forEachIndexed { index, point ->
                                if (index == 0) moveTo(point.x, point.y)
                                else lineTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = currentColor,
                            style = Stroke(width = currentStrokeWidth)
                        )
                    }
                }
                WhiteboardTool.RECTANGLE -> {
                    val minX = minOf(startPoint.x, currentDragPoint.x)
                    val minY = minOf(startPoint.y, currentDragPoint.y)
                    val w = kotlin.math.abs(currentDragPoint.x - startPoint.x)
                    val h = kotlin.math.abs(currentDragPoint.y - startPoint.y)
                    drawRect(
                        color = currentColor,
                        topLeft = Offset(minX, minY),
                        size = Size(w, h),
                        style = Stroke(width = currentStrokeWidth)
                    )
                }
                WhiteboardTool.CIRCLE -> {
                    val radius = (currentDragPoint - startPoint).getDistance()
                    drawCircle(
                        color = currentColor,
                        radius = radius,
                        center = startPoint,
                        style = Stroke(width = currentStrokeWidth)
                    )
                }
                WhiteboardTool.LINE -> {
                    drawLine(
                        color = currentColor,
                        start = startPoint,
                        end = currentDragPoint,
                        strokeWidth = currentStrokeWidth
                    )
                }
                else -> {}
            }
        }
        
        canvas.restore()
    }
}

// ── Toolbar Component ───────────────────────────────────────────────────────

@Composable
fun WhiteboardToolbar(
    currentTool: WhiteboardTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    onToolChange: (WhiteboardTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit
) {
    val colors = listOf(
        Color.Black, Color.Red, Color.Blue, Color.Green, 
        Color.Yellow, Color.Magenta, Color.Cyan, Color.White
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Triple(WhiteboardTool.SELECT, Icons.Default.TouchApp, "Selecionar"),
                    Triple(WhiteboardTool.PAN, Icons.Default.OpenWith, "Mover"),
                    Triple(WhiteboardTool.PEN, Icons.Default.Edit, "Pincel"),
                    Triple(WhiteboardTool.RECTANGLE, Icons.Default.CropSquare, Res.get(R.string.retangulo)),
                    Triple(WhiteboardTool.CIRCLE, Icons.Default.Circle, Res.get(R.string.circulo)),
                    Triple(WhiteboardTool.LINE, Icons.AutoMirrored.Filled.ShowChart, "Linha"),
                    Triple(WhiteboardTool.TEXT, Icons.Default.TextFields, "Texto"),
                    Triple(WhiteboardTool.ERASER, Icons.Default.CleaningServices, "Borracha")
                ).forEach { (tool, icon, desc) ->
                    IconButton(
                        onClick = { onToolChange(tool) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (currentTool == tool) MaterialTheme.colorScheme.primaryContainer 
                                else Color.Transparent, 
                                CircleShape
                            )
                    ) {
                        Icon(icon, contentDescription = desc, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(color, CircleShape)
                            .clickable { onColorChange(color) }
                            .padding(2.dp)
                            .background(
                                if (currentColor == color) Color.White.copy(alpha = 0.5f) 
                                else Color.Transparent, 
                                CircleShape
                            )
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.LineWeight, contentDescription = "Espessura", modifier = Modifier.size(16.dp))
                Slider(
                    value = currentStrokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = 1f..20f,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}
