package com.dynamicisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.dynamicisland.model.IslandEvent

/**
 * Detects phone call state changes and updates the Dynamic Island.
 * Registered dynamically inside [DynamicIslandService] to respect lifecycle.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var callStartTime = 0L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastState = TelephonyManager.CALL_STATE_RINGING
                val callerName = lookupContactName(context, number) ?: number
                DynamicIslandServiceV3.sendEvent(
                    IslandEvent.ShowCall(
                        name       = callerName,
                        number     = number,
                        isIncoming = true
                    )
                )
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                callStartTime = System.currentTimeMillis()
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // Answered incoming call
                    val callerName = lookupContactName(context, number) ?: number
                    DynamicIslandServiceV3.sendEvent(
                        IslandEvent.ShowCall(
                            name       = callerName,
                            number     = number,
                            isIncoming = false
                        )
                    )
                }
                lastState = TelephonyManager.CALL_STATE_OFFHOOK
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val duration = if (callStartTime > 0)
                    (System.currentTimeMillis() - callStartTime) / 1000
                else 0L
                callStartTime = 0
                lastState = TelephonyManager.CALL_STATE_IDLE
                DynamicIslandServiceV3.sendEvent(IslandEvent.CallEnded(duration))
            }
        }
    }

    /**
     * Attempts to look up a contact display name from the number.
     * Returns null if not found (falls back to raw number).
     */
    private fun lookupContactName(context: Context, number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
