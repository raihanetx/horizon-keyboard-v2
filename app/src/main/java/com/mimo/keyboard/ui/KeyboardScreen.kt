package com.mimo.keyboard.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.KeyboardSettings
import com.mimo.keyboard.KeyboardTab
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.MiMoInputMethodService
import com.mimo.keyboard.VoiceRecognizer
import com.mimo.keyboard.ui.panels.ClipboardPanel
import com.mimo.keyboard.ui.panels.SettingsPanel
import com.mimo.keyboard.ui.panels.TranslatePanel
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Main keyboard screen composable.
 * Assembles all keyboard components into the final layout.
 *
 * Layout (matching HTML prototype, bottom to top):
 * ┌─────────────────────────────┐
 * │  Toolbar Header             │  48dp, tab buttons (with border-top)
 * ├─────────────────────────────┤
 * │  Suggestion Bar             │  40dp, appears when typing (animated slide)
 * ├─────────────────────────────┤
 * │  Main Area                  │  220dp height area
 * │  +- QWERTY Keyboard OR      │
 * │  +- Active Panel            │
 * └─────────────────────────────┘
 */
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    settings: KeyboardSettings? = null,
    voiceRecognizer: VoiceRecognizer? = null,
    inputMethodService: MiMoInputMethodService? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track which number/symbol layer is active (local UI state, not in ViewModel)
    var isNumberLayer by remember { mutableStateOf(false) }

    // Reset isNumberLayer when the ViewModel resets (field switch)
    LaunchedEffect(viewModel.resetGeneration) {
        isNumberLayer = false
    }

    // Read settings as Compose state
    var isHapticsEnabled by remember { mutableStateOf(settings?.isHapticsEnabled ?: true) }
    var isSoundEnabled by remember { mutableStateOf(settings?.isSoundEnabled ?: false) }
    var isShowSuggestions by remember { mutableStateOf(settings?.isShowSuggestions ?: true) }
    var longPressDelayMs by remember { mutableStateOf(settings?.longPressDelayMs?.toLong() ?: 300L) }
    var keyHeightMultiplier by remember { mutableStateOf(settings?.keyHeightMultiplier ?: 1.0f) }

    // Poll settings for changes
    LaunchedEffect(viewModel.currentTab) {
        while (isActive) {
            settings?.let {
                val newHaptics = it.isHapticsEnabled
                val newSound = it.isSoundEnabled
                val newSuggestions = it.isShowSuggestions
                val newDelay = it.longPressDelayMs.toLong()
                val newHeight = it.keyHeightMultiplier
                var settingsChanged = false
                if (newHaptics != isHapticsEnabled) { isHapticsEnabled = newHaptics; settingsChanged = true }
                if (newSound != isSoundEnabled) { isSoundEnabled = newSound; settingsChanged = true }
                if (newSuggestions != isShowSuggestions) { isShowSuggestions = newSuggestions; settingsChanged = true }
                if (newDelay != longPressDelayMs) { longPressDelayMs = newDelay; settingsChanged = true }
                if (newHeight != keyHeightMultiplier) { keyHeightMultiplier = newHeight; settingsChanged = true }
                if (settingsChanged) {
                    viewModel.refreshSuggestions()
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    // Main container — WRAP_CONTENT height so the keyboard is only as tall
    // as its content. The system positions it at the bottom via InputMethodService.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(HorizonColors.Background)
    ) {
        // -- Toolbar Header (with border-top like HTML .tb) ----
        ToolbarHeader(
            currentTab = viewModel.currentTab,
            isVoiceActive = viewModel.currentTab == KeyboardTab.VOICE,
            onTabSelected = { tab ->
                // Stop voice when switching away from voice tab
                if (viewModel.currentTab == KeyboardTab.VOICE && tab != KeyboardTab.VOICE) {
                    voiceRecognizer?.stopListening()
                }
                viewModel.switchTab(tab)
            },
            viewModel = viewModel,
            voiceRecognizer = voiceRecognizer,
            settings = settings,
            inputMethodService = inputMethodService
        )

        // -- Suggestion Bar (appears when typing) -----------
        SuggestionBar(
            isVisible = viewModel.showSuggestions && isShowSuggestions,
            suggestions = viewModel.suggestions,
            onSuggestionClick = { word ->
                viewModel.onKeyPress(KeyAction.SuggestionInsert(word))
            }
        )

        // -- Main Area (panels + keyboard) ------------------
        // Fixed height instead of .weight(1f) to prevent the keyboard from
        // expanding to fill the entire screen. The keyboard should be compact
        // and anchored at the bottom, not stretched to the top.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(HorizonColors.Background)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            // Panel visibility based on current tab
            when (viewModel.currentTab) {
                KeyboardTab.KEYBOARD -> {
                    QwertyKeyboard(
                        isShiftOn = viewModel.isShiftOn,
                        isNumberLayer = isNumberLayer,
                        longPressDelayMs = longPressDelayMs,
                        keyHeightMultiplier = keyHeightMultiplier,
                        onKeyPress = { action ->
                            if (isHapticsEnabled) {
                                performHapticFeedback(context)
                            }
                            if (isSoundEnabled) {
                                performKeyPressSound(context)
                            }
                            when (action) {
                                KeyAction.NumberToggle -> {
                                    isNumberLayer = !isNumberLayer
                                    if (viewModel.isShiftOn) {
                                        viewModel.onKeyPress(KeyAction.Shift)
                                    }
                                }
                                else -> viewModel.onKeyPress(action)
                            }
                        }
                    )
                }
                KeyboardTab.VOICE -> {
                    VoicePanel(
                        viewModel = viewModel,
                        voiceRecognizer = voiceRecognizer,
                        inputMethodService = inputMethodService
                    )
                }
                KeyboardTab.TRANSLATE -> {
                    TranslatePanel(sourceText = viewModel.textValue, viewModel = viewModel)
                }
                KeyboardTab.CLIPBOARD -> {
                    ClipboardPanel(viewModel = viewModel)
                }
                KeyboardTab.SETTINGS -> {
                    SettingsPanel(settings = settings)
                }
            }
        }
    }
}

/**
 * Voice typing panel — full panel view with language selector,
 * start/stop controls, and live recognized text preview.
 */
@Composable
private fun VoicePanel(
    viewModel: KeyboardViewModel,
    voiceRecognizer: VoiceRecognizer?,
    inputMethodService: MiMoInputMethodService? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentVoiceLang = viewModel.voiceLanguage
    val isListening = viewModel.isVoiceListening
    val recognizedText = viewModel.voiceRecognizedText

    // Check mic permission
    val hasMicPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)

    val currentLangInfo = KeyboardSettings.VOICE_LANGUAGES.find { it.locale == currentVoiceLang }
        ?: KeyboardSettings.VOICE_LANGUAGES.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Language selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VOICE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.Accent,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Language toggle buttons
            KeyboardSettings.VOICE_LANGUAGES.forEach { lang ->
                val isSelected = lang.locale == currentVoiceLang
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) HorizonColors.Accent.copy(alpha = 0.2f)
                            else HorizonColors.KeyboardSurface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HorizonColors.Accent.copy(alpha = 0.6f)
                            else HorizonColors.BorderPrimary,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isListening) {
                                    viewModel.setVoiceLanguage(lang.locale)
                                }
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${lang.shortCode} ${lang.displayName}",
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) HorizonColors.Accent else HorizonColors.TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Animated voice indicator
        VoiceAnimationBars(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Mic button — large start/stop
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isListening) HorizonColors.Error.copy(alpha = 0.15f)
                    else HorizonColors.Accent.copy(alpha = 0.15f)
                )
                .border(
                    width = 1.5.dp,
                    color = if (isListening) HorizonColors.Error.copy(alpha = 0.6f)
                    else HorizonColors.Accent.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!hasMicPermission) return@clickable
                        if (isListening) {
                            voiceRecognizer?.stopListening()
                        } else {
                            voiceRecognizer?.startListening()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isListening) "STOP" else "MIC",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isListening) HorizonColors.Error else HorizonColors.Accent,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Recognized text preview
        if (recognizedText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HorizonColors.KeyboardSurface)
                    .border(
                        width = 1.dp,
                        color = HorizonColors.Accent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = recognizedText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = HorizonColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        } else if (!hasMicPermission) {
            // Permission request button — tappable to request mic permission
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Microphone access needed\nfor voice typing",
                    fontSize = 11.sp,
                    color = HorizonColors.TextMuted,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HorizonColors.Accent.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            color = HorizonColors.Accent.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                // Request permission through the InputMethodService
                                inputMethodService?.onRequestMicPermission?.invoke()
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ALLOW MICROPHONE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HorizonColors.Accent,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else if (!isListening) {
            Text(
                text = "Tap MIC to start voice typing",
                fontSize = 11.sp,
                color = HorizonColors.TextExtraMuted,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Text(
                text = "Listening...",
                fontSize = 11.sp,
                color = HorizonColors.Accent.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * QWERTY keyboard layout composable.
 */
@Composable
private fun QwertyKeyboard(
    isShiftOn: Boolean,
    isNumberLayer: Boolean,
    longPressDelayMs: Long = 300L,
    keyHeightMultiplier: Float = 1.0f,
    onKeyPress: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = if (isNumberLayer) {
        KeyboardLayout.numberRows
    } else {
        KeyboardLayout.qwertyRows
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        rows.forEach { rowKeys ->
            KeyboardRow(
                keys = rowKeys,
                isShiftActive = isShiftOn,
                onPress = onKeyPress,
                longPressDelayMs = longPressDelayMs,
                keyHeightMultiplier = keyHeightMultiplier
            )
        }
    }
}

/**
 * Performs haptic feedback on key press.
 */
private fun performHapticFeedback(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(12)
            }
        }
    } catch (e: Exception) {
        // Silently ignore vibration errors
    }
}

/**
 * Plays the system key press sound if sound feedback is enabled.
 */
private fun performKeyPressSound(context: Context) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, -1f)
    } catch (e: Exception) {
        // Silently ignore sound errors
    }
}
