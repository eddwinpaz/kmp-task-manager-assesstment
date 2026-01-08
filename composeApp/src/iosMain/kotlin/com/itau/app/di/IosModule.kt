package com.itau.app.di

import com.itau.app.data.local.DatabaseDriverFactory
import com.itau.app.data.local.SecureStorage
import com.itau.app.data.local.SecureStorageImpl
import com.itau.app.data.network.ConnectivityMonitor
import com.itau.app.data.network.IosConnectivityMonitor
import org.koin.dsl.module

val iosModule = module {
    single { DatabaseDriverFactory() }
    single<ConnectivityMonitor> { IosConnectivityMonitor() }
    single<SecureStorage> { SecureStorageImpl() }
}
