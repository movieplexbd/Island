package com.dynamicisland.customization

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.ui.IslandDarkGray
import com.dynamicisland.ui.IslandMedGray
import com.dynamicisland.ui.IslandWhite

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║  CUSTOM SHAPE ENGINE + RUNTIME EDITOR — v2.0         ║
 * ║  Developer mode + per-app customization              ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ─────────────────────────────────────────────────────────────────────────────
//  1. CUSTOM ISLAND SHAPES
// ─────────────────────────────────────────────────────────────────────────────

enum class IslandShape(val label: String, val emoji: String) {
    PILL        ("Pill",      "💊"),
    ROUNDED_RECT("Card",      "▬"),
    SQUIRCLE    ("Squircle",  "⬛"),
    STADIUM     ("Stadium",   "⬬"),
    HEXAGON     ("Hexagon",   "⬡"),
    TEARDROP    ("Teardrop",  "◉"),
    DIAMOND     ("Diamond",   "◈")
}

/**
 * Returns a Compose [Shape] for each island shape preset.
 * PILL and SQUIRCLE are the most "native" feeling.
 */
fun islandShapeFor(preset: IslandShape, cornerFraction: Float = 1f): Shape {
    return when (preset) {
        IslandShape.PILL         -> RoundedCornerShape(50)
        IslandShape.ROUNDED_RECT -> RoundedCornerShape((16 * cornerFraction).dp)
        IslandShape.SQUIRCLE     -> SquircleShape(cornerFraction)
        IslandShape.STADIUM      -> RoundedCornerShape(50)
        IslandShape.HEXAGON      -> HexagonShape()
        IslandShape.TEARDROP     -> RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp, bottomEnd = 50.dp, bottomStart = 16.dp)
        IslandShape.DIAMOND      -> DiamondShape()
    }
}

// Custom shape implementations

class SquircleShape(private val factor: Float = 1f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): androidx.compose.ui.graphics.Outline {
        val path = Path()
        val w = size.width; val h = size.height
        val r = minOf(w, h) / 2f * factor
        // Superellipse approximation using cubic Bezier
        val k = 0.65f * r
        path.moveTo(w / 2, 0f)
        path.cubicTo(w / 2 + k, 0f, w, h / 2 - k, w, h / 2)
        path.cubicTo(w, h / 2 + k, w / 2 + k, h, w / 2, h)
        path.cubicTo(w / 2 - k, h, 0f, h / 2 + k, 0f, h / 2)
        path.cubicTo(0f, h / 2 - k, w / 2 - k, 0f, w / 2, 0f)
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

class HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): androidx.compose.ui.graphics.Outline {
        val path = Path()
        val cx = size.width / 2f; val cy = size.height / 2f
        val r  = minOf(cx, cy) * 0.95f
        for (i in 0..5) {
            val angle = Math.toRadians(60.0 * i - 30.0)
            val x = cx + r * Math.cos(angle).toFloat()
            val y = cy + r * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

class DiamondShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): androidx.compose.ui.graphics.Outline {
        val path = Path()
        val cx = size.width / 2f; val cy = size.height / 2f
        path.moveTo(cx, 0f)
        path.lineTo(size.width, cy)
        path.lineTo(cx, size.height)
        path.lineTo(0f, cy)
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. ANIMATION ENGINE SETTINGS
// ─────────────────────────────────────────────────────────────────────────────

data class AnimationSettings(
    val springDamping: Float    = Spring.DampingRatioMediumBouncy,
    val springStiffness: Float  = Spring.StiffnessMedium,
    val glowEnabled: Boolean    = true,
    val particlesEnabled: Boolean = true,
    val blurEnabled: Boolean    = true,
    val motionBlurEnabled: Boolean = true,
    val hapticEnabled: Boolean  = true,
    val fps120Enabled: Boolean  = true,
    val reduceMotion: Boolean   = false
)

val DefaultAnimationSettings = AnimationSettings()
val ReducedMotionSettings    = AnimationSettings(
    glowEnabled       = false,
    particlesEnabled  = false,
    blurEnabled       = false,
    motionBlurEnabled = false,
    reduceMotion      = true
)
val UltraSmoothSettings = AnimationSettings(
    springDamping     = Spring.DampingRatioLowBouncy,
    springStiffness   = Spring.StiffnessLow,
    fps120Enabled     = true
)

// ─────────────────────────────────────────────────────────────────────────────
//  3. RUNTIME ANIMATION EDITOR (Composable UI)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RuntimeAnimationEditor(
    settings: AnimationSettings,
    onUpdate: (AnimationSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(IslandDarkGray)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Tune,
                null,
                tint = Color(0xFF007AFF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Animation Editor",
                color = IslandWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, null, tint = IslandWhite.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            }
        }

        // Preset buttons
        Text("Presets", color = IslandWhite.copy(alpha = 0.5f), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetChip("Default") { onUpdate(DefaultAnimationSettings) }
            PresetChip("Smooth")  { onUpdate(UltraSmoothSettings) }
            PresetChip("Minimal") { onUpdate(ReducedMotionSettings) }
        }

        HorizontalDivider(color = IslandWhite.copy(alpha = 0.08f))

        // Spring controls
        Text("Spring Physics", color = IslandWhite.copy(alpha = 0.5f), fontSize = 11.sp)

        LabeledSlider(
            label = "Damping",
            value = settings.springDamping,
            range = 0.1f..1.0f,
            color = Color(0xFF007AFF),
            onChange = { onUpdate(settings.copy(springDamping = it)) }
        )
        LabeledSlider(
            label = "Stiffness",
            value = settings.springStiffness / 1000f,
            range = 0.05f..1.0f,
            color = Color(0xFF34C759),
            onChange = { onUpdate(settings.copy(springStiffness = it * 1000f)) }
        )

        HorizontalDivider(color = IslandWhite.copy(alpha = 0.08f))

        // Effect toggles
        Text("Effects", color = IslandWhite.copy(alpha = 0.5f), fontSize = 11.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EffectToggle("✨ Glow effect",      settings.glowEnabled)      { onUpdate(settings.copy(glowEnabled = it)) }
            EffectToggle("💫 Particles",        settings.particlesEnabled)  { onUpdate(settings.copy(particlesEnabled = it)) }
            EffectToggle("🌫 Background blur",  settings.blurEnabled)       { onUpdate(settings.copy(blurEnabled = it)) }
            EffectToggle("📳 Haptic feedback",  settings.hapticEnabled)     { onUpdate(settings.copy(hapticEnabled = it)) }
            EffectToggle("🔄 120Hz mode",       settings.fps120Enabled)     { onUpdate(settings.copy(fps120Enabled = it)) }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(IslandMedGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = IslandWhite, fontSize = 12.sp)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = IslandWhite.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.width(70.dp)
        )
        Slider(
            value         = value,
            onValueChange = onChange,
            valueRange    = range,
            modifier      = Modifier.weight(1f),
            colors        = SliderDefaults.colors(
                thumbColor       = color,
                activeTrackColor = color.copy(alpha = 0.7f),
                inactiveTrackColor = IslandWhite.copy(alpha = 0.15f)
            ),
            thumb = { Box(Modifier.size(12.dp).background(color, RoundedCornerShape(6.dp))) }
        )
        Text(
            "${"%.2f".format(value)}",
            color = IslandWhite.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun EffectToggle(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = IslandWhite.copy(alpha = 0.8f), fontSize = 13.sp)
        Switch(
            checked         = enabled,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = IslandWhite,
                checkedTrackColor   = Color(0xFF007AFF),
                uncheckedThumbColor = IslandWhite.copy(alpha = 0.5f),
                uncheckedTrackColor = IslandMedGray
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4. DEVELOPER MODE PANEL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeveloperModePanel(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onInjectTestEvent: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D1B0D))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚙️", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Developer Mode",
                color = Color(0xFF34C759),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF34C759)
                ),
                modifier = Modifier.scale(0.8f)
            )
        }

        if (isEnabled) {
            Text("Inject Test Events", color = IslandWhite.copy(alpha = 0.5f), fontSize = 11.sp)

            val testEvents = listOf(
                "📞 Incoming Call",
                "🎵 Music Start",
                "🔔 Notification",
                "🔋 Charging",
                "⏱️ Timer 30s",
                "🌤️ Weather",
                "🎮 Gaming"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(testEvents) { event ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A2A1A))
                            .clickable { onInjectTestEvent(event) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(event, color = Color(0xFF34C759), fontSize = 11.sp)
                    }
                }
            }

            // Debug info
            DevInfoRow("Render", "Compose Canvas + RenderEffect")
            DevInfoRow("Stack size", "Max 4 concurrent activities")
            DevInfoRow("Physics", "Spring stiffness=Medium, bouncy")
        }
    }
}

@Composable
private fun DevInfoRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, color = IslandWhite.copy(alpha = 0.4f), fontSize = 11.sp)
        Text(value, color = Color(0xFF34C759).copy(alpha = 0.7f), fontSize = 11.sp)
    }
}
