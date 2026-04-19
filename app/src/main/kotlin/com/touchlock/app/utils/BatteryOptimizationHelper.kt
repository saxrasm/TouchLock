package com.touchlock.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * BatteryOptimizationHelper — Phase 5 (Task 5.4)
 *
 * Checks whether the system is battery-optimising this app (which would kill
 * the lock service after a few minutes of running in the background), and
 * prompts the user to exempt it — but only once per install.
 *
 * The "dismissed" flag is stored in shared prefs so it survives across
 * ViewModel recreations without needing DataStore coroutines.
 */
object BatteryOptimizationHelper {

    private const val PREFS_NAME  = "touchlock_battery_prefs"
    private const val KEY_DISMISSED = "battery_prompt_dismissed"

    /**
     * Returns true if the OS is currently optimising battery for this app.
     * On Android 6+ (API 23+) uses PowerManager.isIgnoringBatteryOptimizations.
     */
    fun isOptimized(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Has the user already dismissed this prompt? */
    fun wasPromptDismissed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DISMISSED, false)
    }

    private fun markDismissed(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    /**
     * Show the battery optimisation dialog if:
     *  1. The app IS being optimised (i.e. at risk of being killed), AND
     *  2. The user has NOT already dismissed the prompt.
     *
     * Call this from MainActivity on first lock press.
     */
    fun showIfNeeded(context: Context) {
        if (!isOptimized(context)) return
        if (wasPromptDismissed(context)) return

        MaterialAlertDialogBuilder(context)
            .setTitle("⚡ Battery Optimisation")
            .setMessage(
                "Your phone's battery saver may stop TouchLock from running " +
                "after a few minutes.\n\n" +
                "To keep the lock active:\n\n" +
                "Open Battery Settings → Find TouchLock → Set to \"Unrestricted\" (or equivalent).\n\n" +
                "Tap \"Open Settings\" to do this now."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                markDismissed(context)
                openBatterySettings(context)
            }
            .setNegativeButton("Later") { _, _ ->
                markDismissed(context)
            }
            .setCancelable(false)
            .show()
    }

    /** Opens the direct battery-optimisation exemption page for this app. */
    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback — some OEMs block the direct intent
            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
    }
}
