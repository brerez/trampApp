package com.example.tramapp.domain.junction

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationNodesTest {

    private val home = GeoPoint(50.0755, 14.4378)
    private val nodes = DestinationNodes(
        byDestination = mapOf(Destination.HOME to setOf("U1"), Destination.WORK to setOf("U2")),
        anchors = mapOf(Destination.HOME to home, Destination.WORK to GeoPoint(50.10, 14.40)),
    )

    @Test
    fun `a destination within 500 m of the junction is dropped`() {
        val nearHome = GeoPoint(50.0770, 14.4378) // ~170 m north
        assertEquals(setOf(Destination.WORK), nodes.awayFrom(nearHome).byDestination.keys)
    }

    @Test
    fun `destinations far from the junction are kept`() {
        val elsewhere = GeoPoint(50.05, 14.30)
        assertEquals(setOf(Destination.HOME, Destination.WORK), nodes.awayFrom(elsewhere).byDestination.keys)
    }

    @Test
    fun `a destination without an anchor is kept`() {
        val noAnchors = nodes.copy(anchors = emptyMap())
        assertEquals(2, noAnchors.awayFrom(home).byDestination.size)
    }
}
