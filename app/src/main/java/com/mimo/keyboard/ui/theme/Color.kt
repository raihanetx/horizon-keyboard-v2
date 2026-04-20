package com.mimo.keyboard.ui.theme

import androidx.compose.ui.graphics.Color

// Exact color tokens from the HTML prototype's CSS custom properties
object HorizonColors {
    // --bg: #1c1c1e  (main background)
    val Background = Color(0xFF1C1C1E)

    // --kb: #2c2c2e  (keyboard surface)
    val KeyboardSurface = Color(0xFF2C2C2E)

    // --t: #fff  (primary text)
    val TextPrimary = Color(0xFFFFFFFF)

    // --tm: #a0a0a8  (muted text / toolbar icons)
    val TextMuted = Color(0xFFA0A0A8)

    // --tx: #636366  (extra-muted / label text)
    val TextExtraMuted = Color(0xFF636366)

    // --a: #0a84ff  (accent blue)
    val Accent = Color(0xFF0A84FF)

    // Key gradient top: #3a3a3c
    val KeyGradientTop = Color(0xFF3A3A3C)

    // Key gradient bottom: #2c2c2e
    val KeyGradientBottom = Color(0xFF2C2C2E)

    // Key shadow: #151517
    val KeyShadow = Color(0xFF151517)

    // Key pressed: #48484a
    val KeyPressed = Color(0xFF48484A)

    // Special key background (shift, backspace, 123): #48484a
    val SpecialKeyBackground = Color(0xFF48484A)

    // Border colors
    val BorderPrimary = Color(0xFF3A3A3C)
    val BorderSecondary = Color(0xFF2C2C2E)

    // Error / Voice cancel: #ff453a
    val Error = Color(0xFFFF453A)

    // Terminal green: #32d74b
    val TerminalGreen = Color(0xFF32D74B)

    // Pure black for body
    val PureBlack = Color(0xFF000000)
}
