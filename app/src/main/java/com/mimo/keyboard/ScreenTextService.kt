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
 */
class ScreenTextService : AccessibilityService() {

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
     */
    private fun scanScreenForLinks() {
        val root = rootInActiveWindow ?: return
        val allText = StringBuilder()

        try {
            collectTextFromNode(root, allText)
            val textContent = allText.toString()

            if (textContent.isNotEmpty()) {
                val urls = extractUrls(textContent)
                if (urls.isNotEmpty()) {
                    ScreenLinkStore.addLinks(urls)
                }
            }
        } catch (e: Exception) {
            // Node traversal can throw SecurityException or other exceptions
            // on some devices — ignore silently
        }
    }

    /**
     * Recursively collects text from an accessibility node and its children.
     * Limits depth and text length to prevent performance issues.
     */
    private fun collectTextFromNode(node: AccessibilityNodeInfo, builder: StringBuilder) {
        // Safety limit: don't collect more than 50000 chars
        if (builder.length > 50000) return

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
        for (i in 0 until node.childCount) {
            try {
                node.getChild(i)?.let { child ->
                    collectTextFromNode(child, builder)
                }
            } catch (e: Exception) {
                // Child access can fail — skip this child
            }
        }
    }

    /**
     * Extracts URLs from a text string using regex.
     * Supports http://, https://, ftp:// and www. patterns.
     */
    private fun extractUrls(text: String): List<String> {
        val urlRegex = Regex(
            """(?i)\b((?:https?://|ftp://|www\.)[^\s<>\[\]{}'"`|\\^~]+)""",
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
}
