package com.touchlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.touchlock.app.databinding.ActivityMainBinding
import com.touchlock.app.services.FloatingButtonService
import com.touchlock.app.services.LockOverlayService
import com.touchlock.app.utils.BatteryOptimizationHelper
import com.touchlock.app.utils.PermissionHelper

/**
 * MainActivity — Debug Agent rewrite
 *
 * BUG-01: Uses SharedPreferences 'setup_complete' to skip wizard after initial setup.
 * BUG-04: Big button now toggles the FLOATING BUTTON, not the lock itself.
 *         The only way to lock the screen is via the floating overlay button.
 *
 * Greets the user by name (stored by WelcomeActivity).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DEBUG_FB"
        private const val PREFS_NAME = "touchlock_prefs"
        private const val KEY_PARENT_NAME = "parent_name"
    }

    private lateinit var binding: ActivityMainBinding

    // Receives lock/unlock events broadcast by LockOverlayService
    private val lockStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LockOverlayService.ACTION_LOCK   -> updateLockUi(true)
                LockOverlayService.ACTION_UNLOCK -> updateLockUi(false)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BUG-01: check if setup is complete
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val setupDone = prefs.getBoolean(SetupWizardActivity.KEY_SETUP_COMPLETE, false)

        if (!setupDone || !PermissionHelper.allPermissionsGranted(this)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Greet the parent
        val parentName = prefs.getString(KEY_PARENT_NAME, null)
        binding.tvGreeting.text = if (!parentName.isNullOrBlank()) "Hi $parentName 👋" else "Hi there 👋"

        // Show battery optimization prompt once
        BatteryOptimizationHelper.showIfNeeded(this)

        setupToggleButton()
        setupToolbarIcons()
        updateFloatingButtonUi(FloatingButtonService.isRunning)
        updateLockUi(LockOverlayService.isLocked)
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return

        // Register for lock/unlock broadcasts
        val filter = IntentFilter().apply {
            addAction(LockOverlayService.ACTION_LOCK)
            addAction(LockOverlayService.ACTION_UNLOCK)
        }
        registerReceiver(lockStateReceiver, filter, RECEIVER_NOT_EXPORTED)

        updateFloatingButtonUi(FloatingButtonService.isRunning)
        updateLockUi(LockOverlayService.isLocked)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(lockStateReceiver) } catch (_: Exception) {}
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    /**
     * BUG-04: Big button toggles the floating overlay button ON/OFF.
     * It does NOT lock the screen directly.
     */
    private fun setupToggleButton() {
        binding.btnToggleFloating.setOnClickListener { view ->
            Log.d(TAG, "Toggle tapped — isRunning: ${FloatingButtonService.isRunning}")

            // Bounce animation
            view.animate()
                .scaleX(0.92f).scaleY(0.92f)
                .setDuration(80)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()

            if (FloatingButtonService.isRunning) {
                Log.d(TAG, "Stopping FloatingButtonService")
                FloatingButtonService.stop(this)
                updateFloatingButtonUi(false)
            } else {
                Log.d(TAG, "Starting FloatingButtonService")
                FloatingButtonService.start(this)
                updateFloatingButtonUi(true)
            }
        }
    }

    private fun setupToolbarIcons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnHelp.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    // ── UI updates ────────────────────────────────────────────────────────────

    private fun updateFloatingButtonUi(active: Boolean) {
        if (active) {
            binding.btnToggleFloating.text = "Hide Lock Button"
            binding.tvFloatingStatus.text = "Floating button is active — tap it to lock"
            binding.tvFloatingStatus.visibility = View.VISIBLE
        } else {
            binding.btnToggleFloating.text = "Show Lock Button"
            binding.tvFloatingStatus.text = "Floating button is off"
            binding.tvFloatingStatus.visibility = View.VISIBLE
        }
    }

    private fun updateLockUi(locked: Boolean) {
        if (locked) {
            binding.tvStatusIcon.text  = "🔒"
            binding.tvStatusLabel.text = getString(R.string.status_locked)
            binding.tvStatusLabel.setTextColor(getColor(R.color.locked_color))
            binding.tvHint.text        = getString(R.string.hint_unlock)

            // Pulse animation for status indicator
            binding.statusIndicator.animate()
                .scaleX(1.05f).scaleY(1.05f)
                .setDuration(700)
                .withEndAction {
                    binding.statusIndicator.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(700)
                        .withEndAction { if (LockOverlayService.isLocked) updateLockUi(true) }
                        .start()
                }.start()
        } else {
            // Cancel any pending pulse
            binding.statusIndicator.animate().cancel()
            binding.statusIndicator.scaleX = 1f
            binding.statusIndicator.scaleY = 1f

            binding.tvStatusIcon.text  = "🔓"
            binding.tvStatusLabel.text = getString(R.string.status_unlocked)
            binding.tvStatusLabel.setTextColor(getColor(R.color.unlocked_color))
            binding.tvHint.text        = getString(R.string.hint_lock)
        }
    }
}
