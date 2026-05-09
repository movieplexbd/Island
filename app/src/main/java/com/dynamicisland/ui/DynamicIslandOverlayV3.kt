package com.dynamicisland.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import com.dynamicisland.animation.motionBlurTrail
import com.dynamicisland.customization.AnimationSettings
import com.dynamicisland.customization.IslandShape
import com.dynamicisland.customization.islandShapeFor
import com.dynamicisland.gesture.IslandGestureEngine
import com.dynamicisland.gesture.islandGestures
import com.dynamicisland.model.*
import com.dynamicisland.rendering.*
import com.dynamicisland.stack.StackItem
import com.dynamicisland.stack.StackedIslandView
import com.dynamicisland.stack.stackExtraHeight
import com.dynamicisland.theme.IslandTheme
import com.dynamicisland.ui.islands.*
import com.dynamicisland.notification.QuickReplyPopup
import com.dynamicisland.widgets.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║     DYNAMIC ISLAND OVERLAY V3 — Master Compositor   ║
 * ║  Split-pill · V3 islands · Gradient themes          ║
 * ║  Sensor-adaptive glow · Offline badge               ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Layer order (bottom → top):
 *  1. Liquid morph background (glassmorphism + gradient)
 *  2. Breathing glow (sensor-adaptive intensity)
 *  3. Ambient edge rim light
 *  4. Stack / split content
 *  5. Particle canvas
 *  6. Popups (control center, quick reply, alarms)
 *  7. Offline indicator badge
 */
@Composable
fun DynamicIslandOverlayV3(
    state: IslandState,
    expansion: IslandExpansion,
    secondaryState: IslandState?,          // V3: split-pill secondary
    stack: List<StackItem>,
    theme: IslandTheme,
    animSettings: AnimationSettings,
    shape: IslandShape,
    gestureEngine: IslandGestureEngine,
    showControlCenter: Boolean,
    showQuickReply: Boolean,
    quickReplySender: String,
    particleTrigger: String?,
    adaptiveAlpha: Float = 0f,
    isOffline: Boolean = false,           // V3: offline indicator
    autoBrightnessHint: Float = 0.5f,    // V3: from ambient light sensor
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
    onAlarmSnooze: () -> Unit,
    onAlarmDismiss: () -> Unit,
) {
    val isSplit = expansion == IslandExpansion.SPLIT && secondaryState != null

    // ── Geometry ──────────────────────────────────────────────────────────────
    val extraH  = stackExtraHeight(stack, expansion == IslandExpansion.EXPANDED)
    val baseW: Dp; val baseH: Dp
    when {
        isSplit                                    -> { baseW = 340.dp; baseH = 44.dp }
        expansion == IslandExpansion.COLLAPSED     -> { baseW = 120.dp; baseH = 34.dp }
        expansion == IslandExpansion.COMPACT       -> { baseW = targetCompactW(state); baseH = targetCompactH(state) }
        else                                       -> { baseW = targetExpandedW(state); baseH = targetExpandedH(state) }
    }
    val targetH = baseH + extraH.dp

    // ── Springs ───────────────────────────────────────────────────────────────
    val spring = spring<Dp>(dampingRatio = animSettings.springDamping, stiffness = animSettings.springStiffness)
    val animW by animateDpAsState(baseW,   spring, label = "w")
    val animH by animateDpAsState(targetH, spring, label = "h")
    val animR by animateDpAsState(
        if (expansion == IslandExpansion.EXPANDED) 26.dp else 50.dp, spring, label = "r")

    // ── Particles ─────────────────────────────────────────────────────────────
    val activeEmitters = remember { mutableStateListOf<ParticleEmitter>() }
    LaunchedEffect(particleTrigger) {
        if (particleTrigger != null && animSettings.particlesEnabled) {
            val emitters = when (particleTrigger) {
                "call"     -> ParticleFactory.callAnswered()
                "charging" -> ParticleFactory.chargingStart()
                "weather"  -> ParticleFactory.stateChange(Color(0xFF64B5F6))
                "steps"    -> ParticleFactory.stateChange(Color(0xFF4CAF50))
                "alarm"    -> ParticleFactory.stateChange(Color(0xFFFFB700))
                else       -> ParticleFactory.stateChange(Color(theme.accentColor.hashCode().toLong()))
            }
            activeEmitters.addAll(emitters)
        }
    }
    LaunchedEffect(activeEmitters.size) {
        kotlinx.coroutines.delay(2500)
        activeEmitters.removeAll { !it.active }
    }

    val composeShape = islandShapeFor(shape)

    // ── Sensor-adaptive glow intensity ────────────────────────────────────────
    val effectiveGlowIntensity = glowIntensity(state) * autoBrightnessHint

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        // ── Main island ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(animW)
                .height(animH)
                .then(
                    if (animSettings.glowEnabled)
                        Modifier.breathingGlow(
                            color     = theme.accentColor,
                            intensity = effectiveGlowIntensity,
                            layers    = 3
                        )
                    else Modifier
                )
                .graphicsLayer {
                    clip                = true
                    this.shape          = composeShape
                    shadowElevation     = 32f
                    ambientShadowColor  = Color.Black
                    spotShadowColor     = Color.Black
                }
                // V3: gradient glassmorphism
                .then(
                    if (theme.gradientColors.size >= 2)
                        Modifier.background(
                            Brush.linearGradient(theme.gradientColors),
                            shape = composeShape
                        )
                    else Modifier
                )
                .glassmorphism(
                    baseColor      = theme.baseColor,
                    shimmerEnabled = expansion == IslandExpansion.EXPANDED && animSettings.glowEnabled
                )
                .then(
                    if (animSettings.glowEnabled)
                        Modifier.ambientEdgeLight(
                            topColor    = Color.White.copy(alpha = 0.2f),
                            bottomColor = theme.accentColor.copy(alpha = 0.1f)
                        )
                    else Modifier
                )
                .then(
                    if (state is IslandState.Charging && state.isCharging)
                        Modifier.chargingEnergyEffect(true, state.percentage, theme.accentColor)
                    else Modifier
                )
                .islandGestures(gestureEngine),
            contentAlignment = Alignment.TopCenter
        ) {
            // ── Split-pill layout ─────────────────────────────────────────────
            if (isSplit && secondaryState != null) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    // Primary (left)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        IslandContentV3(state, IslandExpansion.COMPACT, theme,
                            onPlayPause, onNextTrack, onPreviousTrack, onAlarmSnooze, onAlarmDismiss)
                    }
                    // Divider
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.5f)
                        .background(Color.White.copy(alpha = 0.2f)))
                    // Secondary (right)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        IslandContentV3(secondaryState, IslandExpansion.COMPACT, theme,
                            {}, {}, {}, {}, {})
                    }
                }
            }
            // ── Stack layout ──────────────────────────────────────────────────
            else if (stack.size > 1) {
                StackedIslandView(
                    stack      = stack,
                    isExpanded = expansion == IslandExpansion.EXPANDED,
                    onDotTap   = onDotTap,
                    primaryContent = { item ->
                        IslandContentV3(item.state, expansion, theme,
                            onPlayPause, onNextTrack, onPreviousTrack, onAlarmSnooze, onAlarmDismiss)
                    },
                    secondaryContent = { item ->
                        IslandContentV3(item.state, IslandExpansion.COMPACT,
                            com.dynamicisland.theme.ThemeCatalog.OBSIDIAN,
                            {}, {}, {}, {}, {})
                    }
                )
            }
            // ── Single activity ───────────────────────────────────────────────
            else {
                AnimatedContent(
                    targetState   = Triple(state, expansion, theme.id),
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), 0.88f)) togetherWith fadeOut(tween(160))
                    },
                    label = "island-v3"
                ) { (s, e, _) ->
                    IslandContentV3(s, e, theme, onPlayPause, onNextTrack, onPreviousTrack,
                        onAlarmSnooze, onAlarmDismiss)
                }
            }

            // ── Particles ─────────────────────────────────────────────────────
            if (animSettings.particlesEnabled && activeEmitters.isNotEmpty()) {
                ParticleCanvas(modifier = Modifier.matchParentSize(), emitters = activeEmitters)
            }

            // ── V3: Offline badge ─────────────────────────────────────────────
            if (isOffline) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Box(modifier = Modifier.size(7.dp).background(Color(0xFFFFB300),
                        RoundedCornerShape(3.5.dp)))
                }
            }
        }

        // ── Popups ────────────────────────────────────────────────────────────
        MiniControlCenter(visible = showControlCenter, onDismiss = onControlCenterDismiss)

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

// ── V3 Content Router ─────────────────────────────────────────────────────────

@Composable
fun IslandContentV3(
    state: IslandState,
    expansion: IslandExpansion,
    theme: IslandTheme,
    onPlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onAlarmSnooze: () -> Unit,
    onAlarmDismiss: () -> Unit,
) {
    when (state) {
        is IslandState.Idle            -> IdleIsland()
        is IslandState.PhoneCall       -> CallIsland(state, expansion)
        is IslandState.NowPlaying      -> MusicIsland(state, expansion, onPlayPause, onNextTrack, onPreviousTrack)
        is IslandState.Notification    -> NotificationIsland(state, expansion)
        is IslandState.Charging        -> ChargingIsland(state, expansion)
        is IslandState.Navigation      -> NavigationIsland(state, expansion)
        is IslandState.Timer           -> TimerIsland(state, expansion)
        is IslandState.LiveActivity    -> LiveActivityIsland(state, expansion)
        // V3
        is IslandState.Weather         -> WeatherIslandV3(state, expansion)
        is IslandState.StepCounter     -> StepCounterIsland(state, expansion)
        is IslandState.Alarm           -> AlarmIsland(state, expansion, onAlarmSnooze, onAlarmDismiss)
        is IslandState.FocusMode       -> FocusModeIsland(state, expansion)
        is IslandState.ClipboardSnippet -> ClipboardIsland(state)
        is IslandState.SleepScore      -> SleepScoreIsland(state, expansion)
        is IslandState.SportScore      -> SportScoreIsland(state, expansion)
        is IslandState.VoiceAssist     -> VoiceAssistIsland(state)
        is IslandState.DownloadProgress -> DownloadIsland(state)
        is IslandState.ScreenRecording -> ScreenRecordingIsland(state)
    }
}

// ── Geometry helpers ──────────────────────────────────────────────────────────

private fun targetCompactW(s: IslandState) = when (s) {
    is IslandState.PhoneCall      -> 220.dp
    is IslandState.NowPlaying     -> 240.dp
    is IslandState.Notification   -> 280.dp
    is IslandState.Charging       -> 190.dp
    is IslandState.Weather        -> 220.dp
    is IslandState.StepCounter    -> 200.dp
    is IslandState.Alarm          -> 230.dp
    is IslandState.FocusMode      -> 200.dp
    is IslandState.SportScore     -> 280.dp
    is IslandState.VoiceAssist    -> 240.dp
    is IslandState.DownloadProgress -> 260.dp
    is IslandState.ScreenRecording -> 200.dp
    else                          -> 200.dp
}

private fun targetCompactH(s: IslandState) = when (s) {
    is IslandState.PhoneCall    -> 44.dp
    is IslandState.NowPlaying   -> 44.dp
    is IslandState.Notification -> 50.dp
    is IslandState.Weather      -> 44.dp
    else                        -> 44.dp
}

private fun targetExpandedW(s: IslandState) = when (s) {
    is IslandState.NowPlaying   -> 340.dp
    is IslandState.PhoneCall    -> 320.dp
    is IslandState.Weather      -> 340.dp
    is IslandState.StepCounter  -> 320.dp
    is IslandState.SleepScore   -> 320.dp
    is IslandState.FocusMode    -> 320.dp
    else                        -> 320.dp
}

private fun targetExpandedH(s: IslandState) = when (s) {
    is IslandState.NowPlaying   -> 180.dp
    is IslandState.PhoneCall    -> 130.dp
    is IslandState.Weather      -> 200.dp
    is IslandState.StepCounter  -> 130.dp
    is IslandState.SleepScore   -> 150.dp
    is IslandState.FocusMode    -> 120.dp
    is IslandState.Alarm        -> 120.dp
    else                        -> 120.dp
}

private fun glowIntensity(s: IslandState) = when (s) {
    is IslandState.PhoneCall      -> 0.8f
    is IslandState.Alarm          -> 0.9f
    is IslandState.Charging       -> 0.6f
    is IslandState.NowPlaying     -> 0.4f
    is IslandState.VoiceAssist    -> 0.7f
    is IslandState.ScreenRecording -> 0.75f
    is IslandState.FocusMode      -> 0.5f
    else                          -> 0.3f
}
