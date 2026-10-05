package com.jagajaga.calendarmap

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** The map's visible area; west > east when it spans the antimeridian. */
data class Bounds(val north: Double, val south: Double, val east: Double, val west: Double) {
    operator fun contains(p: LatLon): Boolean {
        if (p.lat > north || p.lat < south) return false
        return if (west <= east) p.lon in west..east else p.lon >= west || p.lon <= east
    }

    /** Grow by [fraction] of the size on each side, so pins near the edge are ready. */
    fun padded(fraction: Double): Bounds {
        val dLat = (north - south) * fraction
        val width = if (west <= east) east - west else east + 360 - west
        val dLon = width * fraction
        val grown = Bounds(
            (north + dLat).coerceAtMost(90.0), (south - dLat).coerceAtLeast(-90.0),
            wrapLon(east + dLon), wrapLon(west - dLon),
        )
        return if (width + 2 * dLon >= 360) grown.copy(east = 180.0, west = -180.0) else grown
    }

    companion object {
        /** Bounds of a map region given by its centre and span (as MapKit reports it). */
        fun fromCenter(lat: Double, lon: Double, latSpan: Double, lonSpan: Double): Bounds =
            if (lonSpan >= 360) {
                Bounds((lat + latSpan / 2).coerceAtMost(90.0), (lat - latSpan / 2).coerceAtLeast(-90.0), 180.0, -180.0)
            } else {
                Bounds(
                    (lat + latSpan / 2).coerceAtMost(90.0), (lat - latSpan / 2).coerceAtLeast(-90.0),
                    wrapLon(lon + lonSpan / 2), wrapLon(lon - lonSpan / 2),
                )
            }

        internal fun wrapLon(x: Double) = ((x + 540) % 360) - 180
    }
}

/** Recognising event location text that isn't a physical place, or is already coordinates. */
object GeoText {
    private val COORDS = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
    private val VIRTUAL = Regex(
        """^(https?://|www\.)|zoom\.us|meet\.google|teams\.microsoft|^(online|virtual|remote|zoom|google meet|microsoft teams|teams|skype|phone|call)$""",
        RegexOption.IGNORE_CASE,
    )

    fun parseCoordinates(text: String): LatLon? {
        val m = COORDS.matchEntire(text) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLon(lat, lon) else null
    }

    fun looksVirtual(text: String): Boolean = VIRTUAL.containsMatchIn(text.trim())
}

object Distance {
    fun haversineM(a: LatLon, b: LatLon): Double {
        val r = 6_371_000.0
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun km(a: LatLon, b: LatLon): Double = haversineM(a, b) / 1000.0

    private fun rad(deg: Double) = deg * PI / 180.0
}
