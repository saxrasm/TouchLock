package com.touchlock.app.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * LockOverlayView — TRANSPARENT touch-blocking overlay.
 *
 * The overlay is nearly invisible so the user can still SEE content
 * underneath (YouTube, videos, etc.) while ALL touches are blocked.
 *
 * Features:
 *   - Tiny pill badge at top showing "🔒 Locked"
 *   - Badge fades to 10% after 4 seconds
 *   - Badge resets on volume key press
 *   - Backup unlock: long-press bottom-right corner for 3 seconds
 */
class LockOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LockOverlay"
        private const val BADGE_FADE_DELAY    = 4_000L
        private const val BADGE_FADE_DURATION = 800L
        private const val BADGE_MIN_ALPHA     = 0.10f
        private const val FADE_IN_DURATION    = 200L
        private const val BACKUP_HOLD_MS      = 3000L
        private const val CORNER_DP           = 80
    }

    var onBackupUnlock: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var fadeAnimator: ObjectAnimator? = null
    private var fadeInAnimator: ObjectAnimator? = null
    private lateinit var badgeContainer: LinearLayout

    // Backup unlock
    private var backupActive = false
    private val backupRunnable = Runnable {
        Log.d(TAG, "BACKUP UNLOCK — 3s hold!")
        onBackupUnlock?.invoke()
    }

    private val badgeFadeRunnable = Runnable {
        fadeAnimator = ObjectAnimator.ofFloat(badgeContainer, "alpha", 1f, BADGE_MIN_ALPHA).apply {
            duration = BADGE_FADE_DURATION
            start()
        }
    }

    init {
        // ── TRANSPARENT — user can see YouTube/videos underneath ──
        setBackgroundColor(Color.parseColor("#01000000"))  // 0.4% alpha black = invisible

        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        alpha = 0f

        // ── Small pill badge at top ──
        badgeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
            background  = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#CC111118"))
                setStroke(1, Color.parseColor("#2A2A3A"))
            }

            addView(TextView(context).apply {
                text     = "🔒"
                textSize = 14f
            })

            addView(TextView(context).apply {
                text          = "  Locked  •  Vol ↑↓ hold 2s"
                textSize      = 12f
                setTextColor(Color.parseColor("#9CA3AF"))
                letterSpacing = 0.02f
            })
        }

        addView(badgeContainer, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity   = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(48)
        })
    }

    // ── Touch — block everything, detect backup unlock ──────────────────────

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isInCorner(event)) {
                    backupActive = true
                    handler.postDelayed(backupRunnable, BACKUP_HOLD_MS)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (backupActive && !isInCorner(event)) cancelBackup()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelBackup()
        }
        return true  // consume ALL touches
    }

    private fun isInCorner(e: MotionEvent): Boolean {
        val px = dpToPx(CORNER_DP)
        return e.x > (width - px) && e.y > (height - px)
    }

    private fun cancelBackup() {
        if (backupActive) { handler.removeCallbacks(backupRunnable); backupActive = false }
    }

    // ── Animations ──────────────────────────────────────────────────────────

    fun animateIn() {
        fadeInAnimator = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
            duration = FADE_IN_DURATION
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { startBadgeFade() }
            })
            start()
        }
    }

    private fun startBadgeFade() {
        badgeContainer.alpha = 1f
        handler.removeCallbacks(badgeFadeRunnable)
        handler.postDelayed(badgeFadeRunnable, BADGE_FADE_DELAY)
    }

    fun resetBadge() {
        handler.removeCallbacks(badgeFadeRunnable)
        fadeAnimator?.cancel()
        badgeContainer.alpha = 1f
        startBadgeFade()
    }

    fun cleanupAnimations() {
        handler.removeCallbacksAndMessages(null)
        fadeAnimator?.cancel(); fadeInAnimator?.cancel()
        fadeAnimator = null; fadeInAnimator = null
    }

    private fun dpToPx(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
}
