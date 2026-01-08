package com.itau.app.data.remote

import platform.Foundation.NSBundle

/**
 * iOS simulator can access localhost directly.
 *
 * For production, use HTTPS with SSL pinning configured in
 * Info.plist and URLSession configuration.
 *
 * SSL Pinning Production Setup:
 * 1. Configure App Transport Security in Info.plist
 * 2. Implement URLSession delegate for certificate validation
 * 3. Use HTTPS URL
 */
actual fun getApiBaseUrl(): String {
    // Read from Info.plist or use default for development
    // Use local IP for physical device testing, localhost for simulator
    return NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String
        ?: "http://192.168.1.101:3000/"
}

actual fun isDebugMode(): Boolean {
    // Check if running in debug configuration
    return NSBundle.mainBundle.objectForInfoDictionaryKey("DEBUG_MODE") as? Boolean ?: true
}
