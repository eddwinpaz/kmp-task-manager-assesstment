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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itau.app.domain.model.auth.MfaType

@Composable
fun MfaScreen(
    viewModel: AuthViewModel,
    onVerificationSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var mfaCode by remember { mutableStateOf("") }
    var rememberDevice by remember { mutableStateOf(false) }

    // Navigate on successful verification
    if (state.isAuthenticated && !state.requiresMfa) {
        onVerificationSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF6200))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = "itau",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            // MFA verification card
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
                        text = "Autenticación de Dos Factores",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val mfaTypeText = when (state.mfaType) {
                        MfaType.SMS -> "Enviamos un código a tu teléfono"
                        MfaType.EMAIL -> "Enviamos un código a tu correo"
                        MfaType.TOTP -> "Ingresa el código de tu app autenticadora"
                        MfaType.PUSH_NOTIFICATION -> "Aprueba la notificación en tu dispositivo"
                    }

                    Text(
                        text = mfaTypeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Code input field
                    OutlinedTextField(
                        value = mfaCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                mfaCode = it
                            }
                        },
                        label = { Text("Código de Verificación") },
                        placeholder = { Text("Ingresa el código de 6 dígitos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (mfaCode.length == 6) {
                                    viewModel.processIntent(
                                        AuthIntent.VerifyMfa(mfaCode, rememberDevice)
                                    )
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Remember device checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberDevice,
                            onCheckedChange = { rememberDevice = it },
                            enabled = !state.isLoading,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFFF6200)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recordar este dispositivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Verify button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.processIntent(
                                AuthIntent.VerifyMfa(mfaCode, rememberDevice)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = mfaCode.length == 6 && !state.isLoading,
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
                                text = "Verificar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Back to login link
                    TextButton(
                        onClick = onBackToLogin,
                        enabled = !state.isLoading
                    ) {
                        Text(
                            text = "Volver al Inicio de Sesión",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF6200)
                        )
                    }
                }
            }
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
