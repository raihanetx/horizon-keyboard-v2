package com.mimo.keyboard.ui

import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyboardSettings
import com.mimo.keyboard.KeyboardTab
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.MiMoInputMethodService
import com.mimo.keyboard.MiMoSettingsActivity
import com.mimo.keyboard.VoiceRecognizer
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
    modifier: Modifier = Modifier,
    viewModel: KeyboardViewModel? = null,
    voiceRecognizer: VoiceRecognizer? = null,
    settings: KeyboardSettings? = null,
    inputMethodService: MiMoInputMethodService? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(HorizonColors.KeyboardSurface)
    ) {
        // Use simple conditional rendering instead of AnimatedVisibility.
        // AnimatedVisibility with two overlapping children can intercept
        // touch events even when "invisible", making toolbar buttons
        // unresponsive in the InputMethodService touch dispatch system.
        if (isVoiceActive) {
            VoiceOverlayContent(
                viewModel = viewModel,
                voiceRecognizer = voiceRecognizer,
                settings = settings,
                inputMethodService = inputMethodService,
                onClose = { 
                    voiceRecognizer?.stopListening()
                    onTabSelected(KeyboardTab.KEYBOARD) 
                }
            )
        } else {
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
            .fillMaxWidth()
            .fillMaxHeight()
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
 * Now includes:
 * - Language selector (EN / BN) to choose voice recognition language
 * - Pulsing mic animation with waveform bars
 * - Start/stop voice listening
 * - Live recognized text preview
 * - Close button
 */
@Composable
private fun VoiceOverlayContent(
    viewModel: KeyboardViewModel?,
    voiceRecognizer: VoiceRecognizer?,
    settings: KeyboardSettings?,
    inputMethodService: MiMoInputMethodService? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val currentVoiceLang = viewModel?.voiceLanguage ?: "en-US"
    val isListening = viewModel?.isVoiceListening ?: false
    val recognizedText = viewModel?.voiceRecognizedText ?: ""

    // Check microphone permission
    val hasMicPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Language selector — tap to toggle EN ↔ BN
        val currentLangInfo = KeyboardSettings.VOICE_LANGUAGES.find { it.locale == currentVoiceLang }
            ?: KeyboardSettings.VOICE_LANGUAGES.first()

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isListening) HorizonColors.Accent.copy(alpha = 0.2f)
                    else HorizonColors.KeyboardSurface
                )
                .border(
                    width = 1.dp,
                    color = if (isListening) HorizonColors.Accent.copy(alpha = 0.5f)
                    else HorizonColors.BorderPrimary,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isListening) {
                            // Toggle language
                            val currentIndex = KeyboardSettings.VOICE_LANGUAGES.indexOfFirst { it.locale == currentVoiceLang }
                            val nextIndex = (currentIndex + 1) % KeyboardSettings.VOICE_LANGUAGES.size
                            viewModel?.setVoiceLanguage(KeyboardSettings.VOICE_LANGUAGES[nextIndex].locale)
                        }
                    }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentLangInfo.shortCode,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isListening) HorizonColors.Accent else HorizonColors.TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }

        // Animated voice bars (center)
        VoiceAnimationBars(
            modifier = Modifier.weight(1f)
        )

        // Mic button — start/stop listening
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isListening) HorizonColors.Error.copy(alpha = 0.2f)
                    else HorizonColors.Accent.copy(alpha = 0.2f)
                )
                .border(
                    width = 1.dp,
                    color = if (isListening) HorizonColors.Error.copy(alpha = 0.5f)
                    else HorizonColors.Accent.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!hasMicPermission) {
                            // Request mic permission through the service
                            inputMethodService?.onRequestMicPermission?.invoke()
                            return@clickable
                        }
                        if (isListening) {
                            voiceRecognizer?.stopListening()
                        } else {
                            voiceRecognizer?.startListening()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isListening) "■" else "●",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isListening) HorizonColors.Error else HorizonColors.Accent,
                fontFamily = FontFamily.Monospace
            )
        }

        // Close button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HorizonColors.KeyboardSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2716",
                fontSize = 12.sp,
                color = HorizonColors.TextMuted,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
