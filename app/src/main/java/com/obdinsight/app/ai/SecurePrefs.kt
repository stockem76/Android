package com.obdinsight.app.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's own Anthropic API key (BYOK - bring your own key) and vehicle context using
 * an Android Keystore-backed encrypted file. The key never leaves the device except in direct
 * HTTPS calls the user's own device makes to api.anthropic.com; it is not sent anywhere else,
 * and is excluded from Android auto-backup (see backup_rules.xml / data_extraction_rules.xml).
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var vehicleContext: String
        get() = prefs.getString(KEY_VEHICLE, null) ?: DEFAULT_VEHICLE
        set(value) = prefs.edit().putString(KEY_VEHICLE, value).apply()

    companion object {
        private const val KEY_API_KEY = "anthropic_api_key"
        private const val KEY_VEHICLE = "vehicle_context"
        const val DEFAULT_VEHICLE =
            "Audi A1 8X (2010-2015), 1.2 TSI engine, engine code CAYC - a EA111-family " +
                "turbocharged direct-injection gasoline engine, EU5/OBD-II (EOBD) compliant."
    }
}
