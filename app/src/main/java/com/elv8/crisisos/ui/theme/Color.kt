package com.elv8.crisisos.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PrimaryOrange = Color(0xFFFF3B00)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainerDark = Color(0xFF3D0D00)
val BackgroundDark = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF141414)
val SurfaceVariantDark = Color(0xFF1E1E1E)
val OnBackgroundDark = Color(0xFFF5F5F0)
val OnSurfaceDark = Color(0xFFE8E8E4)
val OnSurfaceVariantDark = Color(0xFF9E9E96)
val ErrorRed = Color(0xFFFF453A)
val OutlineDark = Color(0xFF2E2E2E)
val OutlineVariantDark = Color(0xFF1C1C1C)

// Slightly adjusted pastels for Light Theme
val PrimaryLight = Color(0xFFFF7043)
val PrimaryContainerLight = Color(0xFFFFCCBC)
val BackgroundLight = Color(0xFFFDFDFD)
val SurfaceLight = Color(0xFFF5F5F5)
val SurfaceVariantLight = Color(0xFFEBEBEB)
val OnBackgroundLight = Color(0xFF1A1A1A)
val OnSurfaceLight = Color(0xFF1A1A1A)
val OnSurfaceVariantLight = Color(0xFF4A4A4A)
val OutlineLight = Color(0xFFBDBDBD)
val OutlineVariantLight = Color(0xFFE0E0E0)

val DarkColors = darkColorScheme(
    primary = PrimaryOrange,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorRed,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorRed,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)