package com.example.smartneighborhoodhelper.util

/**
 * LocationHelper.kt — Wraps FusedLocationProviderClient for GPS location.
 *
 * WHY a separate helper?
 *   Location logic involves runtime permissions, callbacks, and error handling.
 *   Keeping it in one place avoids duplicating this code across Activities.
 *
 * WILL BE FULLY IMPLEMENTED IN FEATURE 4 (Report Complaint with GPS).
 */
class LocationHelper {
    // TODO: Implement in Feature 4
    // - getCurrentLocation(context) → Pair<Double, Double> (lat, lng)
    // - handles runtime permission checks
    // - uses FusedLocationProviderClient from play-services-location
}

