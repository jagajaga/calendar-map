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

/**
 * Turns free-text event locations into coordinates with the platform geocoder,
 * caching hits forever and misses for a day so the map loads fast on reopen.
 */
class GeoResolver(context: Context) {
    private val prefs = context.getSharedPreferences("geocache", Context.MODE_PRIVATE)
    private val geocoder = Geocoder(context)

    suspend fun resolve(location: String): LatLon? {
        val text = location.trim()
        if (text.isEmpty() || GeoText.looksVirtual(text)) return null
        GeoText.parseCoordinates(text)?.let { return it }

        val key = text.lowercase()
        prefs.getString(key, null)?.let { cached ->
            if (cached.startsWith(MISS)) {
                val ts = cached.removePrefix(MISS).toLongOrNull() ?: 0L
                if (System.currentTimeMillis() - ts < MISS_TTL_MS) return null
            } else {
                GeoText.parseCoordinates(cached)?.let { return it }
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

    private companion object {
        const val MISS = "miss:"
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000
    }
}
