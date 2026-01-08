package com.itau.app.domain.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Login request credentials.
 * Matches the backend's expected format.
 */
@Serializable
data class LoginCredentials(
    @SerialName("document")
    val document: String,

    @SerialName("password")
    val password: String,

    @SerialName("deviceId")
    val deviceId: String? = null,

    @SerialName("deviceName")
    val deviceName: String? = null,

    @SerialName("platform")
    val platform: String? = null
)

/**
 * Biometric authentication request.
 */
@Serializable
data class BiometricCredentials(
    @SerialName("user_id")
    val userId: String,

    @SerialName("biometric_token")
    val biometricToken: String, // Signed challenge from biometric prompt

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("device_info")
    val deviceInfo: DeviceInfo,

    @SerialName("grant_type")
    val grantType: String = "biometric",

    @SerialName("client_id")
    val clientId: String = "itau-mobile-app"
)

/**
 * Token refresh request.
 */
@Serializable
data class RefreshTokenRequest(
    @SerialName("refreshToken")
    val refreshToken: String,

    @SerialName("deviceId")
    val deviceId: String? = null
)

/**
 * MFA verification request.
 */
@Serializable
data class MfaVerificationRequest(
    @SerialName("mfa_token")
    val mfaToken: String,

    @SerialName("code")
    val code: String,

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("remember_device")
    val rememberDevice: Boolean = false
)

/**
 * Device information for security tracking.
 */
@Serializable
data class DeviceInfo(
    @SerialName("platform")
    val platform: String, // "android" or "ios"

    @SerialName("os_version")
    val osVersion: String,

    @SerialName("app_version")
    val appVersion: String,

    @SerialName("device_model")
    val deviceModel: String,

    @SerialName("device_name")
    val deviceName: String = "",

    @SerialName("is_rooted")
    val isRooted: Boolean = false,

    @SerialName("is_emulator")
    val isEmulator: Boolean = false,

    @SerialName("screen_resolution")
    val screenResolution: String = "",

    @SerialName("timezone")
    val timezone: String = "",

    @SerialName("language")
    val language: String = ""
)

/**
 * Logout request.
 */
@Serializable
data class LogoutRequest(
    @SerialName("refresh_token")
    val refreshToken: String,

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("revoke_all_sessions")
    val revokeAllSessions: Boolean = false
)
