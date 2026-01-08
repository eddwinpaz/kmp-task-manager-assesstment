package com.itau.app.presentation.auth

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.auth.AuthException
import com.itau.app.domain.model.auth.AuthState
import com.itau.app.domain.model.auth.DeviceInfo
import com.itau.app.domain.model.auth.MfaRequiredException
import com.itau.app.domain.model.auth.MfaType
import com.itau.app.domain.usecase.auth.GetAuthStateUseCase
import com.itau.app.domain.usecase.auth.LoginUseCase
import com.itau.app.domain.usecase.auth.LogoutUseCase
import com.itau.app.domain.usecase.auth.RefreshTokenUseCase
import com.itau.app.domain.usecase.auth.VerifyMfaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for authentication management.
 */
class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
    private val getAuthStateUseCase: GetAuthStateUseCase,
    private val verifyMfaUseCase: VerifyMfaUseCase
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        initializeAuthState()
        observeAuthState()
    }

    private fun initializeAuthState() {
        viewModelScope.launch {
            try {
                println("AuthViewModel: Initializing auth state...")

                // Use timeout to prevent hanging forever
                val result = withTimeoutOrNull(3000L) {
                    getAuthStateUseCase.checkInitialState()
                    getAuthStateUseCase.getCurrentState()
                }

                if (result != null) {
                    println("AuthViewModel: Auth state initialized to: $result")
                    _authState.value = result
                    updateUiStateFromAuthState(result)
                } else {
                    println("AuthViewModel: Initialization timed out, defaulting to Unauthenticated")
                    _authState.value = AuthState.Unauthenticated
                    _uiState.update { it.copy(isLoading = false, isAuthenticated = false) }
                }
            } catch (e: Exception) {
                println("AuthViewModel: Failed to initialize auth state: ${e.message}")
                e.printStackTrace()
                _authState.value = AuthState.Unauthenticated
                _uiState.update { it.copy(isLoading = false, isAuthenticated = false) }
            }
        }
    }

    private fun updateUiStateFromAuthState(state: AuthState) {
        when (state) {
            is AuthState.Authenticated -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        userName = state.user.name
                    )
                }
            }
            is AuthState.Unauthenticated -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        userName = null
                    )
                }
            }
            is AuthState.SessionExpired -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        error = "Sesión expirada. Por favor inicia sesión nuevamente."
                    )
                }
            }
            is AuthState.Loading -> {
                _uiState.update { it.copy(isLoading = true) }
            }
            is AuthState.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = state.error.message
                    )
                }
            }
        }
    }

    fun processIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.Login -> login(intent.document, intent.password, intent.deviceInfo)
            is AuthIntent.VerifyMfa -> verifyMfa(intent.code, intent.rememberDevice)
            is AuthIntent.Logout -> logout(intent.revokeAllSessions)
            is AuthIntent.UpdateDocument -> updateDocument(intent.document)
            is AuthIntent.UpdatePassword -> updatePassword(intent.password)
            is AuthIntent.ClearError -> clearError()
            is AuthIntent.NavigateToLogin -> navigateToLogin()
            is AuthIntent.RefreshSession -> refreshSession()
        }
    }

    private fun observeAuthState() {
        getAuthStateUseCase()
            .onEach { state ->
                println("AuthViewModel: Observed auth state change: $state")
                _authState.value = state
                updateUiStateFromAuthState(state)
            }
            .launchIn(viewModelScope)
    }

    private fun login(document: String, password: String, deviceInfo: DeviceInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = loginUseCase(document, password, deviceInfo)) {
                is Result.Success -> {
                    val (user, _) = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            userName = user.name,
                            document = "",
                            password = ""
                        )
                    }
                }
                is Result.Error -> {
                    val exception = result.exception
                    if (exception is MfaRequiredException) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                requiresMfa = true,
                                mfaToken = exception.mfaToken,
                                mfaType = exception.mfaType
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = exception.message ?: "Error desconocido"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun verifyMfa(code: String, rememberDevice: Boolean) {
        viewModelScope.launch {
            val mfaToken = _uiState.value.mfaToken ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = verifyMfaUseCase(mfaToken, code, rememberDevice)) {
                is Result.Success -> {
                    val (user, _) = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            requiresMfa = false,
                            mfaToken = null,
                            userName = user.name,
                            document = "",
                            password = "",
                            mfaCode = ""
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.exception.message ?: "Error desconocido"
                        )
                    }
                }
            }
        }
    }

    private fun logout(revokeAllSessions: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            logoutUseCase(revokeAllSessions)
            _uiState.update { AuthUiState() }
        }
    }

    private fun refreshSession() {
        viewModelScope.launch {
            refreshTokenUseCase()
        }
    }

    private fun updateDocument(document: String) {
        _uiState.update { it.copy(document = document) }
    }

    private fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun navigateToLogin() {
        _uiState.update {
            it.copy(
                requiresMfa = false,
                mfaToken = null,
                mfaCode = ""
            )
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val userName: String? = null,
    val document: String = "",
    val password: String = "",
    val requiresMfa: Boolean = false,
    val mfaToken: String? = null,
    val mfaType: MfaType = MfaType.SMS,
    val mfaCode: String = "",
    val rememberDevice: Boolean = false,
    val error: String? = null
)

sealed interface AuthIntent {
    data class Login(
        val document: String,
        val password: String,
        val deviceInfo: DeviceInfo
    ) : AuthIntent

    data class VerifyMfa(
        val code: String,
        val rememberDevice: Boolean = false
    ) : AuthIntent

    data class Logout(
        val revokeAllSessions: Boolean = false
    ) : AuthIntent

    data class UpdateDocument(val document: String) : AuthIntent
    data class UpdatePassword(val password: String) : AuthIntent
    data object ClearError : AuthIntent
    data object NavigateToLogin : AuthIntent
    data object RefreshSession : AuthIntent
}
