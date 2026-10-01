package com.example.tramapp.glance

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.tramapp.data.local.datastore.UserPreferencesManager
import com.example.tramapp.data.local.datastore.toSessionSettings
import com.example.tramapp.domain.junction.JunctionEngine
import com.example.tramapp.domain.junction.JunctionLocator
import com.example.tramapp.domain.junction.JunctionSelection
import com.example.tramapp.domain.junction.JunctionSelector
import com.example.tramapp.domain.junction.JunctionSnapshot
import com.example.tramapp.domain.junction.LocationFix
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * Foreground (location-typed) service that keeps the junction glance notification live for as
 * long as a session runs (U78 part 3). Started/stopped by [JunctionTileService]; ends itself on
 * "Stop", on notification swipe (delete intent), or on the configured timeout.
 */
@AndroidEntryPoint
class JunctionSessionService : Service() {

    @Inject lateinit var engine: JunctionEngine
    @Inject lateinit var locator: JunctionLocator
    @Inject lateinit var renderer: JunctionNotificationRenderer
    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var preferencesManager: UserPreferencesManager

    companion object {
        private const val TAG = "JunctionSession"
    }

    private val serviceJob = SupervisorJob()
    // Main thread: selector, scheduler and render state are not thread-safe. Network and Room
    // calls suspend off the main thread on their own.
    // A failed refresh/render must not take the whole app (and the dashboard) down with it.
    private val serviceScope = CoroutineScope(
        Dispatchers.Main.immediate + serviceJob +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Session coroutine failed", e) },
    )

    private val selector = JunctionSelector()
    private val headingGate = HeadingGate()
    private val updateLimiter = UpdateLimiter()
    private lateinit var scheduler: SessionScheduler

    private var compassSampler: CompassSampler? = null
    private var compassJob: Job? = null
    private var snapshotJob: Job? = null
    private var cadenceJob: Job? = null
    private var pendingUpdateJob: Job? = null
    private var locationCallback: LocationCallback? = null

    private var currentSelection: JunctionSelection = JunctionSelection.NoFix
    private var currentSnapshot: JunctionSnapshot? = null
    private var collectedNodeId: String? = null
    private var currentHeadingDeg: Double? = null
    private var screenOn: Boolean = true

    private var startTimeMs: Long = 0L
    private var firstRenderLogged = false
    private var started = false

    /** Defaults until the user's settings load (see loadSettings). */
    private fun currentSettings(): SessionSettings = SessionSettings()

    private var healthWarning: String? = null

    private suspend fun loadSettings() {
        val prefs = preferencesManager.userPreferences.first()
        scheduler.settings = prefs.toSessionSettings()
        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        val exempt = pm?.isIgnoringBatteryOptimizations(packageName) ?: false
        healthWarning = SessionHealth.notificationText(SessionHealth.check(prefs.sessionTimeoutMin, exempt))
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenChanged(true)
                Intent.ACTION_SCREEN_OFF -> onScreenChanged(false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startTimeMs = System.currentTimeMillis()
        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        screenOn = pm?.isInteractive ?: true
        scheduler = SessionScheduler(currentSettings(), startTimeMs, screenOn)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            JunctionNotificationRenderer.ACTION_NEXT_STOP -> {
                handleNextStop()
                return START_NOT_STICKY
            }
            JunctionNotificationRenderer.ACTION_STOP -> {
                endSession()
                return START_NOT_STICKY
            }
        }

        if (!started) {
            started = true
            try {
                startForegroundWithLocating()
            } catch (e: SecurityException) {
                // Not eligible for a location FGS right now (e.g. started from the background).
                // Bail out instead of crashing the whole app.
                Log.e(TAG, "Cannot start location foreground service", e)
                started = false
                stopSelf()
                return START_NOT_STICKY
            }
            SessionState.setActive(true)
            beginLocationUpdates()
            serviceScope.launch {
                loadSettings()
                beginCadenceLoop()
            }
            if (screenOn) startCompass()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    // ---------- startup ----------

    private fun startForegroundWithLocating() {
        val content = JunctionNotificationFormatter.format(
            FormatterInput(JunctionSelection.NoFix, null, null, screenOn, System.currentTimeMillis()),
        )
        val notification = renderer.render(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                JunctionNotificationRenderer.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(JunctionNotificationRenderer.NOTIFICATION_ID, notification)
        }
    }

    // ---------- location ----------

    @SuppressLint("MissingPermission")
    private fun beginLocationUpdates() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                handleFix(LocationFix(location.latitude, location.longitude, location.accuracy, System.currentTimeMillis()))
            }
        }
        requestLocationUpdatesForScreenState()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdatesForScreenState() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val priority = if (screenOn) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val intervalMs = if (screenOn) 10_000L else 60_000L

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateDistanceMeters(25f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleFix(LocationFix(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis()))
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun handleFix(fix: LocationFix) {
        serviceScope.launch {
            selector.onFix(fix)
            val radius = preferencesManager.userPreferences.first().displayRadius
            selector.walkingRangeM = radius.toDouble()
            val junctions = locator.junctionsNear(fix.point, radius)
            val selection = selector.setJunctions(junctions)
            onSelectionChanged(selection)

            if (selection is JunctionSelection.Selected) {
                engine.refresh(selection.junction)
                scheduler.onRefreshed(System.currentTimeMillis())
                requeryJunctions(fix)
            }
        }
    }

    /** After a refresh, isTram may have changed (bus-only nodes learned); re-query so they drop
     *  out of the ranked list without waiting for the next fix. */
    private suspend fun requeryJunctions(fix: LocationFix) {
        val radius = preferencesManager.userPreferences.first().displayRadius
        val junctions = locator.junctionsNear(fix.point, radius)
        val selection = selector.setJunctions(junctions)
        onSelectionChanged(selection)
    }

    // ---------- selection / rendering ----------

    private fun onSelectionChanged(selection: JunctionSelection) {
        currentSelection = selection
        val nodeId = (selection as? JunctionSelection.Selected)?.junction?.nodeId
        if (nodeId != collectedNodeId) {
            collectedNodeId = nodeId
            snapshotJob?.cancel()
            currentSnapshot = null
            if (nodeId != null) {
                snapshotJob = serviceScope.launch {
                    engine.snapshot(nodeId).collect { snapshot ->
                        currentSnapshot = snapshot
                        scheduleRender()
                    }
                }
                // A switch (e.g. the first pick turned out bus-only) must fetch the new junction
                // now, not on the next cadence tick 20+ s later. The engine coalesces repeats.
                val junction = (selection as JunctionSelection.Selected).junction
                serviceScope.launch { engine.refresh(junction) }
            }
        }
        scheduleRender()
    }

    /** Every notification update funnels through here so UpdateLimiter's one-per-3s cap (and
     *  trailing-update guarantee) applies uniformly, whatever triggered the change. */
    private fun scheduleRender() {
        val now = System.currentTimeMillis()
        if (updateLimiter.tryAcquire(now)) {
            pendingUpdateJob?.cancel()
            pendingUpdateJob = null
            doRender()
        } else if (pendingUpdateJob == null) {
            val waitMs = max(0L, updateLimiter.nextAllowedAtMs() - now)
            pendingUpdateJob = serviceScope.launch {
                delay(waitMs)
                updateLimiter.tryAcquire(System.currentTimeMillis())
                pendingUpdateJob = null
                doRender()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun doRender() {
        val now = System.currentTimeMillis()
        val content = JunctionNotificationFormatter.format(
            FormatterInput(currentSelection, currentSnapshot, currentHeadingDeg, screenOn, now, healthWarning),
        )
        try {
            val notification = renderer.render(content)
            NotificationManagerCompat.from(this).notify(JunctionNotificationRenderer.NOTIFICATION_ID, notification)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Notification render failed", e)
            return
        }

        if (!firstRenderLogged && content.lines.isNotEmpty()) {
            firstRenderLogged = true
            Log.i(TAG, "First render with rows after ${now - startTimeMs} ms")
        }
    }

    // ---------- cadence ----------

    private fun beginCadenceLoop() {
        cadenceJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (scheduler.shouldStop(now)) {
                    endSession()
                    return@launch
                }
                val waitMs = scheduler.nextWakeAtMs() - now
                if (waitMs > 0) delay(waitMs)

                val now2 = System.currentTimeMillis()
                if (scheduler.shouldStop(now2)) {
                    endSession()
                    return@launch
                }
                if (scheduler.isRefreshDue(now2)) {
                    refreshSelected()
                    scheduler.onRefreshed(System.currentTimeMillis())
                }
            }
        }
    }

    private suspend fun refreshSelected() {
        val selection = currentSelection
        if (selection is JunctionSelection.Selected) {
            engine.refresh(selection.junction)
            requeryJunctions(selection.fix)
        }
    }

    // ---------- screen state ----------

    private fun onScreenChanged(isOn: Boolean) {
        screenOn = isOn
        requestLocationUpdatesForScreenState()

        if (isOn) {
            startCompass()
        } else {
            stopCompass()
            currentHeadingDeg = null
            headingGate.reset()
        }

        val immediate = scheduler.onScreenChanged(isOn, System.currentTimeMillis())
        scheduleRender()
        // The loop may be sleeping toward the other screen state's interval; restart it.
        if (cadenceJob != null) {
            cadenceJob?.cancel()
            beginCadenceLoop()
        }

        if (immediate) {
            serviceScope.launch {
                refreshSelected()
                scheduler.onRefreshed(System.currentTimeMillis())
            }
        }
    }

    // ---------- compass ----------

    private fun startCompass() {
        if (compassJob != null) return
        val sampler = CompassSampler(this)
        compassSampler = sampler
        compassJob = serviceScope.launch {
            sampler.headingFlow().collect { heading ->
                val now = System.currentTimeMillis()
                if (headingGate.shouldRedraw(heading, now)) {
                    currentHeadingDeg = heading
                    scheduleRender()
                }
            }
        }
    }

    private fun stopCompass() {
        compassJob?.cancel()
        compassJob = null
        compassSampler = null
    }

    // ---------- actions ----------

    private fun handleNextStop() {
        serviceScope.launch {
            val selection = selector.cycleNext()
            onSelectionChanged(selection)
            if (selection is JunctionSelection.Selected) {
                engine.refresh(selection.junction)
            }
        }
    }

    private fun endSession() {
        cleanup()
        SessionState.setActive(false)
        requestTileUpdate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        try {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        } catch (_: Exception) {
        }
        stopCompass()
        cadenceJob?.cancel()
        snapshotJob?.cancel()
        pendingUpdateJob?.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
    }

    private fun requestTileUpdate() {
        try {
            TileService.requestListeningState(this, ComponentName(this, JunctionTileService::class.java))
        } catch (_: Exception) {
        }
    }
}
