package com.dynamicisland.ui.islands

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.ui.*
import java.util.concurrent.TimeUnit

/**
 * Phone call island — compact shows caller name + icons,
 * expanded shows full call card with accept/decline.
 */
@Composable
fun CallIsland(
    state: IslandState.PhoneCall,
    expansion: IslandExpansion
) {
    when (expansion) {
        IslandExpansion.COMPACT, IslandExpansion.COLLAPSED -> CompactCall(state)
        IslandExpansion.EXPANDED -> ExpandedCall(state)
    }
}

@Composable
private fun CompactCall(state: IslandState.PhoneCall) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Pulsing green dot
        PulsingDot(color = IslandAccentGreen)

        Spacer(Modifier.width(8.dp))

        Text(
            text = state.callerName.ifBlank { state.callerNumber },
            color = IslandWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        // Quick call icon
        Icon(
            imageVector = if (state.isIncoming) Icons.Rounded.CallReceived else Icons.Rounded.Phone,
            contentDescription = null,
            tint = IslandAccentGreen,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ExpandedCall(state: IslandState.PhoneCall) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row — caller info
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(IslandMedGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = IslandWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = if (state.isIncoming) "Incoming Call" else formatDuration(state.duration),
                    color = IslandAccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = state.callerName.ifBlank { state.callerNumber },
                    color = IslandWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Decline
            CallActionButton(
                icon = Icons.Rounded.CallEnd,
                label = "Decline",
                tint = IslandAccentRed,
                background = IslandAccentRed.copy(alpha = 0.2f)
            )

            // Speaker (for active calls)
            if (!state.isIncoming) {
                CallActionButton(
                    icon = Icons.Rounded.VolumeUp,
                    label = "Speaker",
                    tint = IslandWhite,
                    background = IslandMedGray
                )
            }

            // Accept
            CallActionButton(
                icon = Icons.Rounded.Call,
                label = if (state.isIncoming) "Accept" else "Mute",
                tint = IslandAccentGreen,
                background = IslandAccentGreen.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = IslandWhite.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "call-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.7f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot-scale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(color, CircleShape)
    )
}

private fun formatDuration(seconds: Long): String {
    val m = TimeUnit.SECONDS.toMinutes(seconds)
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
