package com.jagajaga.calendarmap

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class RangePreset(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    WEEK("Next 7 days"),
    MONTH("Next 30 days"),
    CUSTOM("Custom"),
}

enum class AreaMode(val label: String) {
    VISIBLE_MAP("Visible map area"),
    RADIUS("Within a radius of me"),
}

enum class TravelMode(val label: String, val profile: String, val googleMode: String, val fallbackMps: Double) {
    WALK("Walk", "foot", "walking", 1.3),
    BIKE("Bike", "bike", "bicycling", 4.2),
    CAR("Car", "car", "driving", 8.3),
}

/** Minimum time to spend at each event when planning a route; null = the whole event. */
val STAY_OPTIONS: List<Int?> = listOf(15, 30, 60, null)

/** A [start, end) window in epoch millis. */
data class TimeWindow(val start: Long, val end: Long)

object TimeWindows {
    /**
     * The window a time-range preset asks for. Never starts before [nowMs]:
     * events that have already ended are never shown, ones in progress are.
     *
     * @param customStartDay inclusive local date for [RangePreset.CUSTOM], as epoch days.
     * @param customStartMinute local time on the start day, minutes after midnight (null = 00:00).
     * @param customEndMinute local time on the end day where the range stops
     *   (null = the end of that day).
     * @param zoneId IANA zone, e.g. "Europe/Berlin".
     */
    fun compute(
        preset: RangePreset,
        customStartDay: Long?,
        customEndDay: Long?,
        customStartMinute: Int?,
        customEndMinute: Int?,
        nowMs: Long,
        zoneId: String,
    ): TimeWindow {
        val tz = TimeZone.of(zoneId)
        val now = Instant.fromEpochMilliseconds(nowMs)
        val today = now.toLocalDateTime(tz).date
        fun dayStart(d: LocalDate) = d.atStartOfDayIn(tz).toEpochMilliseconds()
        fun day(d: LocalDate, plus: Int) = d.plus(plus, DateTimeUnit.DAY)
        fun at(d: LocalDate, minute: Int) =
            LocalDateTime(d, LocalTime(minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60))
                .toInstant(tz).toEpochMilliseconds()
        fun nowPlusDays(n: Int) = now.plus(n, DateTimeUnit.DAY, tz).toEpochMilliseconds()
        val raw = when (preset) {
            RangePreset.TODAY -> TimeWindow(dayStart(today), dayStart(day(today, 1)))
            RangePreset.TOMORROW -> TimeWindow(dayStart(day(today, 1)), dayStart(day(today, 2)))
            RangePreset.WEEK -> TimeWindow(nowMs, nowPlusDays(7))
            RangePreset.MONTH -> TimeWindow(nowMs, nowPlusDays(30))
            RangePreset.CUSTOM -> {
                val s = customStartDay?.let { LocalDate.fromEpochDays(it.toInt()) } ?: today
                val e = customEndDay?.let { LocalDate.fromEpochDays(it.toInt()) } ?: s
                TimeWindow(
                    customStartMinute?.let { at(s, it) } ?: dayStart(s),
                    customEndMinute?.let { at(e, it) } ?: dayStart(day(e, 1)),
                )
            }
        }
        val start = maxOf(raw.start, nowMs)
        return TimeWindow(start, maxOf(raw.end, start))
    }
}

object Durations {
    /** "12 min", "2 h", "1 h 5 min". */
    fun format(ms: Long): String {
        val totalMin = ((ms + 30_000) / 60_000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }
}
