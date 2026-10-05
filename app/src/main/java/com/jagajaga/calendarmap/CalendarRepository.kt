package com.jagajaga.calendarmap

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

data class CalEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val description: String,
    val calendarId: Long,
    val calendarName: String,
    val color: Int,
) {
    /** Unique per occurrence: recurring events share [eventId]. */
    val key: String get() = "$eventId@$begin"
}

class CalendarRepository(private val context: Context) {

    fun calendars(): List<CalendarInfo> {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR,
        )
        val out = mutableListOf<CalendarInfo>()
        context.contentResolver.query(
            Calendars.CONTENT_URI, projection, null, null,
            "${Calendars.ACCOUNT_NAME} ASC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "(unnamed)",
                    account = c.getString(2) ?: "",
                    color = c.getInt(3),
                )
            }
        }
        return out
    }

    /** All event occurrences overlapping [start, end), expanded from recurrences. */
    fun events(start: Long, end: Long, calendarIds: Set<Long>?): List<CalEvent> {
        if (calendarIds != null && calendarIds.isEmpty()) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.DESCRIPTION,
            Instances.CALENDAR_ID,
            Instances.CALENDAR_DISPLAY_NAME,
            Instances.DISPLAY_COLOR,
            Instances.SELF_ATTENDEE_STATUS,
            Instances.STATUS,
        )
        val selection = calendarIds?.let { ids ->
            "${Instances.CALENDAR_ID} IN (${ids.joinToString(",")})"
        }
        val out = mutableListOf<CalEvent>()
        context.contentResolver.query(
            uri, projection, selection, null, "${Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(10) == Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (c.getInt(11) == Instances.STATUS_CANCELED) continue
                out += CalEvent(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(no title)",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    location = c.getString(5)?.trim() ?: "",
                    description = c.getString(6) ?: "",
                    calendarId = c.getLong(7),
                    calendarName = c.getString(8) ?: "",
                    color = c.getInt(9),
                )
            }
        }
        return out
    }
}
