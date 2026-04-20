package com.mimo.keyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.ui.theme.HorizonColors

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
    val weight: Float = 1f
)

/**
 * A single keyboard key composable.
 * The clickable covers the ENTIRE key area for reliable touch response.
 * BUG FIX: offset comes BEFORE clickable so touch target moves with visual.
 */
@Composable
fun KeyboardKey(
    keyDef: KeyDef,
    isShiftActive: Boolean = false,
    onPress: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

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

    Box(
        modifier = modifier
            .height(46.dp)
            // FIX #6: offset BEFORE shadow and clickable so touch area moves with visual
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
                onClick = { onPress(keyDef.action) }
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
}

@Composable
fun KeyboardRow(
    keys: List<KeyDef>,
    isShiftActive: Boolean = false,
    onPress: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
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
                    onPress = onPress
                )
            }
        }
    }
}
