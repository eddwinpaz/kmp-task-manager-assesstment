package com.itau.app.data.remote

import com.itau.app.BuildConfig

/**
 * Android uses 10.0.2.2 to access host machine's localhost from emulator.
 *
 * For production, use HTTPS with SSL pinning configured in network_security_config.xml
 * and OkHttp CertificatePinner.
 *
 * SSL Pinning Production Setup:
 * 1. Add network_security_config.xml with certificate pins
 * 2. Configure OkHttp with CertificatePinner
 * 3. Use HTTPS URL
 */
actual fun getApiBaseUrl(): String = BuildConfig.API_BASE_URL

actual fun isDebugMode(): Boolean = BuildConfig.DEBUG
