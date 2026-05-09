package com.dynamicisland.rendering

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*
import kotlin.random.Random

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║          PARTICLE SYSTEM — v2.0                     ║
 * ║  Canvas-based GPU particle effects                  ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Particle presets:
 *  BURST      → expand outward on state change
 *  ORBIT      → float around island edge
 *  SPARKLE    → random twinkle
 *  STREAM_UP  → upward flow (charging)
 *  CONFETTI   → celebration (call answered, etc.)
 */

// ── Particle model ────────────────────────────────────────────────────────────

internal data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,         // 0.0 (dead) – 1.0 (born)
    var size: Float,
    val color: Color,
    val type: ParticleType
)

enum class ParticleType { BURST, ORBIT, SPARKLE, STREAM_UP, CONFETTI }

// ── Particle emitter state ────────────────────────────────────────────────────

class ParticleEmitter(
    val type: ParticleType,
    val color: Color,
    val count: Int = 20,
    val duration: Float = 1.0f  // normalized lifetime
) {
    internal val particles = mutableListOf<Particle>()
    internal var active = true
    internal var elapsed = 0f

    fun spawn(cx: Float, cy: Float, canvasW: Float, canvasH: Float) {
        repeat(count) {
            particles += when (type) {
                ParticleType.BURST -> {
                    val angle = Random.nextFloat() * 2 * PI.toFloat()
                    val speed = Random.nextFloat() * 4f + 1f
                    Particle(cx, cy, cos(angle) * speed, sin(angle) * speed,
                        1f, Random.nextFloat() * 4f + 2f, color, type)
                }
                ParticleType.ORBIT -> {
                    val angle = (it.toFloat() / count) * 2 * PI.toFloat()
                    val r = maxOf(canvasW, canvasH) * 0.55f
                    Particle(cx + cos(angle) * r, cy + sin(angle) * r,
                        -sin(angle) * 0.5f, cos(angle) * 0.5f,
                        1f, 2f + Random.nextFloat() * 2f, color, type)
                }
                ParticleType.SPARKLE -> {
                    Particle(
                        Random.nextFloat() * canvasW,
                        Random.nextFloat() * canvasH,
                        (Random.nextFloat() - 0.5f) * 0.5f,
                        (Random.nextFloat() - 0.5f) * 0.5f,
                        Random.nextFloat(),
                        Random.nextFloat() * 3f + 1f,
                        color.copy(alpha = Random.nextFloat() * 0.8f + 0.2f),
                        type
                    )
                }
                ParticleType.STREAM_UP -> {
                    Particle(
                        cx + (Random.nextFloat() - 0.5f) * canvasW * 0.8f,
                        canvasH + 4f,
                        (Random.nextFloat() - 0.5f) * 0.8f,
                        -(Random.nextFloat() * 3f + 1f),
                        Random.nextFloat(),
                        Random.nextFloat() * 3f + 1f,
                        color,
                        type
                    )
                }
                ParticleType.CONFETTI -> {
                    Particle(
                        Random.nextFloat() * canvasW,
                        -10f,
                        (Random.nextFloat() - 0.5f) * 3f,
                        Random.nextFloat() * 2f + 1f,
                        1f,
                        Random.nextFloat() * 5f + 3f,
                        listOf(
                            Color(0xFFFF3B30), Color(0xFF34C759),
                            Color(0xFF007AFF), Color(0xFFFF9500),
                            Color(0xFFAF52DE)
                        ).random(),
                        type
                    )
                }
            }
        }
    }
}

// ── Compose integration ───────────────────────────────────────────────────────

/**
 * Transparent Canvas overlay that renders the particle system.
 * Place this on top of the island content.
 */
@Composable
fun ParticleCanvas(
    modifier: Modifier = Modifier,
    emitters: List<ParticleEmitter>
) {
    val tick by rememberInfiniteTransition(label = "particle-tick").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16, easing = LinearEasing)),
        label = "tick"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2

        for (emitter in emitters) {
            if (!emitter.active) continue

            // Spawn if empty
            if (emitter.particles.isEmpty()) {
                emitter.spawn(cx, cy, size.width, size.height)
            }

            // Update + draw
            val toRemove = mutableListOf<Particle>()
            for (p in emitter.particles) {
                p.x    += p.vx
                p.y    += p.vy
                p.life -= 0.015f

                // Gravity for confetti
                if (p.type == ParticleType.CONFETTI) p.vy += 0.05f

                if (p.life <= 0f) {
                    toRemove += p
                    continue
                }

                drawParticle(p)
            }
            emitter.particles.removeAll(toRemove)

            // Mark done when all particles expired
            if (emitter.particles.isEmpty()) emitter.active = false
        }
    }
}

private fun DrawScope.drawParticle(p: Particle) {
    drawCircle(
        color  = p.color.copy(alpha = p.color.alpha * p.life),
        radius = p.size * p.life,
        center = Offset(p.x, p.y),
        blendMode = BlendMode.Screen
    )
}

// ── Factory helpers ───────────────────────────────────────────────────────────

object ParticleFactory {
    fun burst(color: Color, count: Int = 25) =
        ParticleEmitter(ParticleType.BURST, color, count)

    fun callAnswered() = listOf(
        ParticleEmitter(ParticleType.BURST,   Color(0xFF34C759), 30),
        ParticleEmitter(ParticleType.SPARKLE, Color(0xFF34C759), 15)
    )

    fun chargingStart() = listOf(
        ParticleEmitter(ParticleType.STREAM_UP, Color(0xFFFF9500), 25),
        ParticleEmitter(ParticleType.SPARKLE,   Color(0xFFFFCC00), 10)
    )

    fun stateChange(color: Color) = listOf(
        ParticleEmitter(ParticleType.BURST,   color, 20),
    )

    fun celebrate() = listOf(
        ParticleEmitter(ParticleType.CONFETTI, Color.White, 40)
    )
}
