package com.jagajaga.calendarmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsrmTest {
    private val pts = listOf(LatLon(37.774892, -122.419387), LatLon(37.7837, -122.4089))

    @Test fun fixedSixDecimals() {
        assertEquals("-122.419387", Osrm.fixed6(-122.419387))
        assertEquals("0.000001", Osrm.fixed6(0.0000012))
        assertEquals("-0.500000", Osrm.fixed6(-0.5))
        assertEquals("37.000000", Osrm.fixed6(37.0))
    }

    @Test fun urls() = assertEquals(
        "https://routing.openstreetmap.de/routed-foot/table/v1/driving/" +
            "-122.419387,37.774892;-122.408900,37.783700?annotations=duration",
        Osrm.tableUrl(pts, TravelMode.WALK),
    )

    @Test fun parsesTableAndEstimatesUnreachable() {
        val body = """{"code":"Ok","durations":[[0,1096.6],[null,0]],"destinations":[]}"""
        val m = Osrm.parseTable(body, pts, TravelMode.WALK)!!
        assertEquals(1_096_600L, m.at(0, 1))
        assertEquals(Osrm.estimateMs(pts[1], pts[0], TravelMode.WALK), m.at(1, 0))
        assertTrue(m.at(1, 0) > 0)
    }

    @Test fun rejectsErrorsAndGarbage() {
        assertNull(Osrm.parseTable("""{"code":"InvalidQuery","message":"bad"}""", pts, TravelMode.CAR))
        assertNull(Osrm.parseTable("<html>502</html>", pts, TravelMode.CAR))
        assertNull(Osrm.parseTable("""{"code":"Ok","durations":[[0,1]]}""", pts, TravelMode.CAR))
        assertNull(Osrm.parseRoute("""{"code":"NoRoute","routes":[]}"""))
    }

    @Test fun parsesRouteGeometry() {
        val body = """{"code":"Ok","routes":[{"geometry":{"type":"LineString","coordinates":[[-122.419387,37.774892],[-122.4089,37.7837]]}}]}"""
        assertEquals(pts, Osrm.parseRoute(body))
    }
}
