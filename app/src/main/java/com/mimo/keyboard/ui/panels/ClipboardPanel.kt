package com.mimo.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Clipboard panel.
 * Maps to #p-clipboard in the HTML prototype.
 * Shows saved clipboard clips in styled boxes.
 */
@Composable
fun ClipboardPanel(
    clips: List<String> = listOf("Hello, World!"),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Clips header box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HorizonColors.KeyboardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "CLIPS",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.TextExtraMuted,
                letterSpacing = 0.12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            clips.forEach { clip ->
                Text(
                    text = clip,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HorizonColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
