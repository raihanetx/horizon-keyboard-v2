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
import androidx.compose.ui.platform.LocalDensity
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
 * FIX: Key styling now uses solid bulky backgrounds with proper padding
 * instead of thin-line boxes that felt insubstantial. Keys now have:
 * - Proper internal padding so text doesn't touch edges
 * - Solid background fills with subtle border for depth
 * - Better visual weight with increased height and rounded corners
 * - Clear pressed/unpressed state differentiation
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

    var showAlternatives by remember { mutableStateOf(false) }
    var isLongPressTriggered by remember { mutableStateOf(false) }
    var consumeNextClick by remember { mutableStateOf(false) }

    // Key repeat for backspace — repeatedly fires delete while held.
    if (keyDef.style == KeyStyle.BACKSPACE) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(longPressDelayMs + 100L)
                isLongPressTriggered = true
                consumeNextClick = true
                while (isActive) {
                    onPress(KeyAction.Backspace)
                    delay(REPEAT_INTERVAL_MS)
                }
            } else {
                isLongPressTriggered = false
            }
        }
    }

    // Long-press detection for key alternatives.
    if (keyDef.alternatives != null && keyDef.style != KeyStyle.BACKSPACE) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(longPressDelayMs)
                if (isActive) {
                    isLongPressTriggered = true
                    consumeNextClick = true
                    showAlternatives = true
                }
            } else {
                isLongPressTriggered = false
            }
        }
    }

    // Determine the key shape — bigger corner radius for bulky feel
    val keyShape = RoundedCornerShape(12.dp)

    // Background color with smooth animation — solid fills for bulky keys
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> HorizonColors.KeyPressed
            keyDef.style == KeyStyle.ENTER -> HorizonColors.Accent
            keyDef.style == KeyStyle.SPECIAL && isShiftActive -> HorizonColors.Accent
            keyDef.style == KeyStyle.SPECIAL || keyDef.style == KeyStyle.BACKSPACE -> HorizonColors.SpecialKeyBackground
            keyDef.style == KeyStyle.SPACE -> HorizonColors.SpaceKeyBackground
            else -> HorizonColors.KeyFillBackground
        },
        animationSpec = tween(durationMillis = 80),
        label = "key_bg"
    )

    // Border color — more visible border for better key definition
    val borderColor by animateColorAsState(
        targetValue = when {
            isPressed -> HorizonColors.KeyPressed.copy(alpha = 0.8f)
            keyDef.style == KeyStyle.ENTER -> HorizonColors.Accent.copy(alpha = 0.7f)
            keyDef.style == KeyStyle.SPECIAL && isShiftActive -> HorizonColors.Accent.copy(alpha = 0.7f)
            keyDef.style == KeyStyle.SPACE -> HorizonColors.BorderPrimary.copy(alpha = 0.3f)
            else -> HorizonColors.KeyBorderNormal
        },
        animationSpec = tween(durationMillis = 80),
        label = "key_border"
    )

    val fontSize = when (keyDef.style) {
        KeyStyle.SPACE -> 12.sp
        KeyStyle.ENTER -> 13.sp
        KeyStyle.BACKSPACE -> 20.sp
        KeyStyle.SPECIAL -> 15.sp
        else -> 20.sp
    }

    val fontWeight = when (keyDef.style) {
        KeyStyle.ENTER -> FontWeight.Bold
        KeyStyle.SPACE -> FontWeight.Medium
        KeyStyle.SPECIAL -> FontWeight.SemiBold
        else -> FontWeight.Medium
    }

    // Key height — base 52dp with multiplier, giving more vertical bulk
    val keyHeightDp = (52 * keyHeightMultiplier)

    Box(modifier = modifier) {
        // Main key — solid bulky box with prominent fill and border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeightDp.dp)
                .then(
                    if (isPressed) Modifier.offset(y = 1.dp) else Modifier
                )
                .shadow(
                    elevation = if (isPressed) 1.dp else 4.dp,
                    shape = keyShape,
                    ambientColor = HorizonColors.KeyShadow,
                    spotColor = HorizonColors.KeyShadow
                )
                .clip(keyShape)
                .background(color = backgroundColor, shape = keyShape)
                .border(
                    width = if (isPressed) 1.dp else 1.5.dp,
                    color = borderColor,
                    shape = keyShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (consumeNextClick) {
                            consumeNextClick = false
                            return@clickable
                        }
                        onPress(keyDef.action)
                    }
                )
                .padding(
                    horizontal = when (keyDef.style) {
                        KeyStyle.SPACE -> 12.dp
                        KeyStyle.ENTER -> 8.dp
                        KeyStyle.SPECIAL -> 6.dp
                        else -> 6.dp
                    },
                    vertical = when (keyDef.style) {
                        KeyStyle.SPACE -> 10.dp
                        KeyStyle.ENTER -> 8.dp
                        else -> 8.dp
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    keyDef.style == KeyStyle.SPACE -> "SPACE"
                    else -> keyDef.label
                },
                color = when {
                    keyDef.style == KeyStyle.ENTER -> HorizonColors.TextPrimary
                    keyDef.style == KeyStyle.SPECIAL && isShiftActive -> HorizonColors.TextPrimary
                    else -> HorizonColors.TextPrimary
                },
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = if (keyDef.style == KeyStyle.SPACE) 2.sp else 0.sp
            )
        }

        // Long-press alternatives popup
        if (showAlternatives && keyDef.alternatives != null) {
            val density = LocalDensity.current
            val popupOffsetY = with(density) { (-12).dp.roundToPx() }

            Popup(
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    showAlternatives = false
                    isLongPressTriggered = false
                    consumeNextClick = false
                },
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, popupOffsetY)
            ) {
                AlternativesPopup(
                    alternatives = keyDef.alternatives,
                    onAlternativeSelected = { action ->
                        onPress(action)
                        showAlternatives = false
                        isLongPressTriggered = false
                        consumeNextClick = false
                    },
                    onDismiss = {
                        showAlternatives = false
                        isLongPressTriggered = false
                        consumeNextClick = false
                    }
                )
            }
        }
    }
}

/**
 * Popup showing alternative key options on long-press.
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
                elevation = 6.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = HorizonColors.KeyShadow,
                spotColor = HorizonColors.KeyShadow
            )
            .background(
                color = HorizonColors.KeyboardSurface,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 1.dp,
                color = HorizonColors.Accent.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alternatives.forEachIndexed { index, (label, action) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(HorizonColors.KeyGradientTop, RoundedCornerShape(7.dp))
                    .border(
                        width = 0.5.dp,
                        color = HorizonColors.BorderPrimary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(7.dp)
                    )
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
            if (index < alternatives.lastIndex) {
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
