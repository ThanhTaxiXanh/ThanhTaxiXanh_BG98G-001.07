package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.signs.OfflineWayTransitionGate
import org.junit.Assert.*
import org.junit.Test

class OfflineWayTransitionGateTest {
    @Test fun requiresThreeDistinctConsecutiveGpsFixesForNewWay() {
        val gate = OfflineWayTransitionGate()
        assertTrue(gate.accept(1, 100L))
        assertFalse(gate.accept(2, 200L))
        assertFalse(gate.accept(2, 300L))
        assertTrue(gate.accept(2, 400L))
        assertTrue(gate.accept(2, 400L))
    }

    @Test fun repeatedEvaluationOfSameFixDoesNotCountAsEvidence() {
        val gate = OfflineWayTransitionGate()
        assertTrue(gate.accept(1, 100L))
        assertFalse(gate.accept(2, 200L))
        assertFalse(gate.accept(2, 200L))
        assertFalse(gate.accept(2, 200L))
        assertFalse(gate.accept(2, 300L))
        assertTrue(gate.accept(2, 400L))
    }

    @Test fun intermittentRoadDoesNotTriggerTransition() {
        val gate = OfflineWayTransitionGate()
        assertTrue(gate.accept(1, 100L))
        assertFalse(gate.accept(2, 200L))
        assertTrue(gate.accept(1, 300L))
        assertFalse(gate.accept(2, 400L))
        assertFalse(gate.accept(2, 500L))
        assertTrue(gate.accept(2, 600L))
    }

    @Test fun resetDropsPreviousWay() {
        val gate = OfflineWayTransitionGate()
        assertTrue(gate.accept(1, 100L))
        assertFalse(gate.accept(2, 200L))
        gate.reset()
        assertTrue(gate.accept(2, 200L))
    }
}
