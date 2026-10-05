package com.jagajaga.calendarmap

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the app in demo mode (made-up San Francisco events) and saves
 * store-listing screenshots to the app's external files dir, where CI pulls them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outDir: File by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    @Test
    fun storeScreenshots() {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra(Demo.EXTRA, true)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntilAtLeastOneExists(hasTestTag("planRouteButton"), 20_000)
            Thread.sleep(8_000) // map tiles
            shot("01-map")

            compose.onNodeWithTag("listButton").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("eventList"), 10_000)
            Thread.sleep(1_500)
            shot("02-list")
            back()

            compose.onNodeWithTag("planRouteButton").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("buildRouteButton"), 10_000)
            compose.onNodeWithTag("listButton").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("eventList"), 10_000)
            for (title in listOf("Founders Coffee", "Design Systems Meetup", "AI Demo Night", "Open Source Hack Night")) {
                compose.onNodeWithTag("eventList").performScrollToNode(hasTestTag("row-$title"))
                compose.onNodeWithTag("row-$title").performClick()
            }
            Thread.sleep(800)
            shot("03-pick-events")
            back()

            compose.onNodeWithTag("buildRouteButton").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("routeSheet"), 60_000)
            Thread.sleep(2_500)
            shot("04-route-itinerary")
            back()
            Thread.sleep(5_000) // route line and tiles
            shot("05-route-map")

            compose.onNodeWithTag("listButton").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("eventList"), 10_000)
            compose.onNodeWithTag("eventList").performScrollToNode(hasTestTag("row-AI Demo Night"))
            compose.onNodeWithTag("row-AI Demo Night").performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("placeSheet"), 10_000)
            Thread.sleep(2_000)
            shot("06-event-details")
            back()

            compose.onNodeWithTag("settingsButton").performClick()
            Thread.sleep(2_000)
            shot("07-settings")
        }
    }

    private fun back() {
        Espresso.pressBack()
        Thread.sleep(1_200)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
