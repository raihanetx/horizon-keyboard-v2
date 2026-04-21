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
class KeyboardViewModel(private val settings: KeyboardSettings? = null) : ViewModel() {

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

    // FIX: Expose settings so UI can check haptics/suggestions/etc.
    // Previously, KeyboardSettings was only used by SettingsPanel — toggling
    // haptics or suggestions had zero effect on keyboard behavior.
    val keyboardSettings: KeyboardSettings? get() = settings

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
                    syncFromInputConnection(ic)
                }
            }
            KeyAction.Space -> {
                val ic = inputConnection
                if (ic != null) {
                    ic.commitText(" ", 1)
                    // FIX: Sync from InputConnection instead of local accumulation
                    syncFromInputConnection(ic)
                }
                // FIX: Auto-capitalize after sentence-ending punctuation
                // When auto-capitalize is ON, pressing space after . ! ? turns shift on
                if (settings?.isAutoCapitalize != false) {
                    val trimmed = textValue.trimEnd()
                    val lastChar = trimmed.lastOrNull()
                    if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
                        isShiftOn = true
                    }
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
                    syncFromInputConnection(ic)
                }

                if (isShiftOn) {
                    isShiftOn = false
                }

                // FIX: Auto-space after punctuation when auto-space is ON.
                // After typing . , ! ? ; : and the setting is enabled,
                // automatically insert a space so user doesn't have to press space.
                if (settings?.isAutoSpace != false) {
                    val autoSpaceAfter = setOf(".", ",", "!", "?", ";", ":")
                    if (key.char in autoSpaceAfter) {
                        val ic2 = inputConnection
                        ic2?.commitText(" ", 1)
                        if (ic2 != null) syncFromInputConnection(ic2)
                    }
                }
            }
            is KeyAction.SuggestionInsert -> {
                val ic = inputConnection
                if (ic != null) {
                    // FIX: Delete the current partial word before inserting suggestion.
                    // Previously, typing "hel" and tapping "Hello" would produce
                    // "helHello " instead of "Hello ".
                    // We read text before cursor, find the last word boundary,
                    // and delete back to it.
                    val beforeCursor = ic.getTextBeforeCursor(MAX_SYNC_LENGTH, 0)?.toString() ?: ""
                    val lastWordStart = beforeCursor.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                    val charsToDelete = if (lastWordStart >= 0) {
                        beforeCursor.length - lastWordStart - 1
                    } else {
                        beforeCursor.length
                    }
                    if (charsToDelete > 0) {
                        ic.deleteSurroundingText(charsToDelete, 0)
                    }
                    ic.commitText(key.word + " ", 1)
                    syncFromInputConnection(ic)
                }
            }
            is KeyAction.InsertText -> {
                val ic = inputConnection
                if (ic != null) {
                    // FIX: Commit raw text as-is without shift logic.
                    // This is used for clipboard paste and translation paste
                    // where the original casing must be preserved.
                    ic.commitText(key.text, 1)
                    syncFromInputConnection(ic)
                }
                // InsertText does NOT toggle shift off — it's not a character key
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
     *
     * Public so MiMoInputMethodService can call it from onUpdateCursorAnchorInfo()
     * when the user taps to reposition the cursor in the text field.
     */
    fun syncFromInputConnection(ic: InputConnection) {
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
        // FIX: Respect isShowSuggestions setting — if user disabled suggestions
        // in SettingsPanel, don't show the suggestion bar regardless of text content.
        val suggestionsEnabled = settings?.isShowSuggestions != false
        showSuggestions = suggestionsEnabled && textValue.isNotEmpty() && currentTab == KeyboardTab.KEYBOARD

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
     * Also uses cursor position to detect sentence boundaries.
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
     * Clears all state (e.g., when input field switches).
     *
     * Note: This does NOT clear the inputConnection reference — that's handled
     * separately by MiMoInputMethodService.onFinishInput(). The IC is refreshed
     * by onStartInput() before reset() is called, so we keep the new IC intact.
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

    /**
     * FIX: New action for inserting raw multi-character text WITHOUT shift logic.
     * Used by ClipboardPanel (paste) and TranslatePanel (paste translation).
     * Unlike KeyAction.Character, this does NOT apply lowercase()/uppercase()
     * based on shift state — the text is committed exactly as-is.
     */
    data class InsertText(val text: String) : KeyAction()
}
