package com.masterisk_f.xwarm.tweet

import com.masterisk_f.xwarm.data.Spot

private val POSTAL_CODE_REGEX = Regex("""^\d{3}-\d{4}$""")

fun isPostalCode(str: String): Boolean = POSTAL_CODE_REGEX.matches(str)

object TweetTextFormatter {

    fun format(
        venueName: String,
        spot: Spot?,
        shareUrl: String,
    ): String {
        val locText = spot?.let { getVenueLocationText(it) } ?: ""
        return "I'm at $venueName$locText\n$shareUrl"
    }

    private fun getVenueLocationText(spot: Spot): String {
        val city = spot.city?.takeIf { it.isNotBlank() }
        val state = spot.state?.takeIf { it.isNotBlank() }
        val formattedAddress = spot.formattedAddress?.filter { it.isNotBlank() }

        if (city != null && state != null) {
            return " in $city, $state"
        }

        if (!formattedAddress.isNullOrEmpty()) {
            val venueAddress = getVenueAddress(formattedAddress)
            if (!venueAddress.isNullOrBlank()) {
                return " in $venueAddress"
            }
        }

        if (state != null) {
            return " in $state"
        }

        if (city != null) {
            return " in $city"
        }

        return ""
    }

    private fun getVenueAddress(formattedAddress: List<String>): String? {
        if (formattedAddress.isEmpty()) return null
        val last = formattedAddress.last()
        if (isPostalCode(last) && formattedAddress.size >= 2) {
            return formattedAddress[formattedAddress.size - 2]
        }
        return last
    }
}
