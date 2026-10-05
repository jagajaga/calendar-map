package com.jagajaga.calendarmap

/**
 * Made-up events in San Francisco, used when the app is started with the
 * `demo` intent extra (screenshot tests and store screenshots). Times are
 * relative to now so a route can always be planned. Mirrors ios/…/DemoData.swift.
 */
object Demo {
    @Volatile
    var enabled = false

    const val EXTRA = "demo"

    val me = LatLon(37.7880, -122.4005)

    val calendars = listOf(
        CalendarInfo(1, "Work", "demo@example.com", 0xFF1E88E5.toInt()),
        CalendarInfo(2, "Tech Week", "demo@example.com", 0xFFF57C00.toInt()),
        CalendarInfo(3, "Friends", "demo@example.com", 0xFF43A047.toInt()),
    )

    private class Spec(
        val title: String,
        val calendar: Int,
        val startHours: Double,
        val lengthHours: Double,
        val location: String,
        val point: LatLon?,
    )

    private val specs = listOf(
        Spec("Founders Coffee", 0, 1.0, 1.5, "Ferry Building, 1 Ferry Building, San Francisco", LatLon(37.7955, -122.3937)),
        Spec("AI Demo Night", 1, 2.0, 3.0, "Moscone Center West, 800 Howard St, San Francisco", LatLon(37.7837, -122.4011)),
        Spec("Design Systems Meetup", 1, 2.5, 2.0, "Salesforce Park, 425 Mission St, San Francisco", LatLon(37.7897, -122.3966)),
        Spec("Open Source Hack Night", 1, 6.5, 3.0, "Dolores Park, San Francisco", LatLon(37.7596, -122.4269)),
        Spec("Team standup", 0, 0.5, 0.25, "Google Meet", null),
        Spec("Product Lunch", 0, 24.0, 1.5, "Washington Square, San Francisco", LatLon(37.8008, -122.4100)),
        Spec("Rooftop Mixer", 2, 29.0, 3.0, "Union Square, San Francisco", LatLon(37.7880, -122.4075)),
        Spec("Board Games & Pizza", 2, 52.0, 3.0, "Hayes Valley, San Francisco", LatLon(37.7765, -122.4241)),
    )

    /** Coordinates the geocoder would have found, by location text. */
    val places: Map<String, LatLon> = specs.mapNotNull { s -> s.point?.let { s.location to it } }.toMap()

    fun events(nowMs: Long = System.currentTimeMillis()): List<CalEvent> {
        val hour = 3_600_000L
        val base = (nowMs / hour + 1) * hour // next full hour
        return specs.mapIndexed { i, s ->
            val cal = calendars[s.calendar]
            val begin = base + (s.startHours * hour).toLong()
            CalEvent(
                eventId = 1000L + i, title = s.title, begin = begin, end = begin + (s.lengthHours * hour).toLong(),
                allDay = false, location = s.location, description = "A made-up event for the demo.",
                calendarId = cal.id, calendarName = cal.name, color = cal.color,
            )
        }
    }
}
