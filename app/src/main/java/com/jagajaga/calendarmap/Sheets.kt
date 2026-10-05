@file:OptIn(ExperimentalMaterial3Api::class)

package com.jagajaga.calendarmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import kotlin.math.roundToInt

// ---------- Event details ----------

@Composable
fun PlaceSheet(
    place: Place,
    planning: Boolean,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag("placeSheet"),
        ) {
            if (planning) {
                item { Text("Pick events for your route", style = MaterialTheme.typography.titleMedium) }
            } else if (place.events.size > 1) {
                item {
                    Text("${place.events.size} events here", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(place.events, key = { it.key }) { ev ->
                if (planning) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(ev.key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ev.key in selectedKeys, onCheckedChange = { onToggle(ev.key) })
                        Column(Modifier.weight(1f)) {
                            Text(ev.title, style = MaterialTheme.typography.bodyLarge)
                            Text(TimeFormat.pinLabel(ev), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    EventCard(ev, place.point, place.distanceKm)
                }
            }
        }
    }
}

@Composable
fun EventCard(event: CalEvent, point: LatLon?, distanceKm: Double?) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(event.color)
                Spacer(Modifier.width(8.dp))
                Text(event.calendarName, style = MaterialTheme.typography.bodyMedium)
            }
            LabeledValue("Starts", TimeFormat.startFull(event))
            LabeledValue("Ends", TimeFormat.endFull(event))
            if (event.location.isNotBlank()) {
                LabeledValue(
                    "Where",
                    event.location + (distanceKm?.let { " (%.1f km away)".format(it) } ?: ""),
                )
            }
            val description = remember(event.description) { plainText(event.description) }
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.location.isNotBlank() || point != null) {
                    FilledTonalButton(onClick = { Actions.openInGoogleMaps(context, event, point) }) {
                        Text("Open in Google Maps")
                    }
                }
                OutlinedButton(onClick = { Actions.openInCalendar(context, event) }) { Text("Calendar") }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row {
        Text(label, Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColorDot(color: Int) {
    Box(Modifier.size(12.dp).background(Color(color or 0xFF000000.toInt()), CircleShape))
}

private fun plainText(s: String): String =
    if ('<' in s) HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() else s.trim()

// ---------- Event list ----------

@Composable
fun EventListSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onPick: (MappedEvent) -> Unit,
    onPickUnmapped: (CalEvent) -> Unit,
    onToggle: (String) -> Unit,
) {
    val shown = state.shown.sortedBy { it.event.begin }
    val shownKeys = shown.mapTo(HashSet()) { it.event.key }
    val hidden = state.mapped.filter { it.event.key !in shownKeys }.sortedBy { it.event.begin }
    val visibleMode = state.settings.areaMode == AreaMode.VISIBLE_MAP
    val planning = state.planning
    val selected = state.selectedKeys
    val onRow = { m: MappedEvent -> if (planning) onToggle(m.event.key) else onPick(m) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.testTag("eventList")) {
            if (planning) {
                item { SectionHeader("Tick events for your route (${selected.size}/${RoutePlanner.MAX_STOPS})") }
            }
            item { SectionHeader((if (visibleMode) "In view" else "On the map") + " (${shown.size})") }
            if (shown.isEmpty()) item { EmptyRow("No events with a place here.") }
            items(shown, key = { "in" + it.event.key }) { m ->
                EventRow(m.event, m.distanceKm, checked = if (planning) m.event.key in selected else null) { onRow(m) }
            }
            if (hidden.isNotEmpty()) {
                item {
                    SectionHeader(
                        (if (visibleMode) "Off-screen" else "Farther than ${state.settings.radiusKm} km") + " (${hidden.size})",
                    )
                }
                items(hidden, key = { "out" + it.event.key }) { m ->
                    EventRow(m.event, m.distanceKm, dim = !planning, checked = if (planning) m.event.key in selected else null) { onRow(m) }
                }
            }
            if (state.unmapped.isNotEmpty()) {
                item { SectionHeader("No place on the map (${state.unmapped.size})") }
                items(state.unmapped, key = { "un" + it.key }) { e -> EventRow(e, null, dim = true) { onPickUnmapped(e) } }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun EventRow(
    event: CalEvent,
    distanceKm: Double?,
    dim: Boolean = false,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val alpha = if (dim) 0.6f else 1f
    Row(
        Modifier.fillMaxWidth().testTag("row-${event.title}").clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
        ColorDot(event.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                TimeFormat.pinLabel(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
            if (event.location.isNotBlank()) {
                Text(
                    event.location + (distanceKm?.let { " · %.0f km".format(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}

// ---------- Settings ----------

@Composable
fun SettingsSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onRadiusCommitted: (Int) -> Unit,
    onClearCache: () -> Unit,
    onGrantLocation: () -> Unit,
) {
    val settings = state.settings
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text("Show events", style = MaterialTheme.typography.titleMedium)
                AreaMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange { it.copy(areaMode = mode) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.areaMode == mode, onClick = { onChange { it.copy(areaMode = mode) } })
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (settings.areaMode == AreaMode.RADIUS) {
                    RadiusEditor(settings.radiusKm, onChange = { r -> onChange { it.copy(radiusKm = r) } }, onCommitted = onRadiusCommitted)
                }
                if (settings.areaMode == AreaMode.RADIUS && !state.hasLocationPermission) {
                    Text(
                        "Location permission is off, so the radius can't be applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onGrantLocation) { Text("Grant location access") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show all-day events", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = settings.showAllDay, onCheckedChange = { v -> onChange { it.copy(showAllDay = v) } })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Calendars", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onChange { it.copy(selectedCalendarIds = null) } }) { Text("All") }
                    TextButton(onClick = { onChange { it.copy(selectedCalendarIds = emptySet()) } }) { Text("None") }
                }
            }
            val byAccount = state.calendars.groupBy { it.account }
            byAccount.forEach { (account, cals) ->
                item(key = "acct-$account") {
                    Text(account, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(cals, key = { "cal-${it.id}" }) { cal ->
                    val checked = settings.selectedCalendarIds?.contains(cal.id) ?: true
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange { it.toggleCalendar(cal.id, state.calendars) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onChange { it.toggleCalendar(cal.id, state.calendars) } })
                        ColorDot(cal.color)
                        Spacer(Modifier.width(8.dp))
                        Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(onClick = onClearCache) { Text("Re-look-up all event places") }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                AboutSection()
            }
        }
    }
}

private fun AppSettings.toggleCalendar(id: Long, all: List<CalendarInfo>): AppSettings {
    val current = selectedCalendarIds ?: all.map { it.id }.toSet()
    val next = if (id in current) current - id else current + id
    return copy(selectedCalendarIds = if (next == all.map { it.id }.toSet()) null else next)
}

@Composable
private fun RadiusEditor(radiusKm: Int, onChange: (Int) -> Unit, onCommitted: (Int) -> Unit) {
    // Slider is logarithmic so both 2 km and 500 km are easy to hit.
    var sliderPos by remember { mutableFloatStateOf(kmToPos(radiusKm)) }
    var text by remember { mutableStateOf(radiusKm.toString()) }
    LaunchedEffect(radiusKm) {
        if (posToKm(sliderPos) != radiusKm) sliderPos = kmToPos(radiusKm)
        if (text.toIntOrNull() != radiusKm) text = radiusKm.toString()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = sliderPos,
            onValueChange = {
                sliderPos = it
                onChange(posToKm(it))
            },
            onValueChangeFinished = { onCommitted(posToKm(sliderPos)) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter(Char::isDigit).take(5)
                text.toIntOrNull()?.takeIf { it in MIN_KM..MAX_KM }?.let {
                    onChange(it)
                    onCommitted(it)
                }
            },
            suffix = { Text("km") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
        )
    }
}

private const val MIN_KM = 1
private const val MAX_KM = 20000
private const val SLIDER_MAX_KM = 1000.0

private fun kmToPos(km: Int): Float =
    (kotlin.math.ln(km.coerceIn(MIN_KM, SLIDER_MAX_KM.toInt()).toDouble()) / kotlin.math.ln(SLIDER_MAX_KM)).toFloat()

private fun posToKm(pos: Float): Int {
    val raw = Math.pow(SLIDER_MAX_KM, pos.toDouble())
    val step = when {
        raw < 10 -> 1.0
        raw < 100 -> 5.0
        else -> 25.0
    }
    return ((raw / step).roundToInt() * step).toInt().coerceIn(MIN_KM, SLIDER_MAX_KM.toInt())
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    }
    Text("About & privacy", style = MaterialTheme.typography.titleMedium)
    Text("Calendar Map $version · open source (MIT)", style = MaterialTheme.typography.bodySmall)
    Row {
        TextButton(onClick = { Actions.openUrl(context, Links.PRIVACY_POLICY) }) { Text("Privacy policy") }
        TextButton(onClick = { Actions.openUrl(context, Links.SOURCE_CODE) }) { Text("Source code") }
    }
    Text(
        "Map data © OpenStreetMap contributors. Routing by OSRM on routing.openstreetmap.de (FOSSGIS).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
