package com.mimo.keyboard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.mimo.keyboard.ui.theme.HorizonColors
import kotlin.math.sin

/**
 * Modern voice recording indicator — sleek soundwave animation with
 * a pulsing mic orb and flowing frequency bars.
 *
 * The design features:
 * - A central pulsing orb that breathes with the "listening" state
 * - Symmetrical frequency bars that animate in a wave pattern
 * - Smooth gradient-like opacity transitions across the bars
 * - Compact height that still feels visually substantial
 */
@Composable
fun VoiceAnimationBars(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_anim")

    // Master wave phase — drives all bar animations
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Secondary wave for organic movement
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase2"
    )

    // Pulsing scale for the center orb
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Outer glow ring pulse
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    // Breathing alpha for the glow
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soundwave bars — 14 bars total (7 left + gap + 7 right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side bars (7 bars, innermost to outermost)
            for (i in 6 downTo 0) {
                val distFromCenter = (i + 1).toFloat()
                // Combine two sine waves for organic movement
                val wave1 = sin(wavePhase + i * 0.6f) * 0.5f + 0.5f
                val wave2 = sin(wavePhase2 + i * 0.4f + 1.2f) * 0.3f + 0.5f
                val combinedWave = (wave1 * 0.7f + wave2 * 0.3f)

                // Height: min 4dp, max scales with wave + distance from center
                val barHeight = 4f + combinedWave * (8f + distFromCenter * 3f)
                // Opacity fades toward edges
                val barAlpha = 0.4f + combinedWave * 0.5f

                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.5.dp)
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            color = HorizonColors.Accent.copy(alpha = barAlpha.coerceIn(0.3f, 0.9f)),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
            }

            // Center gap for the pulsing orb
            Spacer(modifier = Modifier.width(36.dp))

            // Right side bars (7 bars, innermost to outermost)
            for (i in 0..6) {
                val distFromCenter = (i + 1).toFloat()
                val wave1 = sin(wavePhase + i * 0.6f + 0.8f) * 0.5f + 0.5f
                val wave2 = sin(wavePhase2 + i * 0.4f + 2.0f) * 0.3f + 0.5f
                val combinedWave = (wave1 * 0.7f + wave2 * 0.3f)

                val barHeight = 4f + combinedWave * (8f + distFromCenter * 3f)
                val barAlpha = 0.4f + combinedWave * 0.5f

                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.5.dp)
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            color = HorizonColors.Accent.copy(alpha = barAlpha.coerceIn(0.3f, 0.9f)),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
            }
        }

        // Center glow ring — outer soft glow
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(glowScale)
                .clip(CircleShape)
                .background(
                    HorizonColors.Accent.copy(alpha = glowAlpha * 0.3f)
                )
        )

        // Center pulsing orb — solid core
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    HorizonColors.Accent.copy(alpha = 0.9f)
                )
        )

        // Inner bright dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(pulseScale * 0.95f)
                .clip(CircleShape)
                .background(
                    HorizonColors.Accent.copy(alpha = 1f)
                )
        )
    }
}
