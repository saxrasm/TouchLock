package com.touchlock.app.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.touchlock.app.ui.FloatingButtonView

/**
 * FloatingButtonService — draggable lock button with dismiss zone.
 *
 * Features:
 *   - 🔒 button floats above all apps
 *   - Tap → lock screen
 *   - Drag to move
 *   - Drag to bottom ❌ zone → dismiss button
 *   - Static hideButton() / showButton() for lock state
 */
class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingBtnSvc"

        @Volatile var isRunning = false
            private set
        @Volatile private var instance: FloatingButtonService? = null

        fun hideButton() {
            instance?.buttonView?.let { it.post { it.visibility = android.view.View.GONE } }
        }
        fun showButton() {
            instance?.buttonView?.let { it.post { it.visibility = android.view.View.VISIBLE } }
        }

        fun start(context: Context) {
            context.startService(Intent(context, FloatingButtonService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingButtonService::class.java).setAction("STOP")
            )
        }
    }

    private var windowManager: WindowManager? = null
    private var buttonView: FloatingButtonView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Dismiss zone
    private var dismissZone: FrameLayout? = null
    private var dismissParams: WindowManager.LayoutParams? = null
    private var isDismissZoneVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                removeDismissZone()
                removeButton()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (buttonView != null) return START_STICKY

        try {
            addButton()
            isRunning = true
            instance  = this
            Log.d(TAG, "Button added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add button", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        removeDismissZone()
        removeButton()
        isRunning = false
        instance  = null
        super.onDestroy()
    }

    // ── Button ───────────────────────────────────────────────────────────────

    private fun addButton() {
        val view = FloatingButtonView(this)
        buttonView = view

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(300)
        }
        layoutParams = lp

        view.listener = object : FloatingButtonView.Listener {
            override fun onTap() {
                Log.d(TAG, "TAP → lock")
                TouchLockAccessibilityService.lock(this@FloatingButtonService)
            }
            override fun onDragStart() {
                showDismissZone()
            }
            override fun onMove(dx: Int, dy: Int) {
                val p = layoutParams ?: return
                p.x += dx
                p.y += dy
                try { windowManager?.updateViewLayout(view, p) } catch (_: Exception) {}

                // Check if over dismiss zone
                updateDismissZoneHighlight(p)
            }
            override fun onDragEnd() {
                val p = layoutParams ?: return
                if (isOverDismissZone(p)) {
                    Log.d(TAG, "Dragged to dismiss zone — stopping service")
                    removeDismissZone()
                    removeButton()
                    isRunning = false
                    instance  = null
                    stopSelf()
                } else {
                    hideDismissZone()
                }
            }
        }

        windowManager!!.addView(view, lp)
    }

    private fun removeButton() {
        buttonView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            buttonView = null
        }
    }

    // ── Dismiss zone ────────────────────────────────────────────────────────

    private fun showDismissZone() {
        if (isDismissZoneVisible) return

        val zone = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)

            val circle = FrameLayout(this@FloatingButtonService).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#33FF4444"))
                    setStroke(dpToPx(2), Color.parseColor("#FF4444"))
                }
                addView(TextView(this@FloatingButtonService).apply {
                    text      = "✕"
                    textSize  = 22f
                    setTextColor(Color.parseColor("#FF4444"))
                    gravity   = Gravity.CENTER
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            addView(circle, FrameLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply {
                gravity = Gravity.CENTER
            })
        }

        val zp = WindowManager.LayoutParams(
            dpToPx(80), dpToPx(80),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(32)
        }

        dismissZone   = zone
        dismissParams = zp
        isDismissZoneVisible = true
        windowManager?.addView(zone, zp)
    }

    private fun hideDismissZone() {
        removeDismissZone()
    }

    private fun removeDismissZone() {
        if (isDismissZoneVisible) {
            dismissZone?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
            dismissZone = null
            isDismissZoneVisible = false
        }
    }

    private fun updateDismissZoneHighlight(buttonParams: WindowManager.LayoutParams) {
        val over = isOverDismissZone(buttonParams)
        dismissZone?.alpha = if (over) 1f else 0.6f
        dismissZone?.scaleX = if (over) 1.3f else 1f
        dismissZone?.scaleY = if (over) 1.3f else 1f
    }

    private fun isOverDismissZone(buttonParams: WindowManager.LayoutParams): Boolean {
        val dm = resources.displayMetrics
        val screenH = dm.heightPixels
        val btnCenterY = buttonParams.y + dpToPx(30)
        return btnCenterY > screenH - dpToPx(120)
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
