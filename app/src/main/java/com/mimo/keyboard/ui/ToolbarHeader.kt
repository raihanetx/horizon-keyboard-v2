package com.mimo.keyboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyboardTab
import com.mimo.keyboard.R
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Toolbar header at the top of the keyboard area.
 * Maps to the .tb CSS element with 5 tab buttons:
 * Keyboard | Translate | Clipboard | Voice | Settings
 */
@Composable
fun ToolbarHeader(
    currentTab: KeyboardTab,
    isVoiceActive: Boolean = false,
    onTabSelected: (KeyboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(HorizonColors.KeyboardSurface)
    ) {
        // Voice overlay (replaces toolbar when active)
        AnimatedVisibility(
            visible = isVoiceActive,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.fillMaxSize()
        ) {
            VoiceOverlayContent(
                onClose = { onTabSelected(KeyboardTab.KEYBOARD) }
            )
        }

        // Normal toolbar buttons (6 tabs matching HTML)
        AnimatedVisibility(
            visible = !isVoiceActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Keyboard
                Box(modifier = Modifier.weight(1f)) {
                    ToolbarButton(
                        iconRes = R.drawable.ic_keyboard,
                        isSelected = currentTab == KeyboardTab.KEYBOARD,
                        contentDescription = "Keyboard",
                        onClick = { onTabSelected(KeyboardTab.KEYBOARD) }
                    )
                }
                // 2. Translate
                Box(modifier = Modifier.weight(1f)) {
                    ToolbarButton(
                        iconRes = R.drawable.ic_translate,
                        isSelected = currentTab == KeyboardTab.TRANSLATE,
                        contentDescription = "Translate",
                        onClick = { onTabSelected(KeyboardTab.TRANSLATE) }
                    )
                }
                // 3. Clipboard
                Box(modifier = Modifier.weight(1f)) {
                    ToolbarButton(
                        iconRes = R.drawable.ic_clipboard,
                        isSelected = currentTab == KeyboardTab.CLIPBOARD,
                        contentDescription = "Clipboard",
                        onClick = { onTabSelected(KeyboardTab.CLIPBOARD) }
                    )
                }
                // 4. Voice
                Box(modifier = Modifier.weight(1f)) {
                    ToolbarButton(
                        iconRes = R.drawable.ic_mic,
                        isSelected = currentTab == KeyboardTab.VOICE,
                        contentDescription = "Voice",
                        onClick = { onTabSelected(KeyboardTab.VOICE) }
                    )
                }
                // 5. Settings
                Box(modifier = Modifier.weight(1f)) {
                    ToolbarButton(
                        iconRes = R.drawable.ic_settings,
                        isSelected = currentTab == KeyboardTab.SETTINGS,
                        contentDescription = "Settings",
                        onClick = { onTabSelected(KeyboardTab.SETTINGS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    iconRes: Int,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val tintColor = if (isSelected) HorizonColors.Accent else HorizonColors.TextMuted

    Box(
        modifier = Modifier
            .height(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Voice recording overlay that replaces the toolbar.
 * Maps to the .v-overlay element with animated bars.
 */
@Composable
private fun VoiceOverlayContent(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Language label
        Text(
            text = "EN",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = HorizonColors.Accent
        )

        // Animated voice bars
        VoiceAnimationBars(
            modifier = Modifier.weight(1f)
        )

        // Close button
        Text(
            text = "\u2716",
            color = HorizonColors.Error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .padding(5.dp)
        )
    }
}
