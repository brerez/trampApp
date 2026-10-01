package com.example.tramapp.glance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "is a junction session running" flag (U78 part 3). The tile mirrors this to
 * decide its own state and whether a tap should start or stop the service; the service updates
 * it on start/stop so the tile reflects reality even if the service dies unexpectedly.
 */
object SessionState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setActive(value: Boolean) {
        _active.value = value
    }
}
