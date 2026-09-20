package com.masterisk_f.xwarm.ui

import java.util.Locale

object DistanceFormatter {

    fun format(distanceMeters: Int?): String {
        if (distanceMeters == null) return ""
        if (distanceMeters < 0) return "0m"
        return if (distanceMeters < 1000) {
            "${distanceMeters}m"
        } else {
            String.format(Locale.US, "%.1fkm", distanceMeters / 1000.0)
        }
    }
}
