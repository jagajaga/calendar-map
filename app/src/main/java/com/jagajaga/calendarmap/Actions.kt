package com.jagajaga.calendarmap

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast

object Actions {
    fun openInGoogleMaps(context: Context, event: CalEvent, point: LatLon?) {
        val query = event.location.ifBlank { point?.let { "${it.lat},${it.lon}" } ?: return }
        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps"))
        } catch (e: ActivityNotFoundException) {
            launch(context, intent)
        }
    }

    fun openInCalendar(context: Context, event: CalEvent) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        launch(
            context,
            Intent(Intent.ACTION_VIEW, uri)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end),
        )
    }

    fun openAppSettings(context: Context) = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
        }
    }
}
