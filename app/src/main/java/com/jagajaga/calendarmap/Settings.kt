package com.jagajaga.calendarmap

import android.content.Context
import java.time.ZoneId

data class AppSettings(
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    /** `null` means "all calendars", including ones added later. */
    val selectedCalendarIds: Set<Long>? = null,
    val preset: RangePreset = RangePreset.WEEK,
    /** Inclusive local dates for [RangePreset.CUSTOM], stored as epoch days. */
    val customStartDay: Long? = null,
    val customEndDay: Long? = null,
    /** Local times for the custom range, minutes after midnight; null = whole days. */
    val customStartMinute: Int? = null,
    val customEndMinute: Int? = null,
    val showAllDay: Boolean = true,
    val areaMode: AreaMode = AreaMode.VISIBLE_MAP,
    val travelMode: TravelMode = TravelMode.WALK,
    /** Minutes; null = stay for the whole event. */
    val stayMinutes: Int? = 30,
    val routeFromMyLocation: Boolean = true,
) {
    companion object {
        const val DEFAULT_RADIUS_KM = 50
    }
}

/** The [start, end) window in epoch millis that the current settings ask for. */
fun AppSettings.timeWindow(
    nowMs: Long = System.currentTimeMillis(),
    zoneId: String = systemZone.id,
): Pair<Long, Long> =
    TimeWindows.compute(preset, customStartDay, customEndDay, customStartMinute, customEndMinute, nowMs, zoneId).let { it.start to it.end }

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
        customStartMinute = prefs.getInt(K_START_MIN, -1).takeIf { it >= 0 },
        customEndMinute = prefs.getInt(K_END_MIN, -1).takeIf { it >= 0 },
        showAllDay = prefs.getBoolean(K_ALL_DAY, true),
        areaMode = enumPref(K_AREA, AreaMode.VISIBLE_MAP),
        travelMode = enumPref(K_TRAVEL, TravelMode.WALK),
        stayMinutes = prefs.getInt(K_STAY, 30).takeIf { it > 0 },
        routeFromMyLocation = prefs.getBoolean(K_ROUTE_FROM_ME, true),
    )

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    fun save(s: AppSettings) {
        prefs.edit().apply {
            putInt(K_RADIUS, s.radiusKm)
            if (s.selectedCalendarIds == null) remove(K_CALENDARS)
            else putStringSet(K_CALENDARS, s.selectedCalendarIds.map { it.toString() }.toSet())
            putString(K_PRESET, s.preset.name)
            if (s.customStartDay == null) remove(K_START) else putLong(K_START, s.customStartDay)
            if (s.customEndDay == null) remove(K_END) else putLong(K_END, s.customEndDay)
            putInt(K_START_MIN, s.customStartMinute ?: -1)
            putInt(K_END_MIN, s.customEndMinute ?: -1)
            putBoolean(K_ALL_DAY, s.showAllDay)
            putString(K_AREA, s.areaMode.name)
            putString(K_TRAVEL, s.travelMode.name)
            putInt(K_STAY, s.stayMinutes ?: 0)
            putBoolean(K_ROUTE_FROM_ME, s.routeFromMyLocation)
        }.apply()
    }

    private companion object {
        const val K_RADIUS = "radius_km"
        const val K_CALENDARS = "calendar_ids"
        const val K_PRESET = "range_preset"
        const val K_START = "custom_start_day"
        const val K_END = "custom_end_day"
        const val K_START_MIN = "custom_start_minute"
        const val K_END_MIN = "custom_end_minute"
        const val K_ALL_DAY = "show_all_day"
        const val K_AREA = "area_mode"
        const val K_TRAVEL = "travel_mode"
        const val K_STAY = "stay_minutes"
        const val K_ROUTE_FROM_ME = "route_from_my_location"
    }
}

val systemZone: ZoneId get() = ZoneId.systemDefault()
