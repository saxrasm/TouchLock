package com.touchlock.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * ManufacturerHelper — Phase 5 (Task 5.3)
 *
 * Detects known OEM overlays (Xiaomi MIUI, Huawei EMUI, Samsung One UI)
 * that disable foreground services through proprietary battery/autostart
 * management, and shows a targeted help dialog guiding the user to the
 * correct settings page.
 *
 * Call [showManufacturerTipIfNeeded] from SetupWizardActivity's Done step
 * and from MainActivity's first lock call.
 */
object ManufacturerHelper {

    enum class OEM { XIAOMI, HUAWEI, SAMSUNG, OTHER }

    /** Detect the current OEM from Build.MANUFACTURER. */
    fun detectOem(): OEM {
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> OEM.XIAOMI
            "huawei", "honor"         -> OEM.HUAWEI
            "samsung"                 -> OEM.SAMSUNG
            else                      -> OEM.OTHER
        }
    }

    /**
     * Show a modal dialog with OEM-specific guidance if on a known device.
     * Does nothing on vanilla Android (OEM.OTHER).
     */
    fun showManufacturerTipIfNeeded(context: Context) {
        val (title, message, settingsIntent) = when (detectOem()) {
            OEM.XIAOMI -> Triple(
                "Xiaomi / MIUI Battery Settings",
                "Please enable Autostart for TouchLock:\n\n" +
                "Settings → Apps → Manage Apps → TouchLock → Autostart → ON\n\n" +
                "Also set Battery Saver to \"No restrictions\".",
                miuiAutoStartIntent()
            )
            OEM.HUAWEI -> Triple(
                "Huawei / EMUI App Launch Settings",
                "Please allow TouchLock to run in the background:\n\n" +
                "Settings → Battery → App launch → TouchLock → Enable all toggles.\n\n" +
                "Also disable \"Power-intensive prompt\".",
                huaweiAppLaunchIntent()
            )
            OEM.SAMSUNG -> Triple(
                "Samsung One UI Battery Settings",
                "Please exclude TouchLock from battery optimisation:\n\n" +
                "Settings → Battery → Background usage limits → Never sleeping apps → Add TouchLock.",
                samsungBatteryIntent(context)
            )
            OEM.OTHER -> return  // Do nothing on vanilla Android
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                try { context.startActivity(settingsIntent) } catch (_: Exception) {
                    // Fallback to generic battery settings
                    context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    // ── OEM-specific intent builders ──────────────────────────────────────────

    private fun miuiAutoStartIntent(): Intent =
        Intent().apply {
            setClassName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun huaweiAppLaunchIntent(): Intent =
        Intent().apply {
            setClassName("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun samsungBatteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
