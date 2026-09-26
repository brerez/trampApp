package com.example.tramapp.glance

import com.example.tramapp.domain.junction.JunctionSelection
import com.example.tramapp.domain.junction.JunctionSnapshot

enum class SegmentStyle { PLAIN, PLATFORM, LINE, HIGHLIGHT_LABEL, CANCELLED, MUTED }

/** `line` is set for LINE segments (renderer may colour by line). */
data class Segment(val text: String, val style: SegmentStyle, val line: String? = null)

data class ContentLine(val segments: List<Segment>) { 
    val text: String get() = segments.joinToString("") { it.text } 
}

data class NotificationContent(
    val title: String,                 // junction name, or "Tram glance" when no junction
    val lines: List<ContentLine>,      // at most MAX_ROWS row lines
    val overflowText: String?,         // "+N more directions" when rows > MAX_ROWS, else null
    val chipText: String?,             // soonest non-cancelled tram, e.g. "8·1m"; null when none
    val nowBarSummary: String,         // e.g. "Kamenická · 8 in 1 min"; falls back to status text
    val statusLine: String?,           // non-live state text, else null
    val junctionNodeId: String?        // for the content intent (open app on this junction)
)

data class FormatterInput(
    val selection: JunctionSelection,
    val snapshot: JunctionSnapshot?,   // snapshot of the selected junction, if any
    val headingDeg: Double?,
    val screenOn: Boolean,
    val nowMs: Long,
    val extraWarning: String? = null   // e.g. battery warning from SessionHealth; appended as its own status text
)
