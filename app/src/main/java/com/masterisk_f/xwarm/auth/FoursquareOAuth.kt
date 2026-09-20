package com.masterisk_f.xwarm.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object FoursquareOAuth {

    private const val AUTH_URL = "https://foursquare.com/oauth2/authenticate"
    private const val TOKEN_URL = "https://foursquare.com/oauth2/access_token"

    private val CODE_REGEX = Regex("""[?&]code=([^&#]+)""")

    fun buildAuthorizeUrl(clientId: String, redirectUri: String): String {
        return AUTH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId.trim())
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", redirectUri.trim())
            .build()
            .toString()
    }

    fun extractCodeFromUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.contains("error=")) return null

        val match = CODE_REGEX.find(trimmed)
        if (match != null) {
            val rawCode = match.groupValues[1]
            return cleanTrailingFragment(rawCode)
        }

        // If it's not a URL (doesn't contain :// or ?), assume raw code
        if (!trimmed.contains("://") && !trimmed.contains("?") && !trimmed.contains("=")) {
            return cleanTrailingFragment(trimmed)
        }

        return null
    }

    private fun cleanTrailingFragment(code: String): String {
        val hashIdx = code.indexOf('#')
        return if (hashIdx >= 0) code.substring(0, hashIdx) else code
    }

    suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        code: String,
        client: OkHttpClient = defaultClient(),
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("client_secret", clientSecret.trim())
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri.trim())
                .add("code", code.trim())
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from token endpoint")
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val obj = JSONObject(body)
                        obj.optString("error_description").ifEmpty {
                            obj.optString("error").ifEmpty { "HTTP ${response.code}" }
                        }
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    throw IOException("Token exchange failed: $errorMsg")
                }

                val json = JSONObject(body)
                val token = json.optString("access_token")
                if (token.isNullOrBlank()) {
                    throw IOException("No 'access_token' in response")
                }
                token
            }
        }
    }

    fun launchCustomTab(context: Context, url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
