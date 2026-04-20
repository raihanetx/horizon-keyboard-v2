package com.mimo.keyboard.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Suggestion bar that appears above the keyboard when typing.
 * Maps to the .sb CSS element with .sw suggestion word buttons.
 *
 * In the HTML prototype, this slides up with animation when
 * State.val.length > 0 && State.tab === 'keyboard'.
 */
@Composable
fun SuggestionBar(
    isVisible: Boolean,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
        ),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(HorizonColors.Background),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            suggestions.forEachIndexed { index, word ->
                SuggestionWord(
                    word = word,
                    isPrimary = index == 0,
                    isLast = index == suggestions.lastIndex,
                    onClick = { onSuggestionClick(word) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SuggestionWord(
    word: String,
    isPrimary: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(
                if (!isLast) Modifier.padding(end = 1.dp) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = word,
            color = if (isPrimary) HorizonColors.Accent else HorizonColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Border separator between words (matching CSS: border-right: 1px solid #3a3a3c)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .height(24.dp)
                    .background(HorizonColors.BorderPrimary)
            )
        }
    }
}
