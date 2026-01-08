package com.itau.app.presentation.auth

import android.os.Build

actual fun getPlatform(): String = "android"

actual fun getOsVersion(): String = Build.VERSION.RELEASE

actual fun getDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
