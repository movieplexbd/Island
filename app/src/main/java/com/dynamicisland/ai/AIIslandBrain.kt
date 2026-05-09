package com.dynamicisland.ai

import android.content.Context
import android.util.Log
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.model.IslandState
import com.dynamicisland.notification.SmartNotificationFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║           AI ISLAND BRAIN — v2.0                     ║
 * ║  Context-aware, predictive, self-learning overlay    ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Core AI layer that sits between raw events and the island ViewModel.
 * Responsibilities:
 *  1. Priority scoring — decides which state wins when multiple compete
 *  2. Context awareness — knows time of day, usage patterns, recent apps
 *  3. Predictive pre-loading — anticipates the NEXT likely activity
 *  4. Smart suppression — silences low-value interruptions automatically
 *  5. App-aware theming hints — feeds [ThemeEngine] with context signals
 */
class AIIslandBrain(private val context: Context) {

    companion object {
        private const val TAG = "AIIslandBrain"

        // Priority weights (0–100)
        private const val PRIORITY_CALL          = 100
        private const val PRIORITY_ALARM         = 90
        private const val PRIORITY_NAVIGATION    = 80
        private const val PRIORITY_MEDIA         = 60
        private const val PRIORITY_TIMER         = 55
        private const val PRIORITY_CHARGING      = 40
        private const val PRIORITY_NOTIFICATION  = 30
        private const val PRIORITY_SYSTEM        = 10
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Rolling window of recent events for pattern analysis (last 50)
    private val eventHistory = LinkedList<TimestampedEvent>()

    // App usage frequency map — learns over time
    private val appUsageFrequency = mutableMapOf<String, Int>()

    // Notification suppression cooldowns — package → suppress-until epoch
    private val suppressionMap = mutableMapOf<String, Long>()

    // Predicted next state (pre-computed asynchronously)
    private val _predictedNextState = MutableStateFlow<PredictedActivity?>(null)
    val predictedNextState: StateFlow<PredictedActivity?> = _predictedNextState.asStateFlow()

    // Emits scored, deduplicated events ready for the ViewModel
    private val _processedEvent = MutableSharedFlow<ScoredEvent>(extraBufferCapacity = 16)
    val processedEvent: SharedFlow<ScoredEvent> = _processedEvent.asSharedFlow()

    private val notificationFilter = SmartNotificationFilter(context)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submit a raw event. The brain scores it, checks suppression,
     * merges with context, and emits a [ScoredEvent] if it passes.
     */
    fun submitEvent(event: IslandEvent, sourcePackage: String = "") {
        scope.launch {
            val scored = scoreEvent(event, sourcePackage)
            if (scored != null) {
                recordHistory(event, scored.priority)
                _processedEvent.emit(scored)
                updatePrediction(event, sourcePackage)
            } else {
                Log.d(TAG, "Event suppressed: $event (pkg=$sourcePackage)")
            }
        }
    }

    /**
     * Records app usage for frequency-based learning.
     * Call this whenever an app comes to the foreground.
     */
    fun recordAppUsage(packageName: String) {
        appUsageFrequency[packageName] = (appUsageFrequency[packageName] ?: 0) + 1
    }

    /**
     * Suppress all non-critical notifications from a package for [durationMs].
     * e.g. right after dismissing 3 consecutive notifications from same app.
     */
    fun suppressPackage(packageName: String, durationMs: Long = 60_000L) {
        suppressionMap[packageName] = System.currentTimeMillis() + durationMs
        Log.d(TAG, "Suppressing $packageName for ${durationMs / 1000}s")
    }

    fun destroy() {
        scope.cancel()
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private suspend fun scoreEvent(event: IslandEvent, pkg: String): ScoredEvent? {
        // Hard-block suppressed packages
        if (pkg.isNotBlank() && isPackageSuppressed(pkg)) return null

        val basePriority = when (event) {
            is IslandEvent.ShowCall             -> PRIORITY_CALL
            is IslandEvent.MediaUpdate          -> PRIORITY_MEDIA
            is IslandEvent.NotificationReceived -> scoreNotification(event, pkg)
            is IslandEvent.ChargingUpdate       -> PRIORITY_CHARGING
            else                                -> PRIORITY_SYSTEM
        }

        if (basePriority < 0) return null // Filtered out

        // Context modifiers
        val contextBoost = computeContextBoost(event, pkg)
        val finalPriority = (basePriority + contextBoost).coerceIn(0, 100)

        return ScoredEvent(
            event    = event,
            priority = finalPriority,
            context  = buildContext(pkg)
        )
    }

    private suspend fun scoreNotification(
        event: IslandEvent.NotificationReceived,
        pkg: String
    ): Int {
        // Use the smart filter for ML-style scoring
        val filterResult = notificationFilter.evaluate(event)
        return when (filterResult) {
            SmartNotificationFilter.Result.SHOW_HIGH    -> PRIORITY_NOTIFICATION + 15
            SmartNotificationFilter.Result.SHOW_NORMAL  -> PRIORITY_NOTIFICATION
            SmartNotificationFilter.Result.SHOW_LOW     -> PRIORITY_NOTIFICATION - 10
            SmartNotificationFilter.Result.SUPPRESS     -> -1
        }
    }

    private fun computeContextBoost(event: IslandEvent, pkg: String): Int {
        var boost = 0

        // Boost frequently used apps
        val freq = appUsageFrequency[pkg] ?: 0
        boost += min(10, freq / 5)

        // Boost during typical active hours (8am–10pm)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour in 8..22) boost += 5

        // Boost if this is the currently open app (reduce interruption threshold)
        // (Would hook into AccessibilityService in full production)

        return boost
    }

    private fun isPackageSuppressed(pkg: String): Boolean {
        val until = suppressionMap[pkg] ?: return false
        return System.currentTimeMillis() < until
    }

    // ── Prediction Engine ────────────────────────────────────────────────────

    private fun updatePrediction(event: IslandEvent, pkg: String) {
        scope.launch {
            delay(300) // Let current event settle first
            val prediction = predictNext(event, pkg)
            _predictedNextState.value = prediction
        }
    }

    private fun predictNext(lastEvent: IslandEvent, pkg: String): PredictedActivity? {
        // Pattern-based heuristics (would be ML model in production)
        return when {
            lastEvent is IslandEvent.ShowCall && !lastEvent.isIncoming -> {
                // After an outgoing call, user likely goes back to the last app
                PredictedActivity(
                    type        = PredictedType.MEDIA,
                    confidence  = 0.45f,
                    packageHint = pkg,
                    reason      = "Post-call media resumption pattern"
                )
            }
            lastEvent is IslandEvent.ChargingUpdate && lastEvent.isCharging -> {
                PredictedActivity(
                    type        = PredictedType.IDLE_LONG,
                    confidence  = 0.70f,
                    packageHint = null,
                    reason      = "Charging typically precedes extended idle"
                )
            }
            lastEvent is IslandEvent.MediaUpdate && !lastEvent.isPlaying -> {
                PredictedActivity(
                    type        = PredictedType.NOTIFICATION,
                    confidence  = 0.38f,
                    packageHint = pkg,
                    reason      = "Paused media often followed by messaging"
                )
            }
            else -> null
        }
    }

    // ── History ──────────────────────────────────────────────────────────────

    private fun recordHistory(event: IslandEvent, priority: Int) {
        if (eventHistory.size >= 50) eventHistory.removeFirst()
        eventHistory.addLast(TimestampedEvent(event, priority, System.currentTimeMillis()))
    }

    private fun buildContext(pkg: String) = IslandContext(
        packageName     = pkg,
        usageFrequency  = appUsageFrequency[pkg] ?: 0,
        recentEventCount = eventHistory.count {
            System.currentTimeMillis() - it.timestamp < 30_000L
        },
        hourOfDay       = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    )
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class ScoredEvent(
    val event: IslandEvent,
    val priority: Int,         // 0–100
    val context: IslandContext
)

data class IslandContext(
    val packageName: String,
    val usageFrequency: Int,
    val recentEventCount: Int,
    val hourOfDay: Int
)

data class PredictedActivity(
    val type: PredictedType,
    val confidence: Float,     // 0.0–1.0
    val packageHint: String?,
    val reason: String
)

enum class PredictedType { CALL, MEDIA, NOTIFICATION, CHARGING, IDLE_LONG }

private data class TimestampedEvent(
    val event: IslandEvent,
    val priority: Int,
    val timestamp: Long
)
