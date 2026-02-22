package com.example.smartneighborhoodhelper.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.ui.community.CreateCommunityActivity
import com.example.smartneighborhoodhelper.ui.community.DiscoverCommunitiesActivity
import com.example.smartneighborhoodhelper.util.Constants

/**
 * SplashActivity.kt — The very first screen shown when app launches.
 *
 * DEMONSTRATES (for your syllabus):
 *   - Activity lifecycle (onCreate)
 *   - Handler + postDelayed for timed navigation
 *   - SharedPreferences check (is user logged in?)
 *   - Explicit Intents for navigation
 *
 * FLOW:
 *   1. Show splash for 2 seconds
 *   2. Check SharedPreferences:
 *      - If logged in → go to MainActivity (dashboard)
 *      - If first time → go to OnboardingActivity
 *      - If not first time but not logged in → go to RoleSelectionActivity
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sessionManager = SessionManager(this)

        // Handler delays navigation by 2 seconds (2000 milliseconds)
        // Looper.getMainLooper() ensures this runs on the main/UI thread
        Handler(Looper.getMainLooper()).postDelayed({

            val next = when {
                // ✅ If user is logged in, ALWAYS skip onboarding
                sessionManager.isLoggedIn() -> {
                    val communityId = sessionManager.getCommunityId().orEmpty()
                    if (communityId.isBlank()) {
                        // Logged in but no community yet
                        when (sessionManager.getUserRole()) {
                            Constants.ROLE_ADMIN -> Intent(this, CreateCommunityActivity::class.java)
                            Constants.ROLE_RESIDENT -> Intent(this, DiscoverCommunitiesActivity::class.java)
                            else -> Intent(this, RoleSelectionActivity::class.java)
                        }
                    } else {
                        Intent(this, MainActivity::class.java)
                    }
                }

                // First time (not logged in) → onboarding
                !sessionManager.hasSeenOnboarding() -> {
                    Intent(this, OnboardingActivity::class.java)
                }

                // Not logged in → role selection
                else -> {
                    Intent(this, RoleSelectionActivity::class.java)
                }
            }

            startActivity(next)
            finish()  // Remove splash from back stack

        }, 2000)  // 2 second delay
    }
}
