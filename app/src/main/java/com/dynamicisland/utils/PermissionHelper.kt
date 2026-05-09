package com.dynamicisland.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.dynamicisland.service.IslandNotificationListener

object PermissionHelper {

    fun hasOverlayPermission(ctx: Context): Boolean =
        Settings.canDrawOverlays(ctx)

    fun hasNotificationAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver,
            "enabled_notification_listeners") ?: return false
        val cn   = ComponentName(ctx, IslandNotificationListener::class.java)
        return flat.split(":").any {
            runCatching { ComponentName.unflattenFromString(it) == cn }.getOrDefault(false)
        }
    }

    fun hasPhonePermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasActivityRecognition(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun allPermissionsGranted(ctx: Context): Boolean =
        hasOverlayPermission(ctx) && hasNotificationAccess(ctx)
}
