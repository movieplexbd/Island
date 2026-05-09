package com.dynamicisland.ui.islands

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.dynamicisland.model.*
import kotlin.math.*

// ══════════════════════════════════════════════════════════════════════════════
//  WEATHER ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WeatherIslandV3(state: IslandState.Weather, expansion: IslandExpansion) {
    when (expansion) {
        IslandExpansion.EXPANDED -> ExpandedWeather(state)
        else                     -> CompactWeather(state)
    }
}

@Composable
private fun CompactWeather(w: IslandState.Weather) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedWeatherIcon(w.condition, size = 18.dp)
            Text(
                "${w.tempC.toInt()}°",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
        }
        if (w.isCached) {
            Icon(Icons.Rounded.CloudOff, null,
                tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
        }
        Text(
            w.cityName.take(10),
            color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp
        )
    }
}

@Composable
private fun ExpandedWeather(w: IslandState.Weather) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(w.cityName, color = Color.White, fontSize = 13.sp,
                     fontWeight = FontWeight.Medium)
                Text(w.condition.label, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedWeatherIcon(w.condition, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text("${w.tempC.toInt()}°", color = Color.White,
                     fontSize = 42.sp, fontWeight = FontWeight.Thin)
            }
        }
        // Details row
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            WeatherStat("💧", "${w.humidity}%", "Humidity")
            WeatherStat("💨", "${w.windKph.toInt()} km/h", "Wind")
            WeatherStat("🌡️", "${w.feelsLikeC.toInt()}°", "Feels")
            WeatherStat("☀️", "UV ${w.uvIndex}", "UV")
        }
        // Hourly forecast
        if (w.hourlyForecast.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                w.hourlyForecast.take(8).forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                           verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (h.hour == 0) "12am" else if (h.hour < 12) "${h.hour}am"
                            else if (h.hour == 12) "12pm" else "${h.hour - 12}pm",
                            color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp
                        )
                        Text(h.condition.emoji, fontSize = 14.sp)
                        Text("${h.tempC.toInt()}°", color = Color.White, fontSize = 10.sp,
                             fontWeight = FontWeight.Medium)
                        if (h.precipChance > 20) {
                            Text("${h.precipChance}%",
                                color = Color(0xFF64B5F6), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        if (w.isCached) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.CloudOff, null,
                    tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(11.dp))
                Text("Offline data", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun WeatherStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(icon, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

@Composable
fun AnimatedWeatherIcon(condition: WeatherCondition, size: Dp) {
    val inf = rememberInfiniteTransition(label = "wx")
    val pulse by inf.animateFloat(
        initialValue = 0.9f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse), label = "pulse"
    )
    Box(modifier = Modifier.size(size).graphicsLayer { scaleX = pulse; scaleY = pulse },
        contentAlignment = Alignment.Center) {
        Text(condition.emoji, fontSize = (size.value * 0.7f).sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  STEP COUNTER ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun StepCounterIsland(state: IslandState.StepCounter, expansion: IslandExpansion) {
    when (expansion) {
        IslandExpansion.EXPANDED -> ExpandedSteps(state)
        else                     -> CompactSteps(state)
    }
}

@Composable
private fun CompactSteps(s: IslandState.StepCounter) {
    val progress = (s.steps.toFloat() / s.goal).coerceIn(0f, 1f)
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Mini arc
        Canvas(modifier = Modifier.size(28.dp)) {
            val sweep = progress * 300f
            drawArc(Color.White.copy(alpha = 0.2f), -240f, 300f, false,
                style = Stroke(3f, cap = StrokeCap.Round))
            drawArc(Color(0xFF4CAF50), -240f, sweep, false,
                style = Stroke(3f, cap = StrokeCap.Round))
        }
        Column {
            Text("${s.steps.formatSteps()}", color = Color.White,
                 fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("steps", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        }
        s.heartRate?.let { hr ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("❤️", fontSize = 10.sp)
                Text("$hr", color = Color(0xFFEF9A9A), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ExpandedSteps(s: IslandState.StepCounter) {
    val progress = (s.steps.toFloat() / s.goal).coerceIn(0f, 1f)
    val animProgress by animateFloatAsState(progress, tween(800, easing = FastOutSlowInEasing), label = "steps")

    Row(modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Activity ring
        Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = 6f
                drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false,
                    style = Stroke(12f, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2))
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFF4CAF50))),
                    startAngle = -90f, sweepAngle = animProgress * 360f, useCenter = false,
                    style = Stroke(12f, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏃", fontSize = 18.sp)
                Text("${(progress * 100).toInt()}%",
                     color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Stats
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${s.steps.formatSteps()} / ${s.goal.formatSteps()}",
                 color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepStat("🔥", "${s.calories}", "kcal")
                StepStat("📍", "${String.format("%.1f", s.distanceKm)}", "km")
                StepStat("⏱️", "${s.activeMinutes}", "min")
            }
            s.heartRate?.let { hr ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("❤️", fontSize = 14.sp)
                    Text("$hr BPM", color = Color(0xFFEF9A9A),
                         fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun StepStat(icon: String, value: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(icon, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(unit, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

private fun Int.formatSteps(): String =
    if (this >= 1000) "${this / 1000}.${(this % 1000) / 100}k" else "$this"

// ══════════════════════════════════════════════════════════════════════════════
//  ALARM ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun AlarmIsland(
    state: IslandState.Alarm,
    expansion: IslandExpansion,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "alarm")
    val shake by inf.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(80, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake"
    )
    val scale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse"
    )

    when (expansion) {
        IslandExpansion.EXPANDED -> {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp),
                   verticalArrangement = Arrangement.spacedBy(10.dp),
                   horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⏰", fontSize = 24.sp,
                         modifier = Modifier.graphicsLayer { translationX = shake; scaleX = scale; scaleY = scale })
                    Column {
                        Text(state.timeString, color = Color.White,
                             fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(state.label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.snoozable) {
                        OutlinedButton(
                            onClick = onSnooze,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Snooze ${state.snoozeMinutes}m",
                                 color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Dismiss", color = Color.White, fontSize = 12.sp,
                             fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        else -> {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⏰", fontSize = 16.sp,
                         modifier = Modifier.graphicsLayer { translationX = shake })
                    Text(state.timeString, color = Color.White,
                         fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text("Tap to dismiss", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  FOCUS MODE ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun FocusModeIsland(state: IslandState.FocusMode, expansion: IslandExpansion) {
    val progress = (state.remainingMinutes.toFloat() / state.totalMinutes).coerceIn(0f, 1f)
    val animP by animateFloatAsState(progress, tween(1000), label = "focus")
    val inf = rememberInfiniteTransition(label = "focus_glow")
    val glowAlpha by inf.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "ga"
    )

    when (expansion) {
        IslandExpansion.EXPANDED -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(
                            Color(0xFF7C4DFF).copy(alpha = glowAlpha), CircleShape))
                        Column {
                            Text(state.sessionName, color = Color.White,
                                 fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Focus session", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${state.remainingMinutes}m left",
                             color = Color(0xFF7C4DFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (state.appsBlocked > 0)
                            Text("${state.appsBlocked} apps blocked",
                                 color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    }
                }
                LinearProgressIndicator(
                    progress = { animP },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF7C4DFF),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FocusStat("📵", "${state.appsBlocked}", "blocked")
                    FocusStat("⏱️", "${state.totalMinutes - state.remainingMinutes}", "elapsed min")
                    FocusStat("🎯", "${state.remainingMinutes}", "remaining min")
                }
            }
        }
        else -> {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).background(
                    Color(0xFF7C4DFF).copy(alpha = glowAlpha), CircleShape))
                Text("Focus", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${state.remainingMinutes}m",
                     color = Color(0xFF7C4DFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FocusStat(icon: String, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(icon, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SPORT SCORE ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun SportScoreIsland(state: IslandState.SportScore, expansion: IslandExpansion) {
    val inf = rememberInfiniteTransition(label = "live")
    val livePulse by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "lp"
    )

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(state.homeTeam.take(6), color = Color.White,
             fontSize = 12.sp, fontWeight = FontWeight.Bold,
             modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${state.homeScore}  –  ${state.awayScore}",
                 color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (state.status.contains("LIVE", ignoreCase = true)) {
                    Box(Modifier.size(6.dp).background(
                        Color(0xFFFF1744).copy(alpha = livePulse), CircleShape))
                }
                Text(state.status, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
        Text(state.awayTeam.take(6), color = Color.White,
             fontSize = 12.sp, fontWeight = FontWeight.Bold,
             textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  VOICE ASSIST ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun VoiceAssistIsland(state: IslandState.VoiceAssist) {
    val inf = rememberInfiniteTransition(label = "voice")
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Waveform bars
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { i ->
                val height by inf.animateFloat(
                    initialValue = 6f, targetValue = 18f + i * 4f,
                    animationSpec = infiniteRepeatable(
                        tween(300 + i * 80, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse), label = "h$i"
                )
                Box(Modifier.width(3.dp).height(height.dp)
                    .background(Color(0xFF4FC3F7), RoundedCornerShape(1.5.dp)))
            }
        }
        Text(
            if (state.isProcessing) "Processing…"
            else state.resultText ?: state.promptText,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  DOWNLOAD PROGRESS ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun DownloadIsland(state: IslandState.DownloadProgress) {
    val animP by animateFloatAsState(state.progress, tween(300), label = "dl")
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (state.isInstalling) Icons.Rounded.InstallMobile else Icons.Rounded.Download,
            null, tint = Color.White, modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(state.appName, color = Color.White, fontSize = 11.sp,
                     maxLines = 1, overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.weight(1f))
                Text(
                    if (state.isInstalling) "Installing…"
                    else "${(state.progress * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
                )
            }
            LinearProgressIndicator(
                progress = { animP },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                color = Color(0xFF64B5F6),
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SCREEN RECORDING ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ScreenRecordingIsland(state: IslandState.ScreenRecording) {
    val inf = rememberInfiniteTransition(label = "rec")
    val recAlpha by inf.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ra"
    )
    val minutes = state.durationSeconds / 60
    val secs    = state.durationSeconds % 60

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(Color(0xFFFF1744).copy(alpha = recAlpha), CircleShape))
        Text(
            if (state.isCasting) "Casting to ${state.castTarget}" else "Recording",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
        Text(
            String.format("%02d:%02d", minutes, secs),
            color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SLEEP SCORE ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun SleepScoreIsland(state: IslandState.SleepScore, expansion: IslandExpansion) {
    when (expansion) {
        IslandExpansion.EXPANDED -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp),
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Sleep Score", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text(when {
                            state.score >= 85 -> "Excellent 🌟"
                            state.score >= 70 -> "Good 😴"
                            state.score >= 50 -> "Fair 😐"
                            else              -> "Poor 😔"
                        }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${state.score}", color = when {
                        state.score >= 85 -> Color(0xFF4CAF50)
                        state.score >= 70 -> Color(0xFF64B5F6)
                        state.score >= 50 -> Color(0xFFFFB700)
                        else              -> Color(0xFFFF5252)
                    }, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SleepStat("🛏️", String.format("%.1fh", state.hoursSlept), "slept")
                    SleepStat("🌊", "${state.deepSleepPercent}%", "deep")
                }
                Text(state.recommendation,
                     color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 2,
                     overflow = TextOverflow.Ellipsis)
            }
        }
        else -> {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("😴", fontSize = 16.sp)
                Text("Sleep ${state.score}", color = Color.White,
                     fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("· ${String.format("%.1fh", state.hoursSlept)}",
                     color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SleepStat(icon: String, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(icon, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  CLIPBOARD ISLAND  — v3.0
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ClipboardIsland(state: IslandState.ClipboardSnippet) {
    val icon = when (state.category) {
        ClipCategory.URL     -> "🔗"
        ClipCategory.PHONE   -> "📞"
        ClipCategory.EMAIL   -> "✉️"
        ClipCategory.CODE    -> "💻"
        ClipCategory.ADDRESS -> "📍"
        ClipCategory.TEXT    -> "📋"
    }
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(icon, fontSize = 16.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(state.text, color = Color.White, fontSize = 11.sp,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("from ${state.sourceApp}", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        }
        Text("Tap to copy", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
    }
}
