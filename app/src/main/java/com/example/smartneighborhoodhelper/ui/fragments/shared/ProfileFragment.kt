package com.example.smartneighborhoodhelper.ui.fragments.shared

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.smartneighborhoodhelper.data.local.prefs.SessionManager
import com.example.smartneighborhoodhelper.databinding.FragmentProfileBinding
import com.example.smartneighborhoodhelper.ui.onboarding.RoleSelectionActivity
import com.example.smartneighborhoodhelper.ui.profile.AboutAppActivity
import com.example.smartneighborhoodhelper.ui.profile.ChangePasswordActivity
import com.example.smartneighborhoodhelper.ui.profile.EditProfileActivity
import com.example.smartneighborhoodhelper.ui.profile.HelpSupportActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager(requireContext())

        fun bindUserInfo() {
            val name = session.getUserName().orEmpty()
            binding.tvName.text = name.ifBlank { "User" }
            binding.tvAvatar.text = if (name.isNotBlank()) name.first().uppercase() else "U"
            binding.tvEmail.text = FirebaseAuth.getInstance().currentUser?.email ?: "—"
            binding.tvRole.text = (session.getUserRole() ?: "user").replaceFirstChar { it.uppercase() }
        }

        // Populate user info
        bindUserInfo()

        // Settings options
        binding.optEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }
        binding.optChangePassword.setOnClickListener {
            startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }
        binding.optHelp.setOnClickListener {
            startActivity(Intent(requireContext(), HelpSupportActivity::class.java))
        }
        binding.optAbout.setOnClickListener {
            startActivity(Intent(requireContext(), AboutAppActivity::class.java))
        }

        // Logout with confirmation dialog
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout") { _, _ ->
                    session.clearSession()
                    FirebaseAuth.getInstance().signOut()

                    val intent = Intent(requireContext(), RoleSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    activity?.finish()
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh session-backed fields after Edit Profile
        val session = SessionManager(requireContext())
        val name = session.getUserName().orEmpty()
        binding.tvName.text = name.ifBlank { "User" }
        binding.tvAvatar.text = if (name.isNotBlank()) name.first().uppercase() else "U"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
