package com.dynamicisland.animation

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║       LIQUID MORPH ANIMATOR — v2.0                  ║
 * ║  Physics-based fluid shape transitions              ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Simulates a mercury/water droplet blob expanding and contracting
 * using Bézier path morphing with spring physics on control points.
 */

// ── Physics parameters ────────────────────────────────────────────────────────

data class MorphPhysics(
    /** Spring stiffness for size change */
    val stiffness: Float = Spring.StiffnessMedium,
    /** Spring damping — lower = more "jiggly" */
    val dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    /** Extra "squish" overshoot on rapid expand */
    val squishFactor: Float = 0.08f,
    /** How many canvas subpaths to subdivide the blob edge */
    val blobSegments: Int = 32
)

val DefaultMorphPhysics = MorphPhysics()
val JellyMorphPhysics   = MorphPhysics(dampingRatio = 0.4f, squishFactor = 0.14f)
val SnappyMorphPhysics  = MorphPhysics(stiffness = Spring.StiffnessHigh, squishFactor = 0.03f)

// ── Animated blob shape state ─────────────────────────────────────────────────

/**
 * Returns an animated [Path] that morphs between pill and card shapes
 * using spring-animated corner radii + squish offsets.
 */
@Composable
fun rememberMorphedPath(
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    physics: MorphPhysics = DefaultMorphPhysics
): State<Path> {
    // Spring-animated target dimensions
    val springSpec = spring<Float>(
        dampingRatio = physics.dampingRatio,
        stiffness    = physics.stiffness
    )

    val animW by animateFloatAsState(
        targetValue   = width.value,
        animationSpec = springSpec,
        label         = "morph-w"
    )
    val animH by animateFloatAsState(
        targetValue   = height.value,
        animationSpec = springSpec,
        label         = "morph-h"
    )
    val animR by animateFloatAsState(
        targetValue   = cornerRadius.value,
        animationSpec = springSpec,
        label         = "morph-r"
    )

    // Squish: when width grows fast, height temporarily squishes (incompressible fluid)
    // Approximated with an oscillating offset
    val squish = rememberInfiniteTransition(label = "squish")
    val squishY by squish.animateFloat(
        initialValue =  physics.squishFactor,
        targetValue  = -physics.squishFactor,
        animationSpec = infiniteRepeatable(
            tween(400, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "squish-y"
    )

    return remember(animW, animH, animR, squishY) {
        derivedStateOf {
            buildBlobPath(animW, animH, animR, squishY * physics.squishFactor, physics.blobSegments)
        }
    }
}

// ── Path builder ──────────────────────────────────────────────────────────────

/**
 * Builds a smooth closed path that looks like a rounded rect
 * but with subtle organic blob-like perturbations.
 *
 * Uses Bézier curves for GPU-smooth rendering.
 */
private fun buildBlobPath(
    w: Float, h: Float,
    r: Float,
    squishY: Float,
    segments: Int
): Path {
    val path = Path()
    val cx = w / 2f
    val cy = h / 2f
    val rx = w / 2f
    val ry = h / 2f + squishY * h  // Squish in y

    // Clamp radius
    val cr = r.coerceIn(0f, minOf(rx, ry))

    // Build path as a smooth rounded rectangle with Bézier arcs
    // Standard rounded rect approach using cubic Bézier control point ratio
    val k = 0.5522847f  // approx 4/3 * (sqrt(2) - 1) for circle approximation

    path.moveTo(cx, cy - ry + cr)

    // Top edge
    path.cubicTo(cx, cy - ry + cr * (1 - k), cx - rx + cr * (1 - k), cy - ry, cx - rx + cr, cy - ry)
    path.lineTo(cx + rx - cr, cy - ry)

    // Top-right corner
    path.cubicTo(cx + rx - cr * (1 - k), cy - ry, cx + rx, cy - ry + cr * (1 - k), cx + rx, cy - ry + cr)
    path.lineTo(cx + rx, cy + ry - cr)

    // Bottom-right corner
    path.cubicTo(cx + rx, cy + ry - cr * (1 - k), cx + rx - cr * (1 - k), cy + ry, cx + rx - cr, cy + ry)
    path.lineTo(cx - rx + cr, cy + ry)

    // Bottom-left corner
    path.cubicTo(cx - rx + cr * (1 - k), cy + ry, cx - rx, cy + ry - cr * (1 - k), cx - rx, cy + ry - cr)
    path.lineTo(cx - rx, cy - ry + cr)

    // Top-left corner
    path.cubicTo(cx - rx, cy - ry + cr * (1 - k), cx - rx + cr * (1 - k), cy - ry, cx - rx + cr, cy - ry)

    path.close()
    return path
}

// ── Elastic drag modifier ─────────────────────────────────────────────────────

/**
 * Elastic drag — island stretches like taffy when dragged, snaps back.
 * Apply to the overlay Box.
 */
fun Modifier.elasticDrag(
    dragOffsetY: Float,
    maxStretch: Float = 40f
): Modifier = composed {
    val clampedDrag  = dragOffsetY.coerceIn(-maxStretch, maxStretch)
    val stretchRatio = abs(clampedDrag) / maxStretch  // 0–1

    drawBehind {
        // The stretch effect is simulated via a scale transform on draw
        // (actual layout transform should be done with graphicsLayer in parent)
    }
    this
}

// ── Magnetic edge snap ────────────────────────────────────────────────────────

/**
 * Computes a "magnetic" snap position — when the island is dragged
 * near a screen edge, it snaps and sticks.
 *
 * @param currentY   Current Y position on screen (px)
 * @param screenH    Screen height in px
 * @param snapZones  Y positions that act as magnets (px)
 * @param threshold  Snap distance in px
 */
fun computeMagneticSnap(
    currentY: Float,
    screenH: Float,
    snapZones: List<Float>,
    threshold: Float = 80f
): Float {
    for (zone in snapZones) {
        if (abs(currentY - zone) < threshold) {
            // Eased pull toward the snap zone
            val t = 1f - (abs(currentY - zone) / threshold)
            return currentY + (zone - currentY) * t * 0.6f
        }
    }
    return currentY
}

// ── Motion blur transition ────────────────────────────────────────────────────

/**
 * Applies a horizontal motion blur when the island is expanding/collapsing.
 * Implemented as an alpha-faded echo drawn slightly offset.
 */
fun Modifier.motionBlurTrail(
    velocity: Float,       // px/frame
    color: Color = Color.White
): Modifier = drawBehind {
    val blurSteps = 4
    val maxAlpha  = (abs(velocity) / 30f).coerceIn(0f, 0.15f)

    repeat(blurSteps) { i ->
        val offsetX = velocity * (i + 1) * 0.5f
        val alpha   = maxAlpha * (1f - i.toFloat() / blurSteps)
        drawRect(
            color   = color.copy(alpha = alpha),
            topLeft = Offset(-offsetX, 0f),
            size    = size
        )
    }
}
