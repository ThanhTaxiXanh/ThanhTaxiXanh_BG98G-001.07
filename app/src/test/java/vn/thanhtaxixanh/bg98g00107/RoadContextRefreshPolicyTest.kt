package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.signs.RoadContextRefreshPolicy

class RoadContextRefreshPolicyTest {
    @Test fun highSpeedLooksFurtherAheadAndUsesLargerContext() {
        val slow = RoadContextRefreshPolicy.plan(21.0, 106.0, 90.0, true, 5.0)
        val fast = RoadContextRefreshPolicy.plan(21.0, 106.0, 90.0, true, 25.0)
        assertTrue(fast.centerLon > slow.centerLon)
        assertTrue(fast.radiusM > slow.radiusM)
        assertTrue(fast.minIntervalMs < slow.minIntervalMs)
    }

    @Test fun stoppedVehicleDoesNotProjectContextForward() {
        val p = RoadContextRefreshPolicy.plan(21.0, 106.0, 90.0, false, 0.0)
        assertTrue(kotlin.math.abs(p.centerLat - 21.0) < 1e-12)
        assertTrue(kotlin.math.abs(p.centerLon - 106.0) < 1e-12)
    }

    @Test fun refreshRequiresEnoughMovementAfterCooldown() {
        val p = RoadContextRefreshPolicy.plan(0.0, 0.001, 90.0, true, 10.0)
        assertFalse(RoadContextRefreshPolicy.shouldRefresh(
            500L, 0L, p.centerLat, p.centerLon, p, true
        ))
        assertFalse(RoadContextRefreshPolicy.shouldRefresh(
            2_000L, 0L, p.centerLat, p.centerLon, p, true
        ))
        val moved = RoadContextRefreshPolicy.plan(0.0, 0.002, 90.0, true, 10.0)
        assertTrue(RoadContextRefreshPolicy.shouldRefresh(
            2_000L, 0L, p.centerLat, p.centerLon, moved, true
        ))
    }

    @Test fun emptyContextRetriesWithoutHammeringDatabase() {
        val p = RoadContextRefreshPolicy.plan(21.0, 106.0, 0.0, false, 0.0)
        assertFalse(RoadContextRefreshPolicy.shouldRefresh(
            1_999L, 0L, 21.0, 106.0, p, false
        ))
        assertTrue(RoadContextRefreshPolicy.shouldRefresh(
            2_000L, 0L, 21.0, 106.0, p, false
        ))
    }
}
