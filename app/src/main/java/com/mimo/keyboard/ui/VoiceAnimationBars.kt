package com.mimo.keyboard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Animated voice recording bars.
 * FIX #3: Uses a single InfiniteTransition shared across all bars
 * instead of 25 separate ones, reducing CPU overhead.
 */
@Composable
fun VoiceAnimationBars(
    modifier: Modifier = Modifier
) {
    // Single shared transition for all bars
    val infiniteTransition = rememberInfiniteTransition(label = "voice_bars")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(25) { index ->
            val barHeight by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = 32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400,
                        delayMillis = index * 40
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .width(4.dp)
                    .height(barHeight.dp)
                    .background(
                        color = HorizonColors.Accent,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}
