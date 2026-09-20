package com.masterisk_f.xwarm

import com.masterisk_f.xwarm.auth.FoursquareOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoursquareOAuthTest {

    @Test
    fun buildAuthorizeUrl_formatsCorrectly() {
        val url = FoursquareOAuth.buildAuthorizeUrl(
            clientId = "CLIENT123",
            redirectUri = "xwarm://oauth/callback"
        )
        assertEquals(
            "https://foursquare.com/oauth2/authenticate?client_id=CLIENT123&response_type=code&redirect_uri=xwarm%3A%2F%2Foauth%2Fcallback",
            url
        )
    }

    @Test
    fun extractCodeFromUrl_withLocalhostAndTrailingHash() {
        val input = "http://localhost/callback?code=CODE_ABC123#_=_"
        assertEquals("CODE_ABC123", FoursquareOAuth.extractCodeFromUrl(input))
    }

    @Test
    fun extractCodeFromUrl_withCustomScheme() {
        val input = "xwarm://oauth/callback?code=CODE_XYZ789"
        assertEquals("CODE_XYZ789", FoursquareOAuth.extractCodeFromUrl(input))
    }

    @Test
    fun extractCodeFromUrl_withMultipleParams() {
        val input = "http://localhost:8080/callback?state=test&code=CODE_MULTI&foo=bar#_=_"
        assertEquals("CODE_MULTI", FoursquareOAuth.extractCodeFromUrl(input))
    }

    @Test
    fun extractCodeFromUrl_rawCode() {
        val input = "PURE_CODE_WITHOUT_URL"
        assertEquals("PURE_CODE_WITHOUT_URL", FoursquareOAuth.extractCodeFromUrl(input))
    }

    @Test
    fun extractCodeFromUrl_whenDeniedOrError() {
        val input = "http://localhost/callback?error=access_denied"
        assertNull(FoursquareOAuth.extractCodeFromUrl(input))
    }

    @Test
    fun extractCodeFromUrl_empty() {
        assertNull(FoursquareOAuth.extractCodeFromUrl(""))
        assertNull(FoursquareOAuth.extractCodeFromUrl("   "))
    }
}
