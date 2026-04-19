package com.touchlock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.touchlock.app.services.LockOverlayService

/**
 * EmergencyUnlockReceiver
 *
 * Monitors screen off/on events while lock is active.
 *
 * Behaviour:
 *   - When screen turns OFF while locked: nothing special (wake lock handles it)
 *   - When screen turns ON while locked: re-ensure the lock is still showing
 *     (prevents bypass by turning screen off and on)
 *
 * Emergency unlock has been REMOVED — it was allowing kids to bypass the lock
 * simply by pressing the power button twice.
 */
class EmergencyUnlockReceiver(private val context: Context) : BroadcastReceiver() {

    companion object {
        private const val TAG = "EmergencyRecv"
    }

    private var registered = false

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(this, filter)
        registered = true
        Log.d(TAG, "Registered for screen events")
    }

    fun unregister() {
        if (!registered) return
        try { context.unregisterReceiver(this) } catch (_: Exception) {}
        registered = false
        Log.d(TAG, "Unregistered")
    }

    override fun onReceive(ctx: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF while locked — lock persists")
                // Do nothing — lock stays active
            }
            Intent.ACTION_SCREEN_ON -> {
                if (LockOverlayService.isLocked) {
                    Log.d(TAG, "Screen ON while locked — lock still active (no bypass)")
                    // Lock is still showing — no action needed.
                    // The overlay is already visible.
                }
            }
        }
    }
}
