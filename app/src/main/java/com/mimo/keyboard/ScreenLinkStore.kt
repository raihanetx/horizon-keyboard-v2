package com.mimo.keyboard

/**
 * Shared singleton that holds screen-detected links.
 *
 * Both the AccessibilityService (ScreenTextService) and the
 * InputMethodService (MiMoInputMethodService) run in the same
 * process, so they share the same static state.
 *
 * Flow:
 * 1. ScreenTextService reads screen text via Accessibility
 * 2. It extracts URLs and stores them here
 * 3. TerminalPanel in the keyboard reads from here
 * 4. User taps a link → copies to clipboard → pastes in browser
 */
object ScreenLinkStore {

    /**
     * Links detected from the screen, most recent first.
     * Limited to 20 to prevent memory issues.
     */
    var links: List<String> = emptyList()
        private set

    /**
     * Whether the Accessibility Service is currently active.
     */
    var isServiceActive: Boolean = false

    /**
     * Adds new links to the store, avoiding duplicates.
     * Most recent links appear first.
     */
    fun addLinks(newLinks: List<String>) {
        val existing = links.toSet()
        val unique = newLinks.filter { it !in existing }
        if (unique.isNotEmpty()) {
            links = (unique + links).take(20)
        }
    }

    /**
     * Removes a specific link.
     */
    fun removeLink(url: String) {
        links = links.filter { it != url }
    }

    /**
     * Clears all stored links.
     */
    fun clearAll() {
        links = emptyList()
    }
}
