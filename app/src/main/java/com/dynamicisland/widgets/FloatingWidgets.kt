package com.dynamicisland.widgets

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.ui.IslandAccentBlue
import com.dynamicisland.ui.IslandAccentGreen
import com.dynamicisland.ui.IslandAccentOrange
import com.dynamicisland.ui.IslandAccentRed
import com.dynamicisland.ui.IslandWhite
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║         FLOATING MINI WIDGETS — v2.0                ║
 * ║  FPS · Internet Speed · RAM/CPU · Battery · Weather ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Each widget is self-contained and polling-based.
 * They produce [StateFlow]s consumed by the island UI.
 */

// ── DATA MODELS ───────────────────────────────────────────────────────────────

data class PerformanceData(
    val fpsEstimate: Int       = 60,
    val ramUsedMb: Int         = 0,
    val ramTotalMb: Int        = 0,
    val cpuPercent: Int        = 0,
    val downloadKbps: Long     = 0,
    val uploadKbps: Long       = 0
)

data class BatteryHealthData(
    val percentage: Int        = 100,
    val isCharging: Boolean    = false,
    val healthPercent: Int     = 100,    // Battery capacity vs design (approx)
    val temperature: Float     = 0f,    // Celsius
    val voltage: Int           = 0      // mV
)

data class WeatherData(
    val tempC: Float           = 0f,
    val condition: String      = "Clear",
    val humidity: Int          = 0,
    val icon: String           = "☀️"
)

// ── PERFORMANCE MONITOR ───────────────────────────────────────────────────────

class PerformanceMonitor(private val context: Context) {

    private val scope       = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activityMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _data = MutableStateFlow(PerformanceData())
    val data: StateFlow<PerformanceData> = _data.asStateFlow()

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var lastNetTime = System.currentTimeMillis()

    // Simple FPS estimator using Choreographer callback timing
    private var lastFrameNs  = 0L
    private var frameCount   = 0
    private var fpsEstimate  = 60

    fun start() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                _data.value = PerformanceData(
                    fpsEstimate   = fpsEstimate,
                    ramUsedMb     = getRamUsedMb(),
                    ramTotalMb    = getRamTotalMb(),
                    cpuPercent    = estimateCpuUsage(),
                    downloadKbps  = getDownloadKbps(),
                    uploadKbps    = getUploadKbps()
                )
            }
        }
    }

    fun stop() { scope.cancel() }

    fun reportFrame(nowNs: Long) {
        if (lastFrameNs > 0) {
            val deltaNs = nowNs - lastFrameNs
            if (deltaNs > 0) fpsEstimate = (1_000_000_000L / deltaNs).toInt().coerceIn(1, 240)
        }
        lastFrameNs = nowNs
    }

    private fun getRamUsedMb(): Int {
        val info = ActivityManager.MemoryInfo()
        activityMgr.getMemoryInfo(info)
        return ((info.totalMem - info.availMem) / 1_048_576L).toInt()
    }

    private fun getRamTotalMb(): Int {
        val info = ActivityManager.MemoryInfo()
        activityMgr.getMemoryInfo(info)
        return (info.totalMem / 1_048_576L).toInt()
    }

    private fun estimateCpuUsage(): Int {
        // Approximate via /proc/stat (best-effort, no root)
        return try {
            val stat = java.io.RandomAccessFile("/proc/stat", "r")
            val line = stat.readLine()
            stat.close()
            val parts = line.split(" ").filter { it.isNotBlank() }.drop(1)
            val total = parts.take(7).sumOf { it.toLongOrNull() ?: 0L }
            val idle  = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            if (total > 0) ((1f - idle.toFloat() / total) * 100).toInt() else 0
        } catch (_: Exception) { 0 }
    }

    private fun getDownloadKbps(): Long {
        val now    = System.currentTimeMillis()
        val rxNow  = TrafficStats.getTotalRxBytes()
        val dtMs   = (now - lastNetTime).coerceAtLeast(1L)
        val kbps   = ((rxNow - lastRxBytes) * 8L / dtMs).coerceAtLeast(0L)
        lastRxBytes = rxNow
        lastNetTime = now
        return kbps
    }

    private fun getUploadKbps(): Long {
        val txNow = TrafficStats.getTotalTxBytes()
        val kbps  = ((txNow - lastTxBytes) * 8L / 1000L).coerceAtLeast(0L)
        lastTxBytes = txNow
        return kbps
    }
}

// ── BATTERY HEALTH MONITOR ────────────────────────────────────────────────────

class BatteryHealthMonitor(private val context: Context) {

    private val _data = MutableStateFlow(BatteryHealthData())
    val data: StateFlow<BatteryHealthData> = _data.asStateFlow()

    fun refresh() {
        val intent = context.registerReceiver(
            null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        ) ?: return

        val level       = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale       = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status      = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val health      = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
        val temp        = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltage     = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val isCharging  = status == BatteryManager.BATTERY_STATUS_CHARGING
        val healthPct   = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD    -> 95
            BatteryManager.BATTERY_HEALTH_FAIR    -> 70
            BatteryManager.BATTERY_HEALTH_POOR    -> 40
            else                                  -> 100
        }

        _data.value = BatteryHealthData(
            percentage    = if (scale > 0) (level * 100 / scale) else 0,
            isCharging    = isCharging,
            healthPercent = healthPct,
            temperature   = temp / 10f,
            voltage       = voltage
        )
    }
}

// ── COMPOSE WIDGET COMPOSABLES ────────────────────────────────────────────────

/**
 * Mini FPS + RAM widget for gaming mode island.
 */
@Composable
fun GamingMonitorWidget(data: PerformanceData) {
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // FPS
        MetricPill(
            label = "FPS",
            value = "${data.fpsEstimate}",
            color = fpsColor(data.fpsEstimate)
        )
        // RAM
        MetricPill(
            label = "RAM",
            value = "${data.ramUsedMb}M",
            color = IslandAccentBlue
        )
        // Network
        MetricPill(
            label = "↓",
            value = formatKbps(data.downloadKbps),
            color = IslandAccentGreen
        )
    }
}

/**
 * Internet speed mini widget.
 */
@Composable
fun SpeedMonitorWidget(data: PerformanceData) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SpeedRow("↓", data.downloadKbps, IslandAccentGreen)
        SpeedRow("↑", data.uploadKbps, IslandAccentBlue)
    }
}

@Composable
private fun SpeedRow(arrow: String, kbps: Long, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(arrow, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(formatKbps(kbps), color = IslandWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Battery health activity panel.
 */
@Composable
fun BatteryHealthWidget(data: BatteryHealthData) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.BatteryChargingFull,
            contentDescription = null,
            tint = batteryHealthColor(data.healthPercent),
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                "${data.percentage}% · ${data.healthPercent}% health",
                color = IslandWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${data.temperature}°C · ${data.voltage}mV",
                color = IslandWhite.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Reusable metric pill chip.
 */
@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color.copy(alpha = 0.7f), fontSize = 9.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun fpsColor(fps: Int) = when {
    fps >= 55 -> IslandAccentGreen
    fps >= 30 -> IslandAccentOrange
    else      -> IslandAccentRed
}

private fun batteryHealthColor(pct: Int) = when {
    pct >= 80 -> IslandAccentGreen
    pct >= 60 -> IslandAccentOrange
    else      -> IslandAccentRed
}

private fun formatKbps(kbps: Long) = when {
    kbps >= 1_000_000 -> "${"%.1f".format(kbps / 1_000_000f)} Gbps"
    kbps >= 1_000     -> "${"%.1f".format(kbps / 1_000f)} Mbps"
    else              -> "$kbps Kbps"
}
