package com.jagajaga.calendarmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationProvider {

    fun hasPermission(context: Context) =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** A fresh fix if one arrives within a few seconds, otherwise the best last-known one. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = lm.getProviders(true)
        val lastKnown = enabled
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        val provider = listOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { it != null && it in enabled } ?: return lastKnown

        val fresh = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        lm, provider, signal, ContextCompat.getMainExecutor(context),
                    ) { loc -> if (cont.isActive) cont.resume(loc) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: lastKnown
    }
}
