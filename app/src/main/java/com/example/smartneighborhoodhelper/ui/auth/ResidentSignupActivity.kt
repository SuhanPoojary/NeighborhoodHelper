package com.example.smartneighborhoodhelper.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartneighborhoodhelper.R
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.databinding.ActivityResidentSignupBinding
import com.example.smartneighborhoodhelper.ui.community.DiscoverCommunitiesActivity
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import com.example.smartneighborhoodhelper.util.Constants
import com.example.smartneighborhoodhelper.viewmodel.AuthState
import com.example.smartneighborhoodhelper.viewmodel.AuthViewModel

/**
 * ResidentSignupActivity.kt — Resident registration screen.
 *
 * FLOW:
 *   1. Resident fills: Name, Email, Phone, Password, Confirm Password, Pincode
 *   2. Taps "Discover Communities"
 *   3. Firebase creates account → navigates to DiscoverCommunitiesActivity
 *      passing the pincode so it can search for nearby communities
 */
class ResidentSignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResidentSignupBinding
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResidentSignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionManager = SessionManager(this)
        setupClickListeners()
        observeAuthState()
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }

        binding.btnSignup.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val pincode = binding.etPincode.text.toString().trim()

            if (validateInputs(name, email, phone, password, confirmPassword, pincode)) {
                viewModel.signup(name, email, password, phone, Constants.ROLE_RESIDENT, pincode)
            }
        }
    }

    private fun validateInputs(
        name: String, email: String, phone: String,
        password: String, confirmPassword: String, pincode: String
    ): Boolean {
        var isValid = true
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
        binding.tilPincode.error = null

        if (name.isEmpty()) { binding.tilName.error = getString(R.string.error_empty_name); isValid = false }
        if (email.isEmpty()) { binding.tilEmail.error = getString(R.string.error_empty_email); isValid = false }
        else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.tilEmail.error = getString(R.string.error_invalid_email); isValid = false }
        if (phone.isNotEmpty() && phone.length != 10) { binding.tilPhone.error = getString(R.string.error_invalid_phone); isValid = false }
        if (password.isEmpty()) { binding.tilPassword.error = getString(R.string.error_empty_password); isValid = false }
        else if (password.length < 6) { binding.tilPassword.error = getString(R.string.error_short_password); isValid = false }
        if (confirmPassword != password) { binding.tilConfirmPassword.error = getString(R.string.error_password_mismatch); isValid = false }
        if (pincode.isEmpty()) { binding.tilPincode.error = getString(R.string.error_empty_pincode); isValid = false }
        else if (pincode.length != 6) { binding.tilPincode.error = getString(R.string.error_invalid_pincode); isValid = false }

        return isValid
    }

    private fun observeAuthState() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Idle -> showLoading(false)
                is AuthState.Loading -> showLoading(true)
                is AuthState.Success -> {
                    showLoading(false)
                    val user = state.user
                    sessionManager.saveUserSession(user.uid, user.name, user.email, user.role, user.communityId)
                    // Navigate to Discover Communities with the pincode
                    val intent = Intent(this, DiscoverCommunitiesActivity::class.java)
                    intent.putExtra("pincode", user.pincode)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                is AuthState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSignup.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }
}
