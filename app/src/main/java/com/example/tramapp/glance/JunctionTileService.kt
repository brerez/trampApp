package com.example.tramapp.glance

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.tramapp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that starts/stops a junction session without opening the app (R1, KTD11).
 * State mirrors the process-wide [SessionState]; a tap starts or stops
 * [JunctionSessionService] unless required permissions are missing, in which case it opens the
 * app to ask for them.
 */
class JunctionTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var observerJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile(SessionState.active.value)
        observerJob = scope.launch {
            SessionState.active.collect { active -> updateTile(active) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        observerJob?.cancel()
        observerJob = null
    }

    override fun onClick() {
        super.onClick()
        if (!hasRequiredPermissions()) {
            openAppForPermissions()
            return
        }

        if (SessionState.active.value) {
            startService(Intent(this, JunctionSessionService::class.java).apply {
                action = JunctionNotificationRenderer.ACTION_STOP
            })
        } else {
            val intent = Intent(this, JunctionSessionService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return locationGranted && notificationsGranted
    }

    private fun openAppForPermissions() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("request_permissions", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Tram glance"
        tile.updateTile()
    }
}
