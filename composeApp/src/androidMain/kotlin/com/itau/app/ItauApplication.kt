package com.itau.app

import android.app.Application
import com.itau.app.di.androidModule
import com.itau.app.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ItauApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@ItauApplication)
            modules(sharedModules + androidModule)
        }
    }
}
