package com.example.tramapp.glance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Invisible trampoline the tile launches to start a session. A location-typed foreground
 * service may only start while the app is visible; One UI does not extend that eligibility to
 * tile clicks (the emulator does), so starting it straight from [JunctionTileService] crashed
 * with a SecurityException on the S22+. Starting it from here, while this activity is resumed,
 * is always allowed.
 *
 * It is declared `showWhenLocked` in the manifest so a tile tap on the lock screen starts the
 * session straight away, like the flashlight tile, instead of first asking for the unlock.
 * SystemUI only skips the keyguard for activities that can show over it.
 */
class SessionStartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ContextCompat.startForegroundService(this, Intent(this, JunctionSessionService::class.java))
        } catch (e: RuntimeException) {
            Log.e("SessionStart", "Could not start the junction session", e)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
