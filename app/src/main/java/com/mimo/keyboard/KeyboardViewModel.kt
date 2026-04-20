package com.mimo.keyboard

import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Represents which panel/tab is currently active in the keyboard.
 * Matches the HTML prototype's State.tab values.
 */
enum class KeyboardTab {
    KEYBOARD,
    TRANSLATE,
    CLIPBOARD,
    VOICE,
    TERMINAL,
    SETTINGS
}

/**
 * Central state management for the Horizon Keyboard.
 * Mirrors the JavaScript State object from the HTML prototype.
 */
class KeyboardViewModel : ViewModel() {

    // ── Text state ──────────────────────────────────────────
    var textValue by mutableStateOf("")
        private set

    var isShiftOn by mutableStateOf(false)
        private set

    var currentTab by mutableStateOf(KeyboardTab.KEYBOARD)
        private set

    // ── Suggestion state ────────────────────────────────────
    var showSuggestions by mutableStateOf(false)
        private set

    var suggestions by mutableStateOf(listOf<String>())
        private set

    // ── Input connection ────────────────────────────────────
    var inputConnection: InputConnection? = null

    // ── Key press handling ──────────────────────────────────

    /**
     * Handles a key press from the keyboard UI.
     * Replicates the handlePress() logic from the JS prototype.
     */
    fun onKeyPress(key: KeyAction) {
        when (key) {
            KeyAction.Shift -> {
                isShiftOn = !isShiftOn
            }
            KeyAction.Backspace -> {
                // Use sendKeyEvent for reliable backspace across all input fields
                val ic = inputConnection
                if (ic != null) {
                    // First try to delete composing text, then use deleteSurroundingText
                    val deleted = ic.deleteSurroundingText(1, 0)
                    if (!deleted) {
                        // Fallback: send KeyEvent
                        ic.sendKeyEvent(
                            android.view.KeyEvent(
                                android.view.KeyEvent.ACTION_DOWN,
                                android.view.KeyEvent.KEYCODE_DEL
                            )
                        )
                        ic.sendKeyEvent(
                            android.view.KeyEvent(
                                android.view.KeyEvent.ACTION_UP,
                                android.view.KeyEvent.KEYCODE_DEL
                            )
                        )
                    }
                }
            }
            KeyAction.Space -> {
                textValue += " "
                inputConnection?.commitText(" ", 1)
            }
            KeyAction.Done -> {
                // Close keyboard / send editor action
                inputConnection?.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                switchTab(KeyboardTab.KEYBOARD)
            }
            KeyAction.NumberToggle -> {
                // In a full implementation this would switch to number/symbol layer
                // For now we commit the label text
            }
            is KeyAction.Character -> {
                val char = if (isShiftOn) key.char.uppercase() else key.char.lowercase()
                textValue += char
                inputConnection?.commitText(char, 1)

                // Auto-release shift after single character (matching JS behavior)
                if (isShiftOn) {
                    isShiftOn = false
                }
            }
            is KeyAction.SuggestionInsert -> {
                textValue += key.word + " "
                inputConnection?.commitText(key.word + " ", 1)
            }
        }

        updateSuggestions()
    }

    /**
     * Switches the active panel/tab.
     * Mirrors switchPanel() from the JS prototype.
     */
    fun switchTab(tab: KeyboardTab) {
        currentTab = tab
        updateSuggestions()
    }

    /**
     * Updates the suggestion bar visibility and content.
     * In the prototype, suggestions appear when text is present and keyboard tab is active.
     */
    private fun updateSuggestions() {
        showSuggestions = textValue.isNotEmpty() && currentTab == KeyboardTab.KEYBOARD

        // Placeholder suggestions — in a real keyboard this would come from
        // a prediction engine or dictionary
        if (showSuggestions) {
            suggestions = generateSuggestions(textValue)
        } else {
            suggestions = emptyList()
        }
    }

    /**
     * Generates contextual suggestions based on current input.
     * Placeholder implementation — replace with a real prediction engine.
     */
    private fun generateSuggestions(input: String): List<String> {
        val lastWord = input.trimEnd().split(" ").lastOrNull()?.lowercase() ?: ""
        return when {
            lastWord.isEmpty() -> listOf("Hello", "The", "Thanks")
            lastWord.startsWith("h") -> listOf("Hello", "Hey", "Hi")
            lastWord.startsWith("t") -> listOf("The", "Thanks", "That")
            lastWord.startsWith("w") -> listOf("What", "When", "Where")
            else -> listOf("And", "But", "The")
        }
    }

    /**
     * Clears all state (e.g., when input connection resets).
     */
    fun reset() {
        textValue = ""
        isShiftOn = false
        showSuggestions = false
        suggestions = emptyList()
    }
}

/**
 * Sealed class representing all possible key actions.
 * This replaces the simple string-based key handling from the JS prototype
 * with a type-safe action system.
 */
sealed class KeyAction {
    data object Shift : KeyAction()
    data object Backspace : KeyAction()
    data object Space : KeyAction()
    data object Done : KeyAction()
    data object NumberToggle : KeyAction()
    data class Character(val char: String) : KeyAction()
    data class SuggestionInsert(val word: String) : KeyAction()
}
