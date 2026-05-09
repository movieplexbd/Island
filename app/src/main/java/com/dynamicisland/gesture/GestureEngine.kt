package com.dynamicisland.gesture

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║        GESTURE ENGINE — v2.0                        ║
 * ║  Velocity · Prediction · Docking · Elastic drag     ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ── Gesture Events ────────────────────────────────────────────────────────────

sealed class IslandGesture {
    object Tap                               : IslandGesture()
    object DoubleTap                         : IslandGesture()
    object LongPress                         : IslandGesture()
    data class SwipeUp(val velocity: Float)  : IslandGesture()
    data class SwipeDown(val velocity: Float): IslandGesture()
    data class SwipeLeft(val velocity: Float): IslandGesture()
    data class SwipeRight(val velocity: Float): IslandGesture()
    data class Drag(val offset: Offset, val velocity: Velocity): IslandGesture()
    object PinchExpand                       : IslandGesture()
    object PinchCollapse                     : IslandGesture()
}

// ── Gesture Recognizer ────────────────────────────────────────────────────────

/**
 * Stateful gesture recognizer that emits [IslandGesture] events
 * with velocity tracking and intent prediction.
 */
class IslandGestureEngine {

    private val _events = MutableSharedFlow<IslandGesture>(extraBufferCapacity = 8)
    val events: SharedFlow<IslandGesture> = _events.asSharedFlow()

    // Velocity history for predictive intent
    private val velocityHistory = ArrayDeque<Float>(10)

    // Double-tap detection
    private var lastTapTime = 0L
    private val doubleTapThreshold = 300L  // ms

    // Drag state
    var isDragging by mutableStateOf(false)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < doubleTapThreshold) {
            _events.tryEmit(IslandGesture.DoubleTap)
            lastTapTime = 0  // Reset
        } else {
            _events.tryEmit(IslandGesture.Tap)
            lastTapTime = now
        }
    }

    fun onLongPress() {
        _events.tryEmit(IslandGesture.LongPress)
    }

    fun onDragStart() { isDragging = true }

    fun onDrag(offset: Offset, velocity: Velocity) {
        dragOffset += offset
        velocityHistory.addLast(velocity.y)
        if (velocityHistory.size > 10) velocityHistory.removeFirst()
        _events.tryEmit(IslandGesture.Drag(dragOffset, velocity))
    }

    fun onDragEnd(finalVelocity: Velocity) {
        isDragging = false
        val vy = finalVelocity.y
        val vx = finalVelocity.x

        when {
            vy < -300f -> _events.tryEmit(IslandGesture.SwipeUp(vy))
            vy >  300f -> _events.tryEmit(IslandGesture.SwipeDown(vy))
            vx < -300f -> _events.tryEmit(IslandGesture.SwipeLeft(vx))
            vx >  300f -> _events.tryEmit(IslandGesture.SwipeRight(vx))
        }

        dragOffset = Offset.Zero
        velocityHistory.clear()
    }

    /**
     * Predicts the likely intent from velocity history before the drag ends.
     * Useful for pre-loading the animation frame.
     */
    fun predictIntent(): GestureIntent {
        if (velocityHistory.isEmpty()) return GestureIntent.NONE

        val avgVelocity = velocityHistory.average().toFloat()
        val trend = velocityHistory.zipWithNext { a, b -> b - a }.average().toFloat()

        return when {
            avgVelocity < -200 && trend < 0  -> GestureIntent.WILL_SWIPE_UP
            avgVelocity >  200 && trend > 0  -> GestureIntent.WILL_SWIPE_DOWN
            avgVelocity < -100               -> GestureIntent.MIGHT_SWIPE_UP
            avgVelocity >  100               -> GestureIntent.MIGHT_SWIPE_DOWN
            else                             -> GestureIntent.NONE
        }
    }
}

enum class GestureIntent {
    NONE, WILL_SWIPE_UP, WILL_SWIPE_DOWN,
    MIGHT_SWIPE_UP, MIGHT_SWIPE_DOWN
}

// ── Smart Island Docking ──────────────────────────────────────────────────────

/**
 * Computes docking behavior — allows the user to drag the island
 * to different screen positions, with edge snapping.
 */
object IslandDockingEngine {

    data class DockPosition(val x: Float, val y: Float, val name: String)

    /**
     * Pre-defined dock positions (relative to screen fraction 0.0–1.0).
     */
    val dockPositions = listOf(
        DockPosition(0.5f, 0.05f, "Top Center"),    // Default
        DockPosition(0.5f, 0.92f, "Bottom Center"),  // Bottom
        DockPosition(0.1f, 0.5f,  "Left Middle"),
        DockPosition(0.9f, 0.5f,  "Right Middle")
    )

    /**
     * Snaps a free-drag position to the nearest dock point
     * if within [snapThreshold] (fraction of screen).
     */
    fun snapToNearest(
        dragX: Float, dragY: Float,
        screenW: Float, screenH: Float,
        snapThreshold: Float = 0.12f
    ): DockPosition? {
        val normX = dragX / screenW
        val normY = dragY / screenH

        return dockPositions.minByOrNull { dock ->
            val dx = dock.x - normX
            val dy = dock.y - normY
            Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }?.let { nearest ->
            val dx = nearest.x - normX
            val dy = nearest.y - normY
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist < snapThreshold) nearest else null
        }
    }

    fun toScreenCoords(dock: DockPosition, screenW: Float, screenH: Float): Pair<Float, Float> =
        Pair(dock.x * screenW, dock.y * screenH)
}

// ── Compose Modifier ──────────────────────────────────────────────────────────

/**
 * Attaches the full [IslandGestureEngine] to a composable.
 */
fun Modifier.islandGestures(engine: IslandGestureEngine): Modifier =
    pointerInput(engine) {
        detectTapGestures(
            onTap       = { engine.onTap() },
            onLongPress = { engine.onLongPress() }
        )
    }.pointerInput(engine) {
        detectDragGestures(
            onDragStart = { engine.onDragStart() },
            onDrag      = { change, dragAmount ->
                change.consume()
                engine.onDrag(dragAmount, Velocity.Zero)
            },
            onDragEnd   = { engine.onDragEnd(Velocity.Zero) },
            onDragCancel = { engine.onDragEnd(Velocity.Zero) }
        )
    }
