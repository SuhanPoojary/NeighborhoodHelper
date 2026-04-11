package com.example.smartneighborhoodhelper.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartneighborhoodhelper.databinding.ActivityChangePasswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnChangePassword.setOnClickListener {
            val user = auth.currentUser
            val email = user?.email

            if (user == null || email.isNullOrBlank()) {
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
                finish()
                return@setOnClickListener
            }

            val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
            val newPassword = binding.etNewPassword.text?.toString().orEmpty()
            val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

            if (currentPassword.isBlank()) {
                binding.etCurrentPassword.error = "Required"
                return@setOnClickListener
            }

            if (newPassword.length < 6) {
                binding.etNewPassword.error = "Minimum 6 characters"
                return@setOnClickListener
            }

            if (newPassword != confirm) {
                binding.etConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            setLoading(true)
            lifecycleScope.launch {
                try {
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    user.reauthenticate(credential).await()
                    user.updatePassword(newPassword).await()
                    Toast.makeText(this@ChangePasswordActivity, "Password changed", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@ChangePasswordActivity, e.message ?: "Failed to change password", Toast.LENGTH_SHORT).show()
                } finally {
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnChangePassword.isEnabled = !loading
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }
}

