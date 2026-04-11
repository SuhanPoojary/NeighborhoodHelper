package com.example.smartneighborhoodhelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.data.remote.repository.CommunityRepository
import com.example.smartneighborhoodhelper.data.remote.repository.FcmTokenRepository
import com.example.smartneighborhoodhelper.data.remote.repository.NotificationRepository
import com.example.smartneighborhoodhelper.service.ComplaintStatusService
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminComplaintsFragment
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminDashboardFragment
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminJoinRequestsFragment
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminNotificationsFragment
import com.example.smartneighborhoodhelper.ui.fragments.admin.AdminProvidersFragment
import com.example.smartneighborhoodhelper.ui.fragments.resident.NotificationsFragment
import com.example.smartneighborhoodhelper.ui.fragments.resident.ResidentDashboardFragment
import com.example.smartneighborhoodhelper.ui.fragments.resident.ResidentIssuesFragment
import com.example.smartneighborhoodhelper.ui.fragments.shared.ProfileFragment
import com.example.smartneighborhoodhelper.util.Constants
import com.example.smartneighborhoodhelper.util.NotificationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var bottomNav: BottomNavigationView

    private val notificationRepo = NotificationRepository()
    private val communityRepo = CommunityRepository()
    private val fcmTokenRepo = FcmTokenRepository()

    /**
     * Android 13+ (API 33) requires runtime permission to show notifications.
     * Without this, notifications will be silently blocked by the OS.
     * This launcher shows the system permission dialog when called.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Permission result — we start the service regardless.
        // If denied, notifications won't show but the app still works.
        startComplaintStatusService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // 🔥 Handle notification click navigation
        if (intent.getBooleanExtra("openComplaint", false)) {

            val complaintId = intent.getStringExtra("complaintId")

            if (!complaintId.isNullOrEmpty()) {

                window.decorView.post {
                    val i = Intent(
                        this,
                        com.example.smartneighborhoodhelper.ui.complaint.ComplaintDetailActivity::class.java
                    )
                    i.putExtra("complaintId", complaintId)
                    startActivity(i)
                }
            }
        }
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
                    R.id.nav_join_requests -> AdminJoinRequestsFragment()
                    R.id.nav_notifications -> AdminNotificationsFragment()
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

            // Update badges when user changes tabs (so counts feel live)
            updateBottomNavBadges()
            true
        }

        // Load Home tab by default
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        // Ensure this device is registered for push notifications (best-effort)
        registerFcmTokenIfLoggedIn()

        // ── Request notification permission (Android 13+) & start service ──
        requestNotificationPermissionAndStartService()
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavBadges()
    }

    private fun updateBottomNavBadges() {
        val uid = sessionManager.getUserId().orEmpty()
        if (uid.isBlank()) return

        val role = sessionManager.getUserRole().orEmpty()
        val isAdmin = role == Constants.ROLE_ADMIN

        lifecycleScope.launch {
            try {
                // ── Notifications badge (admin + resident) ──
                val unread = notificationRepo.getUnreadCount(uid)
                if (bottomNav.menu.findItem(R.id.nav_notifications) != null) {
                    val badge = bottomNav.getOrCreateBadge(R.id.nav_notifications)
                    if (unread > 0) {
                        badge.isVisible = true
                        badge.number = unread
                    } else {
                        badge.isVisible = false
                    }
                }

                // ── Join requests badge (admin only) ──
                if (isAdmin && bottomNav.menu.findItem(R.id.nav_join_requests) != null) {
                    val communityId = sessionManager.getCommunityId().orEmpty()
                    val pendingCount = if (communityId.isBlank()) 0 else {
                        communityRepo.getPendingJoinRequests(communityId).size
                    }

                    val badge = bottomNav.getOrCreateBadge(R.id.nav_join_requests)
                    if (pendingCount > 0) {
                        badge.isVisible = true
                        badge.number = pendingCount
                    } else {
                        badge.isVisible = false
                    }
                }
            } catch (_: Exception) {
                // Don't crash UI if badge query fails.
            }
        }
    }

    /**
     * On Android 13+ (API 33), we must ask user for POST_NOTIFICATIONS permission.
     * Without it, the OS silently blocks all notifications.
     * On older Android versions, permission is granted automatically at install.
     */
    private fun requestNotificationPermissionAndStartService() {
        NotificationHelper.createChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — check if permission already granted
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Already granted — just start the service
                startComplaintStatusService()
            } else {
                // Not granted — show system permission dialog
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 12 and below — no runtime permission needed
            startComplaintStatusService()
        }
    }

    /**
     * Start the background service that polls Firestore for complaint updates
     * and shows real phone notifications when status changes.
     */
    private fun startComplaintStatusService() {
        val serviceIntent = Intent(this, ComplaintStatusService::class.java)
        startService(serviceIntent)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun registerFcmTokenIfLoggedIn() {
        val uid = sessionManager.getUserId().orEmpty()
        if (uid.isBlank()) return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM_TOKEN", "MainActivity token: ${token?.take(12)}... len=${token?.length}")
                if (!token.isNullOrBlank()) {
                    fcmTokenRepo.upsertToken(uid, token)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FCM_TOKEN", "MainActivity token fetch failed", e)
                // Ignore - push will simply not work on this device until token fetch succeeds.
            }
    }
}