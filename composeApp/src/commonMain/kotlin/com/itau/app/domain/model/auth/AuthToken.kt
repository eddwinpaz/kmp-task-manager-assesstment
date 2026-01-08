package com.itau.app.domain.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JWT Token pair containing access and refresh tokens.
 *
 * Security Implementation:
 * - Access Token: Short-lived (15 min), used for API authorization
 * - Refresh Token: Long-lived (7 days), used to obtain new access tokens
 * - Both tokens are signed (JWS) and encrypted (JWE) using RS256 + A256GCM
 *
 * Token Structure (Nested JWT - Sign then Encrypt):
 * 1. Inner JWT (JWS): Signed payload with claims
 * 2. Outer JWT (JWE): Encrypted wrapper for confidentiality
 */
@Serializable
data class AuthToken(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String,

    @SerialName("token_type")
    val tokenType: String = "Bearer",

    @SerialName("expires_in")
    val expiresIn: Long = 900, // 15 minutes in seconds

    @SerialName("refresh_expires_in")
    val refreshExpiresIn: Long = 604800, // 7 days in seconds

    @SerialName("issued_at")
    val issuedAt: Long = 0
) {
    /**
     * Check if access token is expired with a buffer of 60 seconds.
     */
    fun isAccessTokenExpired(currentTimeMillis: Long): Boolean {
        val expirationTime = (issuedAt + expiresIn) * 1000
        val bufferMillis = 60_000 // 60 seconds buffer
        return currentTimeMillis >= (expirationTime - bufferMillis)
    }

    /**
     * Check if refresh token is expired.
     */
    fun isRefreshTokenExpired(currentTimeMillis: Long): Boolean {
        val expirationTime = (issuedAt + refreshExpiresIn) * 1000
        return currentTimeMillis >= expirationTime
    }

    /**
     * Format access token with Bearer prefix for Authorization header.
     */
    fun toBearerToken(): String = "$tokenType $accessToken"
}

/**
 * JWT Claims extracted from the access token payload.
 * These claims are validated after decryption and signature verification.
 */
@Serializable
data class JwtClaims(
    @SerialName("sub")
    val subject: String, // User ID

    @SerialName("iss")
    val issuer: String, // Token issuer (e.g., "https://auth.itau.com")

    @SerialName("aud")
    val audience: String, // Intended audience (e.g., "itau-mobile-app")

    @SerialName("exp")
    val expiration: Long, // Expiration timestamp

    @SerialName("iat")
    val issuedAt: Long, // Issued at timestamp

    @SerialName("jti")
    val tokenId: String, // Unique token identifier for revocation

    @SerialName("scope")
    val scope: String = "", // OAuth scopes (e.g., "read write transfer")

    @SerialName("client_id")
    val clientId: String = "", // OAuth client ID

    @SerialName("device_id")
    val deviceId: String = "" // Device fingerprint for binding
)

/**
 * JWT Header for signed tokens (JWS).
 */
@Serializable
data class JwtHeader(
    @SerialName("alg")
    val algorithm: String = "RS256", // RSA SHA-256 signature

    @SerialName("typ")
    val type: String = "JWT",

    @SerialName("kid")
    val keyId: String = "" // Key ID for key rotation support
)

/**
 * JWT Header for encrypted tokens (JWE).
 */
@Serializable
data class JweHeader(
    @SerialName("alg")
    val algorithm: String = "RSA-OAEP-256", // Key encryption algorithm

    @SerialName("enc")
    val encryption: String = "A256GCM", // Content encryption algorithm

    @SerialName("typ")
    val type: String = "JWT",

    @SerialName("kid")
    val keyId: String = "" // Key ID for key rotation
)
