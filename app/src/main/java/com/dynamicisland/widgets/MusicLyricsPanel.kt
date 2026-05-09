package com.dynamicisland.widgets

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.model.IslandState
import com.dynamicisland.ui.IslandWhite
import kotlinx.coroutines.delay

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║     MUSIC LYRICS PANEL + ALBUM ART ANIMATOR — v2.0  ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ─────────────────────────────────────────────────────────────────────────────
//  INTERACTIVE LYRICS PANEL
// ─────────────────────────────────────────────────────────────────────────────

data class LyricLine(
    val text: String,
    val startMs: Long,   // Timestamp when this line is active
    val endMs: Long
)

/**
 * Scrolling, highlighted lyrics panel that syncs to music progress.
 * Auto-scrolls to the current line. Active line is full opacity + slightly larger.
 */
@Composable
fun LyricsPanel(
    modifier: Modifier = Modifier,
    lyrics: List<LyricLine>,
    progressMs: Long,  // Current playback position in ms
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "♪ No lyrics available",
                color = IslandWhite.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val activeLine = lyrics.indexOfLast { it.startMs <= progressMs }
    val listState  = rememberLazyListState()

    // Auto-scroll to active line
    LaunchedEffect(activeLine) {
        if (activeLine >= 0) {
            listState.animateScrollToItem(
                index  = (activeLine - 1).coerceAtLeast(0),
                scrollOffset = 0
            )
        }
    }

    LazyColumn(
        state   = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(lyrics) { idx, line ->
            val isActive = idx == activeLine
            val isFuture = idx > activeLine

            val alpha by animateFloatAsState(
                targetValue   = when { isActive -> 1.0f; isFuture -> 0.25f; else -> 0.45f },
                animationSpec = tween(300),
                label         = "lyric-alpha-$idx"
            )
            val scale by animateFloatAsState(
                targetValue   = if (isActive) 1.05f else 1.0f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                label         = "lyric-scale-$idx"
            )

            Text(
                text       = line.text,
                color      = IslandWhite.copy(alpha = alpha),
                fontSize   = if (isActive) 15.sp else 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ANIMATED ALBUM ART
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Album art with multiple animated effects:
 *  - Slow rotation when playing (vinyl record feel)
 *  - Breathing scale pulse
 *  - Color-extracting glow ring
 *  - Pause animation on stop
 */
@Composable
fun AnimatedAlbumArt(
    modifier: Modifier = Modifier,
    bitmap: android.graphics.Bitmap?,
    isPlaying: Boolean,
    accentColor: Color = Color(0xFF007AFF)
) {
    val rotationSpeed by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0f,
        animationSpec = tween(600),
        label         = "album-rotate-speed"
    )

    val rotation = rememberInfiniteTransition(label = "album-art-rotation")
    val angle by rotation.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            tween(20_000, easing = LinearEasing)
        ),
        label = "angle"
    )

    val pulseTrans = rememberInfiniteTransition(label = "album-pulse")
    val pulseScale by pulseTrans.animateFloat(
        initialValue = 0.97f,
        targetValue  = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = EaseInOutSine), RepeatMode.Reverse
        ),
        label = "pulse-scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.35f * if (isPlaying) pulseScale else 0.3f),
                        Color.Transparent
                    ),
                    radius = size.minDimension * 0.8f
                ),
                radius = size.minDimension * 0.8f
            )
        }

        // Vinyl grooves ring (outer decorative circle)
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(angle * rotationSpeed)
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radii = listOf(0.48f, 0.42f, 0.36f, 0.30f)
            radii.forEach { r ->
                drawCircle(
                    color  = Color.White.copy(alpha = 0.06f),
                    radius = size.minDimension / 2f * r,
                    center = Offset(cx, cy),
                    style  = androidx.compose.ui.graphics.drawscope.Stroke(1f)
                )
            }
        }

        // Album art image
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album Art",
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .scale(if (isPlaying) pulseScale else 1.0f)
                    .clip(RoundedCornerShape(percent = 50))
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                accentColor.copy(alpha = 0.6f),
                                accentColor.copy(alpha = 0.2f),
                                accentColor.copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("♪", fontSize = 28.sp, color = IslandWhite.copy(alpha = 0.7f))
            }
        }

        // Center spindle dot
        Canvas(modifier = Modifier.size(14.dp)) {
            drawCircle(
                color  = Color.White.copy(alpha = 0.9f),
                radius = size.minDimension / 2 * 0.4f
            )
            drawCircle(
                color  = Color.Black.copy(alpha = 0.5f),
                radius = size.minDimension / 2 * 0.15f
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  FULL MUSIC CARD (lyrics + album art combined)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FullMusicCard(
    state: IslandState.NowPlaying,
    lyrics: List<LyricLine> = emptyList(),
    progressMs: Long = 0L
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top: album art + info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedAlbumArt(
                modifier    = Modifier.size(60.dp),
                bitmap      = state.albumArt,
                isPlaying   = state.isPlaying
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.title,
                    color = IslandWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist,
                    color = IslandWhite.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }

        // Lyrics panel
        if (lyrics.isNotEmpty()) {
            LyricsPanel(
                modifier    = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                lyrics      = lyrics,
                progressMs  = progressMs
            )
        }
    }
}
