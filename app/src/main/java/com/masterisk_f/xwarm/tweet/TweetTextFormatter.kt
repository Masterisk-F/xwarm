package com.masterisk_f.xwarm.tweet

data class TweetLocation(
    val city: String? = null,
    val state: String? = null,
    val formattedAddress: List<String>? = null,
)

private val POSTAL_CODE_REGEX = Regex("""^\d{3}-\d{4}$""")

fun isPostalCode(str: String): Boolean = POSTAL_CODE_REGEX.matches(str)

object TweetTextFormatter {

    fun format(
        venueName: String,
        location: TweetLocation?,
        shareUrl: String,
    ): String {
        val locText = location?.let { getVenueLocationText(it) } ?: ""
        return "I'm at $venueName$locText\n$shareUrl"
    }

    private fun getVenueLocationText(location: TweetLocation): String {
        val city = location.city?.takeIf { it.isNotBlank() }
        val state = location.state?.takeIf { it.isNotBlank() }
        val formattedAddress = location.formattedAddress?.filter { it.isNotBlank() }

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
