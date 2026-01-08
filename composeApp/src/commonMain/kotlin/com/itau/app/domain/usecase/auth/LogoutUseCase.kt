package com.itau.app.domain.usecase.auth

import com.itau.app.domain.model.Result
import com.itau.app.domain.repository.AuthRepository

/**
 * Use case for user logout.
 *
 * Revokes tokens server-side and clears local authentication data.
 * Optionally revokes all sessions across devices.
 */
class LogoutUseCase(
    private val authRepository: AuthRepository
) {
    /**
     * Execute logout.
     *
     * @param revokeAllSessions If true, revokes all sessions across all devices
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(revokeAllSessions: Boolean = false): Result<Unit> {
        return authRepository.logout(revokeAllSessions)
    }
}
