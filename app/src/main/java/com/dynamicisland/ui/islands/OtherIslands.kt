package com.dynamicisland.ui.islands

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.ui.*

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATION ISLAND
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NotificationIsland(
    state: IslandState.Notification,
    expansion: IslandExpansion
) {
    when (expansion) {
        IslandExpansion.EXPANDED -> ExpandedNotification(state)
        else                     -> CompactNotification(state)
    }
}

@Composable
private fun CompactNotification(state: IslandState.Notification) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = null,
            tint = IslandAccentBlue,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.appName,
                color = IslandAccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.title,
                color = IslandWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpandedNotification(state: IslandState.Notification) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(IslandAccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = IslandAccentBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = state.appName,
                color = IslandAccentBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text("now", color = IslandWhite.copy(alpha = 0.35f), fontSize = 10.sp)
        }

        Text(
            text = state.title,
            color = IslandWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = state.text,
            color = IslandWhite.copy(alpha = 0.65f),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CHARGING ISLAND
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChargingIsland(
    state: IslandState.Charging,
    expansion: IslandExpansion
) {
    when (expansion) {
        IslandExpansion.EXPANDED -> ExpandedCharging(state)
        else                     -> CompactCharging(state)
    }
}

@Composable
private fun CompactCharging(state: IslandState.Charging) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChargingBolt(animated = state.isCharging)
        Text(
            text = "${state.percentage}%",
            color = IslandWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (state.isCharging) "Charging" else "Disconnected",
            color = batteryColor(state.percentage).copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ExpandedCharging(state: IslandState.Charging) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChargingBolt(animated = state.isCharging, size = 22.dp)
            Column {
                Text(
                    text = if (state.isCharging) "Charging" else "Battery",
                    color = IslandWhite.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Text(
                    text = "${state.percentage}%",
                    color = batteryColor(state.percentage),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Battery bar
        BatteryBar(percentage = state.percentage)
    }
}

@Composable
private fun BatteryBar(percentage: Int) {
    val color = batteryColor(percentage)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(IslandWhite.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percentage / 100f)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.8f), color)
                    )
                )
        )
    }
}

@Composable
private fun ChargingBolt(animated: Boolean, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val flash = rememberInfiniteTransition(label = "bolt-flash")
    val alpha by flash.animateFloat(
        initialValue = 0.6f, targetValue = 1.0f,
        animationSpec = if (animated) {
            infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse)
        } else infiniteRepeatable(snap()),
        label = "bolt-alpha"
    )
    Icon(
        imageVector = Icons.Rounded.Bolt,
        contentDescription = null,
        tint = IslandAccentOrange.copy(alpha = alpha),
        modifier = Modifier.size(size)
    )
}

private fun batteryColor(pct: Int) = when {
    pct > 50 -> IslandAccentGreen
    pct > 20 -> IslandAccentOrange
    else      -> IslandAccentRed
}

// ─────────────────────────────────────────────────────────────────────────────
// NAVIGATION ISLAND
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NavigationIsland(state: IslandState.Navigation, expansion: IslandExpansion) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Navigation,
            contentDescription = null,
            tint = IslandAccentBlue,
            modifier = Modifier
                .size(20.dp)
                .rotate(-45f)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.instruction,
                color = IslandWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${state.distance} · ${state.eta}",
                color = IslandWhite.copy(alpha = 0.55f),
                fontSize = 10.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TIMER ISLAND
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TimerIsland(state: IslandState.Timer, expansion: IslandExpansion) {
    val progress = if (state.totalSeconds > 0)
        1f - (state.remainingSeconds.toFloat() / state.totalSeconds.toFloat())
    else 0f

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(IslandAccentOrange.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = IslandAccentOrange,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTimerSeconds(state.remainingSeconds),
                color = IslandWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.label.isNotBlank()) {
                Text(
                    text = state.label,
                    color = IslandWhite.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
        // Mini arc progress
        MiniArcProgress(progress = progress, color = IslandAccentOrange)
    }
}

@Composable
private fun MiniArcProgress(progress: Float, color: Color) {
    // Simple linear proxy
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(progress.coerceIn(0f, 1f))
                .background(color.copy(alpha = 0.7f))
        )
    }
}

private fun formatTimerSeconds(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE ACTIVITY ISLAND
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiveActivityIsland(state: IslandState.LiveActivity, expansion: IslandExpansion) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(state.emoji, fontSize = 16.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title,
                color = IslandWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.subtitle,
                color = IslandWhite.copy(alpha = 0.55f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
