package com.itau.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.itau.app.domain.model.auth.AuthState
import com.itau.app.presentation.auth.AuthViewModel
import com.itau.app.presentation.auth.LoginScreen
import com.itau.app.presentation.auth.RegisterScreen
import com.itau.app.presentation.tasks.TaskListScreen
import com.itau.app.presentation.tasks.TaskListViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/**
 * Navigation screens for the app
 */
enum class Screen {
    Loading,
    Login,
    Register,
    Tasks
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val authViewModel: AuthViewModel = koinInject()
        val taskViewModel: TaskListViewModel = koinInject()

        val authState by authViewModel.authState.collectAsState()

        // Track current screen for manual navigation (Login <-> Register)
        var manualScreen by remember { mutableStateOf<Screen?>(null) }

        // Derive the effective screen from auth state, with manual override for Login/Register navigation
        val effectiveScreen = when {
            // Manual navigation takes precedence for Login/Register
            manualScreen == Screen.Register -> Screen.Register
            // Authenticated always shows Tasks
            authState is AuthState.Authenticated -> Screen.Tasks
            // Any non-authenticated state shows Login (NOT loading spinner)
            // This prevents infinite spinner - we default to Login and let auth complete in background
            authState is AuthState.Loading -> Screen.Login
            authState is AuthState.Unauthenticated -> Screen.Login
            authState is AuthState.SessionExpired -> Screen.Login
            authState is AuthState.Error -> Screen.Login
            // Fallback
            else -> Screen.Login
        }

        // Reset manual navigation when auth state changes to authenticated
        LaunchedEffect(authState) {
            if (authState is AuthState.Authenticated) {
                manualScreen = null
            }
        }

        AnimatedContent(
            targetState = effectiveScreen,
            transitionSpec = {
                when {
                    // Login <-> Register transition
                    (initialState == Screen.Login && targetState == Screen.Register) ||
                    (initialState == Screen.Register && targetState == Screen.Login) -> {
                        if (targetState == Screen.Register) {
                            slideInHorizontally { it } + fadeIn() togetherWith
                                    slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith
                                    slideOutHorizontally { it } + fadeOut()
                        }
                    }
                    // Any other transition
                    else -> {
                        fadeIn() togetherWith fadeOut()
                    }
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.Loading -> {
                    // This should rarely show now
                    LoadingScreen()
                }
                Screen.Login -> {
                    LoginScreen(
                        viewModel = authViewModel,
                        onNavigateToRegister = {
                            manualScreen = Screen.Register
                        },
                        onLoginSuccess = {
                            // Auth state will update automatically, no need to set screen
                            manualScreen = null
                        }
                    )
                }
                Screen.Register -> {
                    RegisterScreen(
                        onNavigateToLogin = {
                            manualScreen = null // This returns to Login via effectiveScreen
                        },
                        onRegistrationSuccess = {
                            manualScreen = null
                        }
                    )
                }
                Screen.Tasks -> {
                    TaskListScreen(
                        viewModel = taskViewModel,
                        onLogout = {
                            authViewModel.processIntent(
                                com.itau.app.presentation.auth.AuthIntent.Logout(revokeAllSessions = false)
                            )
                            manualScreen = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
