package com.itau.app.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itau.app.domain.model.auth.DeviceInfo

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // Navigate on successful login
    if (state.isAuthenticated && !state.requiresMfa) {
        onLoginSuccess()
    }

    // Show MFA screen if required
    if (state.requiresMfa) {
        MfaScreen(
            viewModel = viewModel,
            onVerificationSuccess = onLoginSuccess,
            onBackToLogin = { viewModel.processIntent(AuthIntent.NavigateToLogin) }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF6200)) // Itau orange
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with logo area
            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = "itaú",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Gestor de Tareas",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Login card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bienvenido",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Text(
                        text = "Inicia sesión para continuar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Document field (RUT)
                    OutlinedTextField(
                        value = state.document,
                        onValueChange = { input ->
                            // Allow digits, dots, dashes, and K/k for Chilean RUT
                            val filtered = input.uppercase().filter { c ->
                                c.isDigit() || c == '.' || c == '-' || c == 'K'
                            }
                            viewModel.processIntent(AuthIntent.UpdateDocument(filtered))
                        },
                        label = { Text("RUT") },
                        placeholder = { Text("12.345.678-9") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = {
                            viewModel.processIntent(AuthIntent.UpdatePassword(it))
                        },
                        label = { Text("Contraseña") },
                        placeholder = { Text("Ingresa tu contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (state.document.isNotBlank() && state.password.isNotBlank()) {
                                    performLogin(viewModel, state.document, state.password)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            performLogin(viewModel, state.document, state.password)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = state.document.isNotBlank() &&
                                  state.password.isNotBlank() &&
                                  !state.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6200)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Iniciar Sesión",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Register link
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "¿No tienes cuenta?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = onNavigateToRegister,
                            enabled = !state.isLoading
                        ) {
                            Text(
                                text = "Regístrate",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6200)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Version info
            Text(
                text = "Versión 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Error snackbar
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.processIntent(AuthIntent.ClearError) }) {
                        Text("Cerrar", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text(error, color = Color.White)
            }
        }
    }
}

private fun performLogin(viewModel: AuthViewModel, document: String, password: String) {
    val deviceInfo = DeviceInfo(
        platform = getPlatform(),
        osVersion = getOsVersion(),
        appVersion = "1.0.0",
        deviceModel = getDeviceModel()
    )
    viewModel.processIntent(AuthIntent.Login(document, password, deviceInfo))
}

// Platform-specific implementations will be provided via expect/actual
expect fun getPlatform(): String
expect fun getOsVersion(): String
expect fun getDeviceModel(): String
