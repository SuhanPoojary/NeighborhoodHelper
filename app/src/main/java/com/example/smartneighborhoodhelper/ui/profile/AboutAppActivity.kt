package com.example.smartneighborhoodhelper.ui.profile
import com.example.smartneighborhoodhelper.BuildConfig
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import com.example.smartneighborhoodhelper.databinding.ActivityAboutAppBinding

class AboutAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"
    }
}

