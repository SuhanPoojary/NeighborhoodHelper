package com.example.smartneighborhoodhelper

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.service.ComplaintStatusService
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminComplaintsFragment
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminDashboardFragment
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminProvidersFragment
import com.example.smartneighborhoodhelper.ui.fragments.resident.ResidentDashboardFragment
import com.example.smartneighborhoodhelper.ui.fragments.resident.ResidentIssuesFragment
import com.example.smartneighborhoodhelper.ui.fragments.resident.NotificationsFragment
import com.example.smartneighborhoodhelper.ui.fragments.shared.ProfileFragment
import com.example.smartneighborhoodhelper.util.Constants
import com.example.smartneighborhoodhelper.util.NotificationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        bottomNav = findViewById(R.id.bottomNav)

        val role = sessionManager.getUserRole()
        val isAdmin = role == Constants.ROLE_ADMIN

        // Load correct menu for the role
        bottomNav.menu.clear()
        bottomNav.inflateMenu(
            if (isAdmin) R.menu.menu_bottom_nav_admin
            else R.menu.menu_bottom_nav_resident
        )

        // Set up tab switching
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = if (isAdmin) {
                when (item.itemId) {
                    R.id.nav_home -> AdminDashboardFragment()
                    R.id.nav_complaints -> AdminComplaintsFragment()
                    R.id.nav_providers -> AdminProvidersFragment()
                    R.id.nav_profile -> ProfileFragment()
                    else -> AdminDashboardFragment()
                }
            } else {
                when (item.itemId) {
                    R.id.nav_home -> ResidentDashboardFragment()
                    R.id.nav_issues -> ResidentIssuesFragment()
                    R.id.nav_notifications -> NotificationsFragment()
                    R.id.nav_profile -> ProfileFragment()
                    else -> ResidentDashboardFragment()
                }
            }
            loadFragment(fragment)
            true
        }

        // Load Home tab by default
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        // ── Start background service for complaint status polling ──
        NotificationHelper.createChannel(this)
        val serviceIntent = Intent(this, ComplaintStatusService::class.java)
        startService(serviceIntent)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}