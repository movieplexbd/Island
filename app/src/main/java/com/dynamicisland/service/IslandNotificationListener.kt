package com.dynamicisland.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.notification.SmartNotificationFilter

/**
 * V3: Notification listener — routes filtered notifications to the island.
 * V3 adds: replyable detection, alarm package detection.
 */
class IslandNotificationListener : NotificationListenerService() {

    private val filter = SmartNotificationFilter(this)

    private val ALARM_PACKAGES = setOf(
        "com.android.deskclock", "com.google.android.deskclock",
        "com.samsung.android.app.clockpackage", "com.oneplus.deskclock"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val extras  = sbn.notification.extras
        val title   = extras.getString("android.title") ?: return
        val text    = extras.getCharSequence("android.text")?.toString() ?: ""
        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        val actions   = sbn.notification.actions ?: emptyArray()
        val replyable = actions.any { action ->
            action.remoteInputs?.isNotEmpty() == true
        }

        val event = IslandEvent.NotificationReceived(appName, title, text, pkg, replyable)
        if (filter.evaluate(event) == SmartNotificationFilter.Result.SUPPRESS) return

        // Detect alarm packages → send alarm event
        if (pkg in ALARM_PACKAGES) {
            DynamicIslandServiceV3.sendCriticalEvent(
                IslandEvent.AlarmFired(title, android.text.format.DateFormat
                    .getTimeFormat(this).format(java.util.Date())))
            return
        }

        DynamicIslandServiceV3.sendEvent(event, pkg)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }
}
