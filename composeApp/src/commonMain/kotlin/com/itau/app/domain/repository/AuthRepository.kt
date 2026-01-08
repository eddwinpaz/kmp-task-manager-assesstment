package com.itau.app.domain.repository

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.auth.AuthState
import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.model.auth.BiometricCredentials
import com.itau.app.domain.model.auth.DeviceInfo
import com.itau.app.domain.model.auth.LoginCredentials
import com.itau.app.domain.model.auth.MfaVerificationRequest
import com.itau.app.domain.model.auth.User
import kotlinx.coroutines.flow.Flow

/**
 * Authentication repository interface.
 *
 * Handles all authentication operations including:
 * - User login/logout
 * - Token management
 * - Session state observation
 * - Biometric authentication
 * - MFA verification
 */
interface AuthRepository {
    /**
     * Observe current authentication state.
     * Emits new state whenever session changes.
     */
    fun observeAuthState(): Flow<AuthState>

    /**
     * Get current authentication state.
     */
    suspend fun getAuthState(): AuthState

    /**
     * Check and initialize authentication state.
     * Should be called on app startup to determine if user is authenticated.
     */
    suspend fun checkInitialAuthState()

    /**
     * Login with document and password.
     *
     * @param document User's document (CPF/CNPJ)
     * @param password User's password
     * @param deviceInfo Device information for security tracking
     * @return Result containing authenticated user and token, or error
     */
    suspend fun login(
        document: String,
        password: String,
        deviceInfo: DeviceInfo
    ): Result<Pair<User, AuthToken>>

    /**
     * Login with biometric authentication.
     *
     * @param credentials Biometric credentials
     * @return Result containing authenticated user and token, or error
     */
    suspend fun loginWithBiometric(
        credentials: BiometricCredentials
    ): Result<Pair<User, AuthToken>>

    /**
     * Verify MFA code and complete authentication.
     *
     * @param mfaToken Temporary MFA token from login response
     * @param code Verification code entered by user
     * @param rememberDevice Whether to skip MFA for this device in future
     * @return Result containing authenticated user and token, or error
     */
    suspend fun verifyMfa(
        mfaToken: String,
        code: String,
        rememberDevice: Boolean = false
    ): Result<Pair<User, AuthToken>>

    /**
     * Logout current session.
     *
     * @param revokeAllSessions If true, revokes all sessions across devices
     * @return Result indicating success or failure
     */
    suspend fun logout(revokeAllSessions: Boolean = false): Result<Unit>

    /**
     * Refresh access token using refresh token.
     * Called automatically when access token expires.
     *
     * @return Result containing new token or error
     */
    suspend fun refreshToken(): Result<AuthToken>

    /**
     * Get current valid access token.
     * Returns null if not authenticated or token expired.
     */
    suspend fun getAccessToken(): String?

    /**
     * Get current authenticated user.
     * Returns null if not authenticated.
     */
    suspend fun getCurrentUser(): User?

    /**
     * Check if user is authenticated with valid token.
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Validate token with server.
     * Used to verify token hasn't been revoked.
     *
     * @return true if token is valid, false otherwise
     */
    suspend fun validateToken(): Boolean

    /**
     * Request password reset email.
     *
     * @param document User's document
     * @param email User's email
     * @return Result indicating success or failure
     */
    suspend fun requestPasswordReset(document: String, email: String): Result<Unit>

    /**
     * Enroll device for biometric authentication.
     *
     * @param publicKey Public key for biometric binding
     * @param attestation Platform attestation data
     * @return Result indicating success or failure
     */
    suspend fun enrollBiometric(publicKey: String, attestation: String): Result<Unit>

    /**
     * Check if biometric authentication is enrolled.
     */
    suspend fun isBiometricEnrolled(): Boolean

    /**
     * Clear all authentication data.
     * Used for complete session cleanup.
     */
    suspend fun clearAuthData()
}
