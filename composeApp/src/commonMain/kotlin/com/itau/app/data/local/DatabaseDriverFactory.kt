package com.itau.app.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific database driver factory.
 *
 * Security considerations:
 * - Database is stored in app's private storage (sandboxed)
 * - Only this app can access the database files
 * - Android: Uses app-private /data/data/[package]/databases/
 * - iOS: Uses app-private Documents directory with Data Protection
 *
 * For highly sensitive data, consider:
 * - Adding SQLCipher for at-rest encryption
 * - Using Android Keystore / iOS Keychain for encryption keys
 * - Implementing secure data deletion on app uninstall
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
