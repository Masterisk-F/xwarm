package com.masterisk_f.xwarm.data

import com.masterisk_f.xwarm.tweet.TweetLocation
import org.json.JSONObject

data class Spot(
    val id: String,
    val name: String,
    val distanceMeters: Int? = null,
    val categoryName: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val formattedAddress: List<String>? = null,
) {
    fun toTweetLocation(): TweetLocation = TweetLocation(
        city = city,
        state = state,
        formattedAddress = formattedAddress
    )

    val subtitle: String
        get() {
            val parts = mutableListOf<String>()
            categoryName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            address?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            return parts.joinToString(" · ")
        }
}

data class CheckInResult(
    val checkinId: String,
    val checkinShortUrl: String,
    val venueName: String,
    val createdAt: Long? = null,
)

object FoursquareJsonParser {

    /**
     * Parses the candidates response from `https://places-api.foursquare.com/geotagging/candidates`.
     *
     * Response shape:
     * {
     *   "candidates": [
     *     {
     *       "fsq_place_id": "...",
     *       "name": "...",
     *       "distance": 123,
     *       "categories": [ { "name": "..." } ],
     *       "location": {
     *         "address": "...",
     *         "locality": "...",
     *         "region": "...",
     *         "formatted_address": "..."
     *       }
     *     }
     *   ]
     * }
     */
    fun parseCandidates(jsonString: String): List<Spot> {
        val root = JSONObject(jsonString)
        val array = root.optJSONArray("candidates")
            ?: root.optJSONArray("results")
            ?: root.optJSONObject("response")?.optJSONArray("venues")
            ?: return emptyList()
        val spots = mutableListOf<Spot>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("fsq_place_id").ifEmpty {
                item.optString("id")
            }
            val name = item.optString("name").ifEmpty {
                item.optString("venue_name")
            }
            if (id.isBlank() || name.isBlank()) continue

            val locObj = item.optJSONObject("location")

            val distance = if (item.has("distance") && !item.isNull("distance")) {
                item.optInt("distance")
            } else if (locObj != null && locObj.has("distance") && !locObj.isNull("distance")) {
                locObj.optInt("distance")
            } else null

            // Category (first one)
            var categoryName: String? = null
            val categories = item.optJSONArray("categories")
            if (categories != null && categories.length() > 0) {
                val catObj = categories.optJSONObject(0)
                categoryName = catObj?.optString("name")?.takeIf { it.isNotBlank() }
            }

            // Location
            var address: String? = null
            var city: String? = null
            var state: String? = null
            var formattedList: List<String>? = null

            if (locObj != null) {
                address = locObj.optString("address").takeIf { it.isNotBlank() }
                city = locObj.optString("locality").ifEmpty {
                    locObj.optString("city")
                }.takeIf { it.isNotBlank() }
                state = locObj.optString("region").ifEmpty {
                    locObj.optString("state")
                }.takeIf { it.isNotBlank() }

                val faObj = locObj.opt("formatted_address") ?: locObj.opt("formattedAddress")
                if (faObj is String && faObj.isNotBlank()) {
                    formattedList = listOf(faObj)
                } else {
                    val faArray = locObj.optJSONArray("formattedAddress")
                    if (faArray != null) {
                        val list = mutableListOf<String>()
                        for (j in 0 until faArray.length()) {
                            val str = faArray.optString(j)
                            if (!str.isNullOrBlank()) list.add(str)
                        }
                        if (list.isNotEmpty()) formattedList = list
                    }
                }
            }

            spots.add(
                Spot(
                    id = id,
                    name = name,
                    distanceMeters = distance,
                    categoryName = categoryName,
                    address = address,
                    city = city,
                    state = state,
                    formattedAddress = formattedList
                )
            )
        }

        return spots
    }

    /**
     * Parses the checkin response from `POST /v2/checkins/add`.
     *
     * Response shape:
     * {
     *   "meta": { "code": 200 },
     *   "response": {
     *     "checkin": {
     *       "id": "...",
     *       "checkinShortUrl": "https://www.swarmapp.com/...",
     *       "createdAt": 1234567890,
     *       "venue": { "name": "..." }
     *     }
     *   }
     * }
     */
    fun parseCheckIn(jsonString: String, fallbackVenueName: String = ""): CheckInResult {
        val root = JSONObject(jsonString)
        val response = root.optJSONObject("response")
            ?: throw IllegalArgumentException("Missing 'response' object in Foursquare response")
        val checkin = response.optJSONObject("checkin")
            ?: throw IllegalArgumentException("Missing 'checkin' object in Foursquare response")

        val id = checkin.optString("id").ifEmpty {
            throw IllegalArgumentException("Missing checkin 'id'")
        }

        val shortUrl = checkin.optString("checkinShortUrl").ifEmpty {
            "https://www.swarmapp.com/c/$id"
        }

        val venueObj = checkin.optJSONObject("venue")
        val venueName = venueObj?.optString("name")?.ifEmpty { fallbackVenueName }
            ?: fallbackVenueName

        val createdAt = if (checkin.has("createdAt")) checkin.optLong("createdAt") else null

        return CheckInResult(
            checkinId = id,
            checkinShortUrl = shortUrl,
            venueName = venueName.ifEmpty { "Venue" },
            createdAt = createdAt
        )
    }
}
