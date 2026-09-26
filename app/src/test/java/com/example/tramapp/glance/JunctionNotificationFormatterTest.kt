package com.example.tramapp.glance

import com.example.tramapp.domain.junction.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class JunctionNotificationFormatterTest {
    
    private val nowMs = 1700000000000L
    private val fix = LocationFix(50.0, 14.4, 10f, nowMs)
    private val platformPos = GeoPoint(50.001, 14.401)
    
    private fun createJunction() = Junction(
        nodeId = "j1",
        name = "Kamenick\u00E1", // Kamenická
        platforms = listOf(Platform("p1", "A", platformPos, true))
    )
    
    private fun createSelection() = JunctionSelection.Selected(
        junction = createJunction(),
        distanceM = 100.0,
        ranked = emptyList(),
        cycleIndex = 0,
        fix = fix
    )
    
    private fun createTram(line: String, mins: Int, isCancelled: Boolean = false, delayMinutes: Int? = null): TramDeparture {
        return TramDeparture(
            tripId = "t1",
            line = line,
            headsign = "End",
            departureEpochMs = nowMs + mins * 60_000L,
            isAtStop = mins == 0,
            delayMinutes = delayMinutes,
            isCancelled = isCancelled,
            isAccessible = true,
            isAirConditioned = false
        )
    }

    private fun createRow(label: String = "Strossmayerovo n\u00E1m\u011Bst\u00ED", trams: List<TramDeparture> = emptyList(), highlights: Set<Destination> = emptySet(), isPlatformFirstRow: Boolean = true): JunctionRow {
        return JunctionRow(
            platformStopId = "p1",
            platformLetter = "A",
            platformPosition = platformPos,
            nextStopId = "n1",
            label = label,
            isResolved = true,
            trams = trams,
            isPlatformFirstRow = isPlatformFirstRow,
            highlights = highlights
        )
    }

    @Test
    fun `1 AE6 8 rows results in 5 row lines and overflow text`() {
        val rows = (1..8).map { createRow("Row $it") }
        val snapshot = JunctionSnapshot(createJunction(), rows, nowMs, SnapshotState.LIVE)
        
        val content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, 0.0, true, nowMs)
        )
        
        assertEquals(5, content.lines.size)
        assertEquals("+3 more directions", content.overflowText)
    }

    @Test
    fun `2 AE7 screenOn=false or headingDeg=null omits arrow, screenOn=true with heading includes arrow`() {
        val row = createRow(isPlatformFirstRow = true)
        val snapshot = JunctionSnapshot(createJunction(), listOf(row), nowMs, SnapshotState.LIVE)
        
        // screenOn = false
        var content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, headingDeg = 0.0, screenOn = false, nowMs = nowMs)
        )
        var firstLineText = content.lines[0].text
        assertTrue("Distance present", firstLineText.contains("m "))
        val arrowStrings = listOf("\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196")
        
        val hasArrowBeforePlatform = content.lines[0].segments.indexOfFirst { it.text.trim() in arrowStrings } < content.lines[0].segments.indexOfFirst { it.style == SegmentStyle.MUTED }
        assertFalse("Arrow absent", hasArrowBeforePlatform)

        // headingDeg = null
        content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, headingDeg = null, screenOn = true, nowMs = nowMs)
        )
        val hasArrowBeforePlatform2 = content.lines[0].segments.indexOfFirst { it.text.trim() in arrowStrings } < content.lines[0].segments.indexOfFirst { it.style == SegmentStyle.MUTED }
        assertFalse("Arrow absent", hasArrowBeforePlatform2)

        // screenOn = true + heading
        content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, headingDeg = 0.0, screenOn = true, nowMs = nowMs)
        )
        val arrowIndex = content.lines[0].segments.indexOfFirst { it.text.trim() in arrowStrings }
        val mutedIndex = content.lines[0].segments.indexOfFirst { it.style == SegmentStyle.MUTED }
        assertTrue("Arrow present", arrowIndex < mutedIndex && arrowIndex != -1)
    }

    @Test
    fun `3 Chip formatting rules`() {
        var chip = JunctionNotificationFormatter.chipText(listOf(createRow(trams = listOf(createTram("8", 1)))), nowMs)
        assertEquals("8\u00B71m", chip)
        
        chip = JunctionNotificationFormatter.chipText(listOf(createRow(trams = listOf(createTram("8", 0)))), nowMs)
        assertEquals("8\u00B7now", chip)
        
        chip = JunctionNotificationFormatter.chipText(listOf(
            createRow(trams = listOf(
                createTram("9", 0, isCancelled = true),
                createTram("8", 1)
            ))
        ), nowMs)
        assertEquals("8\u00B71m", chip)
        
        chip = JunctionNotificationFormatter.chipText(listOf(createRow(trams = listOf(createTram("1234567", 1)))), nowMs)
        assertEquals("1234567", chip)
        
        chip = JunctionNotificationFormatter.chipText(listOf(createRow(trams = listOf(createTram("123456", 1)))), nowMs)
        assertEquals("123456", chip)
    }

    @Test
    fun `4 Cancelled tram and delay formatting`() {
        val row = createRow(trams = listOf(
            createTram("8", 2, isCancelled = true),
            createTram("9", 3, delayMinutes = 3)
        ))
        val snapshot = JunctionSnapshot(createJunction(), listOf(row), nowMs, SnapshotState.LIVE)
        val content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, 0.0, true, nowMs)
        )
        val segments = content.lines[0].segments
        assertTrue("Contains cancelled segment", segments.any { it.style == SegmentStyle.CANCELLED && it.text == " \u2715" })
        assertTrue("Contains delay segment", segments.any { it.style == SegmentStyle.MUTED && it.text == " +3" })
    }

    @Test
    fun `5 R12 line numbers and platform letters are emitted as LINE and PLATFORM segments`() {
        val row = createRow(trams = listOf(createTram("8", 1)))
        val snapshot = JunctionSnapshot(createJunction(), listOf(row), nowMs, SnapshotState.LIVE)
        val content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, 0.0, true, nowMs)
        )
        val segments = content.lines[0].segments
        assertTrue(segments.any { it.style == SegmentStyle.PLATFORM && it.text == "A" })
        assertTrue(segments.any { it.style == SegmentStyle.LINE && it.text == "8" })
    }

    @Test
    fun `6 R8 NoneInRange formatting`() {
        val content = JunctionNotificationFormatter.format(
            FormatterInput(JunctionSelection.NoneInRange(null), null, null, true, nowMs)
        )
        assertEquals("No tram stop nearby", content.statusLine)
        assertTrue(content.lines.isEmpty())
        assertEquals("Tram glance", content.title)
    }

    @Test
    fun `7 Loading formatting`() {
        val content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), JunctionSnapshot(createJunction(), null, nowMs, SnapshotState.LOADING), null, true, nowMs)
        )
        assertEquals("Loading times\u2026", content.statusLine)
        assertEquals("Kamenick\u00E1", content.title)
        assertTrue(content.lines.isEmpty())
    }

    @Test
    fun `8 Highlighted row formatting and non-first row lacks distance`() {
        val row1 = createRow(isPlatformFirstRow = true)
        val row2 = createRow(isPlatformFirstRow = false, highlights = setOf(Destination.HOME))
        val snapshot = JunctionSnapshot(createJunction(), listOf(row1, row2), nowMs, SnapshotState.LIVE)
        val content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), snapshot, 0.0, true, nowMs)
        )
        
        val row2Segments = content.lines[1].segments
        assertTrue(row2Segments.any { it.text == "\u25CF " && it.style == SegmentStyle.PLAIN })
        assertTrue(row2Segments.any { it.style == SegmentStyle.HIGHLIGHT_LABEL })
        assertFalse(row2Segments.any { it.text.contains("m ") && it.style == SegmentStyle.MUTED })
    }

    @Test
    fun `9 Stale and error status lines`() {
        val fetchedTimeStr = Instant.ofEpochMilli(nowMs - 10 * 60_000L).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        
        val row = createRow()
        val errorSnapshot = JunctionSnapshot(createJunction(), listOf(row), nowMs - 10 * 60_000L, SnapshotState.ERROR)
        var content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), errorSnapshot, null, true, nowMs)
        )
        assertEquals("Offline \u00B7 times from $fetchedTimeStr", content.statusLine)
        
        val staleSnapshot = JunctionSnapshot(createJunction(), listOf(row), nowMs - 10 * 60_000L, SnapshotState.LIVE)
        content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), staleSnapshot, null, true, nowMs)
        )
        assertEquals("Times from $fetchedTimeStr", content.statusLine)
        
        val emptyLiveSnapshot = JunctionSnapshot(createJunction(), emptyList(), nowMs, SnapshotState.LIVE)
        content = JunctionNotificationFormatter.format(
            FormatterInput(createSelection(), emptyLiveSnapshot, null, true, nowMs)
        )
        assertEquals("No trams in the next hour", content.statusLine)
        
        val extraWarningInput = FormatterInput(createSelection(), emptyLiveSnapshot, null, true, nowMs, "Battery low")
        content = JunctionNotificationFormatter.format(extraWarningInput)
        assertEquals("No trams in the next hour \u00B7 Battery low", content.statusLine)
    }
}
