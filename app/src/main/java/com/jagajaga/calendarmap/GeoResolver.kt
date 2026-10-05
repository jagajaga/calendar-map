package com.jagajaga.calendarmap

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LatLon(val lat: Double, val lon: Double)

/**
 * Turns free-text event locations into coordinates with the platform geocoder,
 * caching hits forever and misses for a day so the map loads fast on reopen.
 */
class GeoResolver(context: Context) {
    private val prefs = context.getSharedPreferences("geocache", Context.MODE_PRIVATE)
    private val geocoder = Geocoder(context)

    suspend fun resolve(location: String): LatLon? {
        val text = location.trim()
        if (text.isEmpty() || looksVirtual(text)) return null
        parseCoordinates(text)?.let { return it }

        val key = text.lowercase()
        prefs.getString(key, null)?.let { cached ->
            if (cached.startsWith(MISS)) {
                val ts = cached.removePrefix(MISS).toLongOrNull() ?: 0L
                if (System.currentTimeMillis() - ts < MISS_TTL_MS) return null
            } else {
                parseCoordinates(cached)?.let { return it }
            }
        }
        if (!Geocoder.isPresent()) return null

        val result = try {
            lookup(text)
        } catch (e: IOException) {
            return null // network trouble: don't cache, try again next refresh
        } catch (e: IllegalArgumentException) {
            null
        }
        prefs.edit().putString(
            key,
            result?.let { "${it.lat},${it.lon}" } ?: "$MISS${System.currentTimeMillis()}",
        ).apply()
        return result
    }

    fun clearCache() = prefs.edit().clear().apply()

    private suspend fun lookup(text: String): LatLon? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(text, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        val a = addresses.firstOrNull()
                        if (cont.isActive) cont.resume(a?.let { LatLon(it.latitude, it.longitude) })
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resumeWithException(IOException(errorMessage))
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(text, 1)?.firstOrNull()
                    ?.let { LatLon(it.latitude, it.longitude) }
            }
        }

    companion object {
        private const val MISS = "miss:"
        private const val MISS_TTL_MS = 24L * 60 * 60 * 1000
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

        fun looksVirtual(text: String) = VIRTUAL.containsMatchIn(text.trim())
    }
}
