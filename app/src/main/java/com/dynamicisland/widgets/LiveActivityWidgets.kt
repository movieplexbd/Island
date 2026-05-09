package com.dynamicisland.widgets

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.ui.*

// ─────────────────────────────────────────────────────────────────────────────
//  WEATHER LIVE ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Weather island with animated sun/cloud/rain icons and temperature.
 */
@Composable
fun WeatherIsland(
    weather: WeatherData,
    expansion: IslandExpansion
) {
    when (expansion) {
        IslandExpansion.EXPANDED -> ExpandedWeather(weather)
        else                     -> CompactWeather(weather)
    }
}

@Composable
private fun CompactWeather(w: WeatherData) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedWeatherIcon(condition = w.condition, size = 20.dp)
        Text(
            "${w.tempC.toInt()}°",
            color = IslandWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            w.condition,
            color = IslandWhite.copy(alpha = 0.5f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpandedWeather(w: WeatherData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "${w.tempC.toInt()}°C",
                    color = IslandWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Thin
                )
                Text(
                    w.condition,
                    color = IslandWhite.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            AnimatedWeatherIcon(condition = w.condition, size = 48.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WeatherDetailChip("💧", "${w.humidity}%", "Humidity")
            WeatherDetailChip("🌡️", "${w.tempC.toInt()}°", "Feels like")
        }
    }
}

@Composable
private fun WeatherDetailChip(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 14.sp)
        Text(value, color = IslandWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = IslandWhite.copy(alpha = 0.4f), fontSize = 9.sp)
    }
}

@Composable
private fun AnimatedWeatherIcon(condition: String, size: androidx.compose.ui.unit.Dp) {
    val rotation = rememberInfiniteTransition(label = "weather-spin")
    val spin by rotation.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "spin"
    )

    val icon = when {
        condition.contains("rain", true)  -> Icons.Rounded.Umbrella
        condition.contains("cloud", true) -> Icons.Rounded.Cloud
        condition.contains("snow", true)  -> Icons.Rounded.AcUnit
        condition.contains("storm", true) -> Icons.Rounded.Thunderstorm
        condition.contains("fog", true)   -> Icons.Rounded.Cloud
        else                              -> Icons.Rounded.WbSunny
    }
    val tint = when {
        condition.contains("rain", true)  -> Color(0xFF5AC8FA)
        condition.contains("cloud", true) -> Color(0xFFAEAEB2)
        condition.contains("snow", true)  -> Color(0xFFE5F2FF)
        condition.contains("storm", true) -> Color(0xFF636366)
        else                              -> Color(0xFFFFD60A)
    }

    Icon(
        imageVector  = icon,
        contentDescription = null,
        tint         = tint,
        modifier     = Modifier
            .size(size)
            .then(if (icon == Icons.Rounded.WbSunny) Modifier.rotate(spin) else Modifier)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  GAMING MODE ISLAND
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GamingIsland(
    perfData: PerformanceData,
    expansion: IslandExpansion
) {
    when (expansion) {
        IslandExpansion.EXPANDED -> ExpandedGaming(perfData)
        else                     -> CompactGaming(perfData)
    }
}

@Composable
private fun CompactGaming(d: PerformanceData) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.SportsEsports,
            null,
            tint = IslandAccentGreen,
            modifier = Modifier.size(14.dp)
        )
        FpsIndicator(fps = d.fpsEstimate, compact = true)
        Spacer(Modifier.weight(1f))
        // Network
        Text(
            formatSpeed(d.downloadKbps),
            color = IslandAccentGreen.copy(alpha = 0.8f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ExpandedGaming(d: PerformanceData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Gaming Mode",
                color = IslandAccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(Icons.Rounded.SportsEsports, null, tint = IslandAccentGreen, modifier = Modifier.size(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GamingStat("FPS",  "${d.fpsEstimate}",  fpsColor(d.fpsEstimate))
            GamingStat("RAM",  "${d.ramUsedMb}MB",  IslandAccentBlue)
            GamingStat("CPU",  "${d.cpuPercent}%",  cpuColor(d.cpuPercent))
            GamingStat("NET",  formatSpeed(d.downloadKbps), IslandAccentGreen)
        }

        // FPS history bar chart (mini)
        FpsIndicator(fps = d.fpsEstimate, compact = false)
    }
}

@Composable
private fun FpsIndicator(fps: Int, compact: Boolean) {
    val color = fpsColor(fps)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            "$fps FPS",
            color = color,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GamingStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = IslandWhite.copy(alpha = 0.4f), fontSize = 9.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  APP SHORTCUTS ISLAND
// ─────────────────────────────────────────────────────────────────────────────

data class AppShortcut(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)

@Composable
fun AppShortcutsIsland(
    shortcuts: List<AppShortcut>,
    onShortcutTap: (AppShortcut) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        shortcuts.take(5).forEach { shortcut ->
            ShortcutItem(
                shortcut = shortcut,
                onClick  = {
                    onShortcutTap(shortcut)
                    try {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(shortcut.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
        }
    }
}

@Composable
private fun ShortcutItem(shortcut: AppShortcut, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(IslandMedGray, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (shortcut.icon != null) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = IslandWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    shortcut.label.take(1).uppercase(),
                    color = IslandWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            shortcut.label.take(6),
            color = IslandWhite.copy(alpha = 0.5f),
            fontSize = 8.sp
        )
    }
}

/**
 * Loads the top-N recently used apps as shortcuts.
 */
fun loadAppShortcuts(context: Context, count: Int = 5): List<AppShortcut> {
    val pm = context.packageManager
    return try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .take(count)
            .map { info ->
                AppShortcut(
                    packageName = info.packageName,
                    label       = pm.getApplicationLabel(info).toString(),
                    icon        = try { pm.getApplicationIcon(info.packageName) } catch (_: Exception) { null }
                )
            }
    } catch (_: Exception) { emptyList() }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun fpsColor(fps: Int): Color = when {
    fps >= 55 -> Color(0xFF34C759)
    fps >= 30 -> Color(0xFFFF9500)
    else      -> Color(0xFFFF3B30)
}
private fun cpuColor(pct: Int): Color = when {
    pct < 50  -> Color(0xFF34C759)
    pct < 80  -> Color(0xFFFF9500)
    else      -> Color(0xFFFF3B30)
}
private fun formatSpeed(kbps: Long) = when {
    kbps >= 1000 -> "${"%.0f".format(kbps/1000f)}M"
    else         -> "${kbps}K"
}
