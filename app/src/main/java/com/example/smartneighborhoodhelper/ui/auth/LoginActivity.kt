package com.example.smartneighborhoodhelper.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.FcmTokenRepository
import com.example.smartneighborhoodhelper.databinding.ActivityLoginBinding
import com.example.smartneighborhoodhelper.ui.community.CreateCommunityActivity
import com.example.smartneighborhoodhelper.ui.community.DiscoverCommunitiesActivity
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import com.example.smartneighborhoodhelper.util.Constants
import com.example.smartneighborhoodhelper.viewmodel.AuthState
import com.example.smartneighborhoodhelper.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

/**
 * LoginActivity.kt — Login screen for RETURNING users.
 *
 * DEMONSTRATES (for your syllabus):
 *   - Activity lifecycle
 *   - ViewBinding
 *   - Explicit Intents (to MainActivity, RoleSelectionActivity)
 *   - SharedPreferences via SessionManager
 *   - MVVM — observing LiveData from AuthViewModel
 *   - Firebase Auth — forgot password email
 *
 * FLOW:
 *   1. User enters email + password → taps "Log In"
 *   2. Validates inputs → calls viewModel.login()
 *   3. On success → saves session → navigates to MainActivity
 *   4. "Sign Up" → goes to RoleSelectionActivity (to pick Admin/Resident)
 *   5. "Forgot Password" → sends Firebase password reset email
 */
class LoginActivity : AppCompatActivity() {

    // ViewBinding — auto-generated from activity_login.xml
    private lateinit var binding: ActivityLoginBinding

    // ViewModel — survives screen rotation, handles business logic
    // viewModels() is a KTX extension that creates/retrieves the ViewModel
    private val viewModel: AuthViewModel by viewModels()

    // SessionManager — reads/writes SharedPreferences
    private lateinit var sessionManager: SessionManager

    // FCM Token repository
    private val fcmTokenRepo = FcmTokenRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Inflate the layout using ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Step 2: Initialize SessionManager
        sessionManager = SessionManager(this)

        // ✅ IMPORTANT:
        // Don't auto-skip this screen based on SharedPreferences.
        // If a previous user was logged in, that stale session can route to the wrong flow.
        // SplashActivity handles auto-routing for a valid session.

        // Step 4: Set up click listeners
        setupClickListeners()

        // Step 5: Observe ViewModel state changes
        observeAuthState()
    }

    /**
     * Set up button click handlers.
     * DEMONSTRATES: Click listeners, Intents
     */
    private fun setupClickListeners() {
        // Back arrow → go back
        binding.ivBack.setOnClickListener { finish() }

        // Login button — validate inputs, then call ViewModel
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // Validate before sending to Firebase
            if (validateInputs(email, password)) {
                viewModel.login(email, password)
            }
        }

        // "Sign Up" → Role Selection screen (pick Admin or Resident)
        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
        }

        // "Forgot Password?" → send reset email via Firebase
        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilEmail.error = getString(R.string.error_empty_email)
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = getString(R.string.error_invalid_email)
                return@setOnClickListener
            }

            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    /**
     * Validate email and password inputs.
     * Shows error text below each field if invalid.
     *
     * @return true if all inputs are valid, false otherwise
     */
    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        // Clear previous errors
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        // Email validation
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_empty_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }

        // Password validation
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_short_password)
            isValid = false
        }

        return isValid
    }

    /**
     * Observe LiveData from AuthViewModel.
     * DEMONSTRATES: LiveData observation — UI reacts to data changes automatically.
     *
     * "this" is the LifecycleOwner — LiveData only notifies when Activity is active
     * (not when it's in the background). This prevents crashes.
     */
    private fun observeAuthState() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Idle -> {
                    // Default state — nothing to do
                    showLoading(false)
                }
                is AuthState.Loading -> {
                    // Show spinner, hide button
                    showLoading(true)
                }
                is AuthState.Success -> {
                    showLoading(false)
                    val user = state.user
                    Log.d("UID_DEBUG", "Auth UID: ${FirebaseAuth.getInstance().currentUser?.uid}")
                    Log.d("UID_DEBUG", "Session UID: ${sessionManager.getUserId()}")
                    Log.d("UID_DEBUG", "User UID (Firestore): ${user.uid}")

                    // ✅ Always overwrite session with fresh Firestore user profile
                    sessionManager.saveUserSession(
                        uid = user.uid,
                        name = user.name,
                        email = user.email,
                        role = user.role,
                        communityId = user.communityId
                    )

                    // Ensure this device is registered for push notifications right after login.
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token ->
                            Log.d("FCM_TOKEN", token)  // 🔥 FULL TOKEN
                            if (!token.isNullOrBlank()) {
                                fcmTokenRepo.upsertToken(user.uid, token)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.w("FCM_TOKEN", "LoginActivity token fetch failed", e)
                        }

                    // Navigate based on the FRESH user object (not old prefs)
                    navigateAfterLogin(user.role, user.communityId)
                }
                is AuthState.Error -> {
                    showLoading(false)
                    // Show error message as a Toast
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    // Reset ViewModel state so the error doesn't re-trigger on rotation
                    viewModel.resetState()
                }
            }
        }
    }

    /**
     * Toggle loading state — show/hide progress bar and button.
     */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }

    /**
     * Navigate after login using the freshly fetched Firestore user profile.
     */
    private fun navigateAfterLogin(role: String, communityId: String) {
        val target = if (communityId.isBlank()) {
            when (role) {
                Constants.ROLE_ADMIN -> CreateCommunityActivity::class.java
                Constants.ROLE_RESIDENT -> DiscoverCommunitiesActivity::class.java
                else -> MainActivity::class.java
            }
        } else {
            MainActivity::class.java
        }

        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
