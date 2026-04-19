package com.touchlock.app.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.touchlock.app.services.TouchLockAccessibilityService

/**
 * Centralises all permission checks and navigation helpers for TouchLock.
 * Called from SetupWizardActivity and MainActivity (on resume).
 */
object PermissionHelper {

    // ── Checks ──────────────────────────────────────────────────────────────

    /**
     * Returns true if the app can draw overlays over other apps.
     * Required to show the full-screen touch-blocking layer.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Returns true if TouchLockAccessibilityService is currently enabled
     * in the system Accessibility settings.
     */
    fun hasAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val packageName = context.packageName
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == TouchLockAccessibilityService::class.java.name
        }
    }

    /**
     * Returns true when both critical permissions are granted and the app
     * is ready to lock the screen.
     */
    fun allPermissionsGranted(context: Context): Boolean {
        return hasOverlayPermission(context) && hasAccessibilityEnabled(context)
    }

    // ── Navigation helpers ───────────────────────────────────────────────────

    /**
     * Opens the system "Display over other apps" settings page,
     * scoped to this app so the user lands directly on the toggle.
     */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Opens the system Accessibility settings page.
     * The user must scroll to find TouchLock and enable it manually —
     * Android does not allow apps to enable this programmatically.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
