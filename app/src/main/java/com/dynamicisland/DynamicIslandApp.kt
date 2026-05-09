package com.dynamicisland

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dynamicisland.service.DynamicIslandServiceV3
import com.dynamicisland.utils.PermissionHelper

/**
 * V3 Application class — auto-starts service after boot restore.
 */
class DynamicIslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Auto-restart service if permissions already granted (e.g. after update)
        if (PermissionHelper.allPermissionsGranted(this)) {
            ContextCompat.startForegroundService(this,
                Intent(this, DynamicIslandServiceV3::class.java))
        }
    }
}
