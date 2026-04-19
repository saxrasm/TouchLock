package com.touchlock.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.touchlock.app.databinding.ActivityHelpBinding

/**
 * HelpActivity — Phase 3 (Task 3.7)
 *
 * Expandable FAQ list. Tapping a question toggles the answer visibility.
 * Cards are built programmatically from a simple data list so there are
 * no additional RecyclerView/Adapter dependencies needed.
 */
class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding

    // ── FAQ data ──────────────────────────────────────────────────────────────
    private val faqs = listOf(
        "How do I unlock?"
            to "Press Volume Up and Volume Down at the same time and hold for 2 seconds. The screen will unlock immediately.",
        "Why does it need Accessibility permission?"
            to "The Accessibility Service is the only Android-approved way to listen for hardware volume key presses when the screen is blocked.",
        "Does it work during video calls?"
            to "Yes! The transparent overlay blocks touches but does not block sound, video, or incoming calls.",
        "Will it survive closing the app?"
            to "Yes. The lock runs as a Foreground Service, which keeps it alive even when you switch apps or the screen turns off.",
        "Why does the notification stay?"
            to "Android requires Foreground Services to show a persistent notification. You can minimise or hide it in your notification settings.",
        "The app isn't blocking touches?"
            to "Make sure both Accessibility Service and Overlay permission are enabled. Open Settings in the app and check the current status.",
        "Lock stops working after a while?"
            to "Go to your phone's Battery settings, find TouchLock, and disable battery optimisation. Some manufacturers (Xiaomi, Huawei) have extra \"Autostart\" toggles you must also enable.",
        "Emergency unlock?"
            to "Power-press is handled by the lock. If completely stuck, connect to a computer and run: adb shell am startservice -n com.touchlock.app/.services.LockOverlayService --es action STOP"
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        buildFaqCards()
    }

    // ── Build FAQ cards ───────────────────────────────────────────────────────

    private fun buildFaqCards() {
        val dp8  = dpToPx(8)
        val dp16 = dpToPx(16)

        faqs.forEachIndexed { index, (question, answer) ->
            // Wrapper card
            val card = CardView(this).apply {
                radius          = dpToPx(14).toFloat()
                cardElevation   = 0f
                setCardBackgroundColor(getColor(R.color.surface))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp8
                }
                layoutParams = lp
            }

            // Inner vertical layout
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Question row
            val tvQuestion = android.widget.TextView(this).apply {
                text      = "${index + 1}. $question"
                textSize  = 15f
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp16, dp16, dp16, dp16)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            // Answer (initially hidden)
            val tvAnswer = android.widget.TextView(this).apply {
                text      = answer
                textSize  = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(dp16, 0, dp16, dp16)
                visibility = View.GONE
            }

            // Divider (shown when answer is visible)
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                ).apply {
                    leftMargin  = dp16
                    rightMargin = dp16
                }
                setBackgroundColor(getColor(R.color.divider))
                visibility = View.GONE
            }

            // Toggle on question tap
            tvQuestion.setOnClickListener {
                val expanded = tvAnswer.visibility == View.VISIBLE
                tvAnswer.visibility = if (expanded) View.GONE else View.VISIBLE
                divider.visibility  = if (expanded) View.GONE else View.VISIBLE
            }

            inner.addView(tvQuestion)
            inner.addView(divider)
            inner.addView(tvAnswer)
            card.addView(inner)
            binding.faqContainer.addView(card)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
