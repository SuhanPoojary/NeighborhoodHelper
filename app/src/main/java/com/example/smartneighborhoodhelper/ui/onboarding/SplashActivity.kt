package com.example.smartneighborhoodhelper.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.JoinRequestRepository
import com.example.smartneighborhoodhelper.ui.community.CreateCommunityActivity
import com.example.smartneighborhoodhelper.ui.community.DiscoverCommunitiesActivity
import com.example.smartneighborhoodhelper.ui.community.PendingApprovalActivity
import com.example.smartneighborhoodhelper.util.Constants
import kotlinx.coroutines.launch

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
        // Force light mode (theme toggle removed)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sessionManager = SessionManager(this)

        // If opened from a push, MainActivity should open the Notifications tab.
        val openTabFromPush = intent.getStringExtra(Constants.EXTRA_OPEN_TAB)

        // Handler delays navigation by 2 seconds (2000 milliseconds)
        // Looper.getMainLooper() ensures this runs on the main/UI thread
        Handler(Looper.getMainLooper()).postDelayed({

            // ✅ Logged in users skip onboarding
            if (sessionManager.isLoggedIn()) {
                val communityId = sessionManager.getCommunityId().orEmpty()
                val role = sessionManager.getUserRole().orEmpty()

                if (communityId.isNotBlank()) {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        if (!openTabFromPush.isNullOrBlank()) {
                            putExtra(Constants.EXTRA_OPEN_TAB, openTabFromPush)
                        }
                    })
                    finish()
                    return@postDelayed
                }

                // No community yet
                when (role) {
                    Constants.ROLE_ADMIN -> {
                        startActivity(Intent(this, CreateCommunityActivity::class.java))
                        finish()
                    }

                    Constants.ROLE_RESIDENT -> {
                        // SMART ROUTING:
                        // Only show PendingApproval if they actually have a pending request.
                        lifecycleScope.launch {
                            val uid = sessionManager.getUserId().orEmpty()
                            val hasPending = try {
                                JoinRequestRepository().hasPendingForResident(uid)
                            } catch (_: Exception) {
                                false
                            }

                            val next = if (hasPending) {
                                Intent(this@SplashActivity, PendingApprovalActivity::class.java)
                            } else {
                                Intent(this@SplashActivity, DiscoverCommunitiesActivity::class.java)
                            }

                            startActivity(next)
                            finish()
                        }
                    }

                    else -> {
                        startActivity(Intent(this, RoleSelectionActivity::class.java))
                        finish()
                    }
                }

                return@postDelayed
            }

            // Not logged in
            val next = if (!sessionManager.hasSeenOnboarding()) {
                Intent(this, OnboardingActivity::class.java)
            } else {
                Intent(this, RoleSelectionActivity::class.java)
            }

            startActivity(next)
            finish()

        }, 2000)  // 2 second delay
    }
}
