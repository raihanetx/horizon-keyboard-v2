package com.mimo.keyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HorizonColorScheme = darkColorScheme(
    primary = HorizonColors.Accent,
    onPrimary = HorizonColors.TextPrimary,
    secondary = HorizonColors.SpecialKeyBackground,
    onSecondary = HorizonColors.TextPrimary,
    tertiary = HorizonColors.Accent,
    background = HorizonColors.Background,
    onBackground = HorizonColors.TextPrimary,
    surface = HorizonColors.KeyboardSurface,
    onSurface = HorizonColors.TextPrimary,
    surfaceVariant = HorizonColors.KeyGradientTop,
    onSurfaceVariant = HorizonColors.TextMuted,
    outline = HorizonColors.BorderPrimary,
    outlineVariant = HorizonColors.BorderSecondary,
    error = HorizonColors.Error,
    onError = HorizonColors.TextPrimary
)

@Composable
fun HorizonKeyboardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HorizonColorScheme,
        typography = HorizonTypography,
        content = content
    )
}
