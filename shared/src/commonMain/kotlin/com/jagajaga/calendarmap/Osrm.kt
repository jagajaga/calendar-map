package com.jagajaga.calendarmap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/**
 * Request URLs and response parsing for the public OSRM servers run by FOSSGIS
 * (routing.openstreetmap.de). Each platform does the HTTP itself. When the
 * server can't help, [estimatedMatrix] gives straight-line guesses instead.
 */
object Osrm {
    /** Roads aren't straight: inflate crow-flies distance by this factor. */
    private const val DETOUR = 1.35
    private val json = Json { ignoreUnknownKeys = true }

    fun tableUrl(points: List<LatLon>, mode: TravelMode): String =
        "${base(mode)}/table/v1/driving/${coords(points)}?annotations=duration"

    fun routeUrl(points: List<LatLon>, mode: TravelMode): String =
        "${base(mode)}/route/v1/driving/${coords(points)}?overview=full&geometries=geojson"

    /** Travel-time matrix from a table response; unreachable pairs get estimates. Null if unusable. */
    fun parseTable(body: String, points: List<LatLon>, mode: TravelMode): TravelMatrix? = try {
        val root = okRoot(body)
        val rows = root?.get("durations")?.jsonArray
        if (rows == null || rows.size != points.size) {
            null
        } else {
            val m = TravelMatrix(points.size)
            for (i in points.indices) {
                val row = rows[i].jsonArray
                for (j in points.indices) {
                    val seconds = row.getOrNull(j)?.jsonPrimitive?.doubleOrNull
                    m.put(i, j, seconds?.let { (it * 1000).roundToLong() } ?: estimateMs(points[i], points[j], mode))
                }
            }
            m
        }
    } catch (e: Exception) {
        null
    }

    /** Road geometry from a route response. Null if unusable. */
    fun parseRoute(body: String): List<LatLon>? = try {
        val coords = okRoot(body)?.get("routes")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("geometry")?.jsonObject?.get("coordinates")?.jsonArray
        coords?.map {
            val c = it.jsonArray
            LatLon(c[1].jsonPrimitive.content.toDouble(), c[0].jsonPrimitive.content.toDouble())
        }?.takeIf { it.size >= 2 }
    } catch (e: Exception) {
        null
    }

    fun estimateMs(a: LatLon, b: LatLon, mode: TravelMode): Long =
        (Distance.haversineM(a, b) * DETOUR / mode.fallbackMps * 1000).roundToLong()

    fun estimatedMatrix(points: List<LatLon>, mode: TravelMode): TravelMatrix {
        val m = TravelMatrix(points.size)
        for (i in points.indices) for (j in points.indices) m.put(i, j, estimateMs(points[i], points[j], mode))
        return m
    }

    private fun okRoot(body: String): JsonObject? {
        val root = json.parseToJsonElement(body).jsonObject
        return if (root["code"]?.jsonPrimitive?.content == "Ok") root else null
    }

    private fun base(mode: TravelMode) = "https://routing.openstreetmap.de/routed-${mode.profile}"

    internal fun coords(points: List<LatLon>) = points.joinToString(";") { "${fixed6(it.lon)},${fixed6(it.lat)}" }

    /** `%.6f` without String.format, which common Kotlin lacks. */
    internal fun fixed6(x: Double): String {
        val scaled = (x * 1_000_000).roundToLong()
        val neg = scaled < 0
        val abs = if (neg) -scaled else scaled
        val text = "${abs / 1_000_000}.${(abs % 1_000_000).toString().padStart(6, '0')}"
        return if (neg) "-$text" else text
    }
}
