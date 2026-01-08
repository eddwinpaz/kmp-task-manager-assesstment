package com.itau.app.domain.usecase.auth

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.model.auth.DeviceInfo
import com.itau.app.domain.model.auth.InvalidCredentialsException
import com.itau.app.domain.model.auth.User
import com.itau.app.domain.repository.AuthRepository

/**
 * Use case for user login with credentials.
 * Supports Chilean RUT, Brazilian CPF and CNPJ.
 */
class LoginUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        document: String,
        password: String,
        deviceInfo: DeviceInfo
    ): Result<Pair<User, AuthToken>> {
        if (document.isBlank()) {
            return Result.Error(InvalidCredentialsException("Documento es obligatorio"))
        }
        if (password.isBlank()) {
            return Result.Error(InvalidCredentialsException("Contraseña es obligatoria"))
        }

        // Clean document: keep only digits and K (for Chilean RUT)
        val cleanDocument = document.uppercase().replace(Regex("[^0-9K]"), "")

        if (!isValidDocument(cleanDocument)) {
            return Result.Error(InvalidCredentialsException("Documento inválido"))
        }

        return authRepository.login(cleanDocument, password, deviceInfo)
    }

    private fun isValidDocument(document: String): Boolean {
        return when {
            // Chilean RUT: 8-9 characters (digits + optional K)
            document.length in 8..9 -> isValidRut(document)
            else -> false
        }
    }

    /**
     * Validate Chilean RUT (Rol Único Tributario)
     * Format: 8-9 characters, last can be 0-9 or K
     */
    private fun isValidRut(rut: String): Boolean {
        if (rut.length !in 8..9) return false

        // All same digits is invalid
        if (rut.dropLast(1).all { it == rut[0] }) return false

        val body = rut.dropLast(1)
        val checkDigit = rut.last()

        // Body must be all digits
        if (!body.all { it.isDigit() }) return false

        // Calculate check digit
        var sum = 0
        var multiplier = 2
        for (i in body.length - 1 downTo 0) {
            sum += body[i].digitToInt() * multiplier
            multiplier = if (multiplier == 7) 2 else multiplier + 1
        }

        val expectedCheckDigit = when (val remainder = 11 - (sum % 11)) {
            11 -> '0'
            10 -> 'K'
            else -> remainder.digitToChar()
        }

        return checkDigit == expectedCheckDigit
    }

}
