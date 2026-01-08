package com.itau.app.domain.model.auth

import com.itau.app.core.util.currentTimeMillis

/**
 * Authentication state representing the current user session status.
 * Used for reactive UI updates and navigation decisions.
 */
sealed interface AuthState {
    /**
     * Initial state - checking for existing session.
     */
    data object Loading : AuthState

    /**
     * User is authenticated with valid tokens.
     */
    data class Authenticated(
        val user: User,
        val token: AuthToken
    ) : AuthState {
        val isTokenExpiringSoon: Boolean
            get() = token.isAccessTokenExpired(currentTimeMillis())
    }

    /**
     * User is not authenticated - no valid session.
     */
    data object Unauthenticated : AuthState

    /**
     * Session expired - user needs to re-authenticate.
     */
    data class SessionExpired(
        val reason: SessionExpiredReason
    ) : AuthState

    /**
     * Authentication error occurred.
     */
    data class Error(
        val error: AuthException
    ) : AuthState
}

/**
 * Reasons for session expiration.
 */
enum class SessionExpiredReason {
    TOKEN_EXPIRED,
    REFRESH_TOKEN_EXPIRED,
    SESSION_REVOKED,
    SECURITY_VIOLATION,
    DEVICE_CHANGED,
    CONCURRENT_SESSION
}

/**
 * Base exception for authentication errors.
 * Extends Exception so it can be used with Result.Error
 */
open class AuthException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Invalid credentials exception.
 */
class InvalidCredentialsException(
    message: String = "Credenciales inválidas"
) : AuthException(message)

/**
 * Account locked exception.
 */
class AccountLockedException(
    message: String = "Cuenta bloqueada por intentos excesivos",
    val remainingLockTime: Long = 0
) : AuthException(message)

/**
 * Network error exception.
 */
class NetworkException(
    message: String = "Error de conexión",
    cause: Throwable? = null
) : AuthException(message, cause)

/**
 * Server error exception.
 */
class ServerException(
    message: String = "Error del servidor",
    val code: Int = 500
) : AuthException(message)

/**
 * Token error exception.
 */
class TokenException(
    message: String = "Error de autenticación",
    val reason: TokenErrorReason = TokenErrorReason.UNKNOWN
) : AuthException(message)

/**
 * Biometric error exception.
 */
class BiometricException(
    message: String = "Error biométrico",
    val reason: BiometricErrorReason = BiometricErrorReason.UNKNOWN
) : AuthException(message)

/**
 * MFA required exception.
 */
class MfaRequiredException(
    message: String = "Verificación de dos factores requerida",
    val mfaToken: String = "",
    val mfaType: MfaType = MfaType.SMS
) : AuthException(message)

enum class TokenErrorReason {
    EXPIRED,
    INVALID_SIGNATURE,
    DECRYPTION_FAILED,
    REVOKED,
    UNKNOWN
}

enum class BiometricErrorReason {
    NOT_AVAILABLE,
    NOT_ENROLLED,
    LOCKOUT,
    USER_CANCELED,
    UNKNOWN
}

enum class MfaType {
    SMS,
    EMAIL,
    TOTP,
    PUSH_NOTIFICATION
}
