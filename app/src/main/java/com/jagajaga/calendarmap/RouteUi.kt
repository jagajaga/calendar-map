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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PlanningBar(
    selected: Int,
    routing: Boolean,
    onCancel: () -> Unit,
    onBuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.padding(12.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (selected == 0) "Tap pins (or tick events in the list) to pick what you want to attend"
                else "$selected of up to ${RoutePlanner.MAX_STOPS} events picked",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                if (routing) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Planning…")
                } else {
                    Button(onClick = onBuild, enabled = selected > 0, modifier = Modifier.testTag("buildRouteButton")) { Text("Build route") }
                }
            }
        }
    }
}

@Composable
fun RouteBar(route: RouteResult, onShow: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.padding(12.dp).fillMaxWidth().clickable(onClick = onShow)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(summary(route), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onShow) { Text("Details") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

private fun summary(route: RouteResult): String {
    val n = route.plan.visits.size
    val total = route.events.size
    val stops = if (n == total) "$n event${if (n == 1) "" else "s"}" else "$n of $total events"
    val free = route.plan.freeBetweenMs
    val freeText = if (free >= FreeTime.MIN_SHOWN_MS) " · ${TimeFormat.duration(free)} free" else ""
    return "$stops · ${TimeFormat.duration(route.plan.totalTravelMs)} ${route.mode.label.lowercase()}$freeText"
}

@Composable
fun RouteSheet(
    route: RouteResult,
    settings: AppSettings,
    hasLocation: Boolean,
    routing: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onFocus: (LatLon) -> Unit,
) {
    val context = LocalContext.current
    val plan = route.plan
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            modifier = Modifier.testTag("routeSheet"),
        ) {
            item {
                Text("Your route", style = MaterialTheme.typography.titleLarge)
                Text(summary(route), style = MaterialTheme.typography.bodyMedium)
                if (route.estimated) {
                    Text(
                        "Routing service unreachable: times are straight-line estimates (dashed line).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = TravelMode.entries,
                    selected = settings.travelMode,
                    label = { it.label },
                    onSelect = { m -> onChange { it.copy(travelMode = m) } },
                )
                Text("Stay at each event at least", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                ChipRow(
                    options = STAY_OPTIONS,
                    selected = settings.stayMinutes,
                    label = { m -> if (m == null) "Whole event" else TimeFormat.duration(m * 60_000L) },
                    onSelect = { m -> onChange { it.copy(stayMinutes = m) } },
                )
                if (hasLocation) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Start from my location", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = settings.routeFromMyLocation,
                            onCheckedChange = { v -> onChange { it.copy(routeFromMyLocation = v) } },
                        )
                    }
                }
                if (routing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
            }

            if (plan.visits.isEmpty()) {
                item {
                    Text(
                        "None of the picked events can be reached in time. Try a shorter stay or another way to travel.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            plan.departAt?.let { depart ->
                if (plan.visits.isNotEmpty()) {
                    item {
                        val first = route.events[plan.visits[0].stop].event.title
                        plan.gaps.firstOrNull { it.beforeVisit == 0 }
                            ?.let { FreeTime.describe(it, first, atStart = true, zoneId = systemZone.id, clock = TimeFormat::clock) }
                            ?.let { FreeTimeNote(it) }
                        val now = System.currentTimeMillis()
                        Text(
                            if (depart <= now + 60_000) "Leave now from your location"
                            else "Leave your location by ${TimeFormat.clock(depart)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            items(plan.visits.size) { k ->
                val v = plan.visits[k]
                val m = route.events[v.stop]
                if (k > 0) {
                    plan.gaps.firstOrNull { it.beforeVisit == k }
                        ?.let { FreeTime.describe(it, m.event.title, atStart = false, zoneId = systemZone.id, clock = TimeFormat::clock) }
                        ?.let { FreeTimeNote(it) }
                }
                val prevDay = if (k == 0) TimeFormat.localDate(System.currentTimeMillis())
                else TimeFormat.localDate(plan.visits[k - 1].stayFrom)
                if (TimeFormat.localDate(v.stayFrom) != prevDay) {
                    Text(
                        TimeFormat.dayTitle(v.stayFrom),
                        Modifier.padding(top = 12.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (k > 0 || route.start != null) {
                    Text(
                        "↓  ${TimeFormat.duration(v.travelMs)} ${route.mode.label.lowercase()}",
                        Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                StopCard(k + 1, m, v, onClick = { onFocus(m.point) })
                if (k == plan.visits.size - 1) {
                    Text(
                        "Last event ends at ${TimeFormat.clock(MainViewModel.localEnd(m.event))}.",
                        Modifier.padding(start = 12.dp, top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (plan.missed.isNotEmpty()) {
                item {
                    Text(
                        "Can't fit in (${plan.missed.size})",
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(plan.missed) { i ->
                    val e = route.events[i].event
                    Text(
                        "${e.title} · ${TimeFormat.pinLabel(e)}",
                        Modifier.fillMaxWidth().clickable { onFocus(route.events[i].point) }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                if (plan.visits.isNotEmpty()) {
                    Button(onClick = { Actions.openRouteInGoogleMaps(context, route) }, Modifier.fillMaxWidth()) {
                        Text("Open route in Google Maps")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, Modifier.weight(1f)) { Text("Change events") }
                    FilledTonalButton(onClick = onClear, Modifier.weight(1f)) { Text("Clear route") }
                }
            }
        }
    }
}

@Composable
private fun StopCard(number: Int, m: MappedEvent, v: PlanVisit, onClick: () -> Unit) {
    val e = m.event
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(28.dp).background(Color(e.color or 0xFF000000.toInt()), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text("Event: ${TimeFormat.pinLabel(e)}", style = MaterialTheme.typography.bodySmall)
                val begin = MainViewModel.localBegin(e)
                val arriveText = when {
                    v.arrive > begin -> "Arrive ${TimeFormat.clock(v.arrive)} (${TimeFormat.duration(v.arrive - begin)} after it starts)"
                    else -> "Arrive ${TimeFormat.clock(v.arrive)}"
                }
                Text(arriveText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Leave by ${TimeFormat.clock(v.leaveBy)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (e.location.isNotBlank()) {
                    Text(e.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
private fun FreeTimeNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("☕", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
