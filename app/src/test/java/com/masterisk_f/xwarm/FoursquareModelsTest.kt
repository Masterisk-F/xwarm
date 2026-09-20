package com.masterisk_f.xwarm

import com.masterisk_f.xwarm.data.FoursquareJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoursquareModelsTest {

    @Test
    fun parseCandidates_withRealSample() {
        val json = """
        {
          "candidates": [
            {
              "fsq_place_id": "4b8da54ef964a5202e0633e3",
              "name": "Oedo Line Tsukishima Station",
              "distance": 42,
              "categories": [
                { "id": 19046, "name": "Metro Station" }
              ],
              "location": {
                "address": "月島1-5-4",
                "locality": "中央区",
                "region": "東京都",
                "formatted_address": "月島1-5-4, 中央区, 東京都 104-0052"
              }
            },
            {
              "fsq_place_id": "4b5d6e2cf964a520865d29e3",
              "name": "Seven-Eleven",
              "distance": 150,
              "categories": [
                { "id": 17069, "name": "Convenience Store" }
              ],
              "location": {
                "address": "月島2-1-1"
              }
            }
          ]
        }
        """.trimIndent()

        val spots = FoursquareJsonParser.parseCandidates(json)
        assertEquals(2, spots.size)

        val s1 = spots[0]
        assertEquals("4b8da54ef964a5202e0633e3", s1.id)
        assertEquals("Oedo Line Tsukishima Station", s1.name)
        assertEquals(42, s1.distanceMeters)
        assertEquals("Metro Station", s1.categoryName)
        assertEquals("月島1-5-4", s1.address)
        assertEquals("中央区", s1.city)
        assertEquals("東京都", s1.state)
        assertEquals("Metro Station · 月島1-5-4", s1.subtitle)

        val s2 = spots[1]
        assertEquals("4b5d6e2cf964a520865d29e3", s2.id)
        assertEquals("Seven-Eleven", s2.name)
        assertEquals(150, s2.distanceMeters)
        assertEquals("Convenience Store", s2.categoryName)
        assertEquals("月島2-1-1", s2.address)
    }

    @Test
    fun parseCandidates_emptyOrMalformed() {
        assertEquals(0, FoursquareJsonParser.parseCandidates("{}").size)
        assertEquals(0, FoursquareJsonParser.parseCandidates("""{"candidates":[]}""").size)
    }

    @Test
    fun parseCheckIn_withRealSample() {
        val json = """
        {
          "meta": { "code": 200, "requestId": "6421f092f5e2da1fdeb4f888" },
          "response": {
            "checkin": {
              "id": "6421f0405c8d094658112a56",
              "createdAt": 1679945792,
              "type": "checkin",
              "venue": {
                "id": "4b88822df964a52018fd31e3",
                "name": "Lucky Dragon - Chinese Restaurant"
              },
              "checkinShortUrl": "https://www.swarmapp.com/user/123456/checkin/6421f0405c8d094658112a56?s=qtRZ"
            }
          }
        }
        """.trimIndent()

        val res = FoursquareJsonParser.parseCheckIn(json)
        assertEquals("6421f0405c8d094658112a56", res.checkinId)
        assertEquals("https://www.swarmapp.com/user/123456/checkin/6421f0405c8d094658112a56?s=qtRZ", res.checkinShortUrl)
        assertEquals("Lucky Dragon - Chinese Restaurant", res.venueName)
    }

    @Test
    fun parseCheckIn_missingShortUrlFallsBackToConstructed() {
        val json = """
        {
          "meta": { "code": 200 },
          "response": {
            "checkin": {
              "id": "abc123",
              "venue": { "name": "Test Venue" }
            }
          }
        }
        """.trimIndent()

        val res = FoursquareJsonParser.parseCheckIn(json)
        assertEquals("abc123", res.checkinId)
        assertEquals("https://www.swarmapp.com/c/abc123", res.checkinShortUrl)
        assertEquals("Test Venue", res.venueName)
    }
}
