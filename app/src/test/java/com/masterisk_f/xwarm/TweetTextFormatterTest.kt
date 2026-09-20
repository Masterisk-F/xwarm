package com.masterisk_f.xwarm

import com.masterisk_f.xwarm.data.Spot
import com.masterisk_f.xwarm.tweet.TweetTextFormatter
import com.masterisk_f.xwarm.tweet.isPostalCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TweetTextFormatterTest {

    // --- isPostalCode (ported from swarm-linker-js/test/utils.spec.js) ---

    @Test
    fun isPostalCode_validHyphenated() {
        assertTrue(isPostalCode("123-4567"))
    }

    @Test
    fun isPostalCode_nonHyphenated() {
        assertFalse(isPostalCode("1234567"))
    }

    @Test
    fun isPostalCode_empty() {
        assertFalse(isPostalCode(""))
    }

    // --- Tweet formatting (ported from swarm-linker-js/test/index.webhook.spec.js) ---

    @Test
    fun format_whenCityAndStateAvailable() {
        val spot = Spot(
            id = "test",
            name = "<Venue Name>",
            city = "<City>",
            state = "<State>",
            formattedAddress = listOf("", "<Address>", "123-4567")
        )
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = spot,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name> in <City>, <State>\n<URL>", actual)
    }

    @Test
    fun format_whenFormattedAddressContainsPostalCode() {
        val spot = Spot(
            id = "test",
            name = "<Venue Name>",
            city = null,
            state = null,
            formattedAddress = listOf("", "<Address>", "123-4567")
        )
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = spot,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name> in <Address>\n<URL>", actual)
    }

    @Test
    fun format_whenFormattedAddressDoesNotContainPostalCode() {
        val spot = Spot(
            id = "test",
            name = "<Venue Name>",
            city = null,
            state = null,
            formattedAddress = listOf("", "<Address>")
        )
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = spot,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name> in <Address>\n<URL>", actual)
    }

    @Test
    fun format_whenOnlyStateAvailable() {
        val spot = Spot(
            id = "test",
            name = "<Venue Name>",
            city = null,
            state = "<State>",
            formattedAddress = emptyList()
        )
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = spot,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name> in <State>\n<URL>", actual)
    }

    @Test
    fun format_whenOnlyCityAvailable() {
        val spot = Spot(
            id = "test",
            name = "<Venue Name>",
            city = "<City>",
            state = null,
            formattedAddress = null
        )
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = spot,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name> in <City>\n<URL>", actual)
    }

    @Test
    fun format_whenNoLocationDataAvailable() {
        val spot = Spot(
            id = "test",
            name = "<Venue Name>",
            city = null,
            state = null,
            formattedAddress = null
        )
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = spot,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name>\n<URL>", actual)
    }

    @Test
    fun format_whenLocationNull() {
        val actual = TweetTextFormatter.format(
            venueName = "<Venue Name>",
            spot = null,
            shareUrl = "<URL>"
        )
        assertEquals("I'm at <Venue Name>\n<URL>", actual)
    }
}
