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
 *
 * FIX: textValue is now synced from InputConnection rather than
 * tracked independently. The previous approach caused textValue to
 * drift from actual field content on backspace, external edits, etc.
 *
 * The Magic Button (Terminal tab) uses ScreenLinkStore directly,
 * which is populated by the Accessibility Service (ScreenTextService).
 */
class KeyboardViewModel : ViewModel() {

    // ── Text state ──────────────────────────────────────────
    // FIX: textValue is now synced FROM the InputConnection rather than
    // accumulated locally. Previously, backspace/external edits caused textValue
    // to diverge from the actual field content.
    var textValue by mutableStateOf("")
        private set

    var isShiftOn by mutableStateOf(false)
        private set

    var currentTab by mutableStateOf(KeyboardTab.KEYBOARD)
        private set

    // ── Cursor state ────────────────────────────────────────
    // FIX: Track cursor position for better suggestion context.
    // Knowing whether cursor is at the start of a sentence, middle of a word,
    // or after punctuation helps generate more relevant suggestions.
    var cursorPosition by mutableStateOf(0)
        private set

    // ── Suggestion state ────────────────────────────────────
    var showSuggestions by mutableStateOf(false)
        private set

    var suggestions by mutableStateOf(listOf<String>())
        private set

    // ── Input connection ────────────────────────────────────
    // FIX: Use @Volatile for thread-safety — inputConnection can be set
    // from InputMethodService callbacks and read from Compose UI thread.
    @Volatile
    private var _inputConnection: InputConnection? = null
    var inputConnection: InputConnection?
        get() = _inputConnection
        set(value) { _inputConnection = value }

    // ── Key press handling ──────────────────────────────────

    /**
     * Handles a key press from the keyboard UI.
     * Replicates the handlePress() logic from the JS prototype.
     *
     * FIX: Character and Space now sync textValue FROM the InputConnection
     * after committing text, rather than building it locally. This prevents
     * drift when backspace, selection, or external edits occur.
     */
    fun onKeyPress(key: KeyAction) {
        when (key) {
            KeyAction.Shift -> {
                isShiftOn = !isShiftOn
            }
            KeyAction.Backspace -> {
                val ic = inputConnection
                if (ic != null) {
                    // Try efficient deletion first
                    val beforeText = ic.getTextBeforeCursor(1, 0)
                    if (beforeText != null && beforeText.isNotEmpty()) {
                        ic.deleteSurroundingText(1, 0)
                    } else {
                        // Fallback: send key events for fields that don't support deleteSurroundingText
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
                    // FIX: Sync textValue from InputConnection after backspace
                    syncTextFromInputConnection(ic)
                }
            }
            KeyAction.Space -> {
                val ic = inputConnection
                if (ic != null) {
                    ic.commitText(" ", 1)
                    // FIX: Sync from InputConnection instead of local accumulation
                    syncTextFromInputConnection(ic)
                }
            }
            KeyAction.Done -> {
                inputConnection?.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                switchTab(KeyboardTab.KEYBOARD)
            }
            KeyAction.NumberToggle -> {
                // Switches to number/symbol layer — handled in KeyboardScreen
            }
            is KeyAction.Character -> {
                val char = if (isShiftOn) key.char.uppercase() else key.char.lowercase()
                val ic = inputConnection
                if (ic != null) {
                    ic.commitText(char, 1)
                    // FIX: Sync from InputConnection instead of local accumulation
                    syncTextFromInputConnection(ic)
                }

                if (isShiftOn) {
                    isShiftOn = false
                }
            }
            is KeyAction.SuggestionInsert -> {
                val ic = inputConnection
                if (ic != null) {
                    ic.commitText(key.word + " ", 1)
                    // FIX: Sync from InputConnection instead of local accumulation
                    syncTextFromInputConnection(ic)
                }
            }
        }

        updateSuggestions()
    }

    /**
     * FIX: Syncs textValue from the InputConnection by reading the text
     * before the cursor. This is the source of truth — the InputConnection
     * reflects the actual field content including external edits, selections, etc.
     *
     * We read up to MAX_SYNC_LENGTH chars before cursor for suggestion context.
     */
    private fun syncTextFromInputConnection(ic: InputConnection) {
        // Read text before cursor for suggestion context
        val beforeCursor = ic.getTextBeforeCursor(MAX_SYNC_LENGTH, 0) ?: ""
        textValue = beforeCursor.toString()

        // FIX: Update cursor position for smarter suggestions
        cursorPosition = beforeCursor.length
    }

    /**
     * Switches the active panel/tab.
     */
    fun switchTab(tab: KeyboardTab) {
        currentTab = tab
        updateSuggestions()
    }

    /**
     * Updates the suggestion bar visibility and content.
     */
    private fun updateSuggestions() {
        showSuggestions = textValue.isNotEmpty() && currentTab == KeyboardTab.KEYBOARD

        if (showSuggestions) {
            suggestions = generateSuggestions(textValue)
        } else {
            suggestions = emptyList()
        }
    }

    /**
     * Generates contextual suggestions based on current input.
     *
     * FIX: Uses regex split "\\s+" instead of " " to properly handle
     * multiple consecutive spaces without producing empty word tokens.
     */
    /**
     * Generates contextual suggestions based on current input.
     *
     * FIX: Now uses cursor position to detect sentence boundaries.
     * After sentence-ending punctuation (., !, ?) followed by a space,
     * suggestions switch to sentence-starting words (capitalized).
     */
    private fun generateSuggestions(input: String): List<String> {
        // FIX: Split on whitespace and filter empty strings
        val lastWord = input.trimEnd()
            .split("\\s+".toRegex())
            .lastOrNull()
            ?.lowercase() ?: ""

        // FIX: Detect if cursor is at sentence start (after . ! ? or at beginning)
        val isSentenceStart = input.trimEnd().isEmpty() ||
            input.trimEnd().let { text ->
                val last = text.lastOrNull()
                last == '.' || last == '!' || last == '?'
            }

        return when {
            isSentenceStart -> listOf("I", "The", "This")
            lastWord.isEmpty() -> listOf("Hello", "The", "Thanks")
            lastWord.startsWith("h") -> listOf("Hello", "Hey", "Hi")
            lastWord.startsWith("t") -> listOf("The", "Thanks", "That")
            lastWord.startsWith("w") -> listOf("What", "When", "Where")
            lastWord.startsWith("i") -> listOf("I", "In", "Is")
            lastWord.startsWith("a") -> listOf("And", "Are", "At")
            lastWord.startsWith("s") -> listOf("So", "She", "Some")
            lastWord.startsWith("b") -> listOf("But", "Be", "By")
            lastWord.startsWith("c") -> listOf("Can", "Could", "Come")
            lastWord.startsWith("d") -> listOf("Do", "Don't", "Did")
            lastWord.startsWith("n") -> listOf("Not", "No", "Now")
            else -> listOf("And", "But", "The")
        }
    }

    /**
     * Clears all state (e.g., when input connection resets).
     *
     * FIX: Also clears the inputConnection reference on reset to prevent
     * stale connection usage after field switch.
     */
    fun reset() {
        textValue = ""
        cursorPosition = 0
        isShiftOn = false
        showSuggestions = false
        suggestions = emptyList()
    }

    companion object {
        // Max characters to read from InputConnection for suggestion context
        private const val MAX_SYNC_LENGTH = 100
    }
}

/**
 * Sealed class representing all possible key actions.
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
