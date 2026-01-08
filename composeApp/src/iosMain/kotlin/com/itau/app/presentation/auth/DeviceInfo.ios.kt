package com.itau.app.presentation.auth

import platform.UIKit.UIDevice

actual fun getPlatform(): String = "ios"

actual fun getOsVersion(): String = UIDevice.currentDevice.systemVersion

actual fun getDeviceModel(): String = UIDevice.currentDevice.model
