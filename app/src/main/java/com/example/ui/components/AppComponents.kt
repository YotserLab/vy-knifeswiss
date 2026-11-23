package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.Res
import com.example.ui.theme.*
import androidx.compose.material.icons.automirrored.filled.*

// ── Top App Bar ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// PLAT-R11
fun AppTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = OnSurfaceLight,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = Res.get(R.string.voltar),
                        tint = CyanNeon,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = DeepDark,
            titleContentColor      = OnSurfaceLight,
            actionIconContentColor = CyanNeon,
            navigationIconContentColor = CyanNeon,
        ),
    )
}

// ── Category Section Header ──────────────────────────────────────────

/**
 * Category header with a colored vertical accent line on the left.
 * Usado no Dashboard, Settings e About.
 */
@Composable
fun CategoryHeader(
    label: String,
    accentColor: Color = CyanNeon,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(start = horizontalPadding, end = horizontalPadding, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Accent line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(color = accentColor, shape = RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                ),
                color = accentColor,
            )
        }
        if (expanded != null) {
            Icon(
                imageVector     = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Recolher" else "Expandir",
                tint            = accentColor,
                modifier        = Modifier.size(20.dp),
            )
        }
    }
}

// ── Neon Card ────────────────────────────────────────────────────────

/**
 * Card dark com borda colorida e glow sutil no fundo.
 */
@Composable
fun NeonCard(
    borderColor: Color = CyanNeon,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glowColor = borderColor.copy(alpha = 0.12f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                // Glow suave ao redor do card
                drawRoundRect(
                    color       = glowColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .background(color = DarkNavy, shape = RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = borderColor.copy(alpha = 0.4f), shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

// ── Gradient Button ──────────────────────────────────────────────────

/**
 * Button with a horizontal cyan gradient, used for primary actions.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startColor: Color = CyanNeon,
    endColor: Color   = CyanNeonDim,
    icon: (@Composable () -> Unit)? = null,
) {
    val gradient = Brush.horizontalGradient(listOf(startColor, endColor))
    val bgColor  = if (enabled) Color.Transparent else DarkNavyVariant

    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier.height(50.dp),
        shape    = RoundedCornerShape(25.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = Color.Transparent,
            disabledContainerColor = DarkNavyVariant,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) gradient else Brush.horizontalGradient(listOf(DarkNavyVariant, DarkNavyVariant))),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                icon?.invoke()
                if (icon != null) Spacer(Modifier.width(8.dp))
                Text(
                    text       = text,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color      = if (enabled) DeepDark else OnSurfaceDim,
                )
            }
        }
    }
}

// ── VY Badge ─────────────────────────────────────────────────────────

/**
 * Colored pill badge for status, protocols, frequencies, etc.
 */
@Composable
fun VYBadge(
    text: String,
    color: Color = CyanNeon,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp))
            .border(width = 1.dp, color = color.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text      = text,
            style     = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color     = color,
        )
    }
}

// ── Info Row ─────────────────────────────────────────────────────────

/**
 * Label + value row for info cards (NetworkInfo, etc.)
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = OnSurfaceLight,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMuted,
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

// ── Section Divider ──────────────────────────────────────────────────

@Composable
fun VYDivider(
    color: Color = DarkBorder,
    thickness: Dp = 1.dp,
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}

