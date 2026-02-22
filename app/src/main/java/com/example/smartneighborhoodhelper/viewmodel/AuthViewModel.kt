package com.example.smartneighborhoodhelper.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartneighborhoodhelper.data.model.User
import com.example.smartneighborhoodhelper.data.remote.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * AuthViewModel.kt — ViewModel for Login and Signup screens.
 *
 * WHAT IS A ViewModel?
 *   A ViewModel survives configuration changes (like screen rotation).
 *   Without it, rotating your phone would lose all form data and state.
 *   The Activity/Fragment OBSERVES LiveData from the ViewModel.
 *   When the data changes, the UI auto-updates.
 *
 * SEALED CLASS AuthState:
 *   Represents all possible states of an auth operation:
 *   - Idle      → nothing happening yet
 *   - Loading   → Firebase call in progress (show spinner)
 *   - Success   → login/signup succeeded (navigate to next screen)
 *   - Error     → something went wrong (show error message)
 *
 * WHY sealed class?
 *   A sealed class restricts which subclasses can exist.
 *   When you use "when(state)" in the Activity, the compiler forces you
 *   to handle ALL cases — no forgotten states, no crashes.
 */

/** All possible states of an authentication operation */
sealed class AuthState {
    object Idle : AuthState()                    // Default — nothing happening
    object Loading : AuthState()                 // Show progress spinner
    data class Success(val user: User) : AuthState()  // Auth succeeded
    data class Error(val message: String) : AuthState() // Auth failed
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    // _authState is private — only the ViewModel can modify it
    // authState is public — the Activity observes it (read-only)
    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    /**
     * Log in with email and password.
     * viewModelScope.launch runs the coroutine and auto-cancels if ViewModel is destroyed.
     */
    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val user = repository.signIn(email, password)
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message ?: "Login failed. Please try again."
                )
            }
        }
    }

    /**
     * Sign up a new user.
     * Creates Firebase Auth account + Firestore user document.
     */
    fun signup(name: String, email: String, password: String, phone: String, role: String, pincode: String) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val user = repository.signUp(name, email, password, phone, role, pincode)
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message ?: "Signup failed. Please try again."
                )
            }
        }
    }

    /** Reset state back to Idle (e.g., after showing an error) */
    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
