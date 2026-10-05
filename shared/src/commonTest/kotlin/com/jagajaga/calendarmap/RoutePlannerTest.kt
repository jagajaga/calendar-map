package com.jagajaga.calendarmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class RoutePlannerTest {
    private val min = 60_000L
    private val hour = 60 * min

    private fun matrix(vararg rows: LongArray) = arrayOf(*rows)
    private fun row(vararg minutes: Int) = LongArray(minutes.size) { minutes[it] * min }

    @Test fun overlappingEventsBothFitWithShortStays() {
        val stops = listOf(PlanStop(0, 2 * hour), PlanStop(hour, 3 * hour))
        val m = matrix(row(0, 20), row(20, 0))
        val whole = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = null, now = 0)
        assertEquals(1, whole.visits.size, "whole-event stays can't overlap")
        assertEquals(1, whole.missed.size)

        val short = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = 30 * min, now = 0)
        assertEquals(listOf(0, 1), short.visits.map { it.stop })
        assertTrue(short.missed.isEmpty())
    }

    @Test fun picksOrderWithLeastTravelWhenTimesAllowEither() {
        // Both events are open all day; the start is next to A and far from B.
        val stops = listOf(PlanStop(0, 10 * hour), PlanStop(0, 10 * hour))
        val m = matrix(row(0, 10, 99), row(10, 0, 99), row(5, 50, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = 30 * min, now = 0)
        assertEquals(listOf(0, 1), plan.visits.map { it.stop })
        assertEquals(15 * min, plan.totalTravelMs)
    }

    @Test fun timeOrderBeatsShorterTravel() {
        // B ends before A starts, so B must come first even though A is closer.
        val stops = listOf(PlanStop(5 * hour, 6 * hour), PlanStop(2 * hour, 3 * hour))
        val m = matrix(row(0, 30, 99), row(30, 0, 99), row(5, 60, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = null, now = 0)
        assertEquals(listOf(1, 0), plan.visits.map { it.stop })
    }

    @Test fun unreachableEventIsReportedAsMissed() {
        val stops = listOf(PlanStop(hour, 2 * hour), PlanStop(hour, 2 * hour))
        val m = matrix(row(0, 200), row(200, 0), row(10, 10, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = 30 * min, now = 0)
        assertEquals(1, plan.visits.size)
        assertEquals(1, plan.missed.size)
    }

    @Test fun leaveByAccountsForNextEventAndTravel() {
        // A 0–2h, B 1h–2h30; 30 min stay, 20 min leg: you must stay at B by 2h,
        // so you have to leave A by 1h40.
        val stops = listOf(PlanStop(0, 2 * hour), PlanStop(hour, 2 * hour + 30 * min))
        val m = matrix(row(0, 20), row(20, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = 30 * min, now = 0)
        assertEquals(hour + 40 * min, plan.visits[0].leaveBy)
        assertEquals(2 * hour + 30 * min, plan.visits[1].leaveBy)
    }

    @Test fun departsSoAsToArriveAtFirstStart() {
        val stops = listOf(PlanStop(3 * hour, 4 * hour))
        val m = matrix(row(0, 99), row(25, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = null, now = 0)
        assertEquals(3 * hour - 25 * min, plan.departAt)
        assertEquals(25 * min, plan.visits[0].arrive)
    }

    @Test fun tenStopsSolveQuickly() {
        val n = RoutePlanner.MAX_STOPS
        val stops = List(n) { PlanStop(0, 12 * hour) }
        val m = Array(n + 1) { i -> LongArray(n + 1) { j -> if (i == j) 0 else ((i * 7 + j * 13) % 17 + 3) * min } }
        val mark = TimeSource.Monotonic.markNow()
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = 15 * min, now = 0)
        val ms = mark.elapsedNow().inWholeMilliseconds
        assertEquals(n, plan.visits.size)
        assertTrue(ms < 3_000, "took $ms ms")
    }

    @Test fun gapBetweenEventsSubtractsTravel() {
        // A 16:00–18:00, B 21:00–22:00, 30 min apart: free 18:00–20:30.
        val stops = listOf(PlanStop(16 * hour, 18 * hour), PlanStop(21 * hour, 22 * hour))
        val m = matrix(row(0, 30), row(30, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = null, now = 0)
        assertEquals(listOf(PlanGap(1, 18 * hour, 20 * hour + 30 * min)), plan.gaps)
        assertEquals(2 * hour + 30 * min, plan.freeBetweenMs)
    }

    @Test fun noGapWhenYouMustLeaveBeforeTheEnd() {
        // A runs until 19:00 but B starts at 18:30, 20 min away.
        val stops = listOf(PlanStop(16 * hour, 19 * hour), PlanStop(18 * hour + 30 * min, 20 * hour))
        val m = matrix(row(0, 20), row(20, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = 30 * min, now = 0)
        assertEquals(2, plan.visits.size)
        assertTrue(plan.gaps.isEmpty())
    }

    @Test fun freeTimeBeforeSettingOffIsNotCountedAsBetween() {
        val stops = listOf(PlanStop(5 * hour, 6 * hour))
        val m = matrix(row(0, 99), row(15, 0))
        val plan = RoutePlanner.plan(stops, m, hasStart = true, minStayMs = null, now = hour)
        assertEquals(listOf(PlanGap(0, hour, 5 * hour - 15 * min)), plan.gaps)
        assertEquals(0L, plan.freeBetweenMs)
    }

    @Test fun travelMatrixOverloadMatchesArrays() {
        val stops = listOf(PlanStop(0, 2 * hour), PlanStop(hour, 3 * hour))
        val m = TravelMatrix(2)
        m.put(0, 1, 20 * min)
        m.put(1, 0, 20 * min)
        val viaMatrix = RoutePlanner.plan(stops, m, hasStart = false, minStayMs = 30 * min, now = 0)
        val viaArrays = RoutePlanner.plan(stops, matrix(row(0, 20), row(20, 0)), false, 30 * min, 0)
        assertEquals(viaArrays, viaMatrix)
        assertEquals(20 * min, m.at(0, 1))
    }
}
