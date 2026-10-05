package com.jagajaga.calendarmap

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class RangePreset(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    WEEK("Next 7 days"),
    MONTH("Next 30 days"),
    CUSTOM("Custom"),
}

data class AppSettings(
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    /** `null` means "all calendars", including ones added later. */
    val selectedCalendarIds: Set<Long>? = null,
    val preset: RangePreset = RangePreset.WEEK,
    /** Inclusive local dates for [RangePreset.CUSTOM], stored as epoch days. */
    val customStartDay: Long? = null,
    val customEndDay: Long? = null,
    val showAllDay: Boolean = true,
) {
    companion object {
        const val DEFAULT_RADIUS_KM = 50
    }
}

/** The [start, end) window in epoch millis that the current settings ask for. */
fun AppSettings.timeWindow(
    now: ZonedDateTime = ZonedDateTime.now(),
): Pair<Long, Long> {
    val zone = now.zone
    val today = now.toLocalDate()
    fun dayStart(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    return when (preset) {
        RangePreset.TODAY -> dayStart(today) to dayStart(today.plusDays(1))
        RangePreset.TOMORROW -> dayStart(today.plusDays(1)) to dayStart(today.plusDays(2))
        RangePreset.WEEK -> now.toInstant().toEpochMilli() to now.plusDays(7).toInstant().toEpochMilli()
        RangePreset.MONTH -> now.toInstant().toEpochMilli() to now.plusDays(30).toInstant().toEpochMilli()
        RangePreset.CUSTOM -> {
            val s = customStartDay?.let(LocalDate::ofEpochDay) ?: today
            val e = customEndDay?.let(LocalDate::ofEpochDay) ?: s
            dayStart(s) to dayStart(e.plusDays(1))
        }
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        radiusKm = prefs.getInt(K_RADIUS, AppSettings.DEFAULT_RADIUS_KM),
        selectedCalendarIds = prefs.getStringSet(K_CALENDARS, null)
            ?.mapNotNull { it.toLongOrNull() }?.toSet(),
        preset = prefs.getString(K_PRESET, null)
            ?.let { runCatching { RangePreset.valueOf(it) }.getOrNull() } ?: RangePreset.WEEK,
        customStartDay = prefs.getLong(K_START, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
        customEndDay = prefs.getLong(K_END, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
        showAllDay = prefs.getBoolean(K_ALL_DAY, true),
    )

    fun save(s: AppSettings) {
        prefs.edit().apply {
            putInt(K_RADIUS, s.radiusKm)
            if (s.selectedCalendarIds == null) remove(K_CALENDARS)
            else putStringSet(K_CALENDARS, s.selectedCalendarIds.map { it.toString() }.toSet())
            putString(K_PRESET, s.preset.name)
            if (s.customStartDay == null) remove(K_START) else putLong(K_START, s.customStartDay)
            if (s.customEndDay == null) remove(K_END) else putLong(K_END, s.customEndDay)
            putBoolean(K_ALL_DAY, s.showAllDay)
        }.apply()
    }

    private companion object {
        const val K_RADIUS = "radius_km"
        const val K_CALENDARS = "calendar_ids"
        const val K_PRESET = "range_preset"
        const val K_START = "custom_start_day"
        const val K_END = "custom_end_day"
        const val K_ALL_DAY = "show_all_day"
    }
}

val systemZone: ZoneId get() = ZoneId.systemDefault()
