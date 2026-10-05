package com.jagajaga.calendarmap

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoAndTimeTest {
    private val zoneId = "Europe/Berlin"
    private val tz = TimeZone.of(zoneId)
    private val now = LocalDateTime(2026, 10, 5, 13, 30).toInstant(tz).toEpochMilliseconds()
    private fun dayStart(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(tz).toEpochMilliseconds()
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 10, d, h, m).toInstant(tz).toEpochMilliseconds()
    private fun epochDay(d: Int) = LocalDate(2026, 10, d).toEpochDays().toLong()
    private fun window(
        p: RangePreset, s: Long? = null, e: Long? = null, sMin: Int? = null, eMin: Int? = null, nowMs: Long = now,
    ) = TimeWindows.compute(p, s, e, sMin, eMin, nowMs, zoneId)

    @Test fun todayRunsFromNowToMidnight() =
        assertEquals(TimeWindow(now, dayStart(2026, 10, 6)), window(RangePreset.TODAY))

    @Test fun tomorrowCoversNextDay() =
        assertEquals(TimeWindow(dayStart(2026, 10, 6), dayStart(2026, 10, 7)), window(RangePreset.TOMORROW))

    @Test fun weekStartsNow() {
        val sameTimeNextWeek = LocalDateTime(2026, 10, 12, 13, 30).toInstant(tz).toEpochMilliseconds()
        assertEquals(TimeWindow(now, sameTimeNextWeek), window(RangePreset.WEEK))
    }

    @Test fun monthCrossesDstAtSameLocalTime() {
        // Berlin leaves summer time on 25 Oct 2026; "30 days" keeps the local clock time.
        val later = LocalDateTime(2026, 11, 4, 13, 30).toInstant(tz).toEpochMilliseconds()
        assertEquals(later, window(RangePreset.MONTH).end)
    }

    @Test fun customRangeIncludesEndDay() =
        assertEquals(
            TimeWindow(dayStart(2026, 10, 10), dayStart(2026, 10, 13)),
            window(RangePreset.CUSTOM, epochDay(10), epochDay(12)),
        )

    @Test fun customRangeWithTimes() =
        assertEquals(
            TimeWindow(at(7, 18), at(8, 2, 30)),
            window(RangePreset.CUSTOM, epochDay(7), epochDay(8), sMin = 18 * 60, eMin = 2 * 60 + 30),
        )

    @Test fun customRangeAlreadyUnderwayStartsNow() =
        assertEquals(
            TimeWindow(now, at(5, 22)),
            window(RangePreset.CUSTOM, epochDay(5), epochDay(5), sMin = 9 * 60, eMin = 22 * 60),
        )

    @Test fun pastRangeIsEmpty() {
        val w = window(RangePreset.CUSTOM, epochDay(1), epochDay(2))
        assertEquals(w.start, w.end)
        assertEquals(now, w.start)
    }

    @Test fun parsesCoordinateLocations() {
        assertEquals(LatLon(52.52, 13.405), GeoText.parseCoordinates("52.52, 13.405"))
        assertEquals(LatLon(-33.9, 151.2), GeoText.parseCoordinates("-33.9,151.2"))
        assertNull(GeoText.parseCoordinates("95.0, 13.4"))
        assertNull(GeoText.parseCoordinates("Alexanderplatz 1, Berlin"))
    }

    @Test fun skipsVirtualLocations() {
        assertTrue(GeoText.looksVirtual("https://zoom.us/j/123"))
        assertTrue(GeoText.looksVirtual("Google Meet"))
        assertTrue(GeoText.looksVirtual("meet.google.com/abc-defg-hij"))
        assertFalse(GeoText.looksVirtual("Alexanderplatz 1, Berlin"))
        assertFalse(GeoText.looksVirtual("Zoomstraße 5, Hamburg"))
    }

    @Test fun boundsContainment() {
        val sf = Bounds(north = 37.82, south = 37.70, east = -122.35, west = -122.52)
        assertTrue(LatLon(37.7749, -122.4194) in sf)
        assertFalse(LatLon(37.8044, -122.2712) in sf) // Oakland, across the bay
        val pacific = Bounds(north = 10.0, south = -10.0, east = -170.0, west = 170.0)
        assertTrue(LatLon(0.0, 179.0) in pacific)
        assertTrue(LatLon(0.0, -175.0) in pacific)
        assertFalse(LatLon(0.0, 0.0) in pacific)
    }

    @Test fun boundsFromMapKitRegion() {
        val b = Bounds.fromCenter(lat = 37.77, lon = -122.42, latSpan = 0.1, lonSpan = 0.2)
        assertEquals(37.82, b.north, 1e-9)
        assertEquals(37.72, b.south, 1e-9)
        assertEquals(-122.32, b.east, 1e-9)
        assertEquals(-122.52, b.west, 1e-9)
        val wrapped = Bounds.fromCenter(lat = 0.0, lon = 179.0, latSpan = 10.0, lonSpan = 10.0)
        assertTrue(LatLon(0.0, -178.0) in wrapped)
    }

    @Test fun haversine() {
        // Berlin -> Paris is about 878 km.
        val km = Distance.km(LatLon(52.52, 13.405), LatLon(48.8566, 2.3522))
        assertTrue(km in 870.0..890.0, "got $km")
    }
}
