package com.mimo.keyboard

import java.util.Collections

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
 *
 * FIX: All mutable state is now thread-safe using synchronized access.
 * The AccessibilityService writes from a binder thread while Compose
 * reads from the main thread — previously this could cause
 * ConcurrentModificationException or stale reads.
 */
object ScreenLinkStore {

    // Thread-safe backing storage
    private val _links = Collections.synchronizedList(mutableListOf<String>())

    /**
     * Links detected from the screen, most recent first.
     * Limited to 20 to prevent memory issues.
     *
     * FIX: Returns a snapshot copy for safe Compose consumption.
     * The returned list is immutable and safe to iterate on any thread.
     */
    val links: List<String>
        get() = synchronized(_links) { _links.toList() }

    /**
     * Whether the Accessibility Service is currently active.
     * FIX: Made @Volatile for cross-thread visibility.
     */
    @Volatile
    var isServiceActive: Boolean = false

    /**
     * Adds new links to the store, avoiding duplicates.
     * Most recent links appear first.
     *
     * FIX: Synchronized to prevent concurrent modification from
     * AccessibilityService binder thread.
     */
    fun addLinks(newLinks: List<String>) {
        synchronized(_links) {
            val existing = _links.toSet()
            val unique = newLinks.filter { it !in existing }
            if (unique.isNotEmpty()) {
                _links.addAll(0, unique)
                // Trim to max 20
                while (_links.size > MAX_LINKS) {
                    _links.removeAt(_links.size - 1)
                }
            }
        }
    }

    /**
     * Replaces all links with a new set. Used by ScreenTextService to ensure
     * only currently-visible links are shown (prevents stale links from
     * accumulating after the user navigates away from a page).
     *
     * FIX: Previously, links were only ever added via addLinks() and never
     * removed. This meant navigating to a different page would keep showing
     * the old page's links in the Terminal panel. Now, each scan replaces
     * the entire list with only the currently-visible URLs.
     */
    fun replaceLinks(newLinks: List<String>) {
        synchronized(_links) {
            _links.clear()
            _links.addAll(newLinks.take(MAX_LINKS))
        }
    }

    /**
     * Removes a specific link.
     */
    fun removeLink(url: String) {
        synchronized(_links) {
            _links.removeAll { it == url }
        }
    }

    /**
     * Clears all stored links.
     */
    fun clearAll() {
        synchronized(_links) {
            _links.clear()
        }
    }

    private const val MAX_LINKS = 20
}
