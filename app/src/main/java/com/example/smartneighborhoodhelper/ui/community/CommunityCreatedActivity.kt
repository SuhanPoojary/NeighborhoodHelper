package com.example.smartneighborhoodhelper.ui.community

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartneighborhoodhelper.MainActivity
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.databinding.ActivityCommunityCreatedBinding

class CommunityCreatedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunityCreatedBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityCreatedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        val name = intent.getStringExtra(EXTRA_COMMUNITY_NAME).orEmpty()
        val area = intent.getStringExtra(EXTRA_COMMUNITY_AREA).orEmpty()
        val pincode = intent.getStringExtra(EXTRA_COMMUNITY_PINCODE).orEmpty()
        val code = intent.getStringExtra(EXTRA_COMMUNITY_CODE).orEmpty()
        val communityId = intent.getStringExtra(EXTRA_COMMUNITY_ID).orEmpty()

        // Make sure session has communityId too (safety)
        if (communityId.isNotBlank()) sessionManager.saveCommunityId(communityId)

        binding.tvCommunityName.text = name
        binding.tvCommunityArea.text = "$area • $pincode"
        binding.tvCommunityCode.text = code

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Community Code", code))
            Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val shareText = "Join my community on Smart Neighborhood Helper!\n\nCommunity: $name\nCode: $code"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share code"))
        }

        binding.btnGoDashboard.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    companion object {
        const val EXTRA_COMMUNITY_ID = "community_id"
        const val EXTRA_COMMUNITY_NAME = "community_name"
        const val EXTRA_COMMUNITY_AREA = "community_area"
        const val EXTRA_COMMUNITY_PINCODE = "community_pincode"
        const val EXTRA_COMMUNITY_CODE = "community_code"
    }
}

