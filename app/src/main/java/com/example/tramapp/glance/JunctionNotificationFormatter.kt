package com.example.tramapp.glance

import com.example.tramapp.domain.junction.Geo
import com.example.tramapp.domain.junction.JunctionRow
import com.example.tramapp.domain.junction.JunctionSelection
import com.example.tramapp.domain.junction.SnapshotState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object JunctionNotificationFormatter {
    const val MAX_ROWS = 5
    const val TRAMS_PER_ROW = 3
    const val CHIP_MAX_CHARS = 7
    const val STALE_AFTER_MS = 5 * 60_000L

    fun format(input: FormatterInput): NotificationContent {
        val selection = input.selection
        val snapshot = input.snapshot
        val headingDeg = input.headingDeg
        val screenOn = input.screenOn
        val nowMs = input.nowMs
        val extraWarning = input.extraWarning

        var title = "Tram glance"
        var junctionNodeId: String? = null
        var lines = emptyList<ContentLine>()
        var overflowText: String? = null
        var statusLineBase: String? = null

        if (selection is JunctionSelection.NoFix) {
            statusLineBase = "Locating\u2026"
        } else if (selection is JunctionSelection.NoneInRange) {
            statusLineBase = "No tram stop nearby"
        } else if (selection is JunctionSelection.Selected) {
            val junction = selection.junction
            title = junction.name
            junctionNodeId = junction.nodeId

            if (snapshot == null || snapshot.rows == null) {
                statusLineBase = "Loading times\u2026"
            } else {
                val rows = snapshot.rows
                
                if (rows.isNotEmpty()) {
                    val linesToProcess = rows.take(MAX_ROWS)
                    if (rows.size > MAX_ROWS) {
                        overflowText = "+${rows.size - MAX_ROWS} more directions"
                    }

                    lines = linesToProcess.map { row ->
                        val segments = mutableListOf<Segment>()
                        
                        if (row.highlights.isNotEmpty()) {
                            segments.add(Segment("\u25CF ", SegmentStyle.PLAIN))
                        }
                        
                        segments.add(Segment(row.platformLetter, SegmentStyle.PLATFORM))
                        segments.add(Segment(" ", SegmentStyle.PLAIN))
                        
                        if (row.isPlatformFirstRow) {
                            if (headingDeg != null && screenOn) {
                                val arrowStr = Geo.arrow(Geo.bearingDeg(selection.fix.point, row.platformPosition), headingDeg)
                                if (arrowStr != null) {
                                    segments.add(Segment("$arrowStr ", SegmentStyle.PLAIN))
                                }
                            }
                            val d = Geo.distanceM(selection.fix.point, row.platformPosition)
                            segments.add(Segment("${Geo.formatDistance(d)} ", SegmentStyle.MUTED))
                        }
                        
                        segments.add(Segment("\u2192 ", SegmentStyle.PLAIN))
                        segments.add(Segment(row.label, if (row.highlights.isNotEmpty()) SegmentStyle.HIGHLIGHT_LABEL else SegmentStyle.PLAIN))
                        
                        segments.add(Segment("  ", SegmentStyle.PLAIN))
                        
                        val tramsToProcess = row.trams.take(TRAMS_PER_ROW)
                        tramsToProcess.forEachIndexed { index, tram ->
                            if (index > 0) {
                                segments.add(Segment(" \u00B7 ", SegmentStyle.MUTED))
                            }
                            
                            segments.add(Segment(tram.line, SegmentStyle.LINE, line = tram.line))
                            
                            if (tram.isCancelled) {
                                segments.add(Segment(" \u2715", SegmentStyle.CANCELLED))
                            } else {
                                val mins = tram.minutesUntil(nowMs)
                                val timeText = if (mins == 0) "now" else "${mins}m"
                                segments.add(Segment(" $timeText", SegmentStyle.PLAIN))
                                
                                val delay = tram.delayMinutes
                                if (delay != null && delay != 0) {
                                    val sign = if (delay > 0) "+" else ""
                                    segments.add(Segment(" $sign$delay", SegmentStyle.MUTED))
                                }
                            }
                        }
                        ContentLine(segments)
                    }
                }

                val fetchedTime = snapshot.fetchedAtMs?.let {
                    Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                }

                if (snapshot.state == SnapshotState.ERROR) {
                    statusLineBase = "Offline \u00B7 times from $fetchedTime"
                } else if (snapshot.state == SnapshotState.LIVE && snapshot.fetchedAtMs != null && (nowMs - snapshot.fetchedAtMs > STALE_AFTER_MS)) {
                    statusLineBase = "Times from $fetchedTime"
                } else if (rows.isEmpty() && snapshot.state == SnapshotState.LIVE) {
                    statusLineBase = "No trams in the next hour"
                }
            }
        }

        val finalStatusLine = if (statusLineBase != null) {
            if (extraWarning != null) "$statusLineBase \u00B7 $extraWarning" else statusLineBase
        } else {
            extraWarning
        }

        val chip = if (selection is JunctionSelection.Selected && snapshot?.rows != null) {
            chipText(snapshot.rows, nowMs)
        } else null

        val nowBarSummary = if (selection is JunctionSelection.Selected && chip != null) {
            val soonestTram = snapshot?.rows?.flatMap { it.trams }
                ?.filter { !it.isCancelled }
                ?.minByOrNull { it.departureEpochMs }
            
            if (soonestTram != null) {
                val m = soonestTram.minutesUntil(nowMs)
                if (m == 0) "${selection.junction.name} \u00B7 ${soonestTram.line} now"
                else "${selection.junction.name} \u00B7 ${soonestTram.line} in $m min"
            } else {
                finalStatusLine ?: selection.junction.name
            }
        } else {
            finalStatusLine ?: title
        }

        return NotificationContent(
            title = title,
            lines = lines,
            overflowText = overflowText,
            chipText = chip,
            nowBarSummary = nowBarSummary,
            statusLine = finalStatusLine,
            junctionNodeId = junctionNodeId
        )
    }

    fun chipText(rows: List<JunctionRow>, nowMs: Long): String? {
        val soonestTram = rows.flatMap { it.trams }
            .filter { !it.isCancelled }
            .minByOrNull { it.departureEpochMs } ?: return null
            
        val mins = soonestTram.minutesUntil(nowMs)
        val timeStr = if (mins == 0) "now" else "${mins}m"
        val full = "${soonestTram.line}\u00B7$timeStr"
        if (full.length <= CHIP_MAX_CHARS) {
            return full
        }
        val withoutM = "${soonestTram.line}\u00B7$mins"
        if (withoutM.length <= CHIP_MAX_CHARS) {
            return withoutM
        }
        return soonestTram.line.take(CHIP_MAX_CHARS)
    }
}
