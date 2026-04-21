package com.mimo.keyboard.ui.panels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.ScreenLinkStore
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Magic Button Panel (Terminal tab)
 *
 * This is the REAL feature: when a browser terminal shows a link you
 * cannot copy or tap, this panel reads the screen via Accessibility
 * Service, finds URLs, and shows them as tappable cards.
 *
 * Two actions per link:
 * - TAP the link → COPIES to clipboard (so you can paste in browser address bar)
 * - TAP the arrow → OPENS directly in browser
 *
 * If Accessibility is not enabled, shows instructions to enable it.
 *
 * FIX: Improved the polling loop for ScreenLinkStore. The old approach:
 * 1. Polled every 500ms with unconditional state assignment
 * 2. Triggered recomposition every 500ms even when nothing changed
 *
 * The new approach:
 * 1. Only updates Compose state when the value actually changes
 * 2. Increased poll interval from 500ms to 1000ms (links don't change that fast)
 * 3. LaunchedEffect is auto-cancelled when composable leaves composition (tab switch)
 */
@Composable
fun TerminalPanel(
    viewModel: KeyboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // FIX: Polling approach for ScreenLinkStore (which is NOT a Compose State).
    // snapshotFlow won't work here because ScreenLinkStore.links is not observed
    // by Compose's snapshot system. Instead, we use a LaunchedEffect with delay
    // but only trigger recomposition when the value actually changes.
    var screenLinks by remember { mutableStateOf(ScreenLinkStore.links) }
    var isAccessibilityActive by remember { mutableStateOf(ScreenLinkStore.isServiceActive) }

    // Poll ScreenLinkStore periodically. The LaunchedEffect is automatically
    // cancelled when the composable leaves composition (when user switches tabs),
    // so this won't run forever. We only update state when the value changes,
    // avoiding unnecessary recompositions.
    LaunchedEffect(Unit) {
        while (true) {
            val currentLinks = ScreenLinkStore.links
            val currentActive = ScreenLinkStore.isServiceActive
            // Only trigger recomposition if values actually changed
            if (currentLinks != screenLinks) {
                screenLinks = currentLinks
            }
            if (currentActive != isAccessibilityActive) {
                isAccessibilityActive = currentActive
            }
            delay(POLL_INTERVAL_MS)
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
                text = "MAGIC BUTTON",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.Accent,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Green/Red dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isAccessibilityActive) HorizonColors.TerminalGreen
                            else HorizonColors.Error
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isAccessibilityActive) "ACTIVE" else "OFF",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAccessibilityActive) HorizonColors.TerminalGreen else HorizonColors.Error,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Accessibility not enabled warning ───────────────
        if (!isAccessibilityActive) {
            AccessibilityWarning(context = context)
        }

        // ── No links found ─────────────────────────────────
        if (isAccessibilityActive && screenLinks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scanning screen for links...",
                    fontSize = 13.sp,
                    color = HorizonColors.TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Open a page with a link to detect it",
                    fontSize = 10.sp,
                    color = HorizonColors.TextExtraMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Detected Links List ─────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            screenLinks.forEach { url ->
                DetectedLinkCard(
                    url = url,
                    onCopy = { copyToClipboard(context, url) },
                    onOpen = { openUrl(context, url) }
                )
            }
        }

        // ── Bottom tip ──────────────────────────────────────
        if (screenLinks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "TAP link = COPY  |  TAP arrow = OPEN",
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
 * Warning shown when Accessibility Service is not enabled.
 * Provides a button to open Accessibility Settings.
 */
@Composable
private fun AccessibilityWarning(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HorizonColors.Error.copy(alpha = 0.12f))
            .padding(10.dp)
    ) {
        Text(
            text = "ACCESSIBILITY NOT ENABLED",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = HorizonColors.Error,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "To detect links on screen, enable Horizon Keyboard in Accessibility Settings.",
            fontSize = 11.sp,
            color = HorizonColors.TextMuted,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(HorizonColors.Error)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to general settings
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OPEN ACCESSIBILITY SETTINGS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = HorizonColors.TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * A single detected link shown as a card.
 * - Tap the URL text → copies to clipboard
 * - Tap the arrow → opens in browser
 */
@Composable
private fun DetectedLinkCard(
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HorizonColors.KeyboardSurface)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // URL text (tap to COPY)
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = HorizonColors.Accent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
            },
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCopy
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Open in browser arrow button
        Text(
            text = "\u2197",  // ↗ arrow
            fontSize = 18.sp,
            color = HorizonColors.TerminalGreen,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen
                )
                .padding(start = 6.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                .size(28.dp)
                .wrapContentSize(Alignment.Center)
        )
    }
}

/**
 * Copies a URL to the system clipboard.
 */
private fun copyToClipboard(context: Context, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("URL", url)
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        // Clipboard access may fail on some devices — ignore
    }
}

/**
 * Opens a URL in the device's default browser.
 */
private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // No browser available or invalid URL — ignore
    }
}

// Polling interval for checking ScreenLinkStore updates
private const val POLL_INTERVAL_MS = 1000L
