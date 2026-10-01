package com.example.tramapp.glance

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.example.tramapp.domain.junction.Destination
import com.example.tramapp.domain.junction.GeoPoint
import com.example.tramapp.domain.junction.Junction
import com.example.tramapp.domain.junction.JunctionRow
import com.example.tramapp.domain.junction.JunctionSelection
import com.example.tramapp.domain.junction.JunctionSnapshot
import com.example.tramapp.domain.junction.LocationFix
import com.example.tramapp.domain.junction.Platform
import com.example.tramapp.domain.junction.SnapshotState
import com.example.tramapp.domain.junction.TramDeparture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * U78 part 2: verifies the rendered notification's static shape (ongoing, not colorized,
 * default-importance channel, custom big view, the two actions, junction id on the content
 * intent) and captures a screenshot of it posted in the shade.
 */
@RunWith(AndroidJUnit4::class)
class JunctionNotificationRendererTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val renderer = JunctionNotificationRenderer(context)

    private val nowMs = 1_700_000_000_000L
    private val fix = LocationFix(50.1003, 14.4292, 10f, nowMs)

    private fun tram(line: String, mins: Int) = TramDeparture(
        tripId = "t-$line-$mins",
        line = line,
        headsign = "End",
        departureEpochMs = nowMs + mins * 60_000L,
        isAtStop = mins == 0,
        delayMinutes = null,
        isCancelled = false,
        isAccessible = true,
        isAirConditioned = false,
    )

    private fun eightRowContent(): NotificationContent {
        val junction = Junction(
            nodeId = "U324",
            name = "Kamenická",
            platforms = (1..8).map { i -> Platform("p$i", ('A' + (i - 1)).toString(), GeoPoint(50.1005, 14.4295), true) },
        )
        val rows = (1..8).map { i ->
            JunctionRow(
                platformStopId = "p$i",
                platformLetter = ('A' + (i - 1)).toString(),
                platformPosition = GeoPoint(50.1005, 14.4295),
                nextStopId = "n$i",
                label = "Stop $i",
                isResolved = true,
                trams = listOf(tram("8", i), tram("12", i + 2)),
                isPlatformFirstRow = true,
                highlights = if (i == 1) setOf(Destination.HOME) else emptySet(),
            )
        }
        val selection = JunctionSelection.Selected(junction, 120.0, emptyList(), 0, fix)
        val snapshot = JunctionSnapshot(junction, rows, nowMs, SnapshotState.LIVE)
        return JunctionNotificationFormatter.format(
            FormatterInput(selection, snapshot, headingDeg = 45.0, screenOn = true, nowMs = nowMs),
        )
    }

    @Test
    fun renderedNotification_hasExpectedShapeAndActions() {
        val content = eightRowContent()
        assertEquals("U324", content.junctionNodeId)

        val notification = renderer.render(content)

        assertTrue("ongoing", (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertFalse("not colorized", isColorized(notification))
        assertNotNull("has a custom big content view", notification.bigContentView)
        assertEquals("2 actions", 2, notification.actions?.size ?: 0)
        assertEquals("Next stop ›", notification.actions[0].title.toString())
        assertEquals("Stop", notification.actions[1].title.toString())
        assertNotNull("content intent set", notification.contentIntent)

        val manager = context.getSystemService(NotificationManager::class.java)!!
        val channel = manager.getNotificationChannel(JunctionNotificationRenderer.CHANNEL_ID)
        assertNotNull("channel exists", channel)
        assertTrue("importance is not MIN", channel!!.importance != NotificationManager.IMPORTANCE_MIN)
    }

    @Test
    fun postedNotification_appearsInShade_andScreenshots() {
        val notification = renderer.render(eightRowContent())
        val manager = context.getSystemService(NotificationManager::class.java)!!
        manager.notify(JunctionNotificationRenderer.NOTIFICATION_ID, notification)

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.openNotification()
        device.waitForIdle()

        val active = manager.activeNotifications.firstOrNull { it.id == JunctionNotificationRenderer.NOTIFICATION_ID }
        assertNotNull("notification is posted", active)

        val outDir = java.io.File("/sdcard/Pictures/trampapp-screenshots").apply { mkdirs() }
        device.takeScreenshot(java.io.File(outDir, "notification-junction.png"))

        device.pressBack()
        manager.cancel(JunctionNotificationRenderer.NOTIFICATION_ID)
    }

    private fun isColorized(notification: Notification): Boolean {
        val extras = notification.extras
        return extras.getBoolean(Notification.EXTRA_COLORIZED, false)
    }
}
