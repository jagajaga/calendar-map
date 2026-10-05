package com.jagajaga.calendarmap

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreeTimeTest {
    private val zoneId = "America/Los_Angeles"
    private val tz = TimeZone.of(zoneId)
    private fun at(day: Int, h: Int, m: Int = 0) =
        LocalDateTime(2026, 10, day, h, m).toInstant(tz).toEpochMilliseconds()
    private val clock = { ms: Long ->
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
        "${t.dayOfMonth}/${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }
    private fun say(gap: PlanGap, atStart: Boolean = false) = FreeTime.describe(gap, "Demo Night", atStart, zoneId, clock)

    @Test fun shortSlackIsNotMentioned() = assertNull(say(PlanGap(1, at(6, 18), at(6, 18, 10))))

    @Test fun underTwoHours() = assertEquals(
        "45 min free (6/18:00–6/18:45) before heading to Demo Night.",
        say(PlanGap(1, at(6, 18), at(6, 18, 45))),
    )

    @Test fun severalHours() = assertEquals(
        "You have 2 h 30 min free (6/18:00–6/20:30) to do whatever you want before heading to Demo Night.",
        say(PlanGap(1, at(6, 18), at(6, 20, 30))),
    )

    @Test fun overnightSaysUntilWhen() = assertEquals(
        "Free until 8/12:40, then set off for Demo Night.",
        say(PlanGap(1, at(6, 21), at(8, 12, 40))),
    )

    @Test fun lateEveningGapPastMidnightStillCountsHours() = assertEquals(
        "You have 3 h free (6/22:00–7/01:00) to do whatever you want before heading to Demo Night.",
        say(PlanGap(1, at(6, 22), at(7, 1))),
    )

    @Test fun beforeSettingOff() = assertEquals(
        "50 min free before you need to leave (until 6/16:50).",
        say(PlanGap(0, at(6, 16), at(6, 16, 50)), atStart = true),
    )

    @Test fun durations() {
        assertEquals("0 min", Durations.format(0))
        assertEquals("12 min", Durations.format(12 * 60_000L))
        assertEquals("2 h", Durations.format(120 * 60_000L))
        assertEquals("1 h 5 min", Durations.format(65 * 60_000L))
    }
}
