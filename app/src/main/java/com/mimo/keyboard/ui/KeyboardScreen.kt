package com.mimo.keyboard.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.KeyboardSettings
import com.mimo.keyboard.KeyboardTab
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.ui.panels.ClipboardPanel
import com.mimo.keyboard.ui.panels.SettingsPanel
import com.mimo.keyboard.ui.panels.TerminalPanel
import com.mimo.keyboard.ui.panels.TranslatePanel
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Main keyboard screen composable.
 * Assembles all keyboard components into the final layout.
 *
 * Layout (bottom to top):
 * ┌─────────────────────────────┐
 * │  Toolbar Header             │  <- 48dp, tab buttons
 * ├─────────────────────────────┤
 * │  Suggestion Bar             │  <- 40dp, appears when typing
 * ├─────────────────────────────┤
 * │  Main Area                  │  <- wrap content
 * │  +- Active Panel OR         │
 * │  +- QWERTY Keyboard         │
 * └─────────────────────────────┘
 *
 * FIX: Number layer toggle now properly resets shift state when switching
 * layers to prevent inconsistent visual state.
 * FIX: KeyboardSettings is now wired to actual keyboard behavior:
 * - isHapticsEnabled controls vibration on key press
 * - isShowSuggestions controls suggestion bar visibility
 * - isAutoCapitalize enables shift after sentence-ending punctuation
 * - isAutoSpace inserts space after punctuation automatically
 */
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    settings: KeyboardSettings? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track which number/symbol layer is active (local UI state, not in ViewModel)
    var isNumberLayer by remember { mutableStateOf(false) }

    // FIX: Reset isNumberLayer when the ViewModel resets (field switch).
    // Previously, switching input fields would reset shift state (in ViewModel)
    // but NOT reset isNumberLayer (local Compose state), leaving the user
    // stuck on the number layer in the new input field.
    LaunchedEffect(viewModel.resetGeneration) {
        isNumberLayer = false
    }

    // FIX: Read settings as Compose state so changes in SettingsPanel
    // take effect immediately. Previously, toggling settings had no visible effect
    // because nothing re-read them after the toggle.
    var isHapticsEnabled by remember { mutableStateOf(settings?.isHapticsEnabled ?: true) }
    var isSoundEnabled by remember { mutableStateOf(settings?.isSoundEnabled ?: false) }
    var isShowSuggestions by remember { mutableStateOf(settings?.isShowSuggestions ?: true) }
    var longPressDelayMs by remember { mutableStateOf(settings?.longPressDelayMs?.toLong() ?: 300L) }
    var keyHeightMultiplier by remember { mutableStateOf(settings?.keyHeightMultiplier ?: 1.0f) }

    // Poll settings for changes (settings are written by SettingsPanel via SharedPreferences)
    // FIX: Now also polls isSoundEnabled, longPressDelayMs, and keyHeightMultiplier
    // which were previously defined in KeyboardSettings but never read by the UI.
    LaunchedEffect(Unit) {
        while (true) {
            settings?.let {
                val newHaptics = it.isHapticsEnabled
                val newSound = it.isSoundEnabled
                val newSuggestions = it.isShowSuggestions
                val newDelay = it.longPressDelayMs.toLong()
                val newHeight = it.keyHeightMultiplier
                if (newHaptics != isHapticsEnabled) isHapticsEnabled = newHaptics
                if (newSound != isSoundEnabled) isSoundEnabled = newSound
                if (newSuggestions != isShowSuggestions) isShowSuggestions = newSuggestions
                if (newDelay != longPressDelayMs) longPressDelayMs = newDelay
                if (newHeight != keyHeightMultiplier) keyHeightMultiplier = newHeight
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(HorizonColors.Background)
    ) {
        // -- Toolbar Header --------------------------------
        ToolbarHeader(
            currentTab = viewModel.currentTab,
            isVoiceActive = viewModel.currentTab == KeyboardTab.VOICE,
            onTabSelected = { viewModel.switchTab(it) }
        )

        // -- Suggestion Bar (appears when typing) -----------
        // FIX: Respect isShowSuggestions setting
        SuggestionBar(
            isVisible = viewModel.showSuggestions && isShowSuggestions,
            suggestions = viewModel.suggestions,
            onSuggestionClick = { word ->
                viewModel.onKeyPress(KeyAction.SuggestionInsert(word))
            }
        )

        // -- Main Area -------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(HorizonColors.Background)
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        ) {
            // Panel visibility based on current tab
            when (viewModel.currentTab) {
                KeyboardTab.KEYBOARD, KeyboardTab.VOICE -> {
                    QwertyKeyboard(
                        isShiftOn = viewModel.isShiftOn,
                        isNumberLayer = isNumberLayer,
                        longPressDelayMs = longPressDelayMs,
                        keyHeightMultiplier = keyHeightMultiplier,
                        onKeyPress = { action ->
                            // FIX: Only vibrate if haptics are enabled in settings
                            if (isHapticsEnabled) {
                                performHapticFeedback(context)
                            }
                            // FIX: Play key click sound if sound is enabled in settings.
                            // Previously, the isSoundEnabled setting existed in KeyboardSettings
                            // and SettingsPanel but had zero effect — no sound was ever played.
                            if (isSoundEnabled) {
                                performKeyPressSound(context)
                            }
                            when (action) {
                                KeyAction.NumberToggle -> {
                                    // FIX: Toggle number layer and reset shift state.
                                    // Previously, switching to number layer with shift active
                                    // caused visual inconsistency — the shift key appeared active
                                    // in the number layer which doesn't support shifting.
                                    isNumberLayer = !isNumberLayer
                                    if (viewModel.isShiftOn) {
                                        viewModel.onKeyPress(KeyAction.Shift) // Toggle shift off
                                    }
                                }
                                else -> viewModel.onKeyPress(action)
                            }
                        }
                    )
                }
                KeyboardTab.TRANSLATE -> {
                    TranslatePanel(sourceText = viewModel.textValue, viewModel = viewModel)
                }
                KeyboardTab.CLIPBOARD -> {
                    ClipboardPanel(viewModel = viewModel)
                }
                KeyboardTab.TERMINAL -> {
                    // Magic Button — reads screen for URLs via Accessibility, tap to copy/open
                    TerminalPanel(viewModel = viewModel)
                }
                KeyboardTab.SETTINGS -> {
                    SettingsPanel(settings = settings)
                }
            }
        }
    }
}

/**
 * QWERTY keyboard layout composable.
 * Renders the 4-row keyboard matching the HTML prototype.
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
        // Silently ignore vibration errors - not critical for keyboard function
    }
}

/**
 * Plays the system key press sound if sound feedback is enabled.
 *
 * FIX: Previously, the isSoundEnabled setting existed in KeyboardSettings and
 * SettingsPanel but had zero effect — no sound was ever played anywhere in the code.
 * Now this function plays the standard Android keyboard click sound using
 * AudioManager when the setting is enabled.
 */
private fun performKeyPressSound(context: Context) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, -1f)
    } catch (e: Exception) {
        // Silently ignore sound errors - not critical for keyboard function
    }
}
