package com.itau.app.data.local

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS implementation of SecureStorage using Keychain Services.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class SecureStorageImpl : SecureStorage {

    private val serviceName = "com.itau.app.secure"

    override suspend fun putString(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        try {
            // Convert Kotlin String to NSData via byte array
            val bytes = value.encodeToByteArray()
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            saveToKeychain(key, data)
        } catch (e: Exception) {
            println("SecureStorage iOS: Error putting string: ${e.message}")
        }
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val data = loadFromKeychain(key) ?: return@withContext null
            // Convert NSData back to Kotlin String
            val bytes = data.bytes?.readBytes(data.length.toInt())
            bytes?.decodeToString()
        } catch (e: Exception) {
            println("SecureStorage iOS: Error getting string: ${e.message}")
            null
        }
    }

    override suspend fun putLong(key: String, value: Long): Unit = withContext(Dispatchers.IO) {
        try {
            putString(key, value.toString())
        } catch (e: Exception) {
            println("SecureStorage iOS: Error putting long: ${e.message}")
        }
    }

    override suspend fun getLong(key: String): Long? = withContext(Dispatchers.IO) {
        try {
            getString(key)?.toLongOrNull()
        } catch (e: Exception) {
            println("SecureStorage iOS: Error getting long: ${e.message}")
            null
        }
    }

    override suspend fun putBoolean(key: String, value: Boolean): Unit = withContext(Dispatchers.IO) {
        try {
            putString(key, if (value) "1" else "0")
        } catch (e: Exception) {
            println("SecureStorage iOS: Error putting boolean: ${e.message}")
        }
    }

    override suspend fun getBoolean(key: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            getString(key)?.let { it == "1" }
        } catch (e: Exception) {
            println("SecureStorage iOS: Error getting boolean: ${e.message}")
            null
        }
    }

    override suspend fun remove(key: String): Unit = withContext(Dispatchers.IO) {
        try {
            deleteFromKeychain(key)
        } catch (e: Exception) {
            println("SecureStorage iOS: Error removing key: ${e.message}")
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        try {
            val query = createMutableDictionary()
            try {
                addToDict(query, kSecClass, kSecClassGenericPassword)
                addStringToDict(query, kSecAttrService, serviceName)
                SecItemDelete(query)
            } finally {
                CFRelease(query)
            }
        } catch (e: Exception) {
            println("SecureStorage iOS: Error clearing: ${e.message}")
        }
        Unit
    }

    override suspend fun contains(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            loadFromKeychain(key) != null
        } catch (e: Exception) {
            println("SecureStorage iOS: Error checking contains: ${e.message}")
            false
        }
    }

    private fun saveToKeychain(key: String, data: NSData) {
        deleteFromKeychain(key)

        val query = createMutableDictionary()
        try {
            addToDict(query, kSecClass, kSecClassGenericPassword)
            addStringToDict(query, kSecAttrService, serviceName)
            addStringToDict(query, kSecAttrAccount, key)
            addDataToDict(query, kSecValueData, data)
            addToDict(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)

            val status = SecItemAdd(query, null)
            if (status != errSecSuccess) {
                throw SecurityException("Failed to save to Keychain: $status")
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun loadFromKeychain(key: String): NSData? {
        val query = createMutableDictionary()
        try {
            addToDict(query, kSecClass, kSecClassGenericPassword)
            addStringToDict(query, kSecAttrService, serviceName)
            addStringToDict(query, kSecAttrAccount, key)
            addBoolToDict(query, kSecReturnData, true)
            addToDict(query, kSecMatchLimit, kSecMatchLimitOne)

            memScoped {
                val result = alloc<COpaquePointerVar>()
                val status = SecItemCopyMatching(query, result.ptr)

                return if (status == errSecSuccess && result.value != null) {
                    CFBridgingRelease(result.value) as? NSData
                } else {
                    null
                }
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun deleteFromKeychain(key: String) {
        val query = createMutableDictionary()
        try {
            addToDict(query, kSecClass, kSecClassGenericPassword)
            addStringToDict(query, kSecAttrService, serviceName)
            addStringToDict(query, kSecAttrAccount, key)
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun createMutableDictionary(): CFMutableDictionaryRef {
        return CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ) ?: throw IllegalStateException("Failed to create CFMutableDictionary")
    }

    private fun addToDict(dict: CFMutableDictionaryRef, key: CFTypeRef?, value: CFTypeRef?) {
        if (key != null && value != null) {
            CFDictionaryAddValue(dict, key, value)
        }
    }

    private fun addStringToDict(dict: CFMutableDictionaryRef, key: CFTypeRef?, value: String) {
        // Convert Kotlin String to NSData, then to NSString
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        @Suppress("CAST_NEVER_SUCCEEDS")
        val nsString = NSString.create(data, NSUTF8StringEncoding)
        if (nsString != null) {
            val cfValue = CFBridgingRetain(nsString)
            try {
                addToDict(dict, key, cfValue)
            } finally {
                if (cfValue != null) CFRelease(cfValue)
            }
        }
    }

    private fun addDataToDict(dict: CFMutableDictionaryRef, key: CFTypeRef?, value: NSData) {
        val cfValue = CFBridgingRetain(value)
        try {
            addToDict(dict, key, cfValue)
        } finally {
            if (cfValue != null) CFRelease(cfValue)
        }
    }

    private fun addBoolToDict(dict: CFMutableDictionaryRef, key: CFTypeRef?, value: Boolean) {
        if (value) {
            addToDict(dict, key, kCFBooleanTrue)
        }
    }
}

class SecurityException(message: String) : Exception(message)
