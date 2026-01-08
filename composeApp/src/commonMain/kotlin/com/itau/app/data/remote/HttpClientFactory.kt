package com.itau.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * API Configuration
 * For local development:
 * - Android Emulator: use "http://10.0.2.2:3000/"
 * - iOS Simulator: use "http://localhost:3000/"
 * - Physical Device: use your computer's IP (e.g., "http://192.168.1.100:3000/")
 *
 * For production:
 * - Use HTTPS with valid SSL certificate
 * - Enable SSL pinning in platform-specific implementations
 * - Set isDebugMode = false
 */
expect fun getApiBaseUrl(): String
expect fun isDebugMode(): Boolean

/**
 * Security configuration for the HTTP client.
 * SSL pinning is configured in platform-specific code (Android/iOS).
 *
 * Production checklist:
 * - Use HTTPS URLs only
 * - Enable certificate pinning
 * - Disable logging in release builds
 * - Use secure timeouts
 */
fun createHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true  // Handle null values gracefully
                prettyPrint = isDebugMode()
            })
        }

        // Only enable logging in debug mode
        if (isDebugMode()) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("HTTP: $message")
                    }
                }
                level = LogLevel.BODY
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }

        defaultRequest {
            url(getApiBaseUrl())
        }

        // Platform-specific SSL configuration is handled in engine configuration
        // See HttpClientFactory.android.kt and HttpClientFactory.ios.kt
    }
}
