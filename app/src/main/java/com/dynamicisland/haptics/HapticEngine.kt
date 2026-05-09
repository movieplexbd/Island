package com.dynamicisland.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║         HAPTIC ENGINE — v2.0                        ║
 * ║  Contextual haptic feedback for every island event  ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Uses the modern VibrationEffect API for precise,
 * iOS-quality haptic feedback on supported devices.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Whether haptics are enabled globally
    var enabled: Boolean = true

    // Intensity multiplier 0.0–1.0
    var intensity: Float = 1.0f

    // ── Haptic patterns ──────────────────────────────────────────────────────

    /** Light tap — used on expand/collapse */
    fun lightTap() = play(HapticPattern.LIGHT_TAP)

    /** Medium tap — used on long press */
    fun mediumTap() = play(HapticPattern.MEDIUM_TAP)

    /** Heavy impact — used on state change, call ring */
    fun heavyImpact() = play(HapticPattern.HEAVY_IMPACT)

    /** Success pattern — call answered, charging connected */
    fun success() = play(HapticPattern.SUCCESS)

    /** Warning — low battery, missed call */
    fun warning() = play(HapticPattern.WARNING)

    /** Error — permission denied, network error */
    fun error() = play(HapticPattern.ERROR)

    /** Notification arrive — soft double tap */
    fun notificationTap() = play(HapticPattern.NOTIFICATION)

    /** Dismiss swipe — crisp tick */
    fun dismissSwipe() = play(HapticPattern.DISMISS_SWIPE)

    /** Drag feedback — rhythmic ticking as user drags */
    fun dragTick() = play(HapticPattern.DRAG_TICK)

    /** Magnetic snap — satisfying click when island snaps to edge */
    fun magneticSnap() = play(HapticPattern.MAGNETIC_SNAP)

    /** Charging connected — distinctive two-pulse */
    fun chargingConnect() = play(HapticPattern.CHARGING_CONNECT)

    /** Incoming call ring pattern */
    fun incomingCall() = play(HapticPattern.CALL_RING)

    /** Music beat — very subtle, synced to BPM */
    fun musicBeat() = play(HapticPattern.MUSIC_BEAT)

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun play(pattern: HapticPattern) {
        if (!enabled || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val scaledAmplitude = (pattern.amplitude * intensity).toInt().coerceIn(1, 255)
            vibrator.vibrate(
                when {
                    // Waveform pattern
                    pattern.timings != null -> VibrationEffect.createWaveform(
                        pattern.timings, pattern.amplitudes ?: IntArray(pattern.timings.size) { scaledAmplitude },
                        -1  // No repeat
                    )
                    // Predefined effect (if available)
                    pattern.predefined != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                        runCatching {
                            VibrationEffect.createPredefined(pattern.predefined)
                        }.getOrElse {
                            VibrationEffect.createOneShot(pattern.durationMs, scaledAmplitude)
                        }
                    // Single pulse fallback
                    else -> VibrationEffect.createOneShot(pattern.durationMs, scaledAmplitude)
                }
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern.durationMs)
        }
    }
}

// ── Haptic patterns ──────────────────────────────────────────────────────────

enum class HapticPattern(
    val durationMs: Long,
    val amplitude: Int,
    val timings: LongArray? = null,
    val amplitudes: IntArray? = null,
    val predefined: Int? = null
) {
    LIGHT_TAP(
        durationMs = 10, amplitude = 60,
        predefined = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else null
    ),
    MEDIUM_TAP(
        durationMs = 20, amplitude = 120,
        predefined = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_CLICK else null
    ),
    HEAVY_IMPACT(
        durationMs = 40, amplitude = 200,
        predefined = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_HEAVY_CLICK else null
    ),
    SUCCESS(
        durationMs = 100, amplitude = 150,
        timings    = longArrayOf(0, 20, 40, 60),
        amplitudes = intArrayOf(0, 80, 180, 80)
    ),
    WARNING(
        durationMs = 200, amplitude = 150,
        timings    = longArrayOf(0, 30, 50, 30),
        amplitudes = intArrayOf(0, 200, 0, 150)
    ),
    ERROR(
        durationMs = 300, amplitude = 200,
        timings    = longArrayOf(0, 20, 30, 20, 30, 20),
        amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
    ),
    NOTIFICATION(
        durationMs = 60, amplitude = 100,
        timings    = longArrayOf(0, 15, 30, 15),
        amplitudes = intArrayOf(0, 80, 0, 120)
    ),
    DISMISS_SWIPE(
        durationMs = 15, amplitude = 80,
        predefined = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else null
    ),
    DRAG_TICK(
        durationMs = 8, amplitude = 40
    ),
    MAGNETIC_SNAP(
        durationMs = 25, amplitude = 180,
        timings    = longArrayOf(0, 10, 15, 20),
        amplitudes = intArrayOf(0, 100, 0, 200)
    ),
    CHARGING_CONNECT(
        durationMs = 150, amplitude = 160,
        timings    = longArrayOf(0, 20, 30, 40),
        amplitudes = intArrayOf(0, 120, 0, 200)
    ),
    CALL_RING(
        durationMs = 600, amplitude = 255,
        timings    = longArrayOf(0, 300, 200, 300),
        amplitudes = intArrayOf(0, 255, 0, 200)
    ),
    MUSIC_BEAT(
        durationMs = 8, amplitude = 30
    )
}
