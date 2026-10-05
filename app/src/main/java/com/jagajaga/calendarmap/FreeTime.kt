package com.jagajaga.calendarmap

import java.time.Instant
import java.time.ZoneId

/** Human wording for free windows in a route. */
object FreeTime {
    /** Shorter gaps are just slack, not worth mentioning. */
    const val MIN_SHOWN_MS = 15 * 60_000L
    private const val SEVERAL_HOURS_MS = 2 * 60 * 60_000L
    private const val LONG_BREAK_MS = 6 * 60 * 60_000L

    /**
     * @param next title of the event you'll head to afterwards.
     * @param atStart the gap is before leaving your start point.
     * @return null when the gap is too short to mention.
     */
    fun describe(
        gap: RoutePlanner.Gap,
        next: String,
        atStart: Boolean,
        zone: ZoneId = systemZone,
        clock: (Long) -> String = TimeFormat::clock,
    ): String? {
        val len = gap.lengthMs
        if (len < MIN_SHOWN_MS) return null
        val d = TimeFormat.duration(len)
        val crossesDay = Instant.ofEpochMilli(gap.from).atZone(zone).toLocalDate() !=
            Instant.ofEpochMilli(gap.until).atZone(zone).toLocalDate()

        return when {
            // Overnight or multi-day: an hour count like "40 h free" isn't useful.
            crossesDay && len >= LONG_BREAK_MS ->
                "Free until ${clock(gap.until)}, then set off for $next."
            atStart && len >= SEVERAL_HOURS_MS ->
                "You have $d free before you need to leave, until ${clock(gap.until)}. Do whatever you want."
            atStart ->
                "$d free before you need to leave (until ${clock(gap.until)})."
            len >= SEVERAL_HOURS_MS ->
                "You have $d free (${clock(gap.from)}–${clock(gap.until)}) to do whatever you want before heading to $next."
            else ->
                "$d free (${clock(gap.from)}–${clock(gap.until)}) before heading to $next."
        }
    }
}
