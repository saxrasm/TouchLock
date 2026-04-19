package com.touchlock.app.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.touchlock.app.data.SettingsRepository
import com.touchlock.app.ui.LockOverlayView
import com.touchlock.app.utils.UnlockComboDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * TouchLockAccessibilityService — The lock engine.
 *
 * Architecture (FINAL):
 *   The overlay (TYPE_ACCESSIBILITY_OVERLAY) does ALL the work:
 *     - Sits ABOVE everything (highest Z-order in Android)
 *     - Intercepts ALL touch events (including gesture zones)
 *     - Is transparent so user can see content underneath
 *
 *   This service handles:
 *     - Adding/removing the overlay window
 *     - Intercepting hardware keys (Home/Back/Recents)
 *     - Detecting volume key unlock combo
 *     - Re-locking on screen wake
 *
 *   This service does NOT:
 *     - Perform GLOBAL_ACTION_BACK (causes weird loops)
 *     - Monitor window state changes (causes weird behavior)
 *     - Apply immersive mode (breaks nav on install)
 *     - Block anything when not locked
 */
class TouchLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchLockA11y"

        @Volatile var isLocked = false
            private set

        @Volatile private var instance: TouchLockAccessibilityService? = null

        fun lock(context: Context) {
            Log.d(TAG, "lock()")
            instance?.doLock() ?: Log.e(TAG, "A11y not running!")
        }

        fun unlock(context: Context) {
            Log.d(TAG, "unlock()")
            instance?.doUnlock() ?: Log.e(TAG, "A11y not running!")
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: LockOverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var unlockDetector: UnlockComboDetector
    private var screenReceiver: BroadcastReceiver? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val method = runBlocking {
            SettingsRepository(applicationContext).unlockMethod.first()
        }
        unlockDetector = UnlockComboDetector(
            method = method,
            onUnlockComboDetected = ::onUnlockDetected
        )
        Log.d(TAG, "Connected — method=$method — idle")
    }

    override fun onDestroy() {
        doUnlock()
        instance = null
        super.onDestroy()
    }

    // ── Accessibility events — INTENTIONALLY EMPTY ──────────────────────────
    // The overlay handles touch blocking. We do NOT react to window changes.
    // Any GLOBAL_ACTION here causes the "weird behavior" the user reported.

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — overlay handles everything
    }

    override fun onInterrupt() {
        if (::unlockDetector.isInitialized) unlockDetector.reset()
    }

    // ── Key interception ──────────────────────────────────────────────────────
    // This is the ONLY active blocking while locked (hardware keys only).

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        if (isLocked) {
            // Block navigation hardware keys
            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_MENU -> {
                    Log.d(TAG, "BLOCKED key: ${KeyEvent.keyCodeToString(event.keyCode)}")
                    return true  // consume = blocked
                }
            }
        }

        // Volume keys → unlock detector
        if (::unlockDetector.isInitialized) {
            return unlockDetector.onKeyEvent(event, lockIsActive = isLocked)
        }
        return false
    }

    // ── Lock ─────────────────────────────────────────────────────────────────

    private fun doLock() {
        if (isLocked) return

        handler.post {
            try {
                val view = LockOverlayView(this)
                overlayView = view

                view.onBackupUnlock = {
                    Log.d(TAG, "BACKUP UNLOCK!")
                    doUnlock()
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,

                    // NOT_FOCUSABLE: lets A11y receive key events
                    // LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS: covers entire screen
                    // including status bar, nav bar, gesture zones
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

                windowManager!!.addView(view, params)
                isLocked = true

                FloatingButtonService.hideButton()
                LockOverlayService.startLock(this)
                registerScreenReceiver()

                view.post { view.animateIn() }
                Log.d(TAG, "LOCKED ✓")
            } catch (e: Exception) {
                Log.e(TAG, "Lock failed!", e)
            }
        }
    }

    // ── Unlock ───────────────────────────────────────────────────────────────

    private fun doUnlock() {
        if (!isLocked) return

        isLocked = false
        if (::unlockDetector.isInitialized) unlockDetector.reset()
        unregisterScreenReceiver()

        handler.post {
            overlayView?.let {
                it.cleanupAnimations()
                try { windowManager?.removeView(it) } catch (_: Exception) {}
                overlayView = null
            }
            FloatingButtonService.showButton()
            LockOverlayService.stopLock(this)
            Log.d(TAG, "UNLOCKED ✓")
        }
    }

    private fun onUnlockDetected() {
        Log.d(TAG, "COMBO DETECTED → unlock")
        if (::unlockDetector.isInitialized) unlockDetector.reset()
        doUnlock()
    }

    // ── Screen receiver ─────────────────────────────────────────────────────
    // Ensures lock persists through power button press.

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON && isLocked) {
                    Log.d(TAG, "Screen ON — lock still active")
                }
            }
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenReceiver = null
        }
    }
}
