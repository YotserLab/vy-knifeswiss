package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── VY Swiss Knife Dark Pro ─────────────────────────────────────────
// Background & Surfaces
val DeepDark        = Color(0xFF0A0E1A) // App background
val DarkNavy        = Color(0xFF111827) // Cards / elevated surfaces
val SurfaceDark     = DarkNavy          // Alias for elevated surface
val DarkNavyVariant = Color(0xFF1B1F2C) // Surface variant
val DarkBorder      = Color(0xFF1E2D4A) // Card borders

// Primary — Cyan Neon (Rede & Conectividade)
val CyanNeon        = Color(0xFF00D4FF)
val CyanNeonDim     = Color(0xFF00677E)
val CyanNeonAlpha   = Color(0x2200D4FF) // 13% alpha for glow bg

// Secondary — Electric Violet (Utilities)
val ElectricViolet  = Color(0xFF7C3AED)
val VioletLight     = Color(0xFFD2BBFF)
val VioletAlpha     = Color(0x227C3AED)

// Tertiary — Amber Gold (Produtividade)
val AmberGold       = Color(0xFFF59E0B)
val AmberLight      = Color(0xFFFBBF24)
val AmberAlpha      = Color(0x22F59E0B)

// Text
val OnSurfaceLight  = Color(0xFFE8EAF6) // Primary text
val OnSurfaceMuted  = Color(0xFF8B97C8) // Secondary / muted text
val OnSurfaceDim    = Color(0xFF4A5578) // Disabled text

// Semantic
val SuccessGreen    = Color(0xFF22C55E)
val DangerRed       = Color(0xFFEF4444)
val WarningAmber    = Color(0xFFF59E0B)

// Legacy (kept for internal Material3 compatibility)
val Purple80        = Color(0xFF00D4FF) // remapped to Cyan
val PurpleGrey80    = Color(0xFF8B97C8)
val Pink80          = Color(0xFFF59E0B)  // remapped to Amber
val Purple40        = Color(0xFF00A8CC)
val PurpleGrey40    = Color(0xFF4A5578)
val Pink40          = Color(0xFFB45309)
