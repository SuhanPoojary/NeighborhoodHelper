package com.example.smartneighborhoodhelper.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.databinding.ActivityOnboardingBinding

/**
 * OnboardingActivity.kt — Shows 3 swipeable intro pages on first app launch.
 *
 * DEMONSTRATES (for your syllabus):
 *   - ViewPager2 with RecyclerView.Adapter
 *   - SharedPreferences — saving "has seen onboarding" flag
 *   - Dynamic UI creation (dot indicators created in code)
 *   - Activity + Intent navigation
 *
 * FLOW:
 *   - User swipes through 3 pages (or taps Skip)
 *   - On last page, "Next" changes to "Get Started"
 *   - Tapping "Get Started" or "Skip" → saves flag → goes to RoleSelectionActivity
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var dots: Array<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Define the 3 onboarding pages
        val pages = listOf(
            OnboardingPage(
                title = getString(R.string.onboarding_title_1),
                description = getString(R.string.onboarding_desc_1),
                imageRes = R.drawable.ic_onboarding_report
            ),
            OnboardingPage(
                title = getString(R.string.onboarding_title_2),
                description = getString(R.string.onboarding_desc_2),
                imageRes = R.drawable.ic_onboarding_track
            ),
            OnboardingPage(
                title = getString(R.string.onboarding_title_3),
                description = getString(R.string.onboarding_desc_3),
                imageRes = R.drawable.ic_onboarding_community
            )
        )

        // Set up ViewPager with adapter
        binding.viewPager.adapter = OnboardingAdapter(pages)

        // Create dot indicators
        setupDots(pages.size)
        updateDots(0)

        // Listen for page swipes to update dots and button text
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                // On last page, change button text to "Get Started"
                if (position == pages.size - 1) {
                    binding.btnNext.text = getString(R.string.btn_get_started)
                } else {
                    binding.btnNext.text = getString(R.string.btn_next)
                }
            }
        })

        // Next/Get Started button
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.size - 1) {
                // Not last page → go to next page
                binding.viewPager.currentItem = current + 1
            } else {
                // Last page → finish onboarding
                finishOnboarding()
            }
        }

        // Skip button → finish onboarding immediately
        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    /**
     * Create dot indicators dynamically.
     * Each dot is a TextView with "●" character, styled with color.
     */
    private fun setupDots(count: Int) {
        dots = Array(count) {
            TextView(this).apply {
                text = "●"
                textSize = 16f
                setPadding(8, 0, 8, 0)
            }
        }
        binding.dotsLayout.removeAllViews()
        dots.forEach { binding.dotsLayout.addView(it) }
    }

    /** Update dot colors — active dot is green, others are grey */
    private fun updateDots(activeIndex: Int) {
        dots.forEachIndexed { index, dot ->
            if (index == activeIndex) {
                dot.setTextColor(getColor(R.color.primary))
            } else {
                dot.setTextColor(getColor(R.color.divider))
            }
        }
    }

    /** Save onboarding seen flag and navigate to Role Selection */
    private fun finishOnboarding() {
        sessionManager.setOnboardingSeen()
        val intent = Intent(this, RoleSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}

