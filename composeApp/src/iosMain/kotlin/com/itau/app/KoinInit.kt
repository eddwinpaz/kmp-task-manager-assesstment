package com.itau.app

import com.itau.app.di.iosModule
import com.itau.app.di.sharedModules
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(sharedModules + iosModule)
    }
}
