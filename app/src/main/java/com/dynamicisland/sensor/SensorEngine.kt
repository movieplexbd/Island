package com.dynamicisland.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║          SENSOR ENGINE — v3.0                       ║
 * ║  Step counter · Light · Proximity · Heart rate      ║
 * ╚══════════════════════════════════════════════════════╝
 */
class SensorEngine(private val context: Context) {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // ── Step counter ──────────────────────────────────────────────────────────
    val stepCounterFlow: Flow<Int> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: run { close(); return@callbackFlow }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0].toInt())
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged()

    val stepDetectorFlow: Flow<Unit> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { trySend(Unit) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }

    // ── Ambient light ─────────────────────────────────────────────────────────
    val ambientLightFlow: Flow<Float> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
            ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged()

    /** Maps lux → 0.0–1.0 brightness hint for glow intensity */
    val autoBrightnessHint: Flow<Float> = ambientLightFlow.map { lux ->
        when {
            lux < 10f   -> 0.9f   // Very dark — full glow
            lux < 100f  -> 0.6f   // Dim
            lux < 1000f -> 0.35f  // Normal indoor
            else        -> 0.15f  // Bright sunlight — reduce glow
        }
    }

    // ── Proximity ─────────────────────────────────────────────────────────────
    val proximityFlow: Flow<Boolean> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0] < sensor.maximumRange)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged()

    // ── Heart rate (if available, e.g. Wear OS forwarded) ────────────────────
    val heartRateFlow: Flow<Int> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.accuracy >= SensorManager.SENSOR_STATUS_LOW_ACCURACY) {
                    trySend(event.values[0].toInt())
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged()

    // ── Feature detection ─────────────────────────────────────────────────────
    val hasStepCounter: Boolean  get() = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    val hasHeartRate: Boolean    get() = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
    val hasAmbientLight: Boolean get() = manager.getDefaultSensor(Sensor.TYPE_LIGHT) != null
}
