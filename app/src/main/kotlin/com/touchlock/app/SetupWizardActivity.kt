package com.touchlock.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.touchlock.app.data.SettingsRepository
import com.touchlock.app.databinding.ActivitySetupWizardBinding
import com.touchlock.app.utils.ManufacturerHelper
import com.touchlock.app.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 6-step wizard:
 *   0 – Welcome
 *   1 – Accessibility
 *   2 – Overlay permission
 *   3 – Unlock combo choice (3 options)
 *   4 – Screen awake toggle
 *   5 – App Pinning Guide
 *   6 – Done
 */
class SetupWizardActivity : AppCompatActivity() {

    companion object {
        private const val KEY_STEP = "current_step"
        const val PREFS_NAME = "touchlock_prefs"
        const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    private lateinit var binding: ActivitySetupWizardBinding
    private lateinit var settings: SettingsRepository

    private var currentStep = 0
    private val totalSteps  = 7
    private var returningFromSettings = false

    // User choices
    private var selectedCombo    = SettingsRepository.METHOD_VOLUME_COMBO
    private var selectedKeepAwake = true

    private val dots: List<View> by lazy {
        listOf(binding.dot0, binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5, binding.dot6)
    }
    private val accent  = Color.parseColor("#4F8EF7")
    private val surface = Color.parseColor("#1C1C28")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isSetupComplete()) { goToMain(); return }

        binding  = ActivitySetupWizardBinding.inflate(layoutInflater)
        settings = SettingsRepository(this)
        setContentView(binding.root)

        currentStep = savedInstanceState?.getInt(KEY_STEP, 0) ?: 0
        renderStep(currentStep)

        binding.btnNext.setOnClickListener {
            if (currentStep < totalSteps - 1) {
                if (canAdvance(currentStep)) { currentStep++; renderStep(currentStep) }
            } else {
                saveChoices(); markSetupComplete(); goToMain()
            }
        }
        binding.btnAction.setOnClickListener {
            when (currentStep) {
                1 -> { returningFromSettings = true; PermissionHelper.openAccessibilitySettings(this) }
                2 -> { returningFromSettings = true; PermissionHelper.openOverlaySettings(this) }
                5 -> { 
                    returningFromSettings = true
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
                    } catch (e: Exception) {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        if (returningFromSettings) { returningFromSettings = false; if (canAdvance(currentStep)) currentStep++ }
        renderStep(currentStep)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt(KEY_STEP, currentStep) }

    private fun canAdvance(step: Int) = when (step) {
        1 -> PermissionHelper.hasAccessibilityEnabled(this)
        2 -> PermissionHelper.hasOverlayPermission(this)
        else -> true
    }

    // ── Renderers ─────────────────────────────────────────────────────────────

    private fun renderStep(step: Int) {
        clearCustom()
        dots.forEachIndexed { i, d ->
            d.setBackgroundColor(if (i == step) accent else surface)
            d.scaleX = if (i == step) 1.4f else 1f
            d.scaleY = if (i == step) 1.4f else 1f
        }
        binding.tvStepBody.visibility = View.VISIBLE
        binding.tvPermissionStatus.visibility = View.GONE
        binding.btnAction.visibility = View.GONE
        when (step) {
            0 -> renderWelcome()
            1 -> renderAccessibility()
            2 -> renderOverlay()
            3 -> renderCombo()
            4 -> renderAwake()
            5 -> renderAppPinning()
            6 -> renderDone()
        }
    }

    private fun renderWelcome() = with(binding) {
        tvStepIcon.text  = "🔒"
        tvStepTitle.text = getString(R.string.setup_welcome_title)
        tvStepBody.text  = getString(R.string.setup_welcome_body)
        btnNext.text     = getString(R.string.btn_next)
    }

    private fun renderAccessibility() {
        val ok = PermissionHelper.hasAccessibilityEnabled(this)
        with(binding) {
            tvStepIcon.text  = "♿"
            tvStepTitle.text = getString(R.string.setup_accessibility_title)
            tvStepBody.text  = getString(R.string.setup_accessibility_body)
            btnAction.visibility = if (ok) View.GONE else View.VISIBLE
            btnAction.text   = getString(R.string.setup_accessibility_btn)
            tvPermissionStatus.visibility = if (ok) View.VISIBLE else View.GONE
            tvPermissionStatus.text = getString(R.string.permission_granted)
            btnNext.text     = getString(R.string.btn_next)
        }
    }

    private fun renderOverlay() {
        val ok = PermissionHelper.hasOverlayPermission(this)
        with(binding) {
            tvStepIcon.text  = "🛡️"
            tvStepTitle.text = getString(R.string.setup_overlay_title)
            tvStepBody.text  = getString(R.string.setup_overlay_body)
            btnAction.visibility = if (ok) View.GONE else View.VISIBLE
            btnAction.text   = getString(R.string.setup_overlay_btn)
            tvPermissionStatus.visibility = if (ok) View.VISIBLE else View.GONE
            tvPermissionStatus.text = getString(R.string.permission_granted)
            btnNext.text     = getString(R.string.btn_next)
        }
    }

    private fun renderCombo() {
        with(binding) {
            tvStepIcon.text  = "🔓"
            tvStepTitle.text = "Choose Unlock Combo"
            tvStepBody.text  = "Select how you'll unlock the screen:"
            btnNext.text     = getString(R.string.btn_next)
        }
        showCustom(buildCards(
            listOf(
                Card("🔊", "Vol Up + Vol Down", "Hold both buttons for 2 seconds", SettingsRepository.METHOD_VOLUME_COMBO),
                Card("⬇️", "Vol Down Hold", "Hold volume down alone for 3 seconds", SettingsRepository.METHOD_VOL_DOWN),
                Card("⬆️", "Vol Up Hold", "Hold volume up alone for 3 seconds", SettingsRepository.METHOD_VOL_UP),
            ), selectedCombo) { selectedCombo = it; renderStep(3) }
        )
    }

    private fun renderAwake() {
        with(binding) {
            tvStepIcon.text  = if (selectedKeepAwake) "☀️" else "🌙"
            tvStepTitle.text = "Keep Screen On?"
            tvStepBody.text  = "While locked, should the screen stay on?"
            btnNext.text     = getString(R.string.btn_next)
        }
        showCustom(buildCards(
            listOf(
                Card("☀️", "Keep Screen On", "Screen stays on while locked", "on"),
                Card("🌙", "Allow Sleep", "Screen turns off normally", "off"),
            ), if (selectedKeepAwake) "on" else "off"
        ) { selectedKeepAwake = (it == "on"); renderStep(4) })
    }

    private fun renderAppPinning() {
        with(binding) {
            tvStepIcon.text  = "📌"
            tvStepTitle.text = "Block Navigation"
            tvStepBody.text  = "TouchLock blocks touches on the screen.\n\nTo also prevent navigating away (like pressing Home), use Android's 'App Pinning' feature on the app you want to lock (e.g. YouTube).\n\nHow: Open Recents -> Tap App Icon -> Pin."
            btnAction.visibility = View.VISIBLE
            btnAction.text   = "Open Pinning Settings"
            tvPermissionStatus.visibility = View.GONE
            btnNext.text     = getString(R.string.btn_next)
        }
    }

    private fun renderDone() {
        val hint = when (selectedCombo) {
            SettingsRepository.METHOD_VOL_DOWN -> "Volume Down (hold 3s)"
            SettingsRepository.METHOD_VOL_UP   -> "Volume Up (hold 3s)"
            else -> "Volume Up + Down (hold 2s)"
        }
        with(binding) {
            tvStepIcon.text  = "✅"
            tvStepTitle.text = getString(R.string.setup_done_title)
            tvStepBody.text  = "Tap the floating 🔒 button to lock.\nUse $hint to unlock."
            btnNext.text     = getString(R.string.setup_done_btn)
        }
        ManufacturerHelper.showManufacturerTipIfNeeded(this)
    }

    // ── Selection cards ──────────────────────────────────────────────────────

    data class Card(val icon: String, val title: String, val sub: String, val value: String)

    private fun buildCards(cards: List<Card>, selected: String, onSelect: (String) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            cards.forEach { card ->
                val sel = card.value == selected
                addView(FrameLayout(this@SetupWizardActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(if (sel) Color.parseColor("#1A2744") else Color.parseColor("#13131D"))
                        setStroke(dpToPx(2), if (sel) accent else Color.parseColor("#2A2A3A"))
                    }
                    setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
                    setOnClickListener { onSelect(card.value) }

                    val row = LinearLayout(this@SetupWizardActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    row.addView(TextView(this@SetupWizardActivity).apply {
                        text = card.icon; textSize = 24f
                        setPadding(0, 0, dpToPx(12), 0)
                    })

                    val col = LinearLayout(this@SetupWizardActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    col.addView(TextView(this@SetupWizardActivity).apply {
                        text = card.title; textSize = 15f
                        setTextColor(if (sel) Color.WHITE else Color.parseColor("#A0AEC0"))
                    })
                    col.addView(TextView(this@SetupWizardActivity).apply {
                        text = card.sub; textSize = 11f
                        setTextColor(Color.parseColor("#64748B"))
                    })
                    row.addView(col)

                    row.addView(TextView(this@SetupWizardActivity).apply {
                        text = if (sel) "✓" else ""; textSize = 18f
                        setTextColor(accent); gravity = Gravity.CENTER
                    })

                    addView(row)
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(10) })
            }
        }
    }

    // ── Custom view slot ─────────────────────────────────────────────────────

    private var customView: LinearLayout? = null

    private fun showCustom(view: LinearLayout) {
        clearCustom()
        val parent = binding.tvStepBody.parent as? ViewGroup ?: return
        val idx = parent.indexOfChild(binding.tvStepBody)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(12), 0, 0)
            addView(view)
        }
        customView = wrapper
        parent.addView(wrapper, idx + 1)
    }

    private fun clearCustom() {
        customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        customView = null
    }

    // ── Save / Nav ───────────────────────────────────────────────────────────

    private fun saveChoices() {
        CoroutineScope(Dispatchers.IO).launch {
            settings.setUnlockMethod(selectedCombo)
            settings.setKeepScreenAwake(selectedKeepAwake)
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }); finish()
    }

    private fun isSetupComplete() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_SETUP_COMPLETE, false)

    private fun markSetupComplete() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
