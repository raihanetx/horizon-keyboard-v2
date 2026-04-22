package com.mimo.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Terminal panel.
 * Maps to #p-terminal in the HTML prototype.
 *
 * Shows a simple terminal-style interface with command history.
 * Commands are entered via the keyboard and displayed in a scrollable area.
 */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier
) {
    var commandHistory by remember { mutableStateOf(listOf("MiMo Dev Terminal Ready...")) }
    var currentInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(10.dp)
    ) {
        // ── Header ──────────────────────────────────────────
        Text(
            text = "TERMINAL",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = HorizonColors.Accent,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // ── Output area ─────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(HorizonColors.KeyboardSurface)
                .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            commandHistory.forEach { line ->
                Text(
                    text = line,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HorizonColors.TerminalGreen,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Input row ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(HorizonColors.KeyboardSurface)
                .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HorizonColors.TerminalGreen,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (currentInput.isEmpty()) "Command..." else currentInput,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (currentInput.isEmpty()) HorizonColors.TextExtraMuted else HorizonColors.TextPrimary
            )
        }
    }
}
