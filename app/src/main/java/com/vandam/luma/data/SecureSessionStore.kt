@file:Suppress("DEPRECATION")

package com.vandam.luma.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

private const val SECURE_PREFS_FILENAME = "com.vandam.luma.secure"
private const val ACCOUNT_NUMBER_KEY = "account_number"
private const val INSTALLATION_ID_KEY = "installation_id"

class SecureSessionStore private constructor(
    context: Context,
) {
    companion object {
        @Volatile private var instance: SecureSessionStore? = null

        fun getInstance(context: Context): SecureSessionStore =
            instance ?: synchronized(this) {
                instance ?: SecureSessionStore(context.applicationContext).also {
                    instance = it
                }
            }
    }

    private val prefs: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILENAME,
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    var accountNumber: String
        get() = prefs.getString(ACCOUNT_NUMBER_KEY, "") ?: ""
        set(value) {
            val sanitized = value.filter(Char::isDigit).take(16)
            prefs.edit().putString(ACCOUNT_NUMBER_KEY, sanitized).apply()
        }

    val installationId: String
        get() {
            val existingValue = prefs.getString(INSTALLATION_ID_KEY, null)?.trim().orEmpty()

            if (existingValue.isNotEmpty()) {
                return existingValue
            }

            val generatedValue = UUID.randomUUID().toString()
            prefs.edit().putString(INSTALLATION_ID_KEY, generatedValue).apply()
            return generatedValue
        }
}
