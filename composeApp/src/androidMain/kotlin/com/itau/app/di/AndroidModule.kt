package com.itau.app.di

import com.itau.app.data.local.DatabaseDriverFactory
import com.itau.app.data.local.SecureStorage
import com.itau.app.data.local.SecureStorageImpl
import com.itau.app.data.network.AndroidConnectivityMonitor
import com.itau.app.data.network.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single { DatabaseDriverFactory(androidContext()) }
    single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
    single<SecureStorage> { SecureStorageImpl(androidContext()) }
}
