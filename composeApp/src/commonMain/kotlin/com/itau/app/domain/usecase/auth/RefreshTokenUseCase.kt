package com.itau.app.domain.usecase.auth

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.repository.AuthRepository

/**
 * Use case for refreshing access token.
 *
 * Uses refresh token to obtain new access token.
 * Called automatically by HTTP interceptor when token expires.
 */
class RefreshTokenUseCase(
    private val authRepository: AuthRepository
) {
    /**
     * Execute token refresh.
     *
     * @return Result containing new token or error
     */
    suspend operator fun invoke(): Result<AuthToken> {
        return authRepository.refreshToken()
    }
}
