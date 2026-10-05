package com.jagajaga.calendarmap

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object TimeFormat {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")
    private val longDay = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    // All-day events are stored at UTC midnight, so read them in UTC.
    private fun zoneFor(e: CalEvent): ZoneId = if (e.allDay) ZoneOffset.UTC else systemZone

    private fun startDate(e: CalEvent) = Instant.ofEpochMilli(e.begin).atZone(zoneFor(e))
    private fun endDate(e: CalEvent) = Instant.ofEpochMilli(e.end).atZone(zoneFor(e))

    /** Compact label for map pins, e.g. "Tue 7 Oct · 14:00–15:30". */
    fun pinLabel(e: CalEvent): String {
        val s = startDate(e)
        val today = java.time.LocalDate.now(systemZone)
        val dayPrefix = if (s.toLocalDate() == today) "" else s.format(shortDay) + " · "
        return dayPrefix + timeRange(e)
    }

    /** "14:00–15:30", "All day", or "14:00 → Wed 8 Oct 10:00" for multi-day events. */
    fun timeRange(e: CalEvent): String {
        val s = startDate(e)
        val en = endDate(e)
        if (e.allDay) {
            val lastDay = en.minusDays(1).toLocalDate()
            return if (lastDay <= s.toLocalDate()) "All day"
            else "All day → ${lastDay.format(shortDay)}"
        }
        return if (s.toLocalDate() == en.toLocalDate()) {
            "${s.format(time)}–${en.format(time)}"
        } else {
            "${s.format(time)} → ${en.format(shortDay)} ${en.format(time)}"
        }
    }

    fun startFull(e: CalEvent): String = startDate(e).let {
        if (e.allDay) it.format(longDay) else "${it.format(longDay)}, ${it.format(time)}"
    }

    fun endFull(e: CalEvent): String =
        if (e.allDay) endDate(e).minusDays(1).format(longDay)
        else endDate(e).let { "${it.format(longDay)}, ${it.format(time)}" }

    fun day(epochDay: Long): String = java.time.LocalDate.ofEpochDay(epochDay).format(shortDay)
}
