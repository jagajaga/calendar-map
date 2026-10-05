package com.jagajaga.calendarmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches travel times and road geometry from OSRM (see [Osrm] for URLs and
 * parsing). Falls back to straight-line estimates when the server can't be reached.
 */
class Router(private val userAgent: String) {

    class Matrix(val durations: TravelMatrix, val estimated: Boolean)

    data class Path(val points: List<LatLon>, val estimated: Boolean)

    suspend fun matrix(points: List<LatLon>, mode: TravelMode): Matrix = withContext(Dispatchers.IO) {
        val parsed = get(Osrm.tableUrl(points, mode))?.let { Osrm.parseTable(it, points, mode) }
        if (parsed != null) Matrix(parsed, estimated = false) else Matrix(Osrm.estimatedMatrix(points, mode), true)
    }

    suspend fun path(points: List<LatLon>, mode: TravelMode): Path = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext Path(points, estimated = false)
        val parsed = get(Osrm.routeUrl(points, mode))?.let(Osrm::parseRoute)
        parsed?.let { Path(it, estimated = false) } ?: Path(points, estimated = true)
    }

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
