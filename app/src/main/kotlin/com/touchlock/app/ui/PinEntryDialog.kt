package com.touchlock.app.ui

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.animation.CycleInterpolator
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.touchlock.app.R
import com.touchlock.app.data.SettingsRepository
import com.touchlock.app.services.LockOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PinEntryDialog — Phase 5 (Task 5.1)
 *
 * A full-screen Dialog with a custom 4×3 number pad.
 * Shows when the volume-combo fires and PIN mode is enabled.
 *
 * Behaviour:
 *  - Wrong PIN: shake error animation, increment attempts counter.
 *  - 3 wrong attempts: show emergency instructions and disable the pad.
 *  - Correct PIN: dismiss dialog + call LockOverlayService.stopLock().
 *  - PIN is always compared against the SHA-256 hash stored in DataStore.
 */
class PinEntryDialog(
    context: Context,
    private val repository: SettingsRepository,
    private val onUnlockGranted: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private val digitBuffer = StringBuilder()
    private var wrongAttempts = 0
    private val maxAttempts = 3

    // Views — built programmatically (no XML needed for one-off dialog)
    private lateinit var tvDisplay: TextView
    private lateinit var tvStatus: TextView
    private lateinit var padContainer: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
        buildLayout()
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private fun buildLayout() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        // Lock emoji header
        root.addView(TextView(context).apply {
            text     = "🔒"
            textSize = 48f
            gravity  = Gravity.CENTER
        })

        // Title
        root.addView(TextView(context).apply {
            text      = "Enter PIN to unlock"
            textSize  = 18f
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        })

        // PIN display dots (●●●●)
        tvDisplay = TextView(context).apply {
            text      = ""
            textSize  = 28f
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            letterSpacing = 0.4f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(tvDisplay)

        // Status / error text
        tvStatus = TextView(context).apply {
            text      = " "
            textSize  = 14f
            setTextColor(Color.parseColor("#FF6B6B"))
            gravity   = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(tvStatus)

        // Number pad 3×4: 1–9, *, 0, ⌫
        padContainer = GridLayout(context).apply {
            columnCount = 3
            rowCount    = 4
            setPadding(dp(8), 0, dp(8), 0)
        }

        val keys = listOf("1","2","3","4","5","6","7","8","9","*","0","⌫")
        keys.forEach { key -> padContainer.addView(makePadButton(key)) }

        root.addView(padContainer)

        // Cancel link
        root.addView(TextView(context).apply {
            text      = "Emergency: press Power button"
            textSize  = 12f
            setTextColor(Color.parseColor("#A0A0B0"))
            gravity   = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(root)
    }

    private fun makePadButton(label: String): Button {
        return Button(context).apply {
            text      = label
            textSize  = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1F2D50"))
            val lp = GridLayout.LayoutParams().apply {
                width       = dp(80)
                height      = dp(80)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
            layoutParams = lp
            setOnClickListener {
                when (label) {
                    "⌫" -> { if (digitBuffer.isNotEmpty()) digitBuffer.deleteCharAt(digitBuffer.length - 1) }
                    "*" -> { /* reserved for future use */ }
                    else -> digitBuffer.append(label)
                }
                updateDisplay()
                // Auto-submit when user enters 4–6 digits
                if (digitBuffer.length >= 4) checkPin()
            }
        }
    }

    // ── PIN logic ─────────────────────────────────────────────────────────────

    private fun updateDisplay() {
        tvDisplay.text = "●".repeat(digitBuffer.length)
        tvStatus.text  = " "
    }

    private fun checkPin() {
        val entered = digitBuffer.toString()
        digitBuffer.clear()
        updateDisplay()

        CoroutineScope(Dispatchers.IO).launch {
            val storedHash = repository.pinHash.first()
            val enteredHash = repository.hashPin(entered)
            val correct = enteredHash == storedHash
            withContext(Dispatchers.Main) {
                if (correct) {
                    onUnlockGranted()
                    dismiss()
                } else {
                    wrongAttempts++
                    shakeDisplay()
                    if (wrongAttempts >= maxAttempts) {
                        disablePad()
                    } else {
                        tvStatus.text = "Incorrect PIN (${maxAttempts - wrongAttempts} left)"
                    }
                }
            }
        }
    }

    private fun shakeDisplay() {
        ObjectAnimator.ofFloat(tvDisplay, "translationX", -20f, 20f).apply {
            interpolator = CycleInterpolator(4f)
            duration     = 400L
            start()
        }
    }

    private fun disablePad() {
        tvStatus.text = "Too many attempts!\nPress Power to emergency unlock."
        tvStatus.setTextColor(Color.parseColor("#FF6B6B"))
        for (i in 0 until padContainer.childCount) {
            padContainer.getChildAt(i).isEnabled = false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
