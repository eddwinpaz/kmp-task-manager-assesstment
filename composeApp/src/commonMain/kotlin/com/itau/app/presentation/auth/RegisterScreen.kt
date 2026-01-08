package com.itau.app.presentation.auth

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import com.itau.app.data.remote.HttpClientProvider

@Serializable
private data class RegisterRequest(
    val document: String,
    val password: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null
)

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegistrationSuccess: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val httpClientProvider: HttpClientProvider = koinInject()

    var name by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Chilean RUT validation: at least 8 characters (without dots/dashes)
    val cleanDocument = document.filter { it.isDigit() || it == 'K' || it == 'k' }
    val isFormValid = name.isNotBlank() &&
            cleanDocument.length >= 8 &&
            password.length >= 8 &&
            password == confirmPassword

    fun register() {
        if (!isFormValid) return

        scope.launch {
            isLoading = true
            error = null

            try {
                val client = httpClientProvider.getUnauthenticatedClient()
                val response = client.post("auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest(
                        document = document,
                        password = password,
                        name = name,
                        email = email.ifBlank { null }
                    ))
                }

                if (response.status.isSuccess()) {
                    successMessage = "¡Cuenta creada exitosamente!"
                    // Navigate to login after short delay
                    onRegistrationSuccess()
                } else {
                    val responseText = response.bodyAsText()
                    error = try {
                        val errorResponse = Json.decodeFromString<Map<String, String>>(responseText)
                        errorResponse["message"] ?: "Error en el registro"
                    } catch (e: Exception) {
                        "Error en el registro. Por favor intenta de nuevo."
                    }
                }
            } catch (e: Exception) {
                error = "Error de conexión. Por favor verifica tu internet."
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF6200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "itaú",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Registration card
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
                        text = "Crear Cuenta",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Text(
                        text = "Completa los datos para registrarte",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre Completo") },
                        placeholder = { Text("Ingresa tu nombre completo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Document field (RUT)
                    OutlinedTextField(
                        value = document,
                        onValueChange = { input ->
                            // Allow digits, dots, dashes, and K/k for Chilean RUT
                            val filtered = input.uppercase().filter { c ->
                                c.isDigit() || c == '.' || c == '-' || c == 'K'
                            }
                            if (filtered.length <= 12) { // Max length with formatting
                                document = filtered
                            }
                        },
                        label = { Text("RUT") },
                        placeholder = { Text("12.345.678-9") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        isError = document.isNotEmpty() && cleanDocument.length < 8,
                        supportingText = if (document.isNotEmpty() && cleanDocument.length < 8) {
                            { Text("El RUT debe tener al menos 8 caracteres") }
                        } else null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Email field (optional)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo (opcional)") },
                        placeholder = { Text("Ingresa tu correo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        placeholder = { Text("Crea una contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        isError = password.isNotEmpty() && password.length < 8,
                        supportingText = if (password.isNotEmpty() && password.length < 8) {
                            { Text("La contraseña debe tener al menos 8 caracteres") }
                        } else null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Confirm password field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar Contraseña") },
                        placeholder = { Text("Confirma tu contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isFormValid) register()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                        supportingText = if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                            { Text("Las contraseñas no coinciden") }
                        } else null
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Register button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            register()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = isFormValid && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6200)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Crear Cuenta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Login link
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "¿Ya tienes cuenta?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = onNavigateToLogin,
                            enabled = !isLoading
                        ) {
                            Text(
                                text = "Iniciar Sesión",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6200)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Error snackbar
        error?.let { errorMessage ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { error = null }) {
                        Text("Cerrar", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text(errorMessage, color = Color.White)
            }
        }

        // Success snackbar
        successMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = Color(0xFF4CAF50)
            ) {
                Text(message, color = Color.White)
            }
        }
    }
}
