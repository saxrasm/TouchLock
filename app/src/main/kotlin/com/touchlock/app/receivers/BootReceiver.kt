package com.touchlock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.touchlock.app.data.SettingsRepository
import com.touchlock.app.services.FloatingButtonService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BootReceiver — Phase 4 (Task 4.5)
 *
 * Catches BOOT_COMPLETED (plus LOCKED_BOOT_COMPLETED for direct-boot devices).
 * If the user had the floating button enabled, restarts FloatingButtonService.
 * The lock itself is NOT re-activated on boot — the user must tap the button
 * intentionally each session.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        // Read DataStore on a background coroutine (goAsync not needed for short IO)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(context)
                val floatingEnabled = repo.floatingButtonEnabled.first()
                if (floatingEnabled) {
                    FloatingButtonService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
