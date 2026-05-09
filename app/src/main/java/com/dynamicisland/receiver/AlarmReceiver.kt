package com.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.service.DynamicIslandServiceV3
import java.text.SimpleDateFormat
import java.util.*

/**
 * V3: Intercepts alarm broadcasts and shows the Alarm island.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("android.intent.extra.alarm_label") ?: "Alarm"
        val time  = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.AlarmFired(label, time))
    }
}
