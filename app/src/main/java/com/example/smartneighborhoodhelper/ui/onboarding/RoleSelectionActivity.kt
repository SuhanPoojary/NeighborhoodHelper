package com.example.smartneighborhoodhelper.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smartneighborhoodhelper.databinding.ActivityRoleSelectionBinding
import com.example.smartneighborhoodhelper.ui.auth.AdminSignupActivity
import com.example.smartneighborhoodhelper.ui.auth.LoginActivity
import com.example.smartneighborhoodhelper.ui.auth.ResidentSignupActivity

/**
 * RoleSelectionActivity.kt — Landing screen where user picks their role.
 *
 * DEMONSTRATES (for your syllabus):
 *   - Activity with ViewBinding
 *   - Explicit Intents — navigating to different Activities based on user choice
 *   - MaterialCardView click handling
 *
 * FLOW:
 *   "I'm an Admin"    → AdminSignupActivity
 *   "I'm a Resident"  → ResidentSignupActivity
 *   "Log In"           → LoginActivity
 */
class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Admin card → Admin Signup
        binding.cardAdmin.setOnClickListener {
            startActivity(Intent(this, AdminSignupActivity::class.java))
        }

        // Resident card → Resident Signup
        binding.cardResident.setOnClickListener {
            startActivity(Intent(this, ResidentSignupActivity::class.java))
        }

        // "Admin" login button → LoginActivity (role will be detected from Firestore)
        binding.tvLoginAdmin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // "Resident" login button → LoginActivity
        binding.tvLoginResident.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
