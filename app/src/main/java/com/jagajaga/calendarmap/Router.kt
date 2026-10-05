package com.jagajaga.calendarmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class TravelMode(val label: String, val profile: String, val googleMode: String, val fallbackMps: Double) {
    WALK("Walk", "foot", "walking", 1.3),
    BIKE("Bike", "bike", "bicycling", 4.2),
    CAR("Car", "car", "driving", 8.3),
}

/**
 * Travel times and road geometry from the public OSRM servers run by FOSSGIS
 * (routing.openstreetmap.de). When they can't be reached, falls back to
 * straight-line distance at a typical speed so planning still works offline.
 */
class Router(private val userAgent: String) {

    data class Matrix(val durationsMs: Array<LongArray>, val estimated: Boolean)

    data class Path(val points: List<LatLon>, val estimated: Boolean)

    suspend fun matrix(points: List<LatLon>, mode: TravelMode): Matrix = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("${base(mode)}/table/v1/driving/${coords(points)}?annotations=duration")
            val rows = json.getJSONArray("durations")
            val m = Array(points.size) { i ->
                val row = rows.getJSONArray(i)
                LongArray(points.size) { j ->
                    if (row.isNull(j)) estimateMs(points[i], points[j], mode)
                    else (row.getDouble(j) * 1000).roundToLong()
                }
            }
            Matrix(m, estimated = false)
        }.getOrElse {
            Matrix(Array(points.size) { i -> LongArray(points.size) { j -> estimateMs(points[i], points[j], mode) } }, true)
        }
    }

    suspend fun path(points: List<LatLon>, mode: TravelMode): Path = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext Path(points, estimated = false)
        runCatching {
            val json = get("${base(mode)}/route/v1/driving/${coords(points)}?overview=full&geometries=geojson")
            val coordsJson = json.getJSONArray("routes").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates")
            val out = ArrayList<LatLon>(coordsJson.length())
            for (i in 0 until coordsJson.length()) {
                val c = coordsJson.getJSONArray(i)
                out += LatLon(c.getDouble(1), c.getDouble(0))
            }
            Path(out, estimated = false)
        }.getOrElse { Path(points, estimated = true) }
    }

    private fun base(mode: TravelMode) = "https://routing.openstreetmap.de/routed-${mode.profile}"

    private fun coords(points: List<LatLon>) =
        points.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.US, it.lon, it.lat) }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            check(code == 200) { "HTTP $code" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            check(json.optString("code") == "Ok") { json.optString("message") }
            return json
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Roads aren't straight: inflate crow-flies distance by this factor. */
        private const val DETOUR = 1.35

        fun estimateMs(a: LatLon, b: LatLon, mode: TravelMode): Long =
            (haversineM(a, b) * DETOUR / mode.fallbackMps * 1000).roundToLong()

        fun haversineM(a: LatLon, b: LatLon): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }
    }
}
