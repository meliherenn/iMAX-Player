package com.imax.player.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parental Control manager using EncryptedSharedPreferences.
 *
 * Features:
 * - PIN setup and verification (4-digit)
 * - Per-category whitelist (child-safe categories)
 * - Child lock mode toggle
 * - PIN stored encrypted via AndroidKeyStore / EncryptedSharedPreferences
 */
@Singleton
class ParentalControlManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_NAME = "imax_parental_control"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_CHILD_LOCK = "child_lock_enabled"
        const val KEY_WHITELIST_PREFIX = "whitelist_"
        const val KEY_PIN_SET = "pin_set"
    }

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences init failed, falling back to plain prefs")
            // Fallback to regular prefs (less secure but won't crash)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Returns true if a PIN has been set.
     */
    val isPinSet: Boolean
        get() = prefs.getBoolean(KEY_PIN_SET, false)

    /**
     * Returns true if child lock is currently active.
     */
    val isChildLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHILD_LOCK, false)

    /**
     * Set a new 4-digit PIN. Stores its SHA-256 hash.
     * Returns false if PIN format is invalid.
     */
    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_SET, true)
            .apply()
        Timber.d("ParentalControl: PIN set")
        return true
    }

    /**
     * Verify the given PIN. Returns true if it matches.
     */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet) return false
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == storedHash
    }

    /**
     * Enable or disable child lock mode.
     * Requires PIN verification — call [verifyPin] before.
     */
    fun setChildLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHILD_LOCK, enabled).apply()
        Timber.d("ParentalControl: child lock = $enabled")
    }

    /**
     * Add a category to the whitelist (allowed in child lock mode).
     */
    fun addToWhitelist(categoryName: String) {
        prefs.edit().putBoolean("$KEY_WHITELIST_PREFIX${categoryName.lowercase()}", true).apply()
    }

    /**
     * Remove a category from the whitelist.
     */
    fun removeFromWhitelist(categoryName: String) {
        prefs.edit().remove("$KEY_WHITELIST_PREFIX${categoryName.lowercase()}").apply()
    }

    /**
     * Check if a category is allowed in child lock mode.
     */
    fun isCategoryAllowed(categoryName: String): Boolean {
        if (!isChildLockEnabled) return true
        return prefs.getBoolean("$KEY_WHITELIST_PREFIX${categoryName.lowercase()}", false)
    }

    /**
     * Returns all whitelisted category names.
     */
    fun getWhitelistedCategories(): Set<String> {
        return prefs.all
            .filter { it.key.startsWith(KEY_WHITELIST_PREFIX) && it.value == true }
            .map { it.key.removePrefix(KEY_WHITELIST_PREFIX) }
            .toSet()
    }

    /**
     * Clear PIN and disable child lock.
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_SET, false)
            .putBoolean(KEY_CHILD_LOCK, false)
            .apply()
        Timber.d("ParentalControl: PIN cleared")
    }

    /**
     * SHA-256 hash of the PIN for secure storage.
     */
    private fun hashPin(pin: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "PIN hashing failed")
            pin // fallback — shouldn't happen
        }
    }
}
