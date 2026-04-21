package com.mimo.keyboard.ui.panels

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyboardSettings
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Settings panel.
 * Maps to #p-settings in the HTML prototype.
 *
 * FIX: Now has interactive toggle switches for all settings, backed by
 * SharedPreferences for persistence across app restarts. Previously showed
 * only hardcoded static text with no interactivity.
 *
 * Settings sections:
 * - FEEDBACK: Haptics, Sound
 * - INPUT: Auto-capitalize, Auto-space, Show suggestions
 * - APPEARANCE: Key height
 */
@Composable
fun SettingsPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { KeyboardSettings(context) }

    // Read settings as state so changes trigger recomposition
    var isHapticsEnabled by remember { mutableStateOf(settings.isHapticsEnabled) }
    var isSoundEnabled by remember { mutableStateOf(settings.isSoundEnabled) }
    var isAutoCapitalize by remember { mutableStateOf(settings.isAutoCapitalize) }
    var isAutoSpace by remember { mutableStateOf(settings.isAutoSpace) }
    var isShowSuggestions by remember { mutableStateOf(settings.isShowSuggestions) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ──────────────────────────────────────────
        Text(
            text = "SETTINGS",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = HorizonColors.Accent,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        // ── FEEDBACK section ────────────────────────────────
        SettingsSection(title = "FEEDBACK") {
            SettingsToggle(
                label = "Haptic Feedback",
                description = "Vibrate on key press",
                isOn = isHapticsEnabled,
                onToggle = {
                    isHapticsEnabled = !isHapticsEnabled
                    settings.isHapticsEnabled = isHapticsEnabled
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggle(
                label = "Key Press Sound",
                description = "Play sound on key press",
                isOn = isSoundEnabled,
                onToggle = {
                    isSoundEnabled = !isSoundEnabled
                    settings.isSoundEnabled = isSoundEnabled
                }
            )
        }

        // ── INPUT section ───────────────────────────────────
        SettingsSection(title = "INPUT") {
            SettingsToggle(
                label = "Auto-Capitalize",
                description = "Capitalize first letter of sentences",
                isOn = isAutoCapitalize,
                onToggle = {
                    isAutoCapitalize = !isAutoCapitalize
                    settings.isAutoCapitalize = isAutoCapitalize
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggle(
                label = "Auto-Space",
                description = "Insert space after punctuation",
                isOn = isAutoSpace,
                onToggle = {
                    isAutoSpace = !isAutoSpace
                    settings.isAutoSpace = isAutoSpace
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggle(
                label = "Show Suggestions",
                description = "Display word suggestions while typing",
                isOn = isShowSuggestions,
                onToggle = {
                    isShowSuggestions = !isShowSuggestions
                    settings.isShowSuggestions = isShowSuggestions
                }
            )
        }

        // ── ABOUT section ───────────────────────────────────
        SettingsSection(title = "ABOUT") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HorizonColors.TextPrimary
                )
                Text(
                    text = "1.3.0",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HorizonColors.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Horizon Keyboard — A minimal, dark keyboard with Magic Button for link detection.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = HorizonColors.TextExtraMuted,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * A settings section with a label and content.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HorizonColors.KeyboardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(10.dp))
            .padding(12.dp),
        content = content
    ) {
        // Section title
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = HorizonColors.TextExtraMuted,
            letterSpacing = 0.12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Section content
        content()
    }
}

/**
 * A toggle switch row for a single setting.
 * Shows label, description, and a visual toggle indicator.
 */
@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    isOn: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label and description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = HorizonColors.TextPrimary
            )
            Text(
                text = description,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = HorizonColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Toggle switch visual
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (isOn) HorizonColors.Accent else HorizonColors.BorderPrimary
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Toggle knob
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .offset(x = if (isOn) 19.dp else 1.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(HorizonColors.TextPrimary)
            )
        }
    }
}
