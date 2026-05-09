package com.dynamicisland.layout

import android.content.Context
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║       ADAPTIVE LAYOUT ENGINE — v2.0                 ║
 * ║  Foldable · Tablet · Landscape island modes         ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ── Device class detection ────────────────────────────────────────────────────

enum class DeviceFormFactor {
    PHONE_PORTRAIT,    // Standard phone, portrait (default)
    PHONE_LANDSCAPE,   // Phone rotated landscape
    TABLET_PORTRAIT,   // Tablet in portrait
    TABLET_LANDSCAPE,  // Tablet in landscape (wide layout)
    FOLDABLE_FOLDED,   // Folded state (inner cover screen)
    FOLDABLE_UNFOLDED  // Unfolded book layout
}

/**
 * Detects the current device form factor from window + screen metrics.
 */
@Composable
fun rememberFormFactor(): DeviceFormFactor {
    val config = LocalConfiguration.current
    val sw    = config.smallestScreenWidthDp
    val w     = config.screenWidthDp
    val h     = config.screenHeightDp

    return remember(sw, w, h) {
        when {
            sw >= 600 && w >= 840  -> DeviceFormFactor.TABLET_LANDSCAPE
            sw >= 600              -> DeviceFormFactor.TABLET_PORTRAIT
            w > h                  -> DeviceFormFactor.PHONE_LANDSCAPE
            else                   -> DeviceFormFactor.PHONE_PORTRAIT
        }
    }
}

// ── Adaptive island layout spec ───────────────────────────────────────────────

data class IslandLayoutSpec(
    val pillWidth: Dp,
    val pillHeight: Dp,
    val compactWidth: Dp,
    val compactHeight: Dp,
    val expandedWidth: Dp,
    val expandedHeight: Dp,
    val topOffset: Dp,
    val horizontalGravity: HorizontalGravity
)

enum class HorizontalGravity { CENTER, LEFT, RIGHT, SPLIT }

/**
 * Returns the correct layout spec for each form factor.
 */
fun layoutSpecFor(formFactor: DeviceFormFactor): IslandLayoutSpec = when (formFactor) {

    DeviceFormFactor.PHONE_PORTRAIT -> IslandLayoutSpec(
        pillWidth     = 120.dp,  pillHeight = 34.dp,
        compactWidth  = 240.dp,  compactHeight = 44.dp,
        expandedWidth = 340.dp,  expandedHeight = 160.dp,
        topOffset     = 10.dp,
        horizontalGravity = HorizontalGravity.CENTER
    )

    DeviceFormFactor.PHONE_LANDSCAPE -> IslandLayoutSpec(
        // In landscape, island is smaller and docked to top-left
        pillWidth     = 100.dp,  pillHeight = 28.dp,
        compactWidth  = 200.dp,  compactHeight = 36.dp,
        expandedWidth = 280.dp,  expandedHeight = 120.dp,
        topOffset     = 6.dp,
        horizontalGravity = HorizontalGravity.LEFT
    )

    DeviceFormFactor.TABLET_PORTRAIT -> IslandLayoutSpec(
        pillWidth     = 140.dp,  pillHeight = 36.dp,
        compactWidth  = 280.dp,  compactHeight = 50.dp,
        expandedWidth = 400.dp,  expandedHeight = 180.dp,
        topOffset     = 12.dp,
        horizontalGravity = HorizontalGravity.CENTER
    )

    DeviceFormFactor.TABLET_LANDSCAPE -> IslandLayoutSpec(
        // Wide tablet: split into two floating pills
        pillWidth     = 140.dp,  pillHeight = 36.dp,
        compactWidth  = 260.dp,  compactHeight = 48.dp,
        expandedWidth = 380.dp,  expandedHeight = 170.dp,
        topOffset     = 12.dp,
        horizontalGravity = HorizontalGravity.SPLIT  // Two independent islands
    )

    DeviceFormFactor.FOLDABLE_FOLDED -> IslandLayoutSpec(
        // Cover screen — small and compact
        pillWidth     = 100.dp,  pillHeight = 28.dp,
        compactWidth  = 180.dp,  compactHeight = 36.dp,
        expandedWidth = 240.dp,  expandedHeight = 100.dp,
        topOffset     = 8.dp,
        horizontalGravity = HorizontalGravity.CENTER
    )

    DeviceFormFactor.FOLDABLE_UNFOLDED -> IslandLayoutSpec(
        // Unfolded inner display — larger, centered
        pillWidth     = 160.dp,  pillHeight = 40.dp,
        compactWidth  = 320.dp,  compactHeight = 54.dp,
        expandedWidth = 480.dp,  expandedHeight = 200.dp,
        topOffset     = 16.dp,
        horizontalGravity = HorizontalGravity.CENTER
    )
}

// ── Tablet split-view ─────────────────────────────────────────────────────────

/**
 * On wide tablets, the island can split into two: one left, one right.
 * LEFT → primary activity (call/media)
 * RIGHT → secondary info (time, notifications)
 */
@Composable
fun TabletSplitIsland(
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    spec: IslandLayoutSpec
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left pill
        Box(modifier = Modifier.width(spec.compactWidth)) {
            leftContent()
        }
        // Right pill
        Box(modifier = Modifier.width(spec.pillWidth)) {
            rightContent()
        }
    }
}

// ── WindowManager params factory ─────────────────────────────────────────────

/**
 * Builds WindowManager.LayoutParams adapted to the form factor.
 */
fun buildOverlayParams(
    context: Context,
    spec: IslandLayoutSpec
): WindowManager.LayoutParams {
    val density = context.resources.displayMetrics.density
    val topOffsetPx = (spec.topOffset.value * density).toInt()

    return WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = when (spec.horizontalGravity) {
            HorizontalGravity.CENTER -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            HorizontalGravity.LEFT   -> android.view.Gravity.TOP or android.view.Gravity.START
            HorizontalGravity.RIGHT  -> android.view.Gravity.TOP or android.view.Gravity.END
            HorizontalGravity.SPLIT  -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        }
        y = getStatusBarHeight(context) + topOffsetPx
    }
}

private fun getStatusBarHeight(context: Context): Int {
    val r = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (r > 0) context.resources.getDimensionPixelSize(r) else 0
}
