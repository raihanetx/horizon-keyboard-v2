package com.mimo.keyboard.ui.panels

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.ui.theme.HorizonColors
import kotlinx.coroutines.delay

/**
 * Clipboard panel.
 * Maps to #p-clipboard in the HTML prototype.
 *
 * FIX: Now reads from the actual system clipboard instead of showing
 * a hardcoded "Hello, World!" clip. Shows current clipboard content
 * with tap-to-paste functionality. Periodically polls the clipboard
 * for changes while the panel is visible.
 *
 * Features:
 * - Reads current system clipboard content
 * - Tap a clip to paste it into the active input field
 * - Auto-refreshes when clipboard content changes
 * - Shows empty state when clipboard is empty
 */
@Composable
fun ClipboardPanel(
    viewModel: KeyboardViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Track clipboard clips — we store a history of past clips
    var clipboardHistory by remember { mutableStateOf(readClipboardHistory(clipboardManager)) }

    // Poll clipboard for changes while panel is visible
    LaunchedEffect(Unit) {
        while (true) {
            val currentClip = readCurrentClipboard(clipboardManager)
            val currentHistory = clipboardHistory

            // If clipboard has new content, add it to history
            if (currentClip.isNotEmpty() && (currentHistory.isEmpty() || currentHistory.first() != currentClip)) {
                clipboardHistory = (listOf(currentClip) + currentHistory)
                    .distinct()
                    .take(MAX_CLIPBOARD_ITEMS)
            }
            delay(CLIPBOARD_POLL_INTERVAL_MS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(10.dp)
    ) {
        // ── Header ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLIPBOARD",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.Accent,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Text(
                text = "${clipboardHistory.size} clip${if (clipboardHistory.size != 1) "s" else ""}",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = HorizonColors.TextExtraMuted,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Empty state ─────────────────────────────────────
        if (clipboardHistory.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No clips yet",
                    fontSize = 13.sp,
                    color = HorizonColors.TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Copy text to see it here",
                    fontSize = 10.sp,
                    color = HorizonColors.TextExtraMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Clips List ──────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            clipboardHistory.forEach { clipText ->
                ClipCard(
                    text = clipText,
                    onPaste = {
                        // FIX: Use InsertText instead of Character for multi-char paste.
                        // KeyAction.Character applies lowercase()/uppercase() based on shift,
                        // which corrupts pasted text like "Hello, World!" when shift is on.
                        viewModel?.onKeyPress(KeyAction.InsertText(clipText))
                    }
                )
            }
        }

        // ── Bottom tip ──────────────────────────────────────
        if (clipboardHistory.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "TAP to PASTE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = HorizonColors.TextExtraMuted,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * A single clip card showing clipboard text with tap-to-paste.
 */
@Composable
private fun ClipCard(
    text: String,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HorizonColors.KeyboardSurface)
            .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPaste
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clip icon
        Text(
            text = "\u2022",  // • bullet
            fontSize = 10.sp,
            color = HorizonColors.Accent,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp)
        )

        // Clip text
        Text(
            text = text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = HorizonColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Reads the current system clipboard text.
 */
private fun readCurrentClipboard(clipboardManager: ClipboardManager): String {
    return try {
        if (clipboardManager.hasPrimaryClip()) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.text?.toString() ?: ""
            } else {
                ""
            }
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

/**
 * Reads clipboard history from the current clip.
 * Android only provides the current clip, so we simulate history
 * by returning the current clip as the only item initially.
 */
private fun readClipboardHistory(clipboardManager: ClipboardManager): List<String> {
    val currentClip = readCurrentClipboard(clipboardManager)
    return if (currentClip.isNotEmpty()) {
        listOf(currentClip)
    } else {
        emptyList()
    }
}

private const val MAX_CLIPBOARD_ITEMS = 10
private const val CLIPBOARD_POLL_INTERVAL_MS = 1500L
