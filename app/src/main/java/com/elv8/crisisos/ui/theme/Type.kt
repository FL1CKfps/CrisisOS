package com.elv8.crisisos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: Integrate actual downloadable fonts via GoogleFont.Provider for 'Inter'.
// Using Default FontFamily as a placeholder to allow immediate compilation without XML cert bindings.
val InterFontFamily = FontFamily.Default

val CrisisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 57.sp,
        fontWeight = FontWeight.W300
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.W600
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.W700
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.W400
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.W400
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.5.sp
    )
)