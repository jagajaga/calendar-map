package com.jagajaga.calendarmap

import com.jagajaga.calendarmap.RoutePlanner.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private val min = 60_000L
    private val hour = 60 * min

    private fun matrix(vararg rows: LongArray) = arrayOf(*rows)
    private fun row(vararg minutes: Int) = LongArray(minutes.size) { minutes[it] * min }

    @Test fun overlappingEventsBothFitWithShortStays() {
        val stops = listOf(Stop(0, 2 * hour), Stop(hour, 3 * hour))
        val m = matrix(row(0, 20), row(20, 0))
        val whole = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = null, now = 0)
        assertEquals("whole-event stays can't overlap", 1, whole.visits.size)
        assertEquals(1, whole.missed.size)

        val short = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = 30 * min, now = 0)
        assertEquals(listOf(0, 1), short.visits.map { it.stop })
        assertTrue(short.missed.isEmpty())
    }

    @Test fun picksOrderWithLeastTravelWhenTimesAllowEither() {
        // Both events are open all day; the start is next to A and far from B.
        val stops = listOf(Stop(0, 10 * hour), Stop(0, 10 * hour))
        val m = matrix(row(0, 10, 99), row(10, 0, 99), row(5, 50, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = 30 * min, now = 0)
        assertEquals(listOf(0, 1), plan.visits.map { it.stop })
        assertEquals(15 * min, plan.totalTravelMs)
    }

    @Test fun timeOrderBeatsShorterTravel() {
        // B ends before A starts, so B must come first even though A is closer.
        val stops = listOf(Stop(5 * hour, 6 * hour), Stop(2 * hour, 3 * hour))
        val m = matrix(row(0, 30, 99), row(30, 0, 99), row(5, 60, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = null, now = 0)
        assertEquals(listOf(1, 0), plan.visits.map { it.stop })
    }

    @Test fun unreachableEventIsReportedAsMissed() {
        val stops = listOf(Stop(hour, 2 * hour), Stop(hour, 2 * hour))
        val m = matrix(row(0, 200), row(200, 0), row(10, 10, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = 30 * min, now = 0)
        assertEquals(1, plan.visits.size)
        assertEquals(1, plan.missed.size)
    }

    @Test fun leaveByAccountsForNextEventAndTravel() {
        // A 0–2h, B 1h–2h30; 30 min stay, 20 min leg: you must stay at B by 2h,
        // so you have to leave A by 1h40.
        val stops = listOf(Stop(0, 2 * hour), Stop(hour, 2 * hour + 30 * min))
        val m = matrix(row(0, 20), row(20, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = 30 * min, now = 0)
        assertEquals(hour + 40 * min, plan.visits[0].leaveBy)
        assertEquals(2 * hour + 30 * min, plan.visits[1].leaveBy)
    }

    @Test fun departsSoAsToArriveAtFirstStart() {
        val stops = listOf(Stop(3 * hour, 4 * hour))
        val m = matrix(row(0, 99), row(25, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = null, now = 0)
        assertEquals(3 * hour - 25 * min, plan.departAt)
        assertEquals(25 * min, plan.visits[0].arrive)
    }

    @Test fun tenStopsSolveQuickly() {
        val n = RoutePlanner.MAX_STOPS
        val stops = List(n) { Stop(0, 12 * hour) }
        val m = Array(n + 1) { i -> LongArray(n + 1) { j -> if (i == j) 0 else ((i * 7 + j * 13) % 17 + 3) * min } }
        val t0 = System.nanoTime()
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = 15 * min, now = 0)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(n, plan.visits.size)
        assertTrue("took $ms ms", ms < 3_000)
    }
}
