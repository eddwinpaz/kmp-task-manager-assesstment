package com.itau.app.di

import com.itau.app.data.auth.TokenManager
import com.itau.app.data.local.DatabaseDriverFactory
import com.itau.app.data.local.ItauDatabase
import com.itau.app.data.local.SecureStorage
import com.itau.app.data.remote.AuthenticatedHttpClientFactory
import com.itau.app.data.remote.HttpClientProvider
import com.itau.app.data.remote.api.AuthApi
import com.itau.app.data.remote.api.TaskApi
import com.itau.app.data.remote.createHttpClient
import com.itau.app.data.repository.AuthRepositoryImpl
import com.itau.app.data.repository.TaskRepositoryImpl
import com.itau.app.domain.repository.AuthRepository
import com.itau.app.domain.repository.TaskRepository
import com.itau.app.domain.usecase.CreateTaskUseCase
import com.itau.app.domain.usecase.DeleteTaskUseCase
import com.itau.app.domain.usecase.GetTasksUseCase
import com.itau.app.domain.usecase.LoadMoreTasksUseCase
import com.itau.app.domain.usecase.RefreshTasksUseCase
import com.itau.app.domain.usecase.SyncTasksUseCase
import com.itau.app.domain.usecase.ToggleTaskCompletionUseCase
import com.itau.app.domain.usecase.auth.GetAuthStateUseCase
import com.itau.app.domain.usecase.auth.LoginUseCase
import com.itau.app.domain.usecase.auth.LogoutUseCase
import com.itau.app.domain.usecase.auth.RefreshTokenUseCase
import com.itau.app.domain.usecase.auth.VerifyMfaUseCase
import com.itau.app.presentation.auth.AuthViewModel
import com.itau.app.presentation.tasks.TaskListViewModel
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Core module providing common dependencies.
 */
val coreModule = module {
    // JSON serialization
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true  // Coerce nulls to default values
            prettyPrint = false
        }
    }
}

/**
 * Authentication module providing auth-related dependencies.
 */
val authModule = module {
    // Token Manager
    single { TokenManager(get(), get()) }

    // HTTP Clients
    single { AuthenticatedHttpClientFactory(get()) }
    single { HttpClientProvider(get()) }

    // Unauthenticated HTTP client for auth endpoints
    single { createHttpClient() }

    // Auth API
    single { AuthApi(get()) }

    // Auth Repository
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }

    // Auth Use Cases
    factoryOf(::LoginUseCase)
    factoryOf(::LogoutUseCase)
    factoryOf(::RefreshTokenUseCase)
    factoryOf(::GetAuthStateUseCase)
    factoryOf(::VerifyMfaUseCase)

    // Auth ViewModel
    single { AuthViewModel(get(), get(), get(), get(), get()) }
}

/**
 * Data module providing data layer dependencies.
 */
val dataModule = module {
    // Database
    single { ItauDatabase(get<DatabaseDriverFactory>().createDriver()) }

    // Task API (uses authenticated client)
    single { TaskApi(get<HttpClientProvider>().getAuthenticatedClient()) }

    // Task Repository
    single<TaskRepository> { TaskRepositoryImpl(get(), get(), get()) }
}

/**
 * Domain module providing use cases.
 */
val domainModule = module {
    // Task Use Cases
    factoryOf(::GetTasksUseCase)
    factoryOf(::RefreshTasksUseCase)
    factoryOf(::LoadMoreTasksUseCase)
    factoryOf(::CreateTaskUseCase)
    factoryOf(::ToggleTaskCompletionUseCase)
    factoryOf(::DeleteTaskUseCase)
    factoryOf(::SyncTasksUseCase)
}

/**
 * Presentation module providing ViewModels.
 */
val presentationModule = module {
    // Task ViewModel - Using single for ViewModel to survive recomposition
    single { TaskListViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}

/**
 * All shared modules combined.
 */
val sharedModules = listOf(coreModule, authModule, dataModule, domainModule, presentationModule)
