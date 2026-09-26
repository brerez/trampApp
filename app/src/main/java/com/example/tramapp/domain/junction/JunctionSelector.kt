package com.example.tramapp.domain.junction

// pure state holder, not thread-safe; caller serialises calls
class JunctionSelector(var walkingRangeM: Double = 750.0) {
    companion object {
        const val MAX_SWITCH_ACCURACY_M = 50f
        const val SWITCH_MIN_MARGIN_M = 25.0
        const val SWITCH_MIN_MARGIN_FRACTION = 0.15
        const val SWITCH_CONSECUTIVE_FIXES = 2
        const val CYCLE_DEPTH = 3   // nearest, 2nd, 3rd
    }

    var current: JunctionSelection = JunctionSelection.NoFix
        private set

    private var junctions: List<Junction> = emptyList()
    private var anchorNodeId: String? = null
    
    private var challengerNodeId: String? = null
    private var streakCount: Int = 0

    private var overrideNodeId: String? = null
    private var cycleIndex: Int = 0
    private var lastFix: LocationFix? = null

    fun setJunctions(junctions: List<Junction>): JunctionSelection {
        this.junctions = junctions
        return reEvaluate(lastFix)
    }

    fun onFix(fix: LocationFix): JunctionSelection {
        lastFix = fix
        return reEvaluate(fix)
    }

    fun cycleNext(): JunctionSelection {
        val curr = current
        if (curr !is JunctionSelection.Selected) return curr

        val ranked = curr.ranked
        if (ranked.isEmpty()) return curr
        
        val anchorJunction = ranked.find { it.junction.nodeId == anchorNodeId } ?: return curr
        val others = ranked.filter { it.junction.nodeId != anchorNodeId }
        val ordered = listOf(anchorJunction) + others
        
        val numAvailable = Math.min(CYCLE_DEPTH, ordered.size)
        if (numAvailable <= 1) return curr

        cycleIndex = (cycleIndex + 1) % numAvailable
        
        overrideNodeId = if (cycleIndex == 0) null else ordered[cycleIndex].junction.nodeId

        return reEvaluate(lastFix)
    }

    /** Force a junction to be shown (e.g. notification tap / intent); behaves like a cycle override
     *  that clears when the anchor changes. No-op if nodeId unknown. */
    fun pin(nodeId: String): JunctionSelection {
        if (junctions.any { it.nodeId == nodeId }) {
            overrideNodeId = nodeId
        }
        return reEvaluate(lastFix)
    }

    private fun reEvaluate(fix: LocationFix?): JunctionSelection {
        if (fix == null) {
            current = JunctionSelection.NoFix
            return current
        }

        if (junctions.isEmpty()) {
            current = JunctionSelection.NoneInRange(null)
            return current
        }

        val allRanked = junctions.map { j ->
            val minDistance = j.platforms.minOfOrNull { p -> Geo.distanceM(fix.point, p.position) } ?: Double.MAX_VALUE
            RankedJunction(j, minDistance)
        }.sortedBy { it.distanceM }

        val nearestDistance = allRanked.first().distanceM

        if (anchorNodeId == null || allRanked.none { it.junction.nodeId == anchorNodeId }) {
            anchorNodeId = allRanked.first().junction.nodeId
            resetStreak()
            clearOverride()
        } else {
            val anchor = allRanked.first { it.junction.nodeId == anchorNodeId }
            val nearest = allRanked.first()
            
            if (fix.accuracyM <= MAX_SWITCH_ACCURACY_M) {
                if (nearest.junction.nodeId != anchorNodeId) {
                    val distDiff = anchor.distanceM - nearest.distanceM
                    val minRequiredDiff = Math.max(SWITCH_MIN_MARGIN_M, anchor.distanceM * SWITCH_MIN_MARGIN_FRACTION)
                    
                    if (distDiff >= minRequiredDiff) {
                        if (challengerNodeId == nearest.junction.nodeId) {
                            streakCount++
                        } else {
                            challengerNodeId = nearest.junction.nodeId
                            streakCount = 1
                        }
                        
                        if (streakCount >= SWITCH_CONSECUTIVE_FIXES) {
                            anchorNodeId = nearest.junction.nodeId
                            resetStreak()
                            clearOverride()
                        }
                    } else {
                        resetStreak()
                    }
                } else {
                    resetStreak()
                }
            }
        }

        val inRangeRanked = allRanked.filter { it.distanceM <= walkingRangeM }
        val anchorJunction = inRangeRanked.find { it.junction.nodeId == anchorNodeId }
        
        if (anchorJunction == null) {
            current = JunctionSelection.NoneInRange(nearestDistance)
            return current
        }

        val others = inRangeRanked.filter { it.junction.nodeId != anchorNodeId }
        val ordered = listOf(anchorJunction) + others

        var selectedRanked = anchorJunction
        var actualCycleIndex = 0
        
        if (overrideNodeId != null) {
            val overrideJunction = ordered.find { it.junction.nodeId == overrideNodeId }
            if (overrideJunction != null) {
                selectedRanked = overrideJunction
                actualCycleIndex = ordered.indexOf(overrideJunction)
            } else {
                clearOverride()
            }
        }
        
        cycleIndex = actualCycleIndex

        current = JunctionSelection.Selected(
            junction = selectedRanked.junction,
            distanceM = selectedRanked.distanceM,
            ranked = inRangeRanked, // already sorted by distance from allRanked
            cycleIndex = cycleIndex,
            fix = fix
        )
        return current
    }

    private fun resetStreak() {
        challengerNodeId = null
        streakCount = 0
    }

    private fun clearOverride() {
        overrideNodeId = null
        cycleIndex = 0
    }
}
