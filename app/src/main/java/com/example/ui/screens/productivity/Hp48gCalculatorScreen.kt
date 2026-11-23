package com.example.ui.screens.productivity
// PROD-R11: calculator works fully offline, no network calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hp48gCalculatorScreen(onBack: () -> Unit) {
    // HP 48G RPN Stack State
    var stack by remember { mutableStateOf(listOf<Double>()) } // PROD-R08
    var inputBuffer by remember { mutableStateOf("") }
    var precision by rememberSaveable { mutableStateOf(0) } // Default 0 for whole numbers
    
    // Shift modes for functions (orange shift and purple shift)
    var isOrangeShift by remember { mutableStateOf(false) }
    var isPurpleShift by remember { mutableStateOf(false) }

    fun formatValue(value: Double): String {
        if (precision == -1) {
            return String.format(Locale.US, "%.6g", value)
        }
        if (value.isInfinite()) return "Infinity"
        if (value.isNaN()) return "NaN"
        val absVal = abs(value)
        if (absVal != 0.0 && (absVal >= 1e12 || (absVal < 1e-4 && precision > 0))) {
            return String.format(Locale.US, "%.${precision}e", value)
        }
        val formatted = String.format(Locale.US, "%.${precision}f", value)
        return formatted
    }

    // RPN Operations Helper
    fun commitInputBufferToStack() {
        if (inputBuffer.isNotEmpty()) {
            val value = inputBuffer.toDoubleOrNull()
            if (value != null) {
                stack = stack + value
            }
            inputBuffer = ""
        }
    }

    fun pushToStack() {
        if (inputBuffer.isNotEmpty()) {
            val value = inputBuffer.toDoubleOrNull()
            if (value != null) {
                stack = stack + value
            }
            inputBuffer = ""
        } else if (stack.isNotEmpty()) {
            // HP 48G behavior: ENTER with empty buffer duplicates the bottom (last) item of the stack — PROD-R12
            stack = stack + stack.last()
        }
    }

    fun applyBinaryOp(op: (Double, Double) -> Double) {
        commitInputBufferToStack()
        if (stack.size >= 2) {
            val b = stack.last()
            val a = stack[stack.size - 2]
            val rest = stack.subList(0, stack.size - 2)
            stack = rest + op(a, b)
        }
    }

    fun applyUnaryOp(op: (Double) -> Double) {
        commitInputBufferToStack()
        if (stack.isNotEmpty()) {
            val a = stack.last()
            val rest = stack.subList(0, stack.size - 1)
            stack = rest + op(a)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.hp_48g_clone)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showDropdown by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { showDropdown = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(
                                text = if (precision == -1) Res.get(R.string.fix_std) else Res.get(R.string.fix_x, precision),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Res.get(R.string.std_hp_padrao)) },
                                onClick = { precision = -1; showDropdown = false }
                            )
                            (0..9).forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(Res.get(R.string.fix_x_x_casas, p, (p))) },
                                    onClick = { precision = p; showDropdown = false }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1C1D21)) // Dark slate calculator chassis
                .padding(12.dp)
        ) {
            // HEWLETT-PACKARD 48G Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Res.get(R.string.hewlett_packard_48g),
                    color = Color(0xFFC0C0C0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isOrangeShift) Color(0xFFFF9800) else Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isPurpleShift) Color(0xFF9C27B0) else Color.DarkGray)
                    )
                }
            }

            // LCD Retro Screen Container (Greenish Liquid Crystal style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8C9D86)) // Classic HP greenish LCD
                    .border(3.dp, Color(0xFF4A5545), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                // Stack List
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom,
                        reverseLayout = true
                    ) {
                        // Display the input buffer
                        if (inputBuffer.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(" : ", color = Color(0xFF222B1E), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text(Res.get(R.string.x_14, inputBuffer), color = Color(0xFF222B1E), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                            }
                        }
                        // Display the stack values, newest at the bottom
                        val stackList = stack.asReversed()
                        itemsIndexed(stackList) { index, value ->
                            val level = index + 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Res.get(R.string.x_15, level), color = Color(0xFF333E2F), fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                                Text(
                                    text = formatValue(value),
                                    color = Color(0xFF1E261B),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
                
                // Active shifts visual indicator on LCD
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isOrangeShift) {
                        Text(Res.get(R.string.o_shift), color = Color(0xBF1E261B), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (isPurpleShift) {
                        Text(Res.get(R.string.p_shift), color = Color(0xBF1E261B), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button Pad Layout (Authentic high-fidelity rows and columns)
            Column(
                modifier = Modifier.weight(0.65f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Advanced Shift & Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcButton(Res.get(R.string.o_sf), Color(0xFFFF9800), Modifier.weight(1f), textColor = Color.Black) {
                        isOrangeShift = !isOrangeShift
                        isPurpleShift = false
                    }
                    CalcButton(Res.get(R.string.p_sf), Color(0xFF9C27B0), Modifier.weight(1f), textColor = Color.White) {
                        isPurpleShift = !isPurpleShift
                        isOrangeShift = false
                    }
                    CalcButton("SWAP", Color(0xFF37474F), Modifier.weight(1f)) {
                        pushToStack()
                        if (stack.size >= 2) {
                            val last = stack.last()
                            val prev = stack[stack.size - 2]
                            stack = stack.dropLast(2) + last + prev
                        }
                    }
                    CalcButton("DROP", Color(0xFF37474F), Modifier.weight(1f)) {
                        if (inputBuffer.isNotEmpty()) {
                            inputBuffer = ""
                        } else if (stack.isNotEmpty()) {
                            stack = stack.dropLast(1)
                        }
                    }
                    CalcButton("CLEAR", Color(0xFFE53935), Modifier.weight(1f)) {
                        stack = emptyList()
                        inputBuffer = ""
                    }
                }

                // Row 2: Basic scientific functions (Normal, OrangeShift, PurpleShift)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val labelSin = if (isOrangeShift) "ASIN" else if (isPurpleShift) Res.get(R.string.d_r) else "SIN"
                    CalcButton(labelSin, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { asin(it) }
                            isOrangeShift = false
                        } else if (isPurpleShift) {
                            applyUnaryOp { it * PI / 180.0 }
                            isPurpleShift = false
                        } else {
                            applyUnaryOp { sin(it) }
                        }
                    }

                    val labelCos = if (isOrangeShift) "ACOS" else if (isPurpleShift) Res.get(R.string.r_d) else "COS"
                    CalcButton(labelCos, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { acos(it) }
                            isOrangeShift = false
                        } else if (isPurpleShift) {
                            applyUnaryOp { it * 180.0 / PI }
                            isPurpleShift = false
                        } else {
                            applyUnaryOp { cos(it) }
                        }
                    }

                    val labelTan = if (isOrangeShift) "ATAN" else "TAN"
                    CalcButton(labelTan, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { atan(it) }
                            isOrangeShift = false
                        } else {
                            applyUnaryOp { tan(it) }
                        }
                    }

                    val labelSqrt = if (isOrangeShift) Res.get(R.string.x_2_2) else "SQRT"
                    CalcButton(labelSqrt, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { it * it }
                            isOrangeShift = false
                        } else {
                            applyUnaryOp { sqrt(it) }
                        }
                    }

                    val labelPow = if (isOrangeShift) "1/X" else Res.get(R.string.y_x)
                    CalcButton(labelPow, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { 1.0 / it }
                            isOrangeShift = false
                        } else {
                            applyBinaryOp { x, y -> x.pow(y) }
                        }
                    }
                }

                // Row 3: Secondary logs & constants
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val labelLog = if (isOrangeShift) Res.get(R.string.n_10_x) else "LOG"
                    CalcButton(labelLog, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { 10.0.pow(it) }
                            isOrangeShift = false
                        } else {
                            applyUnaryOp { log10(it) }
                        }
                    }

                    val labelLn = if (isOrangeShift) "EXP" else "LN"
                    CalcButton(labelLn, Color(0xFF455A64), Modifier.weight(1f)) {
                        if (isOrangeShift) {
                            applyUnaryOp { exp(it) }
                            isOrangeShift = false
                        } else {
                            applyUnaryOp { ln(it) }
                        }
                    }

                    CalcButton("PI", Color(0xFF455A64), Modifier.weight(1f)) {
                        stack = stack + PI
                    }

                    CalcButton("E", Color(0xFF455A64), Modifier.weight(1f)) {
                        stack = stack + E
                    }

                    CalcButton("+/-", Color(0xFF455A64), Modifier.weight(1f)) {
                        if (inputBuffer.isNotEmpty()) {
                            inputBuffer = if (inputBuffer.startsWith("-")) inputBuffer.substring(1) else Res.get(R.string.x_16, inputBuffer)
                        } else if (stack.isNotEmpty()) {
                            val lastIdx = stack.size - 1
                            val inverted = -stack[lastIdx]
                            stack = stack.subList(0, lastIdx) + inverted
                        }
                    }
                }

                // Row 4: Numbers 7, 8, 9 & division
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcButton("7", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "7" }
                    CalcButton("8", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "8" }
                    CalcButton("9", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "9" }
                    CalcButton("/", Color(0xFFFF5722), Modifier.weight(1f)) { applyBinaryOp { a, b -> a / b } }
                }

                // Row 5: Numbers 4, 5, 6 & multiplication
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcButton("4", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "4" }
                    CalcButton("5", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "5" }
                    CalcButton("6", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "6" }
                    CalcButton("*", Color(0xFFFF5722), Modifier.weight(1f)) { applyBinaryOp { a, b -> a * b } }
                }

                // Row 6: Numbers 1, 2, 3 & subtraction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcButton("1", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "1" }
                    CalcButton("2", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "2" }
                    CalcButton("3", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "3" }
                    CalcButton("-", Color(0xFFFF5722), Modifier.weight(1f)) { applyBinaryOp { a, b -> a - b } }
                }

                // Row 7: Number 0, dot, Backspace, Addition & ENTER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcButton("0", Color(0xFF607D8B), Modifier.weight(1f)) { inputBuffer += "0" }
                    CalcButton(".", Color(0xFF607D8B), Modifier.weight(1f)) {
                        if (!inputBuffer.contains(".")) {
                            inputBuffer += if (inputBuffer.isEmpty()) "0." else "."
                        }
                    }
                    CalcButton("<-", Color(0xFF78909C), Modifier.weight(1f)) {
                        if (inputBuffer.isNotEmpty()) {
                            inputBuffer = inputBuffer.dropLast(1)
                        } else if (stack.isNotEmpty()) {
                            stack = stack.dropLast(1)
                        }
                    }
                    CalcButton("+", Color(0xFFFF5722), Modifier.weight(1f)) { applyBinaryOp { a, b -> a + b } }
                    CalcButton("ENTER", Color(0xFF00C853), Modifier.weight(2f), textColor = Color.White) {
                        pushToStack()
                    }
                }
            }
        }
    }
}

@Composable
fun CalcButton(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
