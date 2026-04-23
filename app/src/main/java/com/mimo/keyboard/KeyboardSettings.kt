package com.mimo.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent keyboard settings stored in SharedPreferences.
 *
 * FIX: Created this class because SettingsPanel previously showed
 * hardcoded static text ("Haptics: Active", "Theme: Dark", etc.)
 * with no interactive toggles. Now all settings are:
 * 1. Persisted across app restarts via SharedPreferences
 * 2. Observable by Compose via StateFlow
 * 3. Shared between the SettingsPanel and the keyboard logic
 *
 * Usage:
 * - KeyboardViewModel reads settings to decide behavior (e.g., haptics on/off)
 * - SettingsPanel writes settings via toggle callbacks
 * - HorizonInputMethodService creates the instance and passes it down
 */
class KeyboardSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "horizon_keyboard_settings",
        Context.MODE_PRIVATE
    )

    // ── Haptic Feedback ─────────────────────────────────────

    /** Whether haptic feedback is enabled on key press */
    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    // ── Sound Feedback ──────────────────────────────────────

    /** Whether key press sound is enabled */
    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    // ── Auto-Capitalize ─────────────────────────────────────

    /** Whether to auto-capitalize the first letter of sentences */
    var isAutoCapitalize: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPITALIZE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPITALIZE, value).apply()

    // ── Auto-Space ──────────────────────────────────────────

    /** Whether to auto-insert space after punctuation */
    var isAutoSpace: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPACE, value).apply()

    // ── Suggestion Bar ──────────────────────────────────────

    /** Whether to show the suggestion bar while typing */
    var isShowSuggestions: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SUGGESTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SUGGESTIONS, value).apply()

    // ── Long Press Delay ────────────────────────────────────

    /** Long press delay in milliseconds before triggering repeat/alternative */
    var longPressDelayMs: Int
        get() = prefs.getInt(KEY_LONG_PRESS_DELAY, 300)
        set(value) = prefs.edit().putInt(KEY_LONG_PRESS_DELAY, value).apply()

    // ── Key Height ──────────────────────────────────────────

    /** Key height multiplier (1.0 = default) */
    var keyHeightMultiplier: Float
        get() = prefs.getFloat(KEY_KEY_HEIGHT, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_KEY_HEIGHT, value).apply()

    // ── Voice Language ──────────────────────────────────────

    /** Preferred voice typing language. "en-US" for English, "bn-BD" for Bangla */
    var voiceLanguage: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, "en-US") ?: "en-US"
        set(value) = prefs.edit().putString(KEY_VOICE_LANGUAGE, value).apply()

    companion object {
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
        private const val KEY_AUTO_SPACE = "auto_space"
        private const val KEY_SHOW_SUGGESTIONS = "show_suggestions"
        private const val KEY_LONG_PRESS_DELAY = "long_press_delay"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_VOICE_LANGUAGE = "voice_language"

        /** Supported voice languages */
        val VOICE_LANGUAGES = listOf(
            VoiceLanguage("en-US", "English", "EN"),
            VoiceLanguage("bn-BD", "বাংলা", "BN")
        )
    }
}

/**
 * Represents a supported voice language.
 */
data class VoiceLanguage(
    val locale: String,
    val displayName: String,
    val shortCode: String
)
