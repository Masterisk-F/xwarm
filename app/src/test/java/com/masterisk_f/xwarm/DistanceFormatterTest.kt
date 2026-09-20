package com.masterisk_f.xwarm

import com.masterisk_f.xwarm.ui.DistanceFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTest {

    @Test
    fun format_metersUnder1000() {
        assertEquals("0m", DistanceFormatter.format(0))
        assertEquals("50m", DistanceFormatter.format(50))
        assertEquals("999m", DistanceFormatter.format(999))
    }

    @Test
    fun format_kilometersAtOrAbove1000() {
        assertEquals("1.0km", DistanceFormatter.format(1000))
        assertEquals("1.2km", DistanceFormatter.format(1234))
        assertEquals("1.5km", DistanceFormatter.format(1500))
        assertEquals("10.0km", DistanceFormatter.format(10000))
        assertEquals("12.5km", DistanceFormatter.format(12500))
    }

    @Test
    fun format_nullReturnsEmpty() {
        assertEquals("", DistanceFormatter.format(null))
    }

    @Test
    fun format_negativeReturnsMeters() {
        assertEquals("0m", DistanceFormatter.format(-10))
    }
}
