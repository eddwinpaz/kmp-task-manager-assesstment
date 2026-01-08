package com.itau.app.domain.usecase.auth

import com.itau.app.domain.model.auth.AuthState
import com.itau.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing authentication state.
 *
 * Provides reactive updates to authentication state changes.
 * Used for navigation decisions and UI updates.
 */
class GetAuthStateUseCase(
    private val authRepository: AuthRepository
) {
    /**
     * Observe authentication state as a Flow.
     *
     * @return Flow of AuthState
     */
    operator fun invoke(): Flow<AuthState> {
        return authRepository.observeAuthState()
    }

    /**
     * Get current authentication state.
     *
     * @return Current AuthState
     */
    suspend fun getCurrentState(): AuthState {
        return authRepository.getAuthState()
    }

    /**
     * Check and initialize authentication state.
     * Should be called on app startup.
     */
    suspend fun checkInitialState() {
        authRepository.checkInitialAuthState()
    }
}
