package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VYDarkColorScheme = darkColorScheme(
    primary              = CyanNeon,
    onPrimary            = Color(0xFF003642),
    primaryContainer     = CyanNeon,
    onPrimaryContainer   = Color(0xFF00586B),

    secondary            = ElectricViolet,
    onSecondary          = OnSurfaceLight,
    secondaryContainer   = Color(0xFF6001D1),
    onSecondaryContainer = Color(0xFFC9AEFF),

    tertiary             = AmberGold,
    onTertiary           = DeepDark,
    tertiaryContainer    = AmberLight,
    onTertiaryContainer  = OnSurfaceLight,

    background           = DeepDark,
    onBackground         = OnSurfaceLight,

    surface              = DarkNavy,
    onSurface            = OnSurfaceLight,
    surfaceVariant       = DarkNavyVariant,
    onSurfaceVariant     = OnSurfaceMuted,

    outline              = DarkBorder,
    outlineVariant       = DarkNavyVariant,

    error                = DangerRed,
    onError              = OnSurfaceLight,
)

// Light fallback (minimal — not normally used)
private val VYLightColorScheme = lightColorScheme(
    primary    = CyanNeonDim,
    secondary  = ElectricViolet,
    tertiary   = AmberGold,
    background = DeepDark,
    surface    = DarkNavy,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Always dark by default
    content: @Composable () -> Unit,
) {
    // Dynamic color disabled to keep design system consistency
    val colorScheme = if (darkTheme) VYDarkColorScheme else VYLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}

