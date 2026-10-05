package com.jagajaga.calendarmap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Android-side settings glue; the logic itself is tested in :shared. */
class LogicTest {
    @Test fun defaultRadiusIs50Km() = assertEquals(50, AppSettings().radiusKm)

    @Test fun visibleAreaIsDefault() = assertEquals(AreaMode.VISIBLE_MAP, AppSettings().areaMode)

    @Test fun timeWindowUsesSharedRules() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 5, 13, 30, 0, 0, zone)
        val (s, e) = AppSettings(preset = RangePreset.TODAY).timeWindow(now.toInstant().toEpochMilli(), zone.id)
        assertEquals(LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli(), s)
        assertEquals(LocalDate.of(2026, 10, 6).atStartOfDay(zone).toInstant().toEpochMilli(), e)
    }
}
