package com.touchlock.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.touchlock.app.data.SettingsRepository
import com.touchlock.app.databinding.ActivitySettingsBinding
import com.touchlock.app.services.FloatingButtonService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SettingsActivity — Phase 3 (Task 3.6)
 *
 * All settings read from and written to SettingsRepository (DataStore).
 * The PIN is hashed with SHA-256 before storage (never plain text).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: SettingsRepository

    // auto-lock delay options in seconds (0 = Off)
    private val autoLockOptions = listOf("Off", "5 seconds", "10 seconds", "30 seconds")
    private val autoLockValues  = listOf(0, 5, 10, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding    = ActivitySettingsBinding.inflate(layoutInflater)
        repository = SettingsRepository(applicationContext)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupAutoLockSpinner()
        loadSettings()
        setupListeners()
    }

    // ── Spinner ───────────────────────────────────────────────────────────────

    private fun setupAutoLockSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            autoLockOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAutoLock.adapter = adapter
    }

    // ── Load current values from DataStore ────────────────────────────────────

    private fun loadSettings() {
        lifecycleScope.launch {
            // Unlock method
            when (repository.unlockMethod.first()) {
                SettingsRepository.METHOD_VOL_DOWN -> binding.rgUnlockMethod.check(R.id.rbVolumeHold)
                else                                  -> binding.rgUnlockMethod.check(R.id.rbVolumeCombo)
            }

            // Auto-lock spinner
            val delaySec = repository.autoLockDelay.first()
            val idx = autoLockValues.indexOf(delaySec).coerceAtLeast(0)
            binding.spinnerAutoLock.setSelection(idx)

            // Toggles
            binding.switchShowMessage.isChecked  = repository.showLockMessage.first()
            binding.switchFloatingBtn.isChecked  = repository.floatingButtonEnabled.first()
            val pinEnabled = repository.pinEnabled.first()
            binding.switchPinEnabled.isChecked   = pinEnabled
            setPinSectionVisible(pinEnabled)
        }
    }

    // ── Listeners → persist every change immediately ──────────────────────────

    private fun setupListeners() {
        // Unlock method
        binding.rgUnlockMethod.setOnCheckedChangeListener { _, checkedId ->
            val method = if (checkedId == R.id.rbVolumeHold)
                SettingsRepository.METHOD_VOL_DOWN
            else
                SettingsRepository.METHOD_VOLUME_COMBO
            lifecycleScope.launch { repository.setUnlockMethod(method) }
        }

        // Auto-lock spinner
        binding.spinnerAutoLock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                lifecycleScope.launch {
                    repository.setAutoLockDelay(autoLockValues[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Show lock message
        binding.switchShowMessage.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { repository.setShowLockMessage(checked) }
        }

        // Floating lock button
        binding.switchFloatingBtn.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { repository.setFloatingButtonEnabled(checked) }
            if (checked) {
                FloatingButtonService.start(this)
            } else {
                FloatingButtonService.stop(this)
            }
        }

        // Enable PIN
        binding.switchPinEnabled.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { repository.setPinEnabled(checked) }
            setPinSectionVisible(checked)
        }

        // Set PIN button
        binding.btnSetPin.setOnClickListener { showSetPinDialog() }
    }

    private fun setPinSectionVisible(visible: Boolean) {
        binding.dividerPin.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnSetPin.visibility  = if (visible) View.VISIBLE else View.GONE
    }

    // ── PIN dialog ────────────────────────────────────────────────────────────

    private fun showSetPinDialog() {
        val input = android.widget.EditText(this).apply {
            hint        = "Enter 4-6 digit PIN"
            inputType   = android.text.InputType.TYPE_CLASS_NUMBER or
                          android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines    = 1
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Set PIN")
            .setMessage("This PIN will be required to unlock the screen.")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    val hash = repository.hashPin(pin)
                    lifecycleScope.launch { repository.setPinHash(hash) }
                } else {
                    showToast("PIN must be 4–6 digits")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
