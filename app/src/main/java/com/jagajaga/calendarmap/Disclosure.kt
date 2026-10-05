package com.jagajaga.calendarmap

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object Links {
    const val PRIVACY_POLICY = "https://jagajaga.me/calendarmap/"
    const val SOURCE_CODE = "https://github.com/jagajaga/calendar-map"
}

/**
 * Google Play's User Data policy wants a prominent, in-app explanation of what
 * personal data leaves the device, shown before the runtime permission prompts.
 */
object Disclosure {
    /** Bump when the disclosed data flows change, so users see the new text. */
    private const val VERSION = 1
    private const val KEY = "disclosure_accepted_version"

    fun isAccepted(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(KEY, 0) >= VERSION

    fun accept(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt(KEY, VERSION).apply()
}

@Composable
fun DisclosureScreen(onContinue: () -> Unit, onOpenPolicy: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Before you start", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Calendar Map shows your calendar events on a map. Here is what it uses and where data goes.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Point(
                "Your calendar",
                "Read on this phone to find each event's time and place. Event titles, descriptions and " +
                    "guests never leave your device.",
            )
            Point(
                "Your location",
                "Used on this phone to measure how far events are and as the starting point of a route. " +
                    "When you plan a route that starts from your location, its coordinates are sent to the " +
                    "routing service below.",
            )
            Point(
                "Sent over the internet",
                "• Event location text (an address or place name) goes to your phone's built-in geocoding " +
                    "service (usually Google's) to find it on the map.\n" +
                    "• The map area you view is loaded from OpenStreetMap's tile servers.\n" +
                    "• Coordinates of the events in a route (and your start point) go to the OpenStreetMap " +
                    "routing service run by FOSSGIS (routing.openstreetmap.de).",
            )
            Point(
                "Never",
                "No accounts, ads, analytics or tracking. The developer receives no data from the app.",
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text("Continue") }
            TextButton(onClick = onOpenPolicy, Modifier.fillMaxWidth()) { Text("Read the privacy policy") }
        }
    }
}

@Composable
private fun Point(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
