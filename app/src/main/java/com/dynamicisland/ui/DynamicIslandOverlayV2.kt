package com.dynamicisland.ui

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dynamicisland.animation.LiquidMorphAnimator
import com.dynamicisland.animation.glassmorphism
import com.dynamicisland.animation.motionBlurTrail
import com.dynamicisland.customization.AnimationSettings
import com.dynamicisland.customization.IslandShape
import com.dynamicisland.customization.islandShapeFor
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.rendering.*
import com.dynamicisland.stack.StackItem
import com.dynamicisland.stack.StackedIslandView
import com.dynamicisland.stack.stackExtraHeight
import com.dynamicisland.theme.IslandTheme
import com.dynamicisland.ui.islands.*
import com.dynamicisland.widgets.MiniControlCenter
import com.dynamicisland.notification.QuickReplyPopup
import com.dynamicisland.rendering.ParticleCanvas
import com.dynamicisland.rendering.ParticleEmitter
import com.dynamicisland.rendering.ParticleFactory
import com.dynamicisland.gesture.IslandGestureEngine
import com.dynamicisland.gesture.islandGestures

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║      DYNAMIC ISLAND OVERLAY V2 — Master UI           ║
 * ║  All rendering systems unified in a single tree      ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Layer order (bottom → top):
 *  1. Liquid morph background (glassmorphism)
 *  2. Breathing glow (animated outer ring)
 *  3. Ambient edge lighting (rim light)
 *  4. Stack content (primary + secondary)
 *  5. Particle canvas (fire-and-forget effects)
 *  6. Control center / quick-reply popup (overlay)
 */
@Composable
fun DynamicIslandOverlayV2(
    state: IslandState,
    expansion: IslandExpansion,
    stack: List<StackItem>,
    theme: IslandTheme,
    animSettings: AnimationSettings,
    shape: IslandShape,
    gestureEngine: IslandGestureEngine,
    showControlCenter: Boolean,
    showQuickReply: Boolean,
    quickReplySender: String,
    particleTrigger: String?,          // null = no new trigger; "call", "charging", etc.
    adaptiveAlpha: Float = 0f,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDotTap: (String) -> Unit,
    onPlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onControlCenterDismiss: () -> Unit,
    onQuickReplySend: (String) -> Unit,
    onQuickReplyDismiss: () -> Unit,
) {
    // ── Target geometry ───────────────────────────────────────────────────────
    val extraH = stackExtraHeight(stack, expansion == IslandExpansion.EXPANDED)

    val baseW: Dp; val baseH: Dp
    when {
        expansion == IslandExpansion.COLLAPSED -> { baseW = 120.dp; baseH = 34.dp }
        expansion == IslandExpansion.COMPACT   -> { baseW = targetCompactW(state); baseH = targetCompactH(state) }
        else                                   -> { baseW = targetExpandedW(state); baseH = targetExpandedH(state) }
    }
    val targetH = baseH + extraH.dp

    // ── Spring animations ─────────────────────────────────────────────────────
    val spring = spring<Dp>(
        dampingRatio = animSettings.springDamping,
        stiffness    = animSettings.springStiffness
    )
    val animW by animateDpAsState(baseW,   spring, label = "w")
    val animH by animateDpAsState(targetH, spring, label = "h")

    // Corner radius spring
    val animR by animateDpAsState(
        if (expansion == IslandExpansion.EXPANDED) 26.dp else 50.dp,
        spring, label = "r"
    )

    // Drag offset for elastic effect
    var dragVelocity by remember { mutableFloatStateOf(0f) }

    // ── Active particles ──────────────────────────────────────────────────────
    val activeEmitters = remember { mutableStateListOf<ParticleEmitter>() }

    // Trigger particles on state change
    LaunchedEffect(particleTrigger) {
        if (particleTrigger != null && animSettings.particlesEnabled) {
            val emitters = when (particleTrigger) {
                "call"     -> ParticleFactory.callAnswered()
                "charging" -> ParticleFactory.chargingStart()
                else       -> ParticleFactory.stateChange(Color(theme.accentColor.hashCode().toLong()))
            }
            activeEmitters.addAll(emitters)
        }
    }
    // Clean up finished emitters
    LaunchedEffect(activeEmitters.size) {
        kotlinx.coroutines.delay(2500)
        activeEmitters.removeAll { !it.active }
    }

    // ── Compose shape ─────────────────────────────────────────────────────────
    val composeShape = islandShapeFor(shape)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        // ── Main island pill ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(animW)
                .height(animH)
                // Glow layer
                .then(
                    if (animSettings.glowEnabled)
                        Modifier.breathingGlow(
                            color     = theme.accentColor,
                            intensity = glowIntensity(state),
                            layers    = 3
                        )
                    else Modifier
                )
                // Clip to shape
                .graphicsLayer {
                    clip     = true
                    this.shape = composeShape
                    shadowElevation = 32f
                    ambientShadowColor = android.graphics.Color.BLACK
                    spotShadowColor    = android.graphics.Color.BLACK
                }
                // Glass background
                .glassmorphism(
                    baseColor      = theme.baseColor,
                    shimmerEnabled = expansion == IslandExpansion.EXPANDED && animSettings.glowEnabled
                )
                // Edge rim light
                .then(
                    if (animSettings.glowEnabled)
                        Modifier.ambientEdgeLight(
                            topColor    = Color.White.copy(alpha = 0.2f),
                            bottomColor = theme.accentColor.copy(alpha = 0.1f)
                        )
                    else Modifier
                )
                // Motion blur
                .then(
                    if (animSettings.motionBlurEnabled && kotlin.math.abs(dragVelocity) > 10f)
                        Modifier.motionBlurTrail(dragVelocity)
                    else Modifier
                )
                // Charging energy effect
                .then(
                    if (state is IslandState.Charging && state.isCharging)
                        Modifier.chargingEnergyEffect(true, state.percentage, theme.accentColor)
                    else Modifier
                )
                // Gestures
                .islandGestures(gestureEngine),
            contentAlignment = Alignment.TopCenter
        ) {
            // ── Stack content ─────────────────────────────────────────────────
            if (stack.size > 1) {
                StackedIslandView(
                    stack       = stack,
                    isExpanded  = expansion == IslandExpansion.EXPANDED,
                    onDotTap    = onDotTap,
                    primaryContent = { item ->
                        IslandContentV2(
                            state   = item.state,
                            expansion = expansion,
                            theme   = theme,
                            onPlayPause     = onPlayPause,
                            onNextTrack     = onNextTrack,
                            onPreviousTrack = onPreviousTrack
                        )
                    },
                    secondaryContent = { item ->
                        SecondaryIslandContent(item)
                    }
                )
            } else {
                // Single activity
                AnimatedContent(
                    targetState  = Pair(state, expansion),
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), 0.88f)) togetherWith
                            fadeOut(tween(160))
                    },
                    label = "island-anim"
                ) { (s, e) ->
                    IslandContentV2(
                        state   = s,
                        expansion = e,
                        theme   = theme,
                        onPlayPause     = onPlayPause,
                        onNextTrack     = onNextTrack,
                        onPreviousTrack = onPreviousTrack
                    )
                }
            }

            // ── Particle overlay ──────────────────────────────────────────────
            if (animSettings.particlesEnabled && activeEmitters.isNotEmpty()) {
                ParticleCanvas(
                    modifier = Modifier.matchParentSize(),
                    emitters = activeEmitters
                )
            }
        }

        // ── Control center (drops below island) ───────────────────────────────
        MiniControlCenter(
            visible   = showControlCenter,
            onDismiss = onControlCenterDismiss
        )

        // ── Quick reply popup ─────────────────────────────────────────────────
        val notifState = state as? IslandState.Notification
        QuickReplyPopup(
            visible      = showQuickReply,
            appName      = notifState?.appName ?: "",
            senderName   = quickReplySender,
            onSend       = onQuickReplySend,
            onDismiss    = onQuickReplyDismiss
        )
    }
}

// ── Content router ────────────────────────────────────────────────────────────

@Composable
private fun IslandContentV2(
    state: IslandState,
    expansion: IslandExpansion,
    theme: IslandTheme,
    onPlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit
) {
    when (state) {
        is IslandState.Idle        -> IdleIsland()
        is IslandState.PhoneCall   -> CallIsland(state, expansion)
        is IslandState.NowPlaying  -> MusicIsland(state, expansion, onPlayPause, onNextTrack, onPreviousTrack)
        is IslandState.Notification -> NotificationIsland(state, expansion)
        is IslandState.Charging    -> ChargingIsland(state, expansion)
        is IslandState.Navigation  -> NavigationIsland(state, expansion)
        is IslandState.Timer       -> TimerIsland(state, expansion)
        is IslandState.LiveActivity -> LiveActivityIsland(state, expansion)
    }
}

@Composable
private fun SecondaryIslandContent(item: StackItem) {
    // Compact secondary — just shows a small label row
    IslandContentV2(
        state   = item.state,
        expansion = IslandExpansion.COMPACT,
        theme   = com.dynamicisland.theme.ThemeCatalog.OBSIDIAN,
        onPlayPause     = {},
        onNextTrack     = {},
        onPreviousTrack = {}
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun targetCompactW(s: IslandState) = when (s) {
    is IslandState.PhoneCall     -> 220.dp
    is IslandState.NowPlaying    -> 240.dp
    is IslandState.Notification  -> 280.dp
    is IslandState.Charging      -> 180.dp
    else                         -> 200.dp
}
private fun targetCompactH(s: IslandState) = when (s) {
    is IslandState.PhoneCall    -> 44.dp
    is IslandState.NowPlaying   -> 44.dp
    is IslandState.Notification -> 50.dp
    else                        -> 44.dp
}
private fun targetExpandedW(s: IslandState) = when (s) {
    is IslandState.NowPlaying   -> 340.dp
    is IslandState.PhoneCall    -> 320.dp
    else                        -> 320.dp
}
private fun targetExpandedH(s: IslandState) = when (s) {
    is IslandState.NowPlaying   -> 180.dp
    is IslandState.PhoneCall    -> 130.dp
    else                        -> 120.dp
}
private fun glowIntensity(s: IslandState) = when (s) {
    is IslandState.PhoneCall    -> 0.8f
    is IslandState.Charging     -> 0.6f
    is IslandState.NowPlaying   -> 0.4f
    else                        -> 0.3f
}
