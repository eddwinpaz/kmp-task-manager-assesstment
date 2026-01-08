package com.itau.app.data.local

/**
 * Secure storage interface for sensitive data.
 *
 * Platform implementations:
 * - Android: EncryptedSharedPreferences with Android Keystore (AES-256-GCM)
 * - iOS: Keychain Services with kSecAttrAccessibleWhenUnlockedThisDeviceOnly
 *
 * Security Features:
 * - Hardware-backed encryption where available
 * - Device-bound keys (cannot be extracted)
 * - Automatic key rotation support
 * - Secure deletion on logout
 */
interface SecureStorage {
    /**
     * Store a string value securely.
     * The value is encrypted before storage.
     *
     * @param key Unique identifier for the value
     * @param value The string value to store (will be encrypted)
     */
    suspend fun putString(key: String, value: String)

    /**
     * Retrieve a securely stored string value.
     * The value is decrypted upon retrieval.
     *
     * @param key Unique identifier for the value
     * @return The decrypted string value, or null if not found
     */
    suspend fun getString(key: String): String?

    /**
     * Store a long value securely.
     *
     * @param key Unique identifier for the value
     * @param value The long value to store
     */
    suspend fun putLong(key: String, value: Long)

    /**
     * Retrieve a securely stored long value.
     *
     * @param key Unique identifier for the value
     * @return The long value, or null if not found
     */
    suspend fun getLong(key: String): Long?

    /**
     * Store a boolean value securely.
     *
     * @param key Unique identifier for the value
     * @param value The boolean value to store
     */
    suspend fun putBoolean(key: String, value: Boolean)

    /**
     * Retrieve a securely stored boolean value.
     *
     * @param key Unique identifier for the value
     * @return The boolean value, or null if not found
     */
    suspend fun getBoolean(key: String): Boolean?

    /**
     * Remove a specific value from secure storage.
     *
     * @param key Unique identifier for the value to remove
     */
    suspend fun remove(key: String)

    /**
     * Clear all values from secure storage.
     * Used during logout to ensure complete session cleanup.
     */
    suspend fun clear()

    /**
     * Check if a key exists in secure storage.
     *
     * @param key Unique identifier to check
     * @return true if the key exists, false otherwise
     */
    suspend fun contains(key: String): Boolean

    companion object {
        // Storage keys for authentication tokens
        const val KEY_ACCESS_TOKEN = "auth_access_token"
        const val KEY_REFRESH_TOKEN = "auth_refresh_token"
        const val KEY_TOKEN_TYPE = "auth_token_type"
        const val KEY_TOKEN_ISSUED_AT = "auth_token_issued_at"
        const val KEY_TOKEN_EXPIRES_IN = "auth_token_expires_in"
        const val KEY_REFRESH_EXPIRES_IN = "auth_refresh_expires_in"
        const val KEY_USER_ID = "auth_user_id"
        const val KEY_USER_EMAIL = "auth_user_email"
        const val KEY_USER_NAME = "auth_user_name"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_BIOMETRIC_TOKEN = "biometric_token"
        const val KEY_LAST_AUTH_TIME = "last_auth_time"
    }
}
