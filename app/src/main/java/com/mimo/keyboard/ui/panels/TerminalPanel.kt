package com.mimo.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Terminal panel.
 * Maps to #p-terminal in the HTML prototype.
 * Shows a developer console with command output and input field.
 *
 * CRITICAL FIX: Cannot use BasicTextField here because it would trigger
 * a recursive IME request (keyboard opening inside keyboard).
 * Instead, we display a non-focusable text display that captures
 * key events from the keyboard's own InputConnection.
 */
@Composable
fun TerminalPanel(
    viewModel: KeyboardViewModel,
    modifier: Modifier = Modifier
) {
    // Terminal command buffer - accumulates typed text
    var commandBuffer by remember { mutableStateOf("") }
    val outputLines = remember { mutableStateListOf("Horizon Dev Terminal Ready...") }

    // Capture text from the ViewModel when terminal tab is active
    // BUG FIX #2: Always sync commandBuffer with textValue, even when empty.
    // Previously, deleting all text left stale content in the terminal display.
    LaunchedEffect(viewModel.textValue) {
        commandBuffer = viewModel.textValue
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(12.dp)
    ) {
        // Output area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            outputLines.forEach { line ->
                Text(
                    text = line,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HorizonColors.TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Input row with $ prompt (non-focusable display only)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HorizonColors.KeyboardSurface, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$",
                color = HorizonColors.TerminalGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp)
            )

            // Display typed text instead of using BasicTextField
            // This prevents recursive IME crash
            Text(
                text = commandBuffer.ifEmpty { "Type a command..." },
                color = if (commandBuffer.isEmpty()) HorizonColors.TextMuted else HorizonColors.TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )

            // Cursor blink indicator
            Text(
                text = "|",
                color = HorizonColors.Accent,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
