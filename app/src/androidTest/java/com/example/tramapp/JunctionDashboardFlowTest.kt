package com.example.tramapp

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.rule.GrantPermissionRule
import com.example.tramapp.util.ScreenshotUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * U10 (R4, R9, R22, R23): with the device location near Kamenická (set beforehand via
 * `adb -s emulator-5554 emu geo fix 14.4292 50.1003`, or a test location provider), the
 * dashboard shows a junction card with platform rows and next-stop labels within the batched
 * fetch + resolver round trip.
 */
class JunctionDashboardFlowTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Pre-grants location AND notification permissions so MainActivity's runtime permission
    // dialog never steals window focus from the Activity (same gotcha as DashboardTrustFlowTest) —
    // otherwise ComposeTestRule can't find any compose hierarchy while GrantPermissionsActivity
    // is on top, which reads as "No compose hierarchies found in the app".
    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ))
        .around(composeRule)

    @Test
    fun junctionCardShowsPlatformRowsAndNextStopLabels() {
        composeRule.waitForIdle()
        ScreenshotUtil.capture(composeRule, "junction-dashboard-loading")

        // Stage 1: the junction card itself (proven reliable in DashboardTrustFlowTest) —
        // structure appears immediately, before any network call completes.
        composeRule.waitUntil(timeoutMillis = 25000) {
            composeRule.onAllNodesWithTag("junction-card").fetchSemanticsNodes().isNotEmpty()
        }

        // Stage 2: rows fill in once the batched fetch + resolver round trip completes. Polled
        // with plain SystemClock waits (rather than a single long waitUntil over the row tag) —
        // the row subtree embeds a GoogleMap sibling in the same screen whose own internal
        // invalidations can keep Compose's idling resource from settling for a single very long
        // wait; short bounded polls give it repeated chances to reach an idle frame instead.
        var found = false
        var foundUnmerged = false
        val deadline = System.currentTimeMillis() + 25000
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithTag("junction-row").fetchSemanticsNodes().isNotEmpty()) {
                found = true
                break
            }
            if (composeRule.onAllNodesWithTag("junction-row", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                foundUnmerged = true
                break
            }
            android.os.SystemClock.sleep(500)
        }

        composeRule.onAllNodesWithTag("junction-card").onFirst().assertExists()
        ScreenshotUtil.capture(composeRule, "junction-dashboard")
        if (!found && !foundUnmerged) {
            composeRule.onRoot(useUnmergedTree = true).printToLog("JDFTDebug")
        }
        assert(found || foundUnmerged) {
            "No junction-row appeared within 25s despite a junction-card being shown."
        }
        if (found) {
            composeRule.onAllNodesWithTag("junction-row").onFirst().assertExists()
            composeRule.onAllNodesWithTag("junction-row-label").onFirst().assertExists()
        } else {
            composeRule.onAllNodesWithTag("junction-row", useUnmergedTree = true).onFirst().assertExists()
            composeRule.onAllNodesWithTag("junction-row-label", useUnmergedTree = true).onFirst().assertExists()
        }
    }
}
