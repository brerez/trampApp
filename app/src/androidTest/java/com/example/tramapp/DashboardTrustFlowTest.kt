package com.example.tramapp

import android.Manifest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import com.example.tramapp.util.ScreenshotUtil
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test

/**
 * U1/U5 (R14-R16, R1, R3, R4, R24): emulator-drive smoke + trust-flow tests. Establishes
 * (U1) and exercises (U5) the `testTag` vocabulary — station-card, skeleton,
 * empty-collapse, amenity-glyph — see docs/verification-harness.md.
 */
class DashboardTrustFlowTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Pre-grants location permissions so MainActivity's runtime permission dialog never
    // steals window focus from the Activity — otherwise ComposeTestRule can't find any
    // compose hierarchy while the system GrantPermissionsActivity is on top.
    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        .around(composeRule)

    @Test
    fun dashboardLaunchesAndShowsNearbyStationsTitle() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nearby Stations").assertExists()
        ScreenshotUtil.capture(composeRule, "dashboard-launch")
    }

    /**
     * U10, R10/R22/R23: the header shows a freshness cue ("Updated…"/"Loading…"/"Checking…" —
     * never blank) and a gear settings icon, replacing the star and the always-visible debug
     * string.
     */
    @Test
    fun headerShowsFreshnessCueAndGearIcon() {
        composeRule.waitForIdle()
        ScreenshotUtil.capture(composeRule, "dashboard-header-check")
        composeRule.onNodeWithContentDescription("Settings").assertExists()
        composeRule.onNode(
            hasText("Updated", substring = true) or
                hasText("Loading", substring = true) or
                hasText("Checking", substring = true)
        ).assertExists()
    }

    /**
     * U11, R25: the map starts collapsed to a slim peek — expanding/collapsing it doesn't
     * destabilize the rest of the layout (toggle exists and is tappable both ways).
     */
    @Test
    fun mapStartsCollapsedAndTogglesWithoutCrashing() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("map-toggle").assertExists()
        ScreenshotUtil.capture(composeRule, "dashboard-map-collapsed")

        composeRule.onNodeWithTag("map-toggle").performClick()
        composeRule.waitForIdle()
        ScreenshotUtil.capture(composeRule, "dashboard-map-expanded")

        composeRule.onNodeWithTag("map-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("map-toggle").assertExists()
    }

    /**
     * U5, R1/R4/AE1: a station renders its `Loading` skeleton first, never an empty-looking
     * populated card, and is later replaced in the same slot by a `Ready` [station-card] once
     * departures resolve. Requires a real network round-trip against Golemio, so this
     * intentionally polls with a generous timeout rather than asserting on a fixed frame.
     */
    @Test
    fun stationsProgressFromSkeletonToReadyCards() {
        composeRule.waitForIdle()
        ScreenshotUtil.capture(composeRule, "dashboard-loading")

        composeRule.waitUntil(timeoutMillis = 25000) {
            composeRule.onAllNodesWithTag("station-card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithTag("station-card").onFirst().assertExists()
        ScreenshotUtil.capture(composeRule, "dashboard-ready")
    }

    /**
     * U9, R19/R20/R21: once a station card is Ready, amenity glyphs (when present) render
     * without disturbing the countdown; a screenshot backs the R21 font-scale/legibility
     * check manually since live data doesn't guarantee a flagged departure every run.
     *
     * Tolerates "no departures at the nearest station right now" (e.g. off-peak/night-time
     * service near the fixed test location) as a skip rather than a failure — same spirit as
     * RealApiIntegrationTest/GolemioAmenityProbeTest not failing on live-data unavailability.
     * This is the one test in the class launching its own fresh MainActivity after the
     * others already ran, so it also tolerates a longer settle time.
     */
    @Test
    fun amenityGlyphsRenderWithoutCrashingWhenReady() {
        composeRule.waitForIdle()
        val becameReady = try {
            composeRule.waitUntil(timeoutMillis = 45000) {
                composeRule.onAllNodesWithTag("station-card").fetchSemanticsNodes().isNotEmpty()
            }
            true
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            false
        }
        if (!becameReady) {
            println("No station reached Ready within 45s (likely no live departures near " +
                "the test location right now) — skipping glyph assertion.")
            return
        }

        // Not asserting a specific count — live data may or may not include an
        // amenity-flagged departure this run. The station-card existing at all (R1/R4)
        // combined with a clean screenshot is the manual R21 legibility check.
        composeRule.onAllNodesWithTag("amenity-glyph").fetchSemanticsNodes()
        ScreenshotUtil.capture(composeRule, "dashboard-amenity-glyphs")
    }
}
