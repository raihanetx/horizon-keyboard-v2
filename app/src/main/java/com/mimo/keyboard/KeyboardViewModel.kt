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

    // FIX: Reset generation counter — incremented every time reset() is called.
    // KeyboardScreen observes this to reset its local Compose state (like isNumberLayer)
    // that can't be reset from the ViewModel directly.
    var resetGeneration by mutableStateOf(0)
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
                    // FIX: Prevent double-space when auto-space is ON.
                    // If auto-space already inserted a space after punctuation, and the
                    // user presses Space manually, we should NOT add another space.
                    // We detect this by checking if the text before cursor already ends
                    // with a punctuation char followed by exactly one space.
                    val beforeText = textValue.trimEnd()
                    val lastChar = beforeText.lastOrNull()
                    val autoSpacePuncts = setOf('.', ',', '!', '?', ';', ':')
                    val wasAutoSpaced = lastChar in autoSpacePuncts &&
                        textValue.endsWith(" ") &&
                        settings?.isAutoSpace != false

                    if (!wasAutoSpaced) {
                        ic.commitText(" ", 1)
                        syncFromInputConnection(ic)
                    }
                }
                // FIX: Auto-capitalize after sentence-ending punctuation + Space.
                // BUG FIX: Previously, handleAutoCapitalize() was called unconditionally,
                // ignoring the isAutoCapitalize setting. Now we respect the setting.
                handleAutoCapitalize()
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

                // FIX: Unified auto-space + auto-capitalize logic.
                // Previously, auto-space was in the Character handler but
                // auto-capitalize was only in the Space handler. This meant:
                // 1. Double space when user presses Space after auto-spaced punctuation
                // 2. Auto-capitalize never fired after auto-space
                // Now both are handled in one place via handlePostCharLogic().
                handlePostCharLogic(key.char)
            }
            is KeyAction.SuggestionInsert -> {
                val ic = inputConnection
                if (ic != null) {
                    // FIX: Delete the current partial word before inserting suggestion.
                    // Previously, typing "hel" and tapping "Hello" would produce
                    // "helHello " instead of "Hello ".
                    // We read text before cursor, find the last word boundary,
                    // and delete back to it.
                    //
                    // BUG FIX: When cursor is at position 0, lastIndexOfAny returns -1.
                    // The old formula `beforeCursor.length - (-1) - 1 = beforeCursor.length`
                    // would delete ALL text before the cursor, not just the current word.
                    // Now we correctly handle lastWordStart == -1: it means there's no
                    // whitespace before the cursor, so the "partial word" spans the entire
                    // text before cursor (which IS the correct thing to delete in that case).
                    // But we must also check that the char before cursor is actually a word
                    // character — if it's whitespace or punctuation, there's nothing to delete.
                    val beforeCursor = ic.getTextBeforeCursor(MAX_SYNC_LENGTH, 0)?.toString() ?: ""
                    val charsToDelete = if (beforeCursor.isEmpty()) {
                        0
                    } else {
                        val lastWordStart = beforeCursor.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                        if (lastWordStart >= 0) {
                            beforeCursor.length - lastWordStart - 1
                        } else {
                            // No whitespace found — entire text before cursor is one word.
                            // Only delete if the last char is a letter/digit (part of a word).
                            // If it's punctuation, user tapped suggestion without typing first.
                            if (beforeCursor.last().isLetterOrDigit()) {
                                beforeCursor.length
                            } else {
                                0
                            }
                        }
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
     * FIX: Unified post-character logic that handles BOTH auto-space and auto-capitalize.
     *
     * Root cause of the double-space bug: auto-space was in the Character handler,
     * auto-capitalize was in the Space handler, and they never coordinated.
     * When user typed "." with auto-space ON, the "." handler auto-inserted " ",
     * then when user pressed Space, another " " was added → ".  " (double space).
     * Also, auto-capitalize only checked in the Space handler, so it never fired
     * after auto-space — shift was never turned on after ". " with auto-space.
     *
     * Now this single method handles both concerns:
     * 1. Auto-space: inserts " " after punctuation (. , ! ? ; :) when enabled
     * 2. Auto-capitalize: turns shift ON after sentence-ending punctuation (. ! ?)
     *    followed by a space (whether manually typed or auto-spaced)
     */
    private fun handlePostCharLogic(char: String) {
        val autoSpacePuncts = setOf(".", ",", "!", "?", ";", ":")
        val sentenceEndPuncts = setOf(".", "!", "?")

        // Auto-space after punctuation
        if (settings?.isAutoSpace != false && char in autoSpacePuncts) {
            val ic = inputConnection
            ic?.commitText(" ", 1)
            if (ic != null) syncFromInputConnection(ic)
        }

        // Auto-capitalize after sentence-ending punctuation + space
        if (settings?.isAutoCapitalize != false && char in sentenceEndPuncts) {
            // The auto-space above (if it fired) already added the space,
            // so auto-capitalize fires immediately after the punctuation + space.
            // If auto-space is OFF, the user must press Space manually, and
            // handleAutoCapitalize() in the Space handler will catch it.
            isShiftOn = true
        }
    }

    /**
     * FIX: Auto-capitalize check for when the user manually presses Space.
     * Checks if the text before cursor ends with sentence-ending punctuation
     * (. ! ?) followed by this space, and turns shift ON if so.
     */
    private fun handleAutoCapitalize() {
        if (settings?.isAutoCapitalize != false) {
            val trimmed = textValue.trimEnd()
            val lastChar = trimmed.lastOrNull()
            if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
                isShiftOn = true
            }
        }
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
        // FIX: Increment reset generation so KeyboardScreen can reset
        // its local state (isNumberLayer) that lives outside the ViewModel.
        resetGeneration++
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
