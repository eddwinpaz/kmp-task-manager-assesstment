package com.itau.app.data.auth

import com.itau.app.core.util.currentTimeMillis
import com.itau.app.data.local.SecureStorage
import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.model.auth.JwtClaims
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Token Manager for JWT authentication.
 *
 * Responsibilities:
 * - Secure storage and retrieval of JWT tokens
 * - Token validation and expiration checking
 * - JWT payload decoding (for non-sensitive claims)
 * - Thread-safe token refresh coordination
 *
 * Security Notes:
 * - Tokens are stored encrypted using platform-specific secure storage
 * - Access tokens are short-lived (15 min) to limit exposure
 * - Refresh tokens are device-bound and can be revoked server-side
 * - Token validation is performed locally for performance
 * - Full cryptographic verification happens server-side
 */
class TokenManager(
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val _tokenState = MutableStateFlow<TokenState>(TokenState.Unknown)
    val tokenState: StateFlow<TokenState> = _tokenState.asStateFlow()

    private val refreshMutex = Mutex()
    private var cachedToken: AuthToken? = null

    /**
     * Initialize token manager by loading stored tokens.
     */
    suspend fun initialize() {
        try {
            println("TokenManager: Initializing...")
            val token = loadStoredToken()
            if (token != null) {
                println("TokenManager: Found stored token")
                cachedToken = token
                val currentTime = currentTimeMillis()
                when {
                    token.isRefreshTokenExpired(currentTime) -> {
                        println("TokenManager: Refresh token expired")
                        _tokenState.value = TokenState.Expired
                        clearTokens()
                    }
                    token.isAccessTokenExpired(currentTime) -> {
                        println("TokenManager: Access token expired, needs refresh")
                        _tokenState.value = TokenState.NeedsRefresh(token)
                    }
                    else -> {
                        println("TokenManager: Token is valid")
                        _tokenState.value = TokenState.Valid(token)
                    }
                }
            } else {
                println("TokenManager: No stored token found")
                _tokenState.value = TokenState.None
            }
        } catch (e: Exception) {
            println("TokenManager: Initialization error: ${e.message}")
            e.printStackTrace()
            _tokenState.value = TokenState.None
        }
    }

    /**
     * Store new tokens securely.
     *
     * @param token The authentication token pair to store
     */
    suspend fun storeToken(token: AuthToken) {
        val tokenWithTimestamp = token.copy(
            issuedAt = if (token.issuedAt == 0L) currentTimeMillis() / 1000 else token.issuedAt
        )

        secureStorage.putString(SecureStorage.KEY_ACCESS_TOKEN, tokenWithTimestamp.accessToken)
        secureStorage.putString(SecureStorage.KEY_REFRESH_TOKEN, tokenWithTimestamp.refreshToken)
        secureStorage.putString(SecureStorage.KEY_TOKEN_TYPE, tokenWithTimestamp.tokenType)
        secureStorage.putLong(SecureStorage.KEY_TOKEN_ISSUED_AT, tokenWithTimestamp.issuedAt)
        secureStorage.putLong(SecureStorage.KEY_TOKEN_EXPIRES_IN, tokenWithTimestamp.expiresIn)
        secureStorage.putLong(SecureStorage.KEY_REFRESH_EXPIRES_IN, tokenWithTimestamp.refreshExpiresIn)
        secureStorage.putLong(SecureStorage.KEY_LAST_AUTH_TIME, currentTimeMillis())

        cachedToken = tokenWithTimestamp
        _tokenState.value = TokenState.Valid(tokenWithTimestamp)
    }

    /**
     * Get the current access token for API requests.
     * Returns null if no valid token is available.
     */
    suspend fun getAccessToken(): String? {
        val token = cachedToken ?: loadStoredToken() ?: return null

        val currentTime = currentTimeMillis()
        return when {
            token.isRefreshTokenExpired(currentTime) -> {
                _tokenState.value = TokenState.Expired
                null
            }
            token.isAccessTokenExpired(currentTime) -> {
                _tokenState.value = TokenState.NeedsRefresh(token)
                null
            }
            else -> token.accessToken
        }
    }

    /**
     * Get the refresh token for token renewal.
     */
    suspend fun getRefreshToken(): String? {
        val token = cachedToken ?: loadStoredToken() ?: return null
        return if (!token.isRefreshTokenExpired(currentTimeMillis())) {
            token.refreshToken
        } else {
            _tokenState.value = TokenState.Expired
            null
        }
    }

    /**
     * Get the current token state.
     */
    suspend fun getCurrentToken(): AuthToken? {
        return cachedToken ?: loadStoredToken()
    }

    /**
     * Coordinate token refresh to prevent multiple simultaneous refresh requests.
     * Uses mutex to ensure only one refresh happens at a time.
     *
     * @param refreshAction The action to perform the refresh
     * @return The new token if refresh succeeded, null otherwise
     */
    suspend fun <T> withRefreshLock(refreshAction: suspend () -> T): T {
        return refreshMutex.withLock {
            refreshAction()
        }
    }

    /**
     * Check if access token needs refresh.
     */
    suspend fun needsRefresh(): Boolean {
        val token = cachedToken ?: loadStoredToken() ?: return false
        val currentTime = currentTimeMillis()
        return token.isAccessTokenExpired(currentTime) && !token.isRefreshTokenExpired(currentTime)
    }

    /**
     * Check if completely expired (refresh token expired).
     */
    suspend fun isExpired(): Boolean {
        val token = cachedToken ?: loadStoredToken() ?: return true
        return token.isRefreshTokenExpired(currentTimeMillis())
    }

    /**
     * Clear all stored tokens.
     * Called during logout or session expiration.
     */
    suspend fun clearTokens() {
        secureStorage.remove(SecureStorage.KEY_ACCESS_TOKEN)
        secureStorage.remove(SecureStorage.KEY_REFRESH_TOKEN)
        secureStorage.remove(SecureStorage.KEY_TOKEN_TYPE)
        secureStorage.remove(SecureStorage.KEY_TOKEN_ISSUED_AT)
        secureStorage.remove(SecureStorage.KEY_TOKEN_EXPIRES_IN)
        secureStorage.remove(SecureStorage.KEY_REFRESH_EXPIRES_IN)
        secureStorage.remove(SecureStorage.KEY_LAST_AUTH_TIME)
        cachedToken = null
        _tokenState.value = TokenState.None
    }

    /**
     * Decode JWT payload without verification.
     * NOTE: This only extracts claims for UI display.
     * Full verification must happen server-side.
     *
     * @param token The JWT token string
     * @return Decoded claims or null if decoding fails
     */
    fun decodeJwtClaims(token: String): JwtClaims? {
        return try {
            // JWT format: header.payload.signature
            val parts = token.split(".")
            if (parts.size != 3) return null

            // Decode base64url payload
            val payload = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let { base64 ->
                    // Add padding if needed
                    val padding = (4 - base64.length % 4) % 4
                    base64 + "=".repeat(padding)
                }

            // For KMP, we need platform-specific Base64 decoding
            // This is a simplified version - in production, use proper Base64 decoder
            val decodedPayload = decodeBase64(payload)
            json.decodeFromString<JwtClaims>(decodedPayload)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadStoredToken(): AuthToken? {
        val accessToken = secureStorage.getString(SecureStorage.KEY_ACCESS_TOKEN) ?: return null
        val refreshToken = secureStorage.getString(SecureStorage.KEY_REFRESH_TOKEN) ?: return null
        val tokenType = secureStorage.getString(SecureStorage.KEY_TOKEN_TYPE) ?: "Bearer"
        val issuedAt = secureStorage.getLong(SecureStorage.KEY_TOKEN_ISSUED_AT) ?: 0L
        val expiresIn = secureStorage.getLong(SecureStorage.KEY_TOKEN_EXPIRES_IN) ?: 900L
        val refreshExpiresIn = secureStorage.getLong(SecureStorage.KEY_REFRESH_EXPIRES_IN) ?: 604800L

        return AuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            refreshExpiresIn = refreshExpiresIn,
            issuedAt = issuedAt
        )
    }

    /**
     * Platform-agnostic Base64 URL decoding.
     * In production, use expect/actual for proper implementation.
     */
    private fun decodeBase64(encoded: String): String {
        // Simplified implementation - in production use platform-specific decoder
        val charTable = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val cleanInput = encoded.replace("=", "")
        val bytes = mutableListOf<Byte>()

        var i = 0
        while (i < cleanInput.length) {
            val sextet1 = charTable.indexOf(cleanInput[i])
            val sextet2 = if (i + 1 < cleanInput.length) charTable.indexOf(cleanInput[i + 1]) else 0
            val sextet3 = if (i + 2 < cleanInput.length) charTable.indexOf(cleanInput[i + 2]) else 0
            val sextet4 = if (i + 3 < cleanInput.length) charTable.indexOf(cleanInput[i + 3]) else 0

            val triple = (sextet1 shl 18) or (sextet2 shl 12) or (sextet3 shl 6) or sextet4

            if (i + 1 < cleanInput.length) bytes.add(((triple shr 16) and 0xFF).toByte())
            if (i + 2 < cleanInput.length) bytes.add(((triple shr 8) and 0xFF).toByte())
            if (i + 3 < cleanInput.length) bytes.add((triple and 0xFF).toByte())

            i += 4
        }

        return bytes.toByteArray().decodeToString()
    }
}

/**
 * Token state for reactive updates.
 */
sealed interface TokenState {
    data object Unknown : TokenState
    data object None : TokenState
    data class Valid(val token: AuthToken) : TokenState
    data class NeedsRefresh(val token: AuthToken) : TokenState
    data object Expired : TokenState
}
