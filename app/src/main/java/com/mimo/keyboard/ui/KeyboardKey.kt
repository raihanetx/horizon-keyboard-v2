package com.mimo.keyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.ui.theme.HorizonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class KeyStyle {
    NORMAL,
    SPECIAL,
    BACKSPACE,
    ENTER,
    SPACE
}

data class KeyDef(
    val label: String,
    val action: KeyAction,
    val style: KeyStyle = KeyStyle.NORMAL,
    val weight: Float = 1f,
    /**
     * FIX: Alternative keys shown on long-press.
     * Maps display label → KeyAction.
     * For example, 'e' could have alternatives for 'é', 'è', 'ë', 'ê'.
     * Null means no long-press alternatives.
     */
    val alternatives: List<Pair<String, KeyAction>>? = null
)

/**
 * A single keyboard key composable.
 * The clickable covers the ENTIRE key area for reliable touch response.
 *
 * FIX: Added long-press support for:
 * 1. Key alternatives (accents, symbols) shown as a popup
 * 2. Key repeat for backspace (hold to delete continuously)
 *
 * The pressed state visual effect:
 * - Unpressed: key has 2dp shadow (raised appearance)
 * - Pressed: key moves down 1dp, shadow reduces to 0dp (pressed in appearance)
 */
@Composable
fun KeyboardKey(
    keyDef: KeyDef,
    isShiftActive: Boolean = false,
    onPress: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    longPressDelayMs: Long = LONG_PRESS_DELAY_MS,
    keyHeightMultiplier: Float = 1.0f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // FIX: Track long-press state for popup and repeat
    var showAlternatives by remember { mutableStateOf(false) }
    var isLongPressTriggered by remember { mutableStateOf(false) }

    // FIX: Key repeat for backspace — repeatedly fires delete while held.
    // Uses longPressDelayMs from settings instead of hardcoded constant.
    if (keyDef.style == KeyStyle.BACKSPACE) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                // Initial delay before repeat starts
                delay(longPressDelayMs + 100L)  // Slightly longer than long-press for repeat
                isLongPressTriggered = true
                // Continuous repeat while pressed
                while (isActive) {
                    onPress(KeyAction.Backspace)
                    delay(REPEAT_INTERVAL_MS)
                }
            } else {
                isLongPressTriggered = false
            }
        }
    }

    // FIX: Long-press detection for key alternatives.
    // Root cause of popup-dismiss bug: The old code set showAlternatives = false
    // after a 50ms delay when isPressed became false. This meant the popup was
    // dismissed almost instantly when the user lifted their finger — they never
    // got a chance to tap an alternative.
    //
    // The fix: When isPressed becomes false, we only reset isLongPressTriggered
    // (so the main key's onClick doesn't fire the primary action). We do NOT
    // dismiss the popup here — the Popup composable's own dismiss handling
    // (dismissOnClickOutside, dismissOnBackPress, onDismissRequest) manages
    // the popup lifecycle. The user can lift their finger after long-pressing
    // and then tap an alternative at their leisure.
    if (keyDef.alternatives != null && keyDef.style != KeyStyle.BACKSPACE) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(longPressDelayMs)
                if (isActive) {
                    isLongPressTriggered = true
                    showAlternatives = true
                }
            } else {
                // Only reset the long-press flag so the main key's onClick
                // doesn't fire the primary action. The popup stays visible
                // until the user taps an alternative or dismisses it.
                isLongPressTriggered = false
            }
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> HorizonColors.KeyPressed
            keyDef.style == KeyStyle.ENTER -> HorizonColors.Accent
            keyDef.style == KeyStyle.SPECIAL && isShiftActive -> HorizonColors.Accent
            keyDef.style == KeyStyle.SPECIAL || keyDef.style == KeyStyle.BACKSPACE -> HorizonColors.SpecialKeyBackground
            else -> HorizonColors.KeyGradientTop
        },
        animationSpec = tween(durationMillis = 80),
        label = "key_bg"
    )

    val fontSize = when (keyDef.style) {
        KeyStyle.SPACE -> 11.sp
        KeyStyle.ENTER -> 12.sp
        else -> 18.sp
    }

    val fontWeight = when (keyDef.style) {
        KeyStyle.ENTER -> FontWeight.Bold
        KeyStyle.SPACE -> FontWeight.Medium
        else -> FontWeight.Medium
    }

    // FIX: Wrapper Box for popup positioning.
    // Note: Compose children are NOT clipped by their parent by default,
    // so the AlternativesPopup can overflow above the key. However, the
    // KeyboardRow's Row and the parent Column in QwertyKeyboard may clip.
    // The popup is positioned with offset(y = -8.dp) to appear above the key.
    Box(modifier = modifier) {
        // Main key
        // FIX: Use keyHeightMultiplier from settings instead of hardcoded 46.dp.
        // The keyHeightMultiplier setting was defined in KeyboardSettings but
        // never wired to the actual key height — changing it had no effect.
        Box(
            modifier = Modifier
                .height((46 * keyHeightMultiplier).dp)
                .then(
                    if (isPressed) Modifier.offset(y = 1.dp) else Modifier
                )
                .shadow(
                    elevation = if (isPressed) 0.dp else 2.dp,
                    shape = RoundedCornerShape(8.dp),
                    ambientColor = HorizonColors.KeyShadow,
                    spotColor = HorizonColors.KeyShadow
                )
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        // FIX: For backspace, only fire on short press (not long press)
                        if (keyDef.style == KeyStyle.BACKSPACE && isLongPressTriggered) {
                            return@clickable // Repeat is handled by LaunchedEffect
                        }
                        // FIX: For keys with alternatives, if long-press was triggered,
                        // don't fire the main action (user will tap an alternative)
                        if (isLongPressTriggered && keyDef.alternatives != null) {
                            return@clickable
                        }
                        onPress(keyDef.action)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    keyDef.style == KeyStyle.SPACE -> "SPACE"
                    else -> keyDef.label
                },
                color = HorizonColors.TextPrimary,
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = if (keyDef.style == KeyStyle.SPACE) 2.sp else 0.sp
            )
        }

        // FIX: Long-press alternatives popup — uses Popup composable to render
        // ABOVE the keyboard layout, preventing clipping by parent containers.
        // Previously, the popup was an inline Box that could be clipped by the
        // parent Row/Column for top-row keys where vertical space is limited.
        if (showAlternatives && keyDef.alternatives != null) {
            Popup(
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    showAlternatives = false
                    isLongPressTriggered = false
                },
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, -8)
            ) {
                AlternativesPopup(
                    alternatives = keyDef.alternatives,
                    onAlternativeSelected = { action ->
                        onPress(action)
                        showAlternatives = false
                        isLongPressTriggered = false
                    },
                    onDismiss = {
                        showAlternatives = false
                        isLongPressTriggered = false
                    }
                )
            }
        }
    }
}

/**
 * Popup showing alternative key options on long-press.
 * Rendered inside a Popup composable so it appears above the keyboard layout
 * without being clipped by parent containers.
 */
@Composable
private fun AlternativesPopup(
    alternatives: List<Pair<String, KeyAction>>,
    onAlternativeSelected: (KeyAction) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = HorizonColors.KeyShadow,
                spotColor = HorizonColors.KeyShadow
            )
            .background(
                color = HorizonColors.KeyboardSurface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = HorizonColors.Accent.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alternatives.forEach { (label, action) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HorizonColors.KeyGradientTop, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onAlternativeSelected(action) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = HorizonColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (label != alternatives.last().first) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<KeyDef>,
    isShiftActive: Boolean = false,
    onPress: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    longPressDelayMs: Long = LONG_PRESS_DELAY_MS,
    keyHeightMultiplier: Float = 1.0f
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        keys.forEach { keyDef ->
            Box(modifier = Modifier.weight(keyDef.weight)) {
                KeyboardKey(
                    keyDef = keyDef,
                    isShiftActive = isShiftActive,
                    onPress = onPress,
                    longPressDelayMs = longPressDelayMs,
                    keyHeightMultiplier = keyHeightMultiplier
                )
            }
        }
    }
}

// Timing constants for key interactions
private const val LONG_PRESS_DELAY_MS = 300L
private const val REPEAT_INTERVAL_MS = 50L
