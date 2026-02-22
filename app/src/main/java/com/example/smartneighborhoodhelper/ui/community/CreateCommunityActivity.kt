package com.example.smartneighborhoodhelper.ui.community

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.CommunityRepository
import com.example.smartneighborhoodhelper.databinding.ActivityCreateCommunityBinding
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import kotlinx.coroutines.launch

/**
 * CreateCommunityActivity — shown right after Admin signup.
 *
 * You saw a black/Hello World screen because this Activity was still using
 * the default template layout (activity_main.xml).
 *
 * Now we load activity_create_community.xml so the flow looks correct.
 *
 * Feature 2 will add real Firestore logic here.
 */
class CreateCommunityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateCommunityBinding
    private lateinit var sessionManager: SessionManager
    private val communityRepository = CommunityRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.ivBack.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }

        binding.btnCreate.setOnClickListener {
            val name = binding.etCommunityName.text?.toString().orEmpty().trim()
            val area = binding.etArea.text?.toString().orEmpty().trim()
            val pincode = binding.etPincode.text?.toString().orEmpty().trim()

            clearErrors()
            if (!validate(name, area, pincode)) return@setOnClickListener

            val uid = sessionManager.getUserId()
            if (uid.isNullOrBlank()) {
                Toast.makeText(this, "Session missing. Please login again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    showLoading(true)
                    val community = communityRepository.createCommunity(name, area, pincode, uid)

                    // Save community id to session so Splash can route later
                    sessionManager.saveCommunityId(community.id)

                    val intent = Intent(this@CreateCommunityActivity, CommunityCreatedActivity::class.java)
                    intent.putExtra(CommunityCreatedActivity.EXTRA_COMMUNITY_ID, community.id)
                    intent.putExtra(CommunityCreatedActivity.EXTRA_COMMUNITY_NAME, community.name)
                    intent.putExtra(CommunityCreatedActivity.EXTRA_COMMUNITY_AREA, community.area)
                    intent.putExtra(CommunityCreatedActivity.EXTRA_COMMUNITY_PINCODE, community.pincode)
                    intent.putExtra(CommunityCreatedActivity.EXTRA_COMMUNITY_CODE, community.code)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    showLoading(false)
                    Toast.makeText(this@CreateCommunityActivity, e.message ?: "Failed to create community", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearErrors() {
        binding.tilCommunityName.error = null
        binding.tilArea.error = null
        binding.tilPincode.error = null
    }

    private fun validate(name: String, area: String, pincode: String): Boolean {
        var ok = true
        if (name.isBlank()) {
            binding.tilCommunityName.error = "Community name required"
            ok = false
        }
        if (area.isBlank()) {
            binding.tilArea.error = "Area required"
            ok = false
        }
        if (pincode.length != 6) {
            binding.tilPincode.error = "Enter 6-digit pincode"
            ok = false
        }
        return ok
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCreate.isEnabled = !loading
        binding.btnCreate.alpha = if (loading) 0.7f else 1f
    }
}
