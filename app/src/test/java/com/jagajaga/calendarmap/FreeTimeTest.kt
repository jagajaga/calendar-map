package com.jagajaga.calendarmap

import com.jagajaga.calendarmap.RoutePlanner.Gap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FreeTimeTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private fun at(day: Int, h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val clock = { ms: Long ->
        val t = java.time.Instant.ofEpochMilli(ms).atZone(zone)
        "%d/%02d:%02d".format(t.dayOfMonth, t.hour, t.minute)
    }
    private fun say(gap: Gap, atStart: Boolean = false) = FreeTime.describe(gap, "Demo Night", atStart, zone, clock)

    @Test fun shortSlackIsNotMentioned() = assertNull(say(Gap(1, at(6, 18), at(6, 18, 10))))

    @Test fun underTwoHours() = assertEquals(
        "45 min free (6/18:00–6/18:45) before heading to Demo Night.",
        say(Gap(1, at(6, 18), at(6, 18, 45))),
    )

    @Test fun severalHours() = assertEquals(
        "You have 2 h 30 min free (6/18:00–6/20:30) to do whatever you want before heading to Demo Night.",
        say(Gap(1, at(6, 18), at(6, 20, 30))),
    )

    @Test fun overnightSaysUntilWhen() = assertEquals(
        "Free until 8/12:40, then set off for Demo Night.",
        say(Gap(1, at(6, 21), at(8, 12, 40))),
    )

    @Test fun lateEveningGapPastMidnightStillCountsHours() = assertEquals(
        "You have 3 h free (6/22:00–7/01:00) to do whatever you want before heading to Demo Night.",
        say(Gap(1, at(6, 22), at(7, 1))),
    )

    @Test fun beforeSettingOff() = assertEquals(
        "50 min free before you need to leave (until 6/16:50).",
        say(Gap(0, at(6, 16), at(6, 16, 50)), atStart = true),
    )
}
