package com.jagajaga.calendarmap

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Events that share one geocoded point; shown as a single pin. */
data class Place(val point: LatLon, val events: List<CalEvent>, val distanceKm: Double?)

data class MappedEvent(val event: CalEvent, val point: LatLon, val distanceKm: Double?)

/** The map's visible area; west > east when it spans the antimeridian. */
data class Bounds(val north: Double, val south: Double, val east: Double, val west: Double) {
    operator fun contains(p: LatLon): Boolean {
        if (p.lat > north || p.lat < south) return false
        return if (west <= east) p.lon in west..east else p.lon >= west || p.lon <= east
    }

    /** Grow by [fraction] of the size on each side, so pins near the edge are ready. */
    fun padded(fraction: Double): Bounds {
        val dLat = (north - south) * fraction
        val width = if (west <= east) east - west else east + 360 - west
        val dLon = width * fraction
        return Bounds(
            (north + dLat).coerceAtMost(90.0), (south - dLat).coerceAtLeast(-90.0),
            wrapLon(east + dLon), wrapLon(west - dLon),
        ).let { if (width + 2 * dLon >= 360) it.copy(east = 180.0, west = -180.0) else it }
    }

    private fun wrapLon(x: Double) = ((x + 540) % 360) - 180
}

data class RouteResult(
    /** Selected events, indexed by [RoutePlanner.Visit.stop]. */
    val events: List<MappedEvent>,
    val plan: RoutePlanner.Plan,
    val path: List<LatLon>,
    val start: LatLon?,
    val mode: TravelMode,
    /** True when travel times are straight-line guesses (routing server unreachable). */
    val estimated: Boolean,
) {
    /** Route position (1-based) of each attended event, by event key. */
    val orderByKey: Map<String, Int> =
        plan.visits.withIndex().associate { (i, v) -> events[v.stop].event.key to i + 1 }
}

data class UiState(
    val hasCalendarPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val calendars: List<CalendarInfo> = emptyList(),
    val myLocation: LatLon? = null,
    val loading: Boolean = false,
    val progress: String? = null,
    /** Everything that geocoded, before the radius filter. */
    val mapped: List<MappedEvent> = emptyList(),
    val unmapped: List<CalEvent> = emptyList(),
    val viewport: Bounds? = null,
    val planning: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val routing: Boolean = false,
    val route: RouteResult? = null,
) {
    private val radiusActive get() = settings.areaMode == AreaMode.RADIUS && myLocation != null

    /** Events that pass the area filter: inside the radius, or inside the visible map. */
    val shown: List<MappedEvent> by lazy {
        when {
            radiusActive -> mapped.filter { it.distanceKm == null || it.distanceKm <= settings.radiusKm }
            settings.areaMode == AreaMode.VISIBLE_MAP && viewport != null -> mapped.filter { it.point in viewport }
            else -> mapped
        }
    }

    /** Count of mapped events the area filter hides. */
    val hidden: Int get() = mapped.size - shown.size

    /** Pins to draw: only those near the visible area are created (lazy loading). */
    val places: List<Place> by lazy {
        val near = viewport?.padded(0.15)
        shown.filter { near == null || it.point in near }
            .groupBy { it.point }
            .map { (p, list) -> Place(p, list.map { it.event }.sortedBy { it.begin }, list.first().distanceKm) }
    }

    val selectedEvents: List<MappedEvent> get() = mapped.filter { it.event.key in selectedKeys }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val calendarRepo = CalendarRepository(app)
    private val geo = GeoResolver(app)
    private val router = Router("${app.packageName} (github.com/jagajaga/calendar-map)")
    private var routeJob: Job? = null

    private val _state = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onPermissionsChanged() {
        val ctx = getApplication<Application>()
        val cal = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        _state.update {
            it.copy(hasCalendarPermission = cal, hasLocationPermission = LocationProvider.hasPermission(ctx))
        }
        refresh(relocate = true)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _state.value.settings
        val new = transform(old)
        if (new == old) return
        store.save(new)
        _state.update { it.copy(settings = new) }
        // These only change client-side filtering or routing; anything else needs a re-query.
        val local = new.copy(
            radiusKm = old.radiusKm, areaMode = old.areaMode, travelMode = old.travelMode,
            stayMinutes = old.stayMinutes, routeFromMyLocation = old.routeFromMyLocation,
        )
        if (local != old) refresh(relocate = false)
        val routeInputs = { s: AppSettings -> Triple(s.travelMode, s.stayMinutes, s.routeFromMyLocation) }
        if (_state.value.route != null && routeInputs(new) != routeInputs(old)) buildRoute()
    }

    fun setViewport(bounds: Bounds) {
        if (_state.value.viewport != bounds) _state.update { it.copy(viewport = bounds) }
    }

    // ---------- Route planning ----------

    fun startPlanning() = _state.update { it.copy(planning = true) }

    fun cancelPlanning() {
        routeJob?.cancel()
        _state.update { it.copy(planning = false, selectedKeys = emptySet(), route = null, routing = false) }
    }

    /** Returns false when the selection is already full. */
    fun toggleSelected(key: String): Boolean {
        val cur = _state.value.selectedKeys
        if (key !in cur && cur.size >= RoutePlanner.MAX_STOPS) return false
        _state.update { it.copy(selectedKeys = if (key in cur) cur - key else cur + key) }
        return true
    }

    /** Back to picking events, keeping the current selection. */
    fun editSelection() = _state.update { it.copy(route = null, planning = true) }

    fun buildRoute() {
        val st = _state.value
        val chosen = st.selectedEvents.sortedBy { it.event.begin }
        if (chosen.isEmpty()) return
        val settings = st.settings
        val start = if (settings.routeFromMyLocation) st.myLocation else null
        routeJob?.cancel()
        _state.update { it.copy(routing = true) }
        routeJob = viewModelScope.launch {
            val points = chosen.map { it.point } + listOfNotNull(start)
            val matrix = router.matrix(points, settings.travelMode)
            val plan = withContext(Dispatchers.Default) {
                RoutePlanner.plan(
                    stops = chosen.map { RoutePlanner.Stop(localBegin(it.event), localEnd(it.event)) },
                    travelMs = matrix.durationsMs,
                    hasStart = start != null,
                    minStayMs = settings.stayMinutes?.let { it * 60_000L },
                    now = System.currentTimeMillis(),
                )
            }
            val ordered = listOfNotNull(start) + plan.visits.map { chosen[it.stop].point }
            val path = router.path(ordered, settings.travelMode)
            _state.update {
                it.copy(
                    routing = false,
                    planning = false,
                    route = RouteResult(chosen, plan, path.points, start, settings.travelMode, matrix.estimated || path.estimated),
                )
            }
        }
    }

    fun clearGeocodeCache() {
        geo.clearCache()
        refresh(relocate = false)
    }

    fun refresh(relocate: Boolean = true) {
        val ctx = getApplication<Application>()
        if (!_state.value.hasCalendarPermission) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, progress = "Reading calendars…") }

            if (relocate || _state.value.myLocation == null) {
                val loc: Location? = LocationProvider.current(ctx)
                if (loc != null) _state.update { it.copy(myLocation = LatLon(loc.latitude, loc.longitude)) }
            }

            val settings = _state.value.settings
            val (start, end) = settings.timeWindow()
            val (calendars, events) = withContext(Dispatchers.IO) {
                calendarRepo.calendars() to calendarRepo.events(start, end, settings.selectedCalendarIds)
            }
            _state.update { it.copy(calendars = calendars) }

            val visible = events.filter { settings.showAllDay || !it.allDay }
            val uniqueLocations = visible.map { it.location }.filter { it.isNotBlank() }.distinct()
            val resolved = HashMap<String, LatLon?>()
            uniqueLocations.forEachIndexed { i, loc ->
                _state.update { it.copy(progress = "Finding places ${i + 1}/${uniqueLocations.size}…") }
                resolved[loc] = geo.resolve(loc)
            }

            val me = _state.value.myLocation
            val mapped = mutableListOf<MappedEvent>()
            val unmapped = mutableListOf<CalEvent>()
            for (e in visible) {
                val p = resolved[e.location]
                if (p == null) unmapped += e else mapped += MappedEvent(e, p, me?.let { distanceKm(it, p) })
            }
            _state.update { s ->
                val keys = mapped.mapTo(HashSet()) { it.event.key }
                s.copy(
                    loading = false, progress = null, mapped = mapped, unmapped = unmapped,
                    selectedKeys = s.selectedKeys.filterTo(HashSet()) { it in keys },
                )
            }
        }
    }

    companion object {
        /** All-day events are stored at UTC midnight; shift them to local midnight. */
        fun localBegin(e: CalEvent): Long = if (!e.allDay) e.begin else utcDayToLocal(e.begin)
        fun localEnd(e: CalEvent): Long = if (!e.allDay) e.end else utcDayToLocal(e.end)

        private fun utcDayToLocal(ms: Long): Long =
            java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(systemZone).toInstant().toEpochMilli()

        fun distanceKm(a: LatLon, b: LatLon): Double {
            val out = FloatArray(1)
            Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, out)
            return out[0] / 1000.0
        }
    }
}
