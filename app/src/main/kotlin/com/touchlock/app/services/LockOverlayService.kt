package com.touchlock.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.touchlock.app.MainActivity
import com.touchlock.app.R
import com.touchlock.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * LockOverlayService — SIMPLIFIED
 *
 * This service now ONLY handles:
 *   1. Foreground notification (required by Android to keep services alive)
 *   2. Wake lock (keep screen on while locked)
 *
 * The actual overlay window is managed by TouchLockAccessibilityService
 * using TYPE_ACCESSIBILITY_OVERLAY — the only window type that covers
 * the status bar, nav bar, and notification shade.
 */
class LockOverlayService : Service() {

    companion object {
        private const val TAG = "LockOverlaySvc"

        const val ACTION_LOCK = "com.touchlock.app.ACTION_LOCK"
        const val ACTION_UNLOCK = "com.touchlock.app.ACTION_UNLOCK"

        const val NOTIFICATION_CHANNEL_ID = "touchlock_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile var isLocked = false
            private set

        fun startLock(context: Context) {
            try {
                val intent = Intent(context, LockOverlayService::class.java)
                    .setAction(ACTION_LOCK)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start lock service", e)
            }
        }

        fun stopLock(context: Context) {
            try {
                val intent = Intent(context, LockOverlayService::class.java)
                    .setAction(ACTION_UNLOCK)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop lock service", e)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        when (intent?.action) {
            ACTION_LOCK -> {
                isLocked = true
                val keepAwake = runBlocking {
                    SettingsRepository(this@LockOverlayService).keepScreenAwake.first()
                }
                if (keepAwake) acquireWakeLock()
                sendBroadcast(Intent(ACTION_LOCK).setPackage(packageName))
                Log.d(TAG, "Lock notification + wake lock active")
            }
            ACTION_UNLOCK -> {
                isLocked = false
                releaseWakeLock()
                sendBroadcast(Intent(ACTION_UNLOCK).setPackage(packageName))
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
                Log.d(TAG, "Unlock — service stopped")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        isLocked = false
        super.onDestroy()
    }

    // ── Wake lock ────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "TouchLock:LockWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L) // 30 minutes max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release(); wakeLock = null }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_lock_title))
            .setContentText(getString(R.string.notification_lock_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
