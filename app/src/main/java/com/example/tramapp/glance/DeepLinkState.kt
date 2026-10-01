package com.example.tramapp.glance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the junction id from a notification content-intent tap (R4) to whatever screen
 * consumes it. MainActivity writes it when it receives EXTRA_JUNCTION_ID; the dashboard (U10)
 * reads and clears it. Kept deliberately tiny/decoupled so U10 does not need to know about
 * notification internals.
 */
@Singleton
class DeepLinkState @Inject constructor() {
    private val _junctionId = MutableStateFlow<String?>(null)
    val junctionId: StateFlow<String?> = _junctionId.asStateFlow()

    fun setJunctionId(nodeId: String?) {
        _junctionId.value = nodeId
    }

    fun consume(): String? {
        val value = _junctionId.value
        _junctionId.value = null
        return value
    }
}
