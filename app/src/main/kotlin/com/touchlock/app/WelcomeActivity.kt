package com.touchlock.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.touchlock.app.databinding.ActivityWelcomeBinding

/**
 * WelcomeActivity — shown on very first launch before anything else.
 *
 * Asks for the parent's name, saves it to SharedPreferences, then navigates
 * to SetupWizardActivity. If the name is already saved, skips straight to
 * MainActivity (or SetupWizardActivity if setup isn't complete yet).
 */
class WelcomeActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "touchlock_prefs"
        private const val KEY_PARENT_NAME = "parent_name"
    }

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val nameExists = !prefs.getString(KEY_PARENT_NAME, null).isNullOrBlank()

        // If name already saved → skip to setup wizard or main
        if (nameExists) {
            val setupComplete = prefs.getBoolean(SetupWizardActivity.KEY_SETUP_COMPLETE, false)
            if (setupComplete) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, SetupWizardActivity::class.java))
            }
            finish()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sequential fade-in animation
        val views = listOf<View>(
            binding.tvIcon,
            binding.tvLine1,
            binding.tvLine2,
            binding.tvSubtitle,
            binding.tilName,
            binding.btnGo
        )
        views.forEachIndexed { index, view ->
            view.animate()
                .alpha(1f)
                .translationYBy(-20f)
                .setStartDelay(200L + index * 200L)
                .setDuration(500)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.btnGo.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim() ?: ""
            if (name.isBlank()) {
                binding.tilName.error = "Please enter your name"
                return@setOnClickListener
            }

            // Save name
            prefs.edit().putString(KEY_PARENT_NAME, name).apply()

            // Navigate to setup wizard
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
        }
    }
}
