package com.itau.app.data.remote.api

import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.model.auth.BiometricCredentials
import com.itau.app.domain.model.auth.LoginCredentials
import com.itau.app.domain.model.auth.LogoutRequest
import com.itau.app.domain.model.auth.MfaVerificationRequest
import com.itau.app.domain.model.auth.RefreshTokenRequest
import com.itau.app.domain.model.auth.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authentication API client.
 *
 * Endpoints follow OAuth 2.0 + PKCE flow with JWT tokens.
 * All tokens are signed (RS256) and encrypted (A256GCM).
 *
 * Security Measures:
 * - All requests use HTTPS with certificate pinning
 * - Device binding via device_id in all requests
 * - Rate limiting with exponential backoff on failure
 * - Secure error responses (no information leakage)
 */
class AuthApi(private val httpClient: HttpClient) {

    /**
     * Authenticate user with credentials.
     * Returns JWT token pair if successful.
     *
     * @param credentials User login credentials
     * @return Authentication response with tokens and user info
     */
    suspend fun login(credentials: LoginCredentials): AuthResponse {
        return httpClient.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }.body()
    }

    /**
     * Authenticate using biometric credentials.
     * Requires prior biometric enrollment.
     *
     * @param credentials Biometric authentication data
     * @return Authentication response with tokens
     */
    suspend fun loginWithBiometric(credentials: BiometricCredentials): AuthResponse {
        return httpClient.post("auth/biometric") {
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }.body()
    }

    /**
     * Refresh access token using refresh token.
     * Called automatically when access token expires.
     *
     * @param request Refresh token request
     * @return New token pair
     */
    suspend fun refreshToken(request: RefreshTokenRequest): AuthToken {
        return httpClient.post("auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Verify MFA code and complete authentication.
     *
     * @param request MFA verification request
     * @return Authentication response with tokens
     */
    suspend fun verifyMfa(request: MfaVerificationRequest): AuthResponse {
        return httpClient.post("auth/mfa/verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Logout and revoke tokens.
     * Invalidates the refresh token server-side.
     *
     * @param request Logout request with token to revoke
     * @param accessToken Current access token for authorization
     */
    suspend fun logout(request: LogoutRequest, accessToken: String) {
        httpClient.post("auth/logout") {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            setBody(request)
        }
    }

    /**
     * Get current user profile.
     *
     * @param accessToken Valid access token
     * @return User profile information
     */
    suspend fun getUserProfile(accessToken: String): User {
        return httpClient.post("auth/me") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body()
    }

    /**
     * Validate token with server.
     * Used to verify token hasn't been revoked.
     *
     * @param accessToken Token to validate
     * @return Validation result
     */
    suspend fun validateToken(accessToken: String): TokenValidationResponse {
        return httpClient.post("auth/validate") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body()
    }

    /**
     * Request password reset.
     *
     * @param request Password reset request
     */
    suspend fun requestPasswordReset(request: PasswordResetRequest) {
        httpClient.post("auth/password/reset") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * Enroll device for biometric authentication.
     *
     * @param request Biometric enrollment request
     * @param accessToken Valid access token
     */
    suspend fun enrollBiometric(request: BiometricEnrollmentRequest, accessToken: String) {
        httpClient.post("auth/biometric/enroll") {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            setBody(request)
        }
    }
}

/**
 * Authentication response containing tokens and user info.
 * Matches the backend's response format.
 */
@Serializable
data class AuthResponse(
    @SerialName("accessToken")
    val accessToken: String,

    @SerialName("refreshToken")
    val refreshToken: String,

    @SerialName("expiresIn")
    val expiresIn: Long = 900,

    @SerialName("refreshExpiresIn")
    val refreshExpiresIn: Long = 604800,

    @SerialName("tokenType")
    val tokenType: String = "Bearer",

    @SerialName("user")
    val user: User,

    @SerialName("requiresMfa")
    val requiresMfa: Boolean = false,

    @SerialName("mfaToken")
    val mfaToken: String? = null,

    @SerialName("mfaType")
    val mfaType: String? = null
) {
    // Convert to AuthToken for compatibility
    val token: AuthToken
        get() = AuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            refreshExpiresIn = refreshExpiresIn
        )
}

/**
 * Token validation response.
 */
@Serializable
data class TokenValidationResponse(
    @SerialName("valid")
    val valid: Boolean,

    @SerialName("expires_in")
    val expiresIn: Long = 0,

    @SerialName("scope")
    val scope: String = ""
)

/**
 * Password reset request.
 */
@Serializable
data class PasswordResetRequest(
    @SerialName("document")
    val document: String,

    @SerialName("email")
    val email: String
)

/**
 * Biometric enrollment request.
 */
@Serializable
data class BiometricEnrollmentRequest(
    @SerialName("device_id")
    val deviceId: String,

    @SerialName("public_key")
    val publicKey: String, // Base64 encoded public key

    @SerialName("attestation")
    val attestation: String // Platform attestation data
)
