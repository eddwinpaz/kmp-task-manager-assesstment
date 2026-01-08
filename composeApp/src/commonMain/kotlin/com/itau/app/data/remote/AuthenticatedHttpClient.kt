package com.itau.app.data.remote

import com.itau.app.data.auth.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Authenticated HTTP Client with JWT token management.
 */
class AuthenticatedHttpClientFactory(
    private val tokenManager: TokenManager
) {
    private val refreshMutex = Mutex()
    private var isRefreshing = false

    fun create(): HttpClient {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = false
                    prettyPrint = isDebugMode()
                })
            }

            if (isDebugMode()) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            val maskedMessage = maskSensitiveData(message)
                            println("HTTP: $maskedMessage")
                        }
                    }
                    level = LogLevel.HEADERS
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }

            defaultRequest {
                url(getApiBaseUrl())
            }
        }

        client.plugin(HttpSend).intercept { request ->
            val token = tokenManager.getAccessToken()
            if (token != null && !isAuthEndpoint(request)) {
                request.header(HttpHeaders.Authorization, "Bearer $token")
            }

            addSecurityHeaders(request)

            val response = execute(request)

            if (response.response.status == HttpStatusCode.Unauthorized && !isAuthEndpoint(request)) {
                val refreshed = refreshTokenIfNeeded()

                if (refreshed) {
                    val newToken = tokenManager.getAccessToken()
                    if (newToken != null) {
                        request.headers.remove(HttpHeaders.Authorization)
                        request.header(HttpHeaders.Authorization, "Bearer $newToken")
                        return@intercept execute(request)
                    }
                }
            }

            response
        }

        return client
    }

    private suspend fun refreshTokenIfNeeded(): Boolean {
        return refreshMutex.withLock {
            if (isRefreshing) {
                return@withLock false
            }

            isRefreshing = true
            try {
                val refreshToken = tokenManager.getRefreshToken() ?: return@withLock false

                tokenManager.withRefreshLock {
                    false
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun isAuthEndpoint(request: HttpRequestBuilder): Boolean {
        val path = request.url.buildString()
        return path.contains("/auth/login") ||
                path.contains("/auth/refresh") ||
                path.contains("/auth/register") ||
                path.contains("/auth/password/reset")
    }

    private fun addSecurityHeaders(request: HttpRequestBuilder) {
        request.header("X-Request-ID", generateRequestId())
        request.header("Cache-Control", "no-store, no-cache, must-revalidate")
        request.header("Pragma", "no-cache")
        request.header("X-Content-Type-Options", "nosniff")
    }

    private fun maskSensitiveData(message: String): String {
        return message
            .replace(Regex("Bearer [A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]*"), "Bearer [MASKED]")
            .replace(Regex("\"password\"\\s*:\\s*\"[^\"]*\""), "\"password\":\"[MASKED]\"")
            .replace(Regex("\"refresh_token\"\\s*:\\s*\"[^\"]*\""), "\"refresh_token\":\"[MASKED]\"")
            .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]*\""), "\"access_token\":\"[MASKED]\"")
    }

    private fun generateRequestId(): String {
        val chars = "0123456789abcdef"
        return (1..32).map { chars.random() }.joinToString("")
    }
}

class HttpClientProvider(
    private val authenticatedFactory: AuthenticatedHttpClientFactory
) {
    private var authenticatedClient: HttpClient? = null
    private var unauthenticatedClient: HttpClient? = null

    fun getAuthenticatedClient(): HttpClient {
        return authenticatedClient ?: authenticatedFactory.create().also {
            authenticatedClient = it
        }
    }

    fun getUnauthenticatedClient(): HttpClient {
        return unauthenticatedClient ?: createHttpClient().also {
            unauthenticatedClient = it
        }
    }

    fun close() {
        authenticatedClient?.close()
        unauthenticatedClient?.close()
        authenticatedClient = null
        unauthenticatedClient = null
    }

    fun resetAuthenticatedClient() {
        authenticatedClient?.close()
        authenticatedClient = null
    }
}
