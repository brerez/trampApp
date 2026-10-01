package com.example.tramapp.glance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.tramapp.MainActivity
import com.example.tramapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a pure [NotificationContent] into an Android [Notification] (U78 part 2).
 *
 * DECISION (owner, on-device test on the S22+, see docs/plans .../Assumptions "U2 outcome"):
 * Samsung One UI 8 / API 36 cannot promote this notification and strips bold/colour spans from
 * plain BigText, so this renderer uses a rich custom RemoteViews layout wrapped in
 * DecoratedCustomViewStyle instead. No promotion extra, no short critical text, not colorized.
 */
@Singleton
class JunctionNotificationRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "junction_glance"
        const val NOTIFICATION_ID = 4201

        const val EXTRA_JUNCTION_ID = "junction_id"

        const val ACTION_NEXT_STOP = "com.example.tramapp.action.NEXT_STOP"
        const val ACTION_STOP = "com.example.tramapp.action.STOP"

        private const val REQUEST_CONTENT = 1001
        private const val REQUEST_NEXT_STOP = 1002
        private const val REQUEST_STOP = 1003
        private const val REQUEST_DELETE = 1004
    }

    /** Idempotent: safe to call on every render. */
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tram junction glance",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Live tram departures for your nearest junction"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun render(content: NotificationContent): Notification {
        ensureChannel()

        // Collapsed height is tight: one row, header on one line. Expanded lets the header wrap
        // so large font scales still show platform, distance and next stop in full.
        val collapsed = buildRemoteViews(R.layout.notification_junction_collapsed, content, maxRows = 1, showOverflow = false, headerMaxLines = 1)
        val expanded = buildRemoteViews(R.layout.notification_junction_expanded, content, maxRows = JunctionNotificationFormatter.MAX_ROWS, showOverflow = true, headerMaxLines = 2)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_tram)
            .setContentTitle(content.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColorized(false)
            .setSilent(true)
            .setCategory(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(contentIntent(content.junctionNodeId))
            .setDeleteIntent(stopIntent(REQUEST_DELETE))
            .addAction(
                R.drawable.ic_tile_tram,
                "Next stop ›",
                nextStopIntent(),
            )
            .addAction(
                R.drawable.ic_tile_tram,
                "Stop",
                stopIntent(REQUEST_STOP),
            )

        return builder.build()
    }

    // ---------- PendingIntents ----------

    private fun contentIntent(junctionNodeId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (junctionNodeId != null) putExtra(EXTRA_JUNCTION_ID, junctionNodeId)
        }
        return PendingIntent.getActivity(
            context, REQUEST_CONTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextStopIntent(): PendingIntent {
        val intent = Intent(context, JunctionSessionService::class.java).apply {
            action = ACTION_NEXT_STOP
        }
        return PendingIntent.getService(
            context, REQUEST_NEXT_STOP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, JunctionSessionService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---------- RemoteViews construction ----------

    private fun buildRemoteViews(
        layoutRes: Int,
        content: NotificationContent,
        maxRows: Int,
        showOverflow: Boolean,
        headerMaxLines: Int,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, layoutRes)
        rv.setTextViewText(R.id.tv_title, content.title)

        val rowsToShow = content.lines.take(maxRows)
        for (line in rowsToShow) {
            rv.addView(R.id.rows_container, buildRowView(line, headerMaxLines))
        }

        if (showOverflow) {
            if (content.overflowText != null) {
                rv.setTextViewText(R.id.tv_overflow, content.overflowText)
                rv.setViewVisibility(R.id.tv_overflow, android.view.View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.tv_overflow, android.view.View.GONE)
            }
        }

        if (content.statusLine != null) {
            rv.setTextViewText(R.id.tv_status, content.statusLine)
            rv.setViewVisibility(R.id.tv_status, android.view.View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.tv_status, android.view.View.GONE)
        }

        return rv
    }

    /** One ContentLine -> a notification_junction_row RemoteViews: prefix spannable + tram chips. */
    private fun buildRowView(line: ContentLine, headerMaxLines: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.notification_junction_row)
        row.setInt(R.id.tv_prefix, "setMaxLines", headerMaxLines)

        val prefixSegments = mutableListOf<Segment>()
        var i = 0
        while (i < line.segments.size && line.segments[i].style != SegmentStyle.LINE) {
            prefixSegments.add(line.segments[i])
            i++
        }
        row.setTextViewText(R.id.tv_prefix, buildSpannable(prefixSegments))

        // Remaining segments: group starting at each LINE segment into one tram chip; skip
        // separator segments (" · ") between chips (chip margin already provides spacing).
        var pendingLine: Segment? = null
        var pendingTrailing = mutableListOf<Segment>()

        fun flush() {
            val lineSeg = pendingLine ?: return
            row.addView(R.id.trams_container, buildTramChip(lineSeg, pendingTrailing))
            pendingLine = null
            pendingTrailing = mutableListOf()
        }

        while (i < line.segments.size) {
            val seg = line.segments[i]
            when {
                seg.style == SegmentStyle.LINE -> {
                    flush()
                    pendingLine = seg
                }
                seg.text.isBlank() && seg.style != SegmentStyle.CANCELLED -> {
                    // Skip the " · " separator (and pure whitespace); chip margin covers spacing.
                }
                else -> pendingTrailing.add(seg)
            }
            i++
        }
        flush()

        return row
    }

    private fun buildTramChip(lineSeg: Segment, trailing: List<Segment>): RemoteViews {
        val chip = RemoteViews(context.packageName, R.layout.notification_junction_tram)
        chip.setTextViewText(R.id.tv_line, lineSeg.text)
        chip.setInt(R.id.tv_line, "setBackgroundResource", LineColors.backgroundResFor(lineSeg.line ?: lineSeg.text))
        chip.setTextViewText(R.id.tv_time, buildSpannable(trailing))
        return chip
    }

    private fun buildSpannable(segments: List<Segment>): CharSequence {
        val builder = SpannableStringBuilder()
        for (seg in segments) {
            val start = builder.length
            builder.append(seg.text)
            val end = builder.length
            when (seg.style) {
                SegmentStyle.PLATFORM, SegmentStyle.HIGHLIGHT_LABEL ->
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                SegmentStyle.MUTED ->
                    builder.setSpan(ForegroundColorSpan(mutedColor), start, end, 0)
                SegmentStyle.CANCELLED ->
                    builder.setSpan(ForegroundColorSpan(cancelledColor), start, end, 0)
                SegmentStyle.LINE, SegmentStyle.PLAIN -> Unit
            }
        }
        return builder
    }

    private val mutedColor by lazy { ContextCompat.getColor(context, R.color.notification_muted_text) }
    private val cancelledColor by lazy { ContextCompat.getColor(context, R.color.notification_cancelled_text) }
}
