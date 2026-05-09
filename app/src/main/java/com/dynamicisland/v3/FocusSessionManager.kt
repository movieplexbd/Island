package com.dynamicisland.v3

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.offline.OfflineCacheManager
import com.dynamicisland.service.DynamicIslandServiceV3
import kotlinx.coroutines.*

/**
 * V3: Manages focus/DND sessions.
 * Integrates with system DND when permission is granted.
 */
class FocusSessionManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = OfflineCacheManager(context)

    fun startSession(name: String, durationMinutes: Int) {
        // Enable DND if allowed
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
        DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.FocusStarted(name, durationMinutes))
    }

    fun endSession() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.FocusEnded)
    }

    fun cleanup() { scope.cancel() }
}
