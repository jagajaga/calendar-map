@file:OptIn(ExperimentalMaterial3Api::class)

package com.jagajaga.calendarmap

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        asked = true
        vm.onPermissionsChanged()
    }
    var disclosed by remember { mutableStateOf(Demo.enabled || Disclosure.isAccepted(context)) }
    LaunchedEffect(disclosed) {
        vm.onPermissionsChanged()
        val missing = !vm.state.value.hasCalendarPermission || !vm.state.value.hasLocationPermission
        if (disclosed && missing && !Demo.enabled) permissionLauncher.launch(MainActivity.PERMISSIONS)
    }
    if (!disclosed) {
        DisclosureScreen(
            onContinue = {
                Disclosure.accept(context)
                disclosed = true
            },
            onOpenPolicy = { Actions.openUrl(context, Links.PRIVACY_POLICY) },
        )
        return
    }

    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var showList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRouteSheet by remember { mutableStateOf(false) }
    var cameraSeq by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf(CameraRequest(id = 0)) }
    fun moveCamera(
        center: LatLon? = null,
        fitRadiusKm: Int? = null,
        fitPoints: List<LatLon>? = null,
        zoom: Double? = null,
    ) {
        cameraSeq++
        camera = CameraRequest(cameraSeq, center, fitRadiusKm, fitPoints, zoom)
    }
    fun frameMyArea() = when (state.settings.areaMode) {
        AreaMode.RADIUS -> moveCamera(fitRadiusKm = state.settings.radiusKm)
        AreaMode.VISIBLE_MAP -> moveCamera(zoom = 14.0)
    }
    fun toggle(key: String) {
        if (!vm.toggleSelected(key)) {
            Toast.makeText(context, "A route can have at most ${RoutePlanner.MAX_STOPS} events", Toast.LENGTH_SHORT).show()
        }
    }

    // Frame the user's area once we first learn where they are.
    var framedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(state.myLocation) {
        if (state.myLocation != null && !framedOnce) {
            framedOnce = true
            frameMyArea()
        }
    }
    // When a route arrives, show it whole and open its itinerary.
    val route = state.route
    LaunchedEffect(route) {
        if (route != null) {
            moveCamera(fitPoints = route.path.ifEmpty { route.events.map { it.point } })
            showRouteSheet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar Map") },
                actions = {
                    IconButton(onClick = { showList = true }, modifier = Modifier.testTag("listButton")) { Icon(Icons.AutoMirrored.Filled.List, "Event list") }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.testTag("settingsButton")) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (!state.planning && route == null && state.hasCalendarPermission) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.myLocation != null) {
                        SmallFloatingActionButton(onClick = { frameMyArea() }) {
                            Icon(Icons.Default.LocationOn, "Show my area")
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { vm.startPlanning() },
                        modifier = Modifier.testTag("planRouteButton"),
                        icon = { Icon(Icons.Default.Place, null) },
                        text = { Text("Plan route") },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.hasCalendarPermission) {
                PermissionPrompt(
                    asked = asked,
                    onGrant = { permissionLauncher.launch(MainActivity.PERMISSIONS) },
                    onOpenSettings = { Actions.openAppSettings(context) },
                )
                return@Column
            }

            // osmdroid's MapView paints outside its bounds inside Compose, which hid
            // this bar. Clip the map and raise the bar above it so either alone holds.
            Column(
                Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                RangeChips(
                    settings = state.settings,
                    onPreset = { p ->
                        if (p == RangePreset.CUSTOM) showDatePicker = true
                        else vm.updateSettings { it.copy(preset = p) }
                    },
                )
                StatusLine(state)
            }

            Box(Modifier.fillMaxSize().clipToBounds()) {
                EventMap(
                    places = state.places,
                    myLocation = state.myLocation,
                    radiusKm = state.settings.radiusKm.takeIf { state.settings.areaMode == AreaMode.RADIUS },
                    selectedKeys = if (state.planning || state.routing) state.selectedKeys else emptySet(),
                    route = route,
                    camera = camera,
                    onPlaceClick = { place ->
                        if (state.planning && place.events.size == 1) toggle(place.events[0].key)
                        else selectedPlace = place
                    },
                    onViewportChanged = vm::setViewport,
                    modifier = Modifier.fillMaxSize(),
                )
                when {
                    state.planning || state.routing -> PlanningBar(
                        selected = state.selectedKeys.size,
                        routing = state.routing,
                        onCancel = vm::cancelPlanning,
                        onBuild = vm::buildRoute,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    route != null && !showRouteSheet -> RouteBar(
                        route = route,
                        onShow = { showRouteSheet = true },
                        onClear = vm::cancelPlanning,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    selectedPlace?.let { place ->
        PlaceSheet(
            place = place,
            planning = state.planning,
            selectedKeys = state.selectedKeys,
            onToggle = ::toggle,
            onDismiss = { selectedPlace = null },
        )
    }
    if (showList) {
        EventListSheet(
            state = state,
            onDismiss = { showList = false },
            onPick = { mapped ->
                showList = false
                moveCamera(center = mapped.point)
                selectedPlace = state.places.firstOrNull { it.point == mapped.point }
                    ?: Place(mapped.point, listOf(mapped.event), mapped.distanceKm)
            },
            onPickUnmapped = { ev -> Actions.openInCalendar(context, ev) },
            onToggle = ::toggle,
        )
    }
    if (showSettings) {
        SettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onChange = vm::updateSettings,
            onRadiusCommitted = { r -> if (state.myLocation != null) moveCamera(fitRadiusKm = r) },
            onClearCache = vm::clearGeocodeCache,
            onGrantLocation = { permissionLauncher.launch(MainActivity.PERMISSIONS) },
        )
    }
    if (showDatePicker) {
        CustomRangeDialog(
            settings = state.settings,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                showDatePicker = false
                vm.updateSettings { it.copy(preset = RangePreset.CUSTOM, customStartDay = start, customEndDay = end) }
            },
        )
    }
    if (route != null && showRouteSheet) {
        RouteSheet(
            route = route,
            settings = state.settings,
            hasLocation = state.myLocation != null,
            routing = state.routing,
            onChange = vm::updateSettings,
            onDismiss = { showRouteSheet = false },
            onEdit = {
                showRouteSheet = false
                vm.editSelection()
            },
            onClear = {
                showRouteSheet = false
                vm.cancelPlanning()
            },
            onFocus = { p ->
                showRouteSheet = false
                moveCamera(center = p)
            },
        )
    }
}

@Composable
private fun PermissionPrompt(asked: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Calendar Map needs access to your calendar to place your events on the map, " +
                "and your location to show only events nearby.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant access") }
        if (asked) {
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}

@Composable
private fun RangeChips(settings: AppSettings, onPreset: (RangePreset) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(RangePreset.entries) { p ->
            val label = if (p == RangePreset.CUSTOM && settings.preset == p && settings.customStartDay != null) {
                val s = TimeFormat.day(settings.customStartDay)
                val e = settings.customEndDay?.let(TimeFormat::day)
                if (e == null || e == s) s else "$s – $e"
            } else p.label
            FilterChip(selected = settings.preset == p, onClick = { onPreset(p) }, label = { Text(label) })
        }
    }
}

@Composable
private fun StatusLine(state: UiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.progress ?: "Loading…", style = MaterialTheme.typography.bodySmall)
            return
        }
        val parts = buildList {
            val n = state.shown.size
            fun events(k: Int) = "$k event${if (k == 1) "" else "s"}"
            add(
                when {
                    state.settings.areaMode == AreaMode.VISIBLE_MAP -> "${events(n)} in view"
                    state.myLocation != null -> "${events(n)} within ${state.settings.radiusKm} km"
                    else -> "${events(n)} (location unknown — radius off)"
                },
            )
            if (state.hidden > 0) {
                add(if (state.settings.areaMode == AreaMode.VISIBLE_MAP) "${state.hidden} off-screen" else "${state.hidden} farther away")
            }
            if (state.unmapped.isNotEmpty()) add("${state.unmapped.size} without a place")
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CustomRangeDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    // The picker works in UTC-midnight millis; convert to/from local epoch days.
    fun dayToUtcMillis(d: Long) = LocalDate.ofEpochDay(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun utcMillisToDay(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = settings.customStartDay?.let(::dayToUtcMillis),
        initialSelectedEndDateMillis = settings.customEndDay?.let(::dayToUtcMillis),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val s = utcMillisToDay(pickerState.selectedStartDateMillis!!)
                    val e = pickerState.selectedEndDateMillis?.let(::utcMillisToDay) ?: s
                    onConfirm(s, e)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = pickerState, modifier = Modifier.weight(1f), title = {
            Text("Show events between", Modifier.padding(start = 24.dp, top = 16.dp))
        })
    }
}
