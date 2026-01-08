package com.itau.app.data.repository

import com.itau.app.core.util.currentTimeMillis
import com.itau.app.data.auth.TokenManager
import com.itau.app.data.auth.TokenState
import com.itau.app.data.local.SecureStorage
import com.itau.app.data.remote.api.AuthApi
import com.itau.app.data.remote.api.BiometricEnrollmentRequest
import com.itau.app.data.remote.api.PasswordResetRequest
import com.itau.app.domain.model.Result
import com.itau.app.domain.model.auth.AccountLockedException
import com.itau.app.domain.model.auth.AuthState
import com.itau.app.domain.model.auth.AuthToken
import com.itau.app.domain.model.auth.BiometricCredentials
import com.itau.app.domain.model.auth.DeviceInfo
import com.itau.app.domain.model.auth.InvalidCredentialsException
import com.itau.app.domain.model.auth.LoginCredentials
import com.itau.app.domain.model.auth.LogoutRequest
import com.itau.app.domain.model.auth.MfaRequiredException
import com.itau.app.domain.model.auth.MfaType
import com.itau.app.domain.model.auth.MfaVerificationRequest
import com.itau.app.domain.model.auth.NetworkException
import com.itau.app.domain.model.auth.RefreshTokenRequest
import com.itau.app.domain.model.auth.ServerException
import com.itau.app.domain.model.auth.SessionExpiredReason
import com.itau.app.domain.model.auth.TokenErrorReason
import com.itau.app.domain.model.auth.TokenException
import com.itau.app.domain.model.auth.User
import com.itau.app.domain.repository.AuthRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Implementation of AuthRepository.
 */
class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    private var cachedUser: User? = null
    private var deviceId: String? = null
    private var isInitialized = false

    override fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    override suspend fun getAuthState(): AuthState {
        if (!isInitialized) {
            initialize()
        }
        return _authState.value
    }

    override suspend fun checkInitialAuthState() {
        if (!isInitialized) {
            initialize()
        }
    }

    private suspend fun initialize() {
        if (isInitialized) return
        isInitialized = true

        try {
            deviceId = secureStorage.getString(SecureStorage.KEY_DEVICE_ID)
            tokenManager.initialize()

            when (val tokenState = tokenManager.tokenState.value) {
                is TokenState.Valid -> {
                    loadCachedUser()?.let { user ->
                        _authState.value = AuthState.Authenticated(user, tokenState.token)
                    } ?: run {
                        try {
                            val user = authApi.getUserProfile(tokenState.token.accessToken)
                            saveUserData(user)
                            _authState.value = AuthState.Authenticated(user, tokenState.token)
                        } catch (e: Exception) {
                            println("AuthRepository: Failed to get user profile: ${e.message}")
                            _authState.value = AuthState.Unauthenticated
                        }
                    }
                }
                is TokenState.NeedsRefresh -> {
                    val result = refreshToken()
                    if (result is Result.Success) {
                        loadCachedUser()?.let { user ->
                            _authState.value = AuthState.Authenticated(user, result.data)
                        } ?: run {
                            _authState.value = AuthState.Unauthenticated
                        }
                    } else {
                        _authState.value = AuthState.SessionExpired(SessionExpiredReason.TOKEN_EXPIRED)
                    }
                }
                is TokenState.Expired -> {
                    _authState.value = AuthState.SessionExpired(SessionExpiredReason.REFRESH_TOKEN_EXPIRED)
                }
                else -> {
                    println("AuthRepository: No valid token found, setting Unauthenticated")
                    _authState.value = AuthState.Unauthenticated
                }
            }
        } catch (e: Exception) {
            println("AuthRepository: Initialization error: ${e.message}")
            e.printStackTrace()
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun login(
        document: String,
        password: String,
        deviceInfo: DeviceInfo
    ): Result<Pair<User, AuthToken>> {
        return try {
            val currentDeviceId = getOrCreateDeviceId()

            val credentials = LoginCredentials(
                document = document,
                password = password,
                deviceId = currentDeviceId,
                deviceName = deviceInfo.deviceModel,
                platform = deviceInfo.platform
            )

            val response = authApi.login(credentials)

            if (response.requiresMfa) {
                return Result.Error(
                    MfaRequiredException(
                        mfaToken = response.mfaToken ?: "",
                        mfaType = MfaType.valueOf(response.mfaType?.uppercase() ?: "SMS")
                    )
                )
            }

            tokenManager.storeToken(response.token)
            saveUserData(response.user)

            cachedUser = response.user
            _authState.value = AuthState.Authenticated(response.user, response.token)

            Result.Success(Pair(response.user, response.token))
        } catch (e: ClientRequestException) {
            handleClientError(e)
        } catch (e: ServerResponseException) {
            Result.Error(ServerException(code = e.response.status.value))
        } catch (e: Exception) {
            Result.Error(NetworkException(cause = e))
        }
    }

    override suspend fun loginWithBiometric(
        credentials: BiometricCredentials
    ): Result<Pair<User, AuthToken>> {
        return try {
            val response = authApi.loginWithBiometric(credentials)

            tokenManager.storeToken(response.token)
            saveUserData(response.user)

            cachedUser = response.user
            _authState.value = AuthState.Authenticated(response.user, response.token)

            Result.Success(Pair(response.user, response.token))
        } catch (e: ClientRequestException) {
            handleClientError(e)
        } catch (e: Exception) {
            Result.Error(NetworkException(cause = e))
        }
    }

    override suspend fun verifyMfa(
        mfaToken: String,
        code: String,
        rememberDevice: Boolean
    ): Result<Pair<User, AuthToken>> {
        return try {
            val currentDeviceId = getOrCreateDeviceId()

            val request = MfaVerificationRequest(
                mfaToken = mfaToken,
                code = code,
                deviceId = currentDeviceId,
                rememberDevice = rememberDevice
            )

            val response = authApi.verifyMfa(request)

            tokenManager.storeToken(response.token)
            saveUserData(response.user)

            cachedUser = response.user
            _authState.value = AuthState.Authenticated(response.user, response.token)

            Result.Success(Pair(response.user, response.token))
        } catch (e: ClientRequestException) {
            handleClientError(e)
        } catch (e: Exception) {
            Result.Error(NetworkException(cause = e))
        }
    }

    override suspend fun logout(revokeAllSessions: Boolean): Result<Unit> {
        return try {
            val accessToken = tokenManager.getAccessToken()
            val refreshToken = tokenManager.getRefreshToken()
            val currentDeviceId = deviceId ?: ""

            if (accessToken != null && refreshToken != null) {
                val request = LogoutRequest(
                    refreshToken = refreshToken,
                    deviceId = currentDeviceId,
                    revokeAllSessions = revokeAllSessions
                )
                authApi.logout(request, accessToken)
            }

            clearAuthData()
            Result.Success(Unit)
        } catch (e: Exception) {
            clearAuthData()
            Result.Success(Unit)
        }
    }

    override suspend fun refreshToken(): Result<AuthToken> {
        return tokenManager.withRefreshLock {
            try {
                val refreshToken = tokenManager.getRefreshToken()
                    ?: return@withRefreshLock Result.Error(
                        TokenException(reason = TokenErrorReason.EXPIRED)
                    )

                val currentDeviceId = getOrCreateDeviceId()

                val request = RefreshTokenRequest(
                    refreshToken = refreshToken,
                    deviceId = currentDeviceId
                )

                val newToken = authApi.refreshToken(request)
                tokenManager.storeToken(newToken)

                cachedUser?.let { user ->
                    _authState.value = AuthState.Authenticated(user, newToken)
                }

                Result.Success(newToken)
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Unauthorized) {
                    _authState.value = AuthState.SessionExpired(SessionExpiredReason.SESSION_REVOKED)
                    clearAuthData()
                }
                handleClientError(e)
            } catch (e: Exception) {
                Result.Error(NetworkException(cause = e))
            }
        }
    }

    override suspend fun getAccessToken(): String? {
        if (tokenManager.needsRefresh()) {
            val result = refreshToken()
            if (result is Result.Error) {
                return null
            }
        }
        return tokenManager.getAccessToken()
    }

    override suspend fun getCurrentUser(): User? {
        return cachedUser ?: loadCachedUser()
    }

    override suspend fun isAuthenticated(): Boolean {
        return tokenManager.getAccessToken() != null
    }

    override suspend fun validateToken(): Boolean {
        return try {
            val accessToken = tokenManager.getAccessToken() ?: return false
            val response = authApi.validateToken(accessToken)
            response.valid
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun requestPasswordReset(document: String, email: String): Result<Unit> {
        return try {
            authApi.requestPasswordReset(PasswordResetRequest(document, email))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(NetworkException(cause = e))
        }
    }

    override suspend fun enrollBiometric(publicKey: String, attestation: String): Result<Unit> {
        return try {
            val accessToken = tokenManager.getAccessToken()
                ?: return Result.Error(TokenException())

            val currentDeviceId = getOrCreateDeviceId()

            val request = BiometricEnrollmentRequest(
                deviceId = currentDeviceId,
                publicKey = publicKey,
                attestation = attestation
            )

            authApi.enrollBiometric(request, accessToken)
            secureStorage.putBoolean(SecureStorage.KEY_BIOMETRIC_ENABLED, true)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(NetworkException(cause = e))
        }
    }

    override suspend fun isBiometricEnrolled(): Boolean {
        return secureStorage.getBoolean(SecureStorage.KEY_BIOMETRIC_ENABLED) ?: false
    }

    override suspend fun clearAuthData() {
        tokenManager.clearTokens()
        secureStorage.remove(SecureStorage.KEY_USER_ID)
        secureStorage.remove(SecureStorage.KEY_USER_EMAIL)
        secureStorage.remove(SecureStorage.KEY_USER_NAME)
        secureStorage.remove(SecureStorage.KEY_BIOMETRIC_TOKEN)
        cachedUser = null
        _authState.value = AuthState.Unauthenticated
    }

    private suspend fun saveUserData(user: User) {
        secureStorage.putString(SecureStorage.KEY_USER_ID, user.id)
        user.email?.let { secureStorage.putString(SecureStorage.KEY_USER_EMAIL, it) }
        secureStorage.putString(SecureStorage.KEY_USER_NAME, user.name)
        cachedUser = user
    }

    private suspend fun loadCachedUser(): User? {
        val id = secureStorage.getString(SecureStorage.KEY_USER_ID) ?: return null
        val email = secureStorage.getString(SecureStorage.KEY_USER_EMAIL)
        val name = secureStorage.getString(SecureStorage.KEY_USER_NAME) ?: return null

        return User(id = id, email = email, name = name).also {
            cachedUser = it
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        return deviceId ?: run {
            val newId = generateDeviceId()
            secureStorage.putString(SecureStorage.KEY_DEVICE_ID, newId)
            deviceId = newId
            newId
        }
    }

    private fun generateDeviceId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }

    private fun <T> handleClientError(e: ClientRequestException): Result<T> {
        return when (e.response.status) {
            HttpStatusCode.Unauthorized -> Result.Error(InvalidCredentialsException())
            HttpStatusCode.Forbidden -> Result.Error(AccountLockedException())
            HttpStatusCode.TooManyRequests -> Result.Error(
                AccountLockedException(message = "Demasiados intentos. Intente mas tarde.")
            )
            else -> Result.Error(ServerException(code = e.response.status.value))
        }
    }
}
