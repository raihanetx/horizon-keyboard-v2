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
import kotlin.math.sin

/**
 * Animated voice recording bars.
 *
 * FIX: Uses a single animated float and derives each bar's height
 * from a sine wave with phase offsets. This creates a smooth, synchronized
 * wave pattern where all bars animate at the same frequency but with
 * staggered phase, eliminating the previous bug where delayMillis
 * inside infiniteRepeatable caused bars to drift out of sync.
 */
@Composable
fun VoiceAnimationBars(
    modifier: Modifier = Modifier
) {
    // Single shared transition driving the wave
    val infiniteTransition = rememberInfiniteTransition(label = "voice_bars")

    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 25
        repeat(barCount) { index ->
            // Phase offset creates staggered wave effect
            val phaseOffset = index.toFloat() / barCount * 2f * Math.PI.toFloat()

            // Sine wave: animProgress drives the wave forward, phaseOffset staggers each bar
            // Result: 0.125 (min height = 4dp) to 1.0 (max height = 32dp)
            val waveValue = (sin((animProgress * 2f * Math.PI.toFloat()) + phaseOffset) + 1f) / 2f
            val barFraction = 0.125f + waveValue * 0.875f  // Range: 0.125 to 1.0
            val barHeight = (barFraction * 32f)

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
