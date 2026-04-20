package com.mimo.keyboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Typography matching the Inter + DM Mono font system from the prototype
val HorizonTypography = Typography(
    // Key labels - 18sp medium (Inter)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,  // Inter maps to system default on Android
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    // Suggestion words - 13sp semibold
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // Small labels like "EN" - 10sp extrabold uppercase
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,  // DM Mono equivalent
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.12.sp
    ),
    // Panel title - 9px uppercase extrabold
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.12.sp
    ),
    // Panel body / monospace text - 14px
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    // Space bar text - 11sp with letter spacing
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 2.sp
    ),
    // Enter key text - 12sp bold
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    ),
    // Console display text - 18px monospace
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    // Console label - 10px uppercase
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.12.sp
    )
)
