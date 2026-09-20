package com.masterisk_f.xwarm.data

import com.masterisk_f.xwarm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class FoursquareApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(message: String = "Foursquare authorization failed. Please log in again.") : FoursquareApiException(message)
    class RateLimited(val resetEpochSeconds: Long?, message: String = "Rate limit reached. Please wait.") : FoursquareApiException(message)
    class QuotaExhausted(message: String = "Foursquare API quota exhausted (HTTP 429).") : FoursquareApiException(message)
    class ServerError(val statusCode: Int, message: String) : FoursquareApiException("Foursquare server error ($statusCode): $message")
    class NetworkError(message: String, cause: Throwable) : FoursquareApiException(message, cause)
}

class FoursquareApi(
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private const val PLACES_API_HOST = "places-api.foursquare.com"
        private const val V2_API_HOST = "api.foursquare.com"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun formatLatLng(lat: Double, lng: Double): String =
            String.format(Locale.US, "%.6f,%.6f", lat, lng)
    }

    /**
     * Search nearby venues candidates using Foursquare API.
     *
     * Tries v2 /venues/search on api.foursquare.com first (same reachable host as checkins and user profile),
     * and falls back to places-api.foursquare.com/geotagging/candidates if needed.
     */
    suspend fun searchNearby(
        lat: Double,
        lng: Double,
        oauthToken: String,
        limit: Int = 20,
    ): Result<List<Spot>> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Primary: v2 venues/search on api.foursquare.com
            val v2Url = "https://$V2_API_HOST/v2/venues/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("v", BuildConfig.FSQ_API_VERSION)
                ?.addQueryParameter("oauth_token", oauthToken)
                ?.addQueryParameter("ll", formatLatLng(lat, lng))
                ?.addQueryParameter("limit", limit.coerceIn(1, 50).toString())
                ?.build()
                ?: throw IllegalArgumentException("Invalid URL")

            val v2Request = Request.Builder()
                .url(v2Url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val v2Result = runCatching {
                client.newCall(v2Request).execute().use { response ->
                    handleCommonErrors(response)
                    val body = response.body?.string() ?: throw IOException("Empty response body")
                    val spots = FoursquareJsonParser.parseCandidates(body)
                    if (spots.isNotEmpty()) spots else null
                }
            }

            if (v2Result.isSuccess && v2Result.getOrNull() != null) {
                return@runCatching v2Result.getOrNull()!!
            }

            // 2. Fallback: Places API geotagging/candidates
            val placesUrl = "https://$PLACES_API_HOST/geotagging/candidates".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("ll", formatLatLng(lat, lng))
                ?.addQueryParameter("limit", limit.coerceIn(1, 50).toString())
                ?.build()
                ?: throw IllegalArgumentException("Invalid URL")

            val placesRequest = Request.Builder()
                .url(placesUrl)
                .addHeader("Authorization", "Bearer $oauthToken")
                .addHeader("X-Places-Api-Version", BuildConfig.FSQ_PLACES_API_VERSION)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(placesRequest).execute().use { response ->
                handleCommonErrors(response)
                val body = response.body?.string() ?: throw IOException("Empty response body")
                FoursquareJsonParser.parseCandidates(body)
            }
        }.recoverCatching { e ->
            if (e is FoursquareApiException) throw e
            throw FoursquareApiException.NetworkError("Failed to fetch nearby spots: ${e.message}", e)
        }
    }

    /**
     * Create a check-in on Foursquare v2 API.
     *
     * Auth: `oauth_token` query parameter ONLY.
     * CRITICAL: Do NOT send Authorization header with v2 endpoints (D-09a).
     * Request body: EMPTY POST body, all parameters are in the query string.
     */
    suspend fun createCheckIn(
        venueId: String,
        oauthToken: String,
        lat: Double? = null,
        lng: Double? = null,
        broadcast: String = "public",
        fallbackVenueName: String = "",
    ): Result<CheckInResult> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = "https://$V2_API_HOST/v2/checkins/add".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("v", BuildConfig.FSQ_API_VERSION)
                ?.addQueryParameter("oauth_token", oauthToken)
                ?.addQueryParameter("venueId", venueId)
                ?.addQueryParameter("broadcast", broadcast)

            if (lat != null && lng != null) {
                urlBuilder?.addQueryParameter("ll", formatLatLng(lat, lng))
            }

            val url = urlBuilder?.build() ?: throw IllegalArgumentException("Invalid URL")

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .post("".toRequestBody(null))
                .build()

            client.newCall(request).execute().use { response ->
                handleCommonErrors(response)
                val body = response.body?.string() ?: throw IOException("Empty response body")
                FoursquareJsonParser.parseCheckIn(body, fallbackVenueName)
            }
        }.recoverCatching { e ->
            if (e is FoursquareApiException) throw e
            throw FoursquareApiException.NetworkError("Failed to check in: ${e.message}", e)
        }
    }

    /**
     * Verifies the OAuth token by calling GET /v2/users/self.
     */
    suspend fun verifyToken(oauthToken: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://$V2_API_HOST/v2/users/self".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("v", BuildConfig.FSQ_API_VERSION)
                ?.addQueryParameter("oauth_token", oauthToken)
                ?.build()
                ?: throw IllegalArgumentException("Invalid URL")

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                handleCommonErrors(response)
                val body = response.body?.string() ?: throw IOException("Empty response body")
                val root = org.json.JSONObject(body)
                val user = root.optJSONObject("response")?.optJSONObject("user")
                user?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: throw FoursquareApiException.ServerError(response.code, "User ID not found in response")
            }
        }
    }

    private fun handleCommonErrors(response: okhttp3.Response) {
        if (response.isSuccessful) return

        when (response.code) {
            401 -> throw FoursquareApiException.Unauthorized()
            403 -> {
                val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                throw FoursquareApiException.RateLimited(reset)
            }
            429 -> throw FoursquareApiException.QuotaExhausted()
            in 500..599 -> throw FoursquareApiException.ServerError(response.code, response.message)
            else -> throw FoursquareApiException.ServerError(response.code, response.message)
        }
    }
}
