package com.masterisk_f.xwarm.data

import com.masterisk_f.xwarm.auth.TokenStore

/**
 * Repository providing nearby spots and check-in operations.
 *
 * D-17 extension point: if the user later desires history-based venue caching
 * (to mitigate Foursquare Places API venue list mismatch vs official Swarm app),
 * merge visited venues from `historysearch` here before returning to callers.
 */
open class SpotRepository(
    private val api: FoursquareApi,
    private val tokenStore: TokenStore,
) {
    open suspend fun getNearbySpots(lat: Double, lng: Double): Result<List<Spot>> {
        val token = tokenStore.getOAuthToken()
            ?: return Result.failure(FoursquareApiException.Unauthorized("Not logged in"))
        return api.searchNearby(lat, lng, token)
    }

    open suspend fun checkIn(
        spot: Spot,
        lat: Double? = null,
        lng: Double? = null,
    ): Result<CheckInResult> {
        val token = tokenStore.getOAuthToken()
            ?: return Result.failure(FoursquareApiException.Unauthorized("Not logged in"))
        return api.createCheckIn(
            venueId = spot.id,
            oauthToken = token,
            lat = lat,
            lng = lng,
            broadcast = "public",
            fallbackVenueName = spot.name
        )
    }

    open suspend fun verifyAndSaveToken(token: String): Result<String> {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Token cannot be blank"))
        }
        val verifyRes = api.verifyToken(cleanToken)
        return verifyRes.onSuccess {
            tokenStore.saveOAuthToken(cleanToken)
        }
    }
}
