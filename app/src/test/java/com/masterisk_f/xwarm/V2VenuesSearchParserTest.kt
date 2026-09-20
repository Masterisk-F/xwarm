package com.masterisk_f.xwarm

import com.masterisk_f.xwarm.data.FoursquareJsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class V2VenuesSearchParserTest {

    @Test
    fun parseVenuesSearch_fromV2Response() {
        val json = """
        {
          "meta": { "code": 200, "requestId": "6aaf7271" },
          "response": {
            "venues": [
              {
                "id": "4b8da54ef964a5202e0633e3",
                "name": "Oedo Line Tsukishima Station",
                "location": {
                  "address": "月島1-5-4",
                  "city": "中央区",
                  "state": "東京都",
                  "distance": 42,
                  "formattedAddress": ["月島1-5-4", "中央区, 東京都", "104-0052"]
                },
                "categories": [
                  { "id": "19046", "name": "Metro Station" }
                ]
              },
              {
                "id": "4b5d6e2cf964a520865d29e3",
                "name": "Seven-Eleven",
                "location": {
                  "address": "月島2-1-1",
                  "distance": 150
                },
                "categories": [
                  { "id": "17069", "name": "Convenience Store" }
                ]
              }
            ]
          }
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
        assertEquals(listOf("月島1-5-4", "中央区, 東京都", "104-0052"), s1.formattedAddress)

        val s2 = spots[1]
        assertEquals("4b5d6e2cf964a520865d29e3", s2.id)
        assertEquals("Seven-Eleven", s2.name)
        assertEquals(150, s2.distanceMeters)
        assertEquals("Convenience Store", s2.categoryName)
        assertEquals("月島2-1-1", s2.address)
    }
}
