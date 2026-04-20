package com.mimo.keyboard.ui

import android.content.Context
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
 */
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track which number/symbol layer is active (local UI state, not in ViewModel)
    var isNumberLayer by remember { mutableStateOf(false) }

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
        SuggestionBar(
            isVisible = viewModel.showSuggestions,
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
                        onKeyPress = { action ->
                            performHapticFeedback(context)
                            if (action == KeyAction.NumberToggle) {
                                isNumberLayer = !isNumberLayer
                            } else {
                                viewModel.onKeyPress(action)
                            }
                        }
                    )
                }
                KeyboardTab.TRANSLATE -> {
                    TranslatePanel(sourceText = viewModel.textValue)
                }
                KeyboardTab.CLIPBOARD -> {
                    ClipboardPanel()
                }
                KeyboardTab.TERMINAL -> {
                    // Pass viewModel so terminal can display typed text
                    // without using BasicTextField (which causes recursive IME crash)
                    TerminalPanel(viewModel = viewModel)
                }
                KeyboardTab.SETTINGS -> {
                    SettingsPanel()
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
                onPress = onKeyPress
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
