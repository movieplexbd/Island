package com.dynamicisland.widgets

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.ui.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║      MINI CONTROL CENTER — v2.0                     ║
 * ║  Swipe-down expandable floating control panel       ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Mimics iOS Control Center but inside the island.
 * Activated by swiping DOWN on an EXPANDED island.
 *
 * Controls:
 *  Row 1: Brightness · Volume · DND · Flashlight
 *  Row 2: WiFi · Bluetooth · Rotation Lock · Hotspot
 */

data class ControlState(
    val brightness: Float    = 0.5f,
    val volume: Float        = 0.5f,
    val isDndEnabled: Boolean = false,
    val isFlashlightOn: Boolean = false,
    val isWifiOn: Boolean    = true,
    val isBluetoothOn: Boolean = false,
    val isRotationLocked: Boolean = false,
    val isHotspotOn: Boolean = false
)

@Composable
fun MiniControlCenter(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ControlState()) }

    // Read initial brightness
    LaunchedEffect(Unit) {
        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .toFloat() / 255f
        } catch (_: Exception) { 0.5f }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                  am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

        state = state.copy(brightness = brightness, volume = vol)
    }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(200)) + expandVertically(tween(280, easing = EaseOutQuart)),
        exit    = fadeOut(tween(150)) + shrinkVertically(tween(200, easing = EaseInQuart))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(IslandDarkGray)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Sliders ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ControlSlider(
                    icon     = Icons.Rounded.BrightnessHigh,
                    value    = state.brightness,
                    color    = Color(0xFFFFCC00),
                    modifier = Modifier.weight(1f),
                    onChange = { v ->
                        state = state.copy(brightness = v)
                        try {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                (v * 255).toInt()
                            )
                        } catch (_: Exception) {}
                    }
                )
                ControlSlider(
                    icon     = Icons.Rounded.VolumeUp,
                    value    = state.volume,
                    color    = Color(0xFF007AFF),
                    modifier = Modifier.weight(1f),
                    onChange = { v ->
                        state = state.copy(volume = v)
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(),
                            0
                        )
                    }
                )
            }

            // ── Toggle row 1 ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlToggle(
                    icon    = if (state.isDndEnabled) Icons.Rounded.DoNotDisturb else Icons.Rounded.NotificationsActive,
                    label   = "DND",
                    active  = state.isDndEnabled,
                    color   = Color(0xFFAF52DE),
                    onClick = { state = state.copy(isDndEnabled = !state.isDndEnabled) }
                )
                ControlToggle(
                    icon    = Icons.Rounded.FlashlightOn,
                    label   = "Light",
                    active  = state.isFlashlightOn,
                    color   = Color(0xFFFFCC00),
                    onClick = { state = state.copy(isFlashlightOn = !state.isFlashlightOn) }
                )
                ControlToggle(
                    icon    = Icons.Rounded.Wifi,
                    label   = "Wi-Fi",
                    active  = state.isWifiOn,
                    color   = Color(0xFF34C759),
                    onClick = { state = state.copy(isWifiOn = !state.isWifiOn) }
                )
                ControlToggle(
                    icon    = Icons.Rounded.Bluetooth,
                    label   = "BT",
                    active  = state.isBluetoothOn,
                    color   = Color(0xFF007AFF),
                    onClick = { state = state.copy(isBluetoothOn = !state.isBluetoothOn) }
                )
            }

            // ── Toggle row 2 ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlToggle(
                    icon    = Icons.Rounded.ScreenRotation,
                    label   = "Rotate",
                    active  = !state.isRotationLocked,
                    color   = Color(0xFFFF9500),
                    onClick = { state = state.copy(isRotationLocked = !state.isRotationLocked) }
                )
                ControlToggle(
                    icon    = Icons.Rounded.Wifi,
                    label   = "Hotspot",
                    active  = state.isHotspotOn,
                    color   = Color(0xFFFF3B30),
                    onClick = { state = state.copy(isHotspotOn = !state.isHotspotOn) }
                )
                ControlToggle(
                    icon    = Icons.Rounded.AirplanemodeActive,
                    label   = "Airplane",
                    active  = false,
                    color   = Color(0xFF8E8E93),
                    onClick = {}
                )
                ControlToggle(
                    icon    = Icons.Rounded.ExpandLess,
                    label   = "Close",
                    active  = false,
                    color   = Color(0xFF636366),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ControlSlider(
    icon: ImageVector,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(IslandMedGray)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(
                "${(value * 100).toInt()}%",
                color = IslandWhite.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
        Slider(
            value            = value,
            onValueChange    = onChange,
            modifier         = Modifier.height(20.dp),
            colors           = SliderDefaults.colors(
                thumbColor           = color,
                activeTrackColor     = color.copy(alpha = 0.8f),
                inactiveTrackColor   = IslandWhite.copy(alpha = 0.15f)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color, CircleShape)
                )
            }
        )
    }
}

@Composable
private fun ControlToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue   = if (active) color.copy(alpha = 0.25f) else IslandMedGray,
        animationSpec = tween(200),
        label         = "toggle-bg"
    )
    val iconTint by animateColorAsState(
        targetValue   = if (active) color else IslandWhite.copy(alpha = 0.45f),
        animationSpec = tween(200),
        label         = "toggle-tint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(label, color = IslandWhite.copy(alpha = 0.45f), fontSize = 9.sp)
    }
}
