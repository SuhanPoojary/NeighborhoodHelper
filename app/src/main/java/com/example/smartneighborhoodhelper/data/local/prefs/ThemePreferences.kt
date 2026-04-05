package com.example.smartneighborhoodhelper.data.local.prefs

import android.content.Context

/**
 * ThemePreferences — stores the user's theme choice.
 *
 * We keep it tiny and syllabus-friendly (SharedPreferences).
 */
object ThemePreferences {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_DARK_MODE = "dark_mode"

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
}

