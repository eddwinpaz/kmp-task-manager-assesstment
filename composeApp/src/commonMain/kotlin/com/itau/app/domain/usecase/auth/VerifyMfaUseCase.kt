package com.itau.app.domain.usecase.auth

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.model.auth.InvalidCredentialsException
import com.itau.app.domain.model.auth.User
import com.itau.app.domain.repository.AuthRepository

/**
 * Use case for MFA verification.
 */
class VerifyMfaUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        mfaToken: String,
        code: String,
        rememberDevice: Boolean = false
    ): Result<Pair<User, AuthToken>> {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return Result.Error(InvalidCredentialsException("Codigo de verificacion invalido"))
        }

        return authRepository.verifyMfa(mfaToken, code, rememberDevice)
    }
}
