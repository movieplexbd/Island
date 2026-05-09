package com.dynamicisland.rendering

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║         BLUR RENDER ENGINE — v2.0                   ║
 * ║  GPU-accelerated blur + glassmorphism via            ║
 * ║  Android RenderEffect API (API 31+)                  ║
 * ╚══════════════════════════════════════════════════════╝
 */
object BlurRenderEngine {

    /**
     * Creates a [RenderEffect] blur chain:
     *   BlurMask → ColorFilter tint → Blend
     *
     * Used on the ComposeView's underlying Android View for
     * true background blur (requires API 31+).
     *
     * @param blurRadius  Gaussian blur radius in pixels (default 24f)
     * @param tintColor   ARGB overlay tint on top of blur
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createGlassEffect(
        blurRadius: Float = 24f,
        tintColor: Int = 0x40FFFFFF  // 25% white
    ): RenderEffect {
        val blur = RenderEffect.createBlurEffect(
            blurRadius, blurRadius,
            Shader.TileMode.MIRROR
        )
        val tint = RenderEffect.createColorFilterEffect(
            android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply {
                    // Add subtle brightness lift
                    setScale(1.05f, 1.05f, 1.05f, 1f)
                }
            ),
            blur
        )
        return tint
    }

    /**
     * Lighter blur for compact states — less GPU load.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createLightGlassEffect(): RenderEffect =
        createGlassEffect(blurRadius = 12f, tintColor = 0x30FFFFFF)

    /**
     * Heavy blur for fully expanded states.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createDeepGlassEffect(): RenderEffect =
        createGlassEffect(blurRadius = 40f, tintColor = 0x55FFFFFF)
}

/**
 * Compose modifier that draws a layered glassmorphism background:
 *  Layer 1: Dark fill  (base)
 *  Layer 2: Noise texture simulation via diagonal gradient
 *  Layer 3: Edge highlight (top rim light)
 *  Layer 4: Specular shimmer (animated)
 */
fun Modifier.glassmorphism(
    baseColor: Color = Color(0xFF0A0A0A),
    shimmerEnabled: Boolean = true
): Modifier = composed {
    val shimmerOffset by if (shimmerEnabled) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = -300f, targetValue = 300f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
            label = "shimmer-x"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    drawBehind {
        // Layer 1: Base fill
        drawRect(color = baseColor)

        // Layer 2: Diagonal noise approximation
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.02f)
                ),
                start = Offset(0f, 0f),
                end   = Offset(size.width, size.height)
            )
        )

        // Layer 3: Top rim light
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                startY = 0f,
                endY   = size.height * 0.3f
            )
        )

        // Layer 4: Animated shimmer
        if (shimmerEnabled) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    start = Offset(shimmerOffset - 100f, 0f),
                    end   = Offset(shimmerOffset + 100f, size.height)
                )
            )
        }

        // Layer 5: Bottom shadow inner
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.25f)
                ),
                startY = size.height * 0.6f,
                endY   = size.height
            )
        )
    }
}

/**
 * Neon glassmorphism variant — adds colored inner glow.
 * Use for gaming mode, special states.
 */
fun Modifier.neonGlass(accentColor: Color): Modifier = composed {
    val pulse = rememberInfiniteTransition(label = "neon-pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow-alpha"
    )

    drawBehind {
        // Dark base
        drawRect(Color(0xFF050505))

        // Neon inner border glow
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = glowAlpha * 0.4f),
                    Color.Transparent,
                    accentColor.copy(alpha = glowAlpha * 0.2f)
                )
            )
        )

        // Center radial glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = glowAlpha * 0.15f),
                    Color.Transparent
                ),
                radius = size.maxDimension * 0.8f
            ),
            radius = size.maxDimension * 0.8f,
            center = Offset(size.width / 2, size.height / 2)
        )
    }
}

/**
 * AGSL (Android Graphics Shading Language) shader for liquid glass.
 * Available on API 33+.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object LiquidGlassShader {
    // AGSL shader that creates a warped lens / liquid glass distortion
    private const val SHADER_SRC = """
        uniform float2 resolution;
        uniform float  time;
        uniform float  strength;
        uniform shader content;
        
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 center = float2(0.5, 0.5);
            float2 delta = uv - center;
            float dist = length(delta);
            
            // Lens distortion
            float distortion = 1.0 + strength * (1.0 - smoothstep(0.0, 0.5, dist));
            float2 warpedUv = center + delta / distortion;
            
            // Subtle ripple
            float ripple = sin(dist * 20.0 - time * 2.0) * 0.003 * strength;
            warpedUv += normalize(delta) * ripple;
            
            warpedUv = clamp(warpedUv, float2(0.0), float2(1.0));
            return content.eval(warpedUv * resolution);
        }
    """

    fun create(strength: Float = 0.3f): RuntimeShader {
        val shader = RuntimeShader(SHADER_SRC)
        shader.setFloatUniform("strength", strength)
        return shader
    }
}
