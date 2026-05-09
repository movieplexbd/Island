package com.dynamicisland.widgets

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║      WAVEFORM VISUALIZER — v2.0                     ║
 * ║  Music · Voice · Call waveforms (Canvas-rendered)   ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ── 1. FULL FLOATING WAVEFORM ─────────────────────────────────────────────────

/**
 * Reactive waveform using Canvas. Simulates FFT-style bars that
 * animate with a realistic audio pattern when [isActive].
 *
 * @param barCount   Number of frequency bands (default 28)
 * @param color      Bar fill color
 * @param isActive   If false, bars go flat
 * @param height     Total height of the waveform
 */
@Composable
fun FloatingWaveform(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    color: Color = Color(0xFF007AFF),
    barCount: Int = 28,
    height: Dp = 32.dp
) {
    // Each bar has its own infinite animation with staggered timing
    val animations = remember(barCount) {
        Array(barCount) { i ->
            // Perlin-like frequency distribution — center bars higher
            val centerBias = 1f - abs(i - barCount / 2f) / (barCount / 2f)
            Triple(
                /* minH */ 0.05f + centerBias * 0.1f,
                /* maxH */ 0.2f + centerBias * 0.7f + (i % 3) * 0.1f,
                /* dur  */ 200 + (i % 7) * 80
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "waveform")

    val barHeights = animations.mapIndexed { i, (minH, maxH, dur) ->
        transition.animateFloat(
            initialValue = minH,
            targetValue  = if (isActive) maxH else minH,
            animationSpec = if (isActive) {
                infiniteRepeatable(tween(dur, easing = EaseInOutSine), RepeatMode.Reverse)
            } else {
                infiniteRepeatable(snap())
            },
            label = "bar-$i"
        ).value
    }

    val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }

    Canvas(modifier = modifier.height(height)) {
        val barWidth   = size.width / (barCount * 2f - 1f)
        val barSpacing = barWidth

        barHeights.forEachIndexed { i, normalizedH ->
            val barH   = normalizedH * heightPx
            val left   = i * (barWidth + barSpacing)
            val top    = (heightPx - barH) / 2f
            val alpha  = 0.5f + normalizedH * 0.5f

            drawRoundRect(
                brush      = Brush.verticalGradient(
                    colors  = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.4f)
                    ),
                    startY  = top,
                    endY    = top + barH
                ),
                topLeft    = Offset(left, top),
                size       = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

// ── 2. SINE-WAVE VISUALIZER ───────────────────────────────────────────────────

/**
 * Animated sine wave for call / voice assistant visualization.
 * Draws 2–3 overlapping waves with phase offset for depth.
 */
@Composable
fun SineWaveVisualizer(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    isActive: Boolean = true,
    amplitude: Dp = 8.dp,
    waveCount: Int = 2
) {
    val transition = rememberInfiniteTransition(label = "sine-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue  = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "phase"
    )

    val ampMultiplier by animateFloatAsState(
        targetValue   = if (isActive) 1f else 0.1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "amp"
    )

    Canvas(modifier = modifier) {
        val ampPx    = amplitude.toPx() * ampMultiplier
        val centerY  = size.height / 2
        val stepPx   = 2f

        repeat(waveCount) { waveIndex ->
            val phaseOffset   = waveIndex * (PI / waveCount).toFloat()
            val waveAlpha     = 1f - waveIndex * 0.35f
            val waveAmplitude = ampPx * (1f - waveIndex * 0.2f)

            val path = Path()
            var first = true

            var x = 0f
            while (x <= size.width) {
                val y = centerY + sin(x / size.width * 4 * PI + phase + phaseOffset).toFloat() * waveAmplitude

                if (first) { path.moveTo(x, y); first = false }
                else path.lineTo(x, y)
                x += stepPx
            }

            drawPath(
                path  = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = waveAlpha),
                        color.copy(alpha = waveAlpha),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = (2f - waveIndex * 0.5f).dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

// ── 3. VOICE ASSISTANT ORB ────────────────────────────────────────────────────

/**
 * The "floating orb" animation for the AI/voice assistant mode.
 * Renders a reactive liquid sphere with harmonic oscillation.
 */
@Composable
fun VoiceAssistantOrb(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    accentColor: Color = Color(0xFF007AFF)
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val pulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(if (isListening) 400 else 1200, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "orb-rotate"
    )

    val innerScale by animateFloatAsState(
        targetValue   = if (isSpeaking) 1.3f else if (isListening) 1.1f else 0.9f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "orb-inner"
    )

    Canvas(modifier = modifier) {
        val cx = size.width  / 2
        val cy = size.height / 2
        val r  = minOf(cx, cy)

        // Outer breathing ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.3f * pulse),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r * 1.4f
            ),
            radius = r * 1.4f,
            center = Offset(cx, cy)
        )

        // Mid ring (rotating gradient)
        val sweepBrush = Brush.sweepGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.7f),
                accentColor.copy(alpha = 0.1f),
                accentColor.copy(alpha = 0.7f)
            ),
            center = Offset(cx, cy)
        )
        drawCircle(
            brush  = sweepBrush,
            radius = r * pulse,
            center = Offset(cx, cy),
            style  = Stroke(2.dp.toPx())
        )

        // Inner solid orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.9f),
                    accentColor.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(cx - r * 0.2f, cy - r * 0.2f),
                radius = r * innerScale
            ),
            radius = r * 0.6f * innerScale,
            center = Offset(cx, cy)
        )
    }
}

// ── 4. CALL WAVEFORM ─────────────────────────────────────────────────────────

/**
 * Animated waveform for active phone calls — shows "voice signal" feel.
 */
@Composable
fun CallWaveform(
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    color: Color = Color(0xFF34C759)
) {
    SineWaveVisualizer(
        modifier  = modifier,
        color     = color,
        isActive  = !isMuted,
        amplitude = 6.dp,
        waveCount = 3
    )
}
