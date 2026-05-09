package com.dynamicisland.stack

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║        STACKED ISLAND VIEW — v2.0                   ║
 * ║  Multi-activity split view with dot navigation      ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Layout modes:
 *  DOTS     → primary content + small colored dots for secondaries
 *  SPLIT    → primary + 1 compact secondary stacked vertically
 *  CAROUSEL → swipe through all activities
 */
@Composable
fun StackedIslandView(
    stack: List<StackItem>,
    isExpanded: Boolean,
    onDotTap: (String) -> Unit,
    primaryContent: @Composable (StackItem) -> Unit,
    secondaryContent: @Composable (StackItem) -> Unit
) {
    if (stack.isEmpty()) return

    val primary   = stack.first()
    val secondary = stack.drop(1)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Primary content ──────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            primaryContent(primary)
        }

        // ── Secondary stack ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible   = secondary.isNotEmpty(),
            enter     = fadeIn() + expandVertically(),
            exit      = fadeOut() + shrinkVertically()
        ) {
            if (isExpanded && secondary.isNotEmpty()) {
                // Split-view: show first secondary as compact card
                Column {
                    StackDivider()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        secondaryContent(secondary.first())
                    }
                }
            } else {
                // Dot mode: show colored indicator dots
                StackDotRow(
                    items    = secondary,
                    onDotTap = onDotTap
                )
            }
        }
    }
}

@Composable
private fun StackDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(horizontal = 16.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

/**
 * Row of colored dots representing stacked secondary activities.
 * Each dot is tappable to focus that activity.
 */
@Composable
private fun StackDotRow(
    items: List<StackItem>,
    onDotTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.take(3).forEachIndexed { index, item ->
            StackDot(
                item     = item,
                index    = index,
                onClick  = { onDotTap(item.id) }
            )
            if (index < items.size - 1) Spacer(Modifier.width(4.dp))
        }
        // If more than 3, show "+N"
        if (items.size > 3) {
            Spacer(Modifier.width(4.dp))
            OverflowDot(count = items.size - 3)
        }
    }
}

@Composable
private fun StackDot(
    item: StackItem,
    index: Int,
    onClick: () -> Unit
) {
    val color = Color(item.accentColor)

    val pulse = rememberInfiniteTransition(label = "dot-pulse-$index")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800 + index * 200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale-$index"
    )

    Box(
        modifier = Modifier
            .size((8 + (2 - index).coerceAtLeast(0)).dp)  // Slight size taper
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun OverflowDot(count: Int) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.3f))
    )
}

/**
 * Determines the optimal layout height for the stack.
 * Returns extra dp needed beyond the primary island height.
 */
fun stackExtraHeight(stack: List<StackItem>, isExpanded: Boolean): Int {
    if (stack.size <= 1) return 0
    return when {
        isExpanded && stack.size >= 2 -> 52  // Split view: extra card + divider
        stack.size >= 2               ->  16  // Dot row
        else                          ->   0
    }
}
