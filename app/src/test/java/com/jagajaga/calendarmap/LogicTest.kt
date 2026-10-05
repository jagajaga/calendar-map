package com.jagajaga.calendarmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LogicTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 5, 13, 30, 0, 0, zone)
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun defaultRadiusIs50Km() = assertEquals(50, AppSettings().radiusKm)

    @Test fun todayCoversWholeLocalDay() {
        val (s, e) = AppSettings(preset = RangePreset.TODAY).timeWindow(now)
        assertEquals(at(2026, 10, 5), s)
        assertEquals(at(2026, 10, 6), e)
    }

    @Test fun tomorrowCoversNextDay() {
        val (s, e) = AppSettings(preset = RangePreset.TOMORROW).timeWindow(now)
        assertEquals(at(2026, 10, 6), s)
        assertEquals(at(2026, 10, 7), e)
    }

    @Test fun weekStartsNow() {
        val (s, e) = AppSettings(preset = RangePreset.WEEK).timeWindow(now)
        assertEquals(now.toInstant().toEpochMilli(), s)
        assertEquals(now.plusDays(7).toInstant().toEpochMilli(), e)
    }

    @Test fun customRangeIncludesEndDay() {
        val settings = AppSettings(
            preset = RangePreset.CUSTOM,
            customStartDay = LocalDate.of(2026, 10, 10).toEpochDay(),
            customEndDay = LocalDate.of(2026, 10, 12).toEpochDay(),
        )
        val (s, e) = settings.timeWindow(now)
        assertEquals(at(2026, 10, 10), s)
        assertEquals(at(2026, 10, 13), e)
    }

    @Test fun parsesCoordinateLocations() {
        assertEquals(LatLon(52.52, 13.405), GeoResolver.parseCoordinates("52.52, 13.405"))
        assertEquals(LatLon(-33.9, 151.2), GeoResolver.parseCoordinates("-33.9,151.2"))
        assertNull(GeoResolver.parseCoordinates("95.0, 13.4"))
        assertNull(GeoResolver.parseCoordinates("Alexanderplatz 1, Berlin"))
    }

    @Test fun skipsVirtualLocations() {
        assertTrue(GeoResolver.looksVirtual("https://zoom.us/j/123"))
        assertTrue(GeoResolver.looksVirtual("Google Meet"))
        assertTrue(GeoResolver.looksVirtual("meet.google.com/abc-defg-hij"))
        assertFalse(GeoResolver.looksVirtual("Alexanderplatz 1, Berlin"))
        assertFalse(GeoResolver.looksVirtual("Zoomstraße 5, Hamburg"))
    }

    @Test fun boundsContainment() {
        val sf = Bounds(north = 37.82, south = 37.70, east = -122.35, west = -122.52)
        assertTrue(LatLon(37.7749, -122.4194) in sf)
        assertFalse(LatLon(37.8044, -122.2712) in sf) // Oakland, across the bay
        // A view spanning the antimeridian (west > east).
        val pacific = Bounds(north = 10.0, south = -10.0, east = -170.0, west = 170.0)
        assertTrue(LatLon(0.0, 179.0) in pacific)
        assertTrue(LatLon(0.0, -175.0) in pacific)
        assertFalse(LatLon(0.0, 0.0) in pacific)
    }

    @Test fun visibleAreaIsDefault() = assertEquals(AreaMode.VISIBLE_MAP, AppSettings().areaMode)
}
