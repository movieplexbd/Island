package com.dynamicisland.ui.islands

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.ui.*

/**
 * Music / media playback island.
 * Compact: album art thumb + title + play/pause.
 * Expanded: full player card with progress.
 */
@Composable
fun MusicIsland(
    state: IslandState.NowPlaying,
    expansion: IslandExpansion,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    when (expansion) {
        IslandExpansion.COLLAPSED,
        IslandExpansion.COMPACT  -> CompactMusic(state, onPlayPause)
        IslandExpansion.EXPANDED -> ExpandedMusic(state, onPlayPause, onNext, onPrevious)
    }
}

@Composable
private fun CompactMusic(state: IslandState.NowPlaying, onPlayPause: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Waveform animation or album art
        if (state.albumArt != null) {
            androidx.compose.foundation.Image(
                bitmap = state.albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            MusicWaveform(isPlaying = state.isPlaying)
        }

        Spacer(Modifier.width(8.dp))

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
                text = state.artist,
                color = IslandWhite.copy(alpha = 0.5f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onPlayPause, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = IslandWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ExpandedMusic(
    state: IslandState.NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row: album art + title/artist
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.albumArt != null) {
                androidx.compose.foundation.Image(
                    bitmap = state.albumArt.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(IslandMedGray, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    MusicWaveform(isPlaying = state.isPlaying)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    color = IslandWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist,
                    color = IslandWhite.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Progress bar
        ProgressBar(progress = state.progress)

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicControlButton(
                icon = Icons.Rounded.SkipPrevious,
                size = 24.dp,
                onClick = onPrevious
            )

            // Play / Pause (larger)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(IslandWhite.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = IslandWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            MusicControlButton(
                icon = Icons.Rounded.SkipNext,
                size = 24.dp,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(IslandWhite.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(IslandWhite.copy(alpha = 0.8f))
        )
    }
}

@Composable
private fun MusicControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size + 12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IslandWhite.copy(alpha = 0.8f),
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Animated music waveform bars — plays when music is active.
 */
@Composable
fun MusicWaveform(isPlaying: Boolean) {
    val bars = 3
    Row(
        modifier = Modifier
            .width(20.dp)
            .height(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            val transition = rememberInfiniteTransition(label = "wave$index")
            val height by transition.animateFloat(
                initialValue = 3f,
                targetValue = 14f,
                animationSpec = if (isPlaying) {
                    infiniteRepeatable(
                        animation = tween(
                            durationMillis = 300 + (index * 100),
                            easing = EaseInOutSine
                        ),
                        repeatMode = RepeatMode.Reverse
                    )
                } else {
                    snap()
                },
                label = "bar-height-$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (isPlaying) height.dp else 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(IslandAccentBlue)
            )
        }
    }
}
