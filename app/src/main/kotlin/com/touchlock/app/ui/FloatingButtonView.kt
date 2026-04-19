package com.touchlock.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.touchlock.app.R

/**
 * FloatingButtonView — draggable 🔒 button.
 *
 * 60dp blue circle with 🔒 emoji.
 * Drag to move. Tap to lock. Reports drag start/end for dismiss zone.
 */
class FloatingButtonView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onTap()
        fun onDragStart()
        fun onMove(dx: Int, dy: Int)
        fun onDragEnd()
    }

    var listener: Listener? = null

    init {
        val sizePx = dpToPx(60)
        layoutParams = LayoutParams(sizePx, sizePx)
        background = context.getDrawable(R.drawable.floating_button_bg)
        elevation  = dpToPx(8).toFloat()

        addView(TextView(context).apply {
            text      = "🔒"
            textSize  = 22f
            gravity   = Gravity.CENTER
            setTypeface(null, Typeface.NORMAL)
        }, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        isClickable = true
        isFocusable = true
    }

    // ── Touch handling ──────────────────────────────────────────────────────

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var hasMoved    = false
    private val dragThreshold = dpToPx(8)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
                hasMoved    = false
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchStartX).toInt()
                val dy = (event.rawY - touchStartY).toInt()
                if (!hasMoved && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                    hasMoved = true
                    animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    listener?.onDragStart()
                }
                if (hasMoved) {
                    listener?.onMove(dx, dy)
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                }
            }
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                if (!hasMoved) {
                    listener?.onTap()
                } else {
                    listener?.onDragEnd()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                if (hasMoved) listener?.onDragEnd()
            }
        }
        return true
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
