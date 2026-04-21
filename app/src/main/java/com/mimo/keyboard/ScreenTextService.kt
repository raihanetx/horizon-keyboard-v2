package com.mimo.keyboard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service that reads screen text and detects URLs.
 *
 * This is the KEY feature of Horizon Keyboard's "Magic Button" (Terminal tab).
 * It solves the problem of browser terminals showing links that you cannot
 * copy or tap — this service reads the screen text, finds URLs, and makes
 * them available in the keyboard's Terminal panel for one-tap copy.
 *
 * How it works:
 * 1. Android notifies this service when the screen content changes
 * 2. We traverse the accessibility node tree to collect all visible text
 * 3. We extract URLs using regex
 * 4. We store them in ScreenLinkStore (shared with the keyboard)
 * 5. The keyboard's Terminal panel reads from ScreenLinkStore
 *
 * Privacy: This service only reads text to find URLs. It does NOT collect,
 * store, or transmit any personal data. No keyboard input is read.
 *
 * FIX: Added throttling to prevent scanning on every accessibility event
 * (which can fire hundreds of times per second during scrolling).
 * FIX: Added recursion depth limit to prevent StackOverflow on deep hierarchies.
 * FIX: Removed redundant (?i) in regex since RegexOption.IGNORE_CASE is already set.
 */
class ScreenTextService : AccessibilityService() {

    // FIX: Throttle scans to at most once per SCAN_THROTTLE_MS
    // to prevent excessive CPU usage during rapid accessibility events
    private var lastScanTimeMs: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenLinkStore.isServiceActive = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only process content changes (not focus changes, scroll events, etc.)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                scanScreenForLinks()
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted — keep existing links but mark as inactive
        ScreenLinkStore.isServiceActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenLinkStore.isServiceActive = false
    }

    /**
     * Scans the current screen's accessibility tree for text containing URLs.
     * Traverses all visible nodes and extracts text content.
     *
     * FIX: Throttled to run at most once per SCAN_THROTTLE_MS to prevent
     * excessive scanning during rapid accessibility events (scrolling, animations).
     * FIX: Recycles the root AccessibilityNodeInfo after use to prevent memory leaks.
     * Android requires recycling these objects when done, or they leak native memory
     * in a long-running service.
     */
    private fun scanScreenForLinks() {
        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs < SCAN_THROTTLE_MS) {
            return // Throttle: skip this scan
        }
        lastScanTimeMs = now

        val root = rootInActiveWindow ?: return
        val allText = StringBuilder()

        try {
            collectTextFromNode(root, allText, maxDepth = MAX_NODE_DEPTH)
            val textContent = allText.toString()

            if (textContent.isNotEmpty()) {
                val urls = extractUrls(textContent)
                if (urls.isNotEmpty()) {
                    // FIX: Replace stale links instead of only adding new ones.
                    // Previously, links accumulated forever — navigating away from a page
                    // kept showing the old page's links. Now we replace the entire list
                    // on each successful scan, so only currently-visible links are shown.
                    ScreenLinkStore.replaceLinks(urls)
                } else {
                    // No URLs found on current screen — clear stale links
                    ScreenLinkStore.clearAll()
                }
            }
        } catch (e: Exception) {
            // Node traversal can throw SecurityException or other exceptions
            // on some devices — ignore silently
        } finally {
            // FIX: Recycle the root node to prevent native memory leaks.
            // AccessibilityNodeInfo objects hold references to native memory
            // that must be explicitly released. In a continuously-running
            // AccessibilityService, failing to recycle causes steady memory growth.
            root.recycle()
        }
    }

    /**
     * Recursively collects text from an accessibility node and its children.
     * Limits depth and text length to prevent performance issues.
     *
     * FIX: Added maxDepth parameter to prevent StackOverflowError on
     * deeply nested view hierarchies (some apps have 100+ depth levels).
     *
     * FIX: Recycle child AccessibilityNodeInfo objects after use.
     * Previously, child nodes obtained via getChild() were never recycled.
     * AccessibilityNodeInfo holds references to native memory that must be
     * explicitly released. In a continuously-running AccessibilityService,
     * failing to recycle causes steady native memory growth (leak).
     */
    private fun collectTextFromNode(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        currentDepth: Int = 0,
        maxDepth: Int = MAX_NODE_DEPTH
    ) {
        // Safety limit: don't collect more than MAX_TEXT_LENGTH chars
        if (builder.length > MAX_TEXT_LENGTH) return

        // FIX: Depth limit to prevent StackOverflow on deep hierarchies
        if (currentDepth > maxDepth) return

        // Get text from this node
        node.text?.let { text ->
            builder.append(text).append(" ")
        }

        // Get content description if available
        node.contentDescription?.let { desc ->
            if (desc.isNotEmpty() && desc != node.text?.toString()) {
                builder.append(desc).append(" ")
            }
        }

        // Recurse into children
        // FIX: Recycle each child node after processing to prevent native memory leaks.
        // AccessibilityNodeInfo objects hold native binder references that leak if not recycled.
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        collectTextFromNode(child, builder, currentDepth + 1, maxDepth)
                    } finally {
                        child.recycle()
                    }
                }
            } catch (e: Exception) {
                // Child access can fail — skip this child
            }
        }
    }

    /**
     * Extracts URLs from a text string using regex.
     * Supports http://, https://, ftp:// and www. patterns.
     *
     * FIX: Removed redundant (?i) inline flag since RegexOption.IGNORE_CASE
     * already makes the entire pattern case-insensitive. Having both was
     * confusing and redundant.
     */
    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex(
            """\b((?:https?://|ftp://|www\.)[^\s<>\[\]{}'"`|\\^~]+)""",
            RegexOption.IGNORE_CASE
        )
        return urlRegex.findAll(text)
            .map { match ->
                var url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
                if (url.startsWith("www.", ignoreCase = true)) {
                    url = "https://$url"
                }
                url
            }
            .filter { it.length > 7 }
            .distinct()
            .toList()
    }

    companion object {
        // Minimum time between scans in milliseconds
        private const val SCAN_THROTTLE_MS = 1000L

        // Maximum text length to collect from accessibility tree
        private const val MAX_TEXT_LENGTH = 50000

        // Maximum depth for accessibility node tree traversal
        private const val MAX_NODE_DEPTH = 50
    }
}
