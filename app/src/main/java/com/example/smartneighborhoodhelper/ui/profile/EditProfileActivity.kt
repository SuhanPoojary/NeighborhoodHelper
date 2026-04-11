package com.example.smartneighborhoodhelper.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.UserRepository
import com.example.smartneighborhoodhelper.databinding.ActivityEditProfileBinding
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var session: SessionManager
    private val userRepo = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Prefill from session (fast)
        binding.etName.setText(session.getUserName().orEmpty())
        binding.etEmail.setText(session.getUserEmail().orEmpty())
        binding.etPhone.setText(session.getUserPhone().orEmpty())

        binding.etEmail.isEnabled = false

        binding.btnSave.setOnClickListener {
            val uid = session.getUserId().orEmpty()
            if (uid.isBlank()) {
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
                finish()
                return@setOnClickListener
            }

            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val phone = binding.etPhone.text?.toString()?.trim().orEmpty()

            if (name.isBlank()) {
                binding.etName.error = "Name is required"
                return@setOnClickListener
            }

            setLoading(true)
            lifecycleScope.launch {
                try {
                    userRepo.updateUser(uid, mapOf(
                        "name" to name,
                        "phone" to phone
                    ))

                    session.updateProfile(name = name, phone = phone)

                    Toast.makeText(this@EditProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@EditProfileActivity, e.message ?: "Failed to update", Toast.LENGTH_SHORT).show()
                } finally {
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSave.isEnabled = !loading
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }
}
