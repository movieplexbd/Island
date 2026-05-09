package com.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.service.DynamicIslandServiceV3
import com.dynamicisland.utils.PermissionHelper

class Receivers {

    /** Auto-start island service on boot. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!PermissionHelper.allPermissionsGranted(context)) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, DynamicIslandServiceV3::class.java)
            )
        }
    }

    /** Charging state receiver → shows Charging island. */
    class ChargingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level      = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale      = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged    = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status     = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            val isWireless = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
            val percentage = if (scale > 0) (level * 100 / scale) else 0
            // Rough time-to-full estimate (very rough: ~1% per minute at normal charge)
            val estimatedMin = if (isCharging) (100 - percentage) else 0

            DynamicIslandServiceV3.sendEvent(
                IslandEvent.ChargingUpdate(percentage, isCharging, isWireless, estimatedMin)
            )
        }
    }
}
