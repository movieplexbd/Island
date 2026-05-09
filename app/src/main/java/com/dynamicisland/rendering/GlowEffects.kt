package com.dynamicisland.rendering

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.PI

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║         GLOW EFFECTS ENGINE — v2.0                   ║
 * ║  Breathing glow · Edge lighting · Shadow depth       ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ── 1. BREATHING GLOW ────────────────────────────────────────────────────────

/**
 * Draws a smooth outer glow that "breathes" — pulses in/out like AirPods pairing.
 * Uses layered radial gradients for a soft, realistic halo.
 *
 * @param color       Glow color (usually accent color from current state)
 * @param intensity   0.0–1.0 base opacity of the glow
 * @param speed       Breath cycle duration in ms (default 2000ms)
 * @param layers      Number of concentric glow rings (1–4)
 */
fun Modifier.breathingGlow(
    color: Color,
    intensity: Float = 0.6f,
    speed: Int = 2000,
    layers: Int = 3
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "breathing-glow")
    val breathe by transition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(speed, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    drawBehind {
        val cx = size.width / 2
        val cy = size.height / 2

        repeat(layers) { layer ->
            val layerAlpha = intensity * breathe * (1f - layer * 0.2f)
            val layerRadius = maxOf(size.width, size.height) *
                (0.7f + layer * 0.25f + breathe * 0.15f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = layerAlpha * 0.5f),
                        color.copy(alpha = layerAlpha * 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = layerRadius
                ),
                radius = layerRadius,
                center = Offset(cx, cy),
                blendMode = BlendMode.Screen
            )
        }
    }
}

// ── 2. AMBIENT EDGE LIGHTING ─────────────────────────────────────────────────

/**
 * Smart ambient edge lighting — color-aware dynamic rim that matches
 * the current island state. Flows around the pill edge.
 *
 * @param topColor    Color for the top rim (usually lighter)
 * @param bottomColor Color for the bottom rim (usually darker)
 * @param thickness   Edge thickness in dp
 */
fun Modifier.ambientEdgeLight(
    topColor: Color,
    bottomColor: Color = Color.Transparent,
    thickness: Dp = 1.5.dp
): Modifier = composed {
    val flow = rememberInfiniteTransition(label = "edge-flow")
    val offset by flow.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "edge-offset"
    )

    drawWithContent {
        drawContent()

        val thickPx = thickness.toPx()

        // Top edge — bright rim light
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    (0.3f + offset * 0.4f).coerceIn(0f, 1f) to topColor.copy(alpha = 0.9f),
                    1.0f to Color.Transparent
                )
            ),
            topLeft = Offset(0f, 0f),
            size    = Size(size.width, thickPx)
        )

        // Bottom edge — subtle shadow
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, bottomColor.copy(alpha = 0.4f), Color.Transparent)
            ),
            topLeft = Offset(0f, size.height - thickPx),
            size    = Size(size.width, thickPx)
        )
    }
}

// ── 3. DYNAMIC SHADOW DEPTH ENGINE ───────────────────────────────────────────

/**
 * Layered multi-shadow system. Simulates physical depth with:
 *  - Key shadow (directional)
 *  - Fill shadow (ambient / scattered)
 *  - Specular bounce (subtle ground reflection)
 *
 * @param elevationLevel  0–5 depth steps (0=flat, 5=floating high)
 * @param color           Shadow tint color (use for colored glows)
 */
fun Modifier.dynamicShadow(
    elevationLevel: Int = 3,
    color: Color = Color.Black
): Modifier {
    val level = elevationLevel.coerceIn(0, 5)
    val keyShadowAlpha  = 0.08f + level * 0.07f
    val fillShadowAlpha = 0.04f + level * 0.03f
    val keyOffsetY      = level * 3f
    val fillRadius      = level * 12f

    return drawBehind {
        // Fill / ambient shadow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = fillShadowAlpha),
                    Color.Transparent
                ),
                center = Offset(size.width / 2, size.height / 2 + keyOffsetY),
                radius = fillRadius + size.maxDimension * 0.5f
            )
        )
    }
}

// ── 4. CHARGING ENERGY EFFECT ────────────────────────────────────────────────

/**
 * Electric energy lines that flow upward during charging.
 * Drawn on Canvas for pure GPU performance.
 */
fun Modifier.chargingEnergyEffect(
    isCharging: Boolean,
    percentage: Int,
    color: Color = Color(0xFFFF9500)
): Modifier = composed {
    if (!isCharging) return@composed this

    val flow = rememberInfiniteTransition(label = "charge-flow")
    val yOffset by flow.animateFloat(
        initialValue = 1f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "y-flow"
    )

    drawBehind {
        val segmentCount = 5
        repeat(segmentCount) { i ->
            val x = (i + 1f) / (segmentCount + 1f) * size.width
            val phase = (yOffset + i.toFloat() / segmentCount) % 1f
            val yStart = size.height * phase
            val yEnd   = (yStart - size.height * 0.3f).coerceAtLeast(0f)
            val alpha  = (1f - phase) * 0.6f * (percentage / 100f)

            drawLine(
                color       = color.copy(alpha = alpha),
                start       = Offset(x + sin(i.toDouble() * PI / 3).toFloat() * 4, yStart),
                end         = Offset(x + sin(i.toDouble() * PI / 3 + 1).toFloat() * 4, yEnd),
                strokeWidth = 1.5.dp.toPx(),
                cap         = StrokeCap.Round
            )
        }
    }
}
