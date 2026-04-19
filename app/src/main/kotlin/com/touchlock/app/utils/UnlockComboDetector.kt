package com.touchlock.app.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * UnlockComboDetector — supports 3 unlock methods:
 *
 *   VOLUME_COMBO  — Hold Vol Up + Vol Down together for 2 seconds
 *   VOL_DOWN_HOLD — Hold Vol Down alone for 3 seconds
 *   VOL_UP_HOLD   — Hold Vol Up alone for 3 seconds
 *
 * All methods use volume keys only. No PIN.
 */
class UnlockComboDetector(
    private val method: String = "VOLUME_COMBO",
    private val onUnlockComboDetected: () -> Unit
) {

    companion object {
        private const val TAG = "UnlockDetector"

        // Method constants
        const val METHOD_VOLUME_COMBO = "VOLUME_COMBO"
        const val METHOD_VOL_DOWN    = "VOL_DOWN_HOLD"
        const val METHOD_VOL_UP      = "VOL_UP_HOLD"
    }

    private val handler = Handler(Looper.getMainLooper())

    // State
    private var volUpHeld   = false
    private var volDownHeld = false
    private var volUpDownTime   = -1L
    private var volDownDownTime = -1L
    private var comboStarted    = false

    private val unlockRunnable = Runnable {
        when (method) {
            METHOD_VOLUME_COMBO -> {
                if (volUpHeld && volDownHeld) {
                    Log.d(TAG, "✅ VOLUME COMBO UNLOCK!")
                    onUnlockComboDetected()
                }
            }
            METHOD_VOL_DOWN -> {
                if (volDownHeld) {
                    Log.d(TAG, "✅ VOL DOWN HOLD UNLOCK!")
                    onUnlockComboDetected()
                }
            }
            METHOD_VOL_UP -> {
                if (volUpHeld) {
                    Log.d(TAG, "✅ VOL UP HOLD UNLOCK!")
                    onUnlockComboDetected()
                }
            }
        }
        reset()
    }

    /**
     * Feed a KeyEvent. Returns true if the event should be consumed (blocked).
     */
    fun onKeyEvent(event: KeyEvent, lockIsActive: Boolean): Boolean {
        val isVolume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                       event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (!isVolume) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(event)
            KeyEvent.ACTION_UP   -> handleUp(event)
        }

        // Consume volume keys while locked (prevent volume popup)
        return lockIsActive
    }

    private fun handleDown(event: KeyEvent) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!volUpHeld) {
                    volUpHeld = true
                    volUpDownTime = System.currentTimeMillis()
                    Log.d(TAG, "Vol UP ↓ pressed")
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!volDownHeld) {
                    volDownHeld = true
                    volDownDownTime = System.currentTimeMillis()
                    Log.d(TAG, "Vol DOWN ↓ pressed")
                }
            }
        }
        checkAndStartTimer()
    }

    private fun handleUp(event: KeyEvent) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP   -> { volUpHeld = false; Log.d(TAG, "Vol UP ↑ released") }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { volDownHeld = false; Log.d(TAG, "Vol DOWN ↑ released") }
        }

        // If any required key released, cancel timer
        when (method) {
            METHOD_VOLUME_COMBO -> {
                if (!volUpHeld || !volDownHeld) cancelTimer()
            }
            METHOD_VOL_DOWN -> {
                if (!volDownHeld) cancelTimer()
            }
            METHOD_VOL_UP -> {
                if (!volUpHeld) cancelTimer()
            }
        }
    }

    private fun checkAndStartTimer() {
        if (comboStarted) return  // timer already running

        val shouldStart = when (method) {
            METHOD_VOLUME_COMBO -> {
                if (volUpHeld && volDownHeld) {
                    val gap = Math.abs(volUpDownTime - volDownDownTime)
                    Log.d(TAG, "Both keys held, gap=${gap}ms")
                    gap <= 500  // pressed within 500ms of each other
                } else false
            }
            METHOD_VOL_DOWN -> volDownHeld
            METHOD_VOL_UP   -> volUpHeld
            else -> false
        }

        if (shouldStart) {
            comboStarted = true
            val holdMs = when (method) {
                METHOD_VOLUME_COMBO -> 2000L
                else -> 3000L  // single key = 3 seconds
            }
            handler.postDelayed(unlockRunnable, holdMs)
            Log.d(TAG, "Timer started — hold for ${holdMs}ms")
        }
    }

    private fun cancelTimer() {
        if (comboStarted) {
            handler.removeCallbacks(unlockRunnable)
            comboStarted = false
            Log.d(TAG, "Timer cancelled")
        }
    }

    fun reset() {
        cancelTimer()
        volUpHeld = false
        volDownHeld = false
        volUpDownTime = -1L
        volDownDownTime = -1L
    }
}
