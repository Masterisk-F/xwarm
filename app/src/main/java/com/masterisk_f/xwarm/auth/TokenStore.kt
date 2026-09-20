@file:Suppress("DEPRECATION")

package com.masterisk_f.xwarm.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface TokenStore {
    fun getOAuthToken(): String?
    fun saveOAuthToken(token: String)
    fun clearOAuthToken()

    fun getClientId(): String?
    fun saveClientId(clientId: String)

    fun getClientSecret(): String?
    fun saveClientSecret(clientSecret: String)

    fun getRedirectUri(): String?
    fun saveRedirectUri(uri: String)
}

class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "xwarm_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to private prefs if EncryptedSharedPreferences fails on non-standard device
            context.getSharedPreferences("xwarm_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    override fun getOAuthToken(): String? = prefs.getString(KEY_OAUTH_TOKEN, null)

    override fun saveOAuthToken(token: String) {
        prefs.edit().putString(KEY_OAUTH_TOKEN, token.trim()).apply()
    }

    override fun clearOAuthToken() {
        prefs.edit().remove(KEY_OAUTH_TOKEN).apply()
    }

    override fun getClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

    override fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    override fun getClientSecret(): String? = prefs.getString(KEY_CLIENT_SECRET, null)

    override fun saveClientSecret(clientSecret: String) {
        prefs.edit().putString(KEY_CLIENT_SECRET, clientSecret.trim()).apply()
    }

    override fun getRedirectUri(): String? = prefs.getString(KEY_REDIRECT_URI, null)

    override fun saveRedirectUri(uri: String) {
        prefs.edit().putString(KEY_REDIRECT_URI, uri.trim()).apply()
    }

    companion object {
        private const val KEY_OAUTH_TOKEN = "fsq_oauth_token"
        private const val KEY_CLIENT_ID = "fsq_client_id"
        private const val KEY_CLIENT_SECRET = "fsq_client_secret"
        private const val KEY_REDIRECT_URI = "fsq_redirect_uri"
    }
}
