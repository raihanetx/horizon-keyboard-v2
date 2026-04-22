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
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
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
 * Layout (matching HTML prototype, bottom to top):
 * ┌─────────────────────────────┐
 * │  Toolbar Header             │  48dp, 6 tab buttons (with border-top)
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

    // Main container - fixed height matching HTML --ma-h: 220px
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HorizonColors.Background)
    ) {
        // -- Toolbar Header (with border-top like HTML .tb) ----
        ToolbarHeader(
            currentTab = viewModel.currentTab,
            isVoiceActive = viewModel.currentTab == KeyboardTab.VOICE,
            onTabSelected = { viewModel.switchTab(it) }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                KeyboardTab.TRANSLATE -> {
                    TranslatePanel(sourceText = viewModel.textValue, viewModel = viewModel)
                }
                KeyboardTab.CLIPBOARD -> {
                    ClipboardPanel(viewModel = viewModel)
                }
                KeyboardTab.TERMINAL -> {
                    TerminalPanel()
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
