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
 * Translate panel.
 * Maps to #p-translate in the HTML prototype.
 * Shows source text and translation result in two styled boxes.
 */
@Composable
fun TranslatePanel(
    sourceText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Source text box
        PanelTextBox(
            label = "Translation Source",
            content = sourceText.ifEmpty { "..." },
            borderColor = HorizonColors.BorderPrimary,
            labelColor = HorizonColors.TextExtraMuted
        )

        // Result box (accent border)
        PanelTextBox(
            label = "Result",
            content = "Ready.",
            borderColor = HorizonColors.Accent,
            labelColor = HorizonColors.Accent
        )
    }
}

@Composable
internal fun PanelTextBox(
    label: String,
    content: String,
    borderColor: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HorizonColors.KeyboardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = labelColor,
            letterSpacing = 0.12.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = HorizonColors.TextPrimary
        )
    }
}
