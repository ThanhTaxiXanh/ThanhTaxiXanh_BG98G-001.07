package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.map.DrivingCameraPolicy

class DrivingCameraPolicyTest {
    @Test fun fasterDrivingZoomsOutAndLooksFurtherAhead() {
        val slow = DrivingCameraPolicy.target(20.0, 4)
        val fast = DrivingCameraPolicy.target(80.0, 4)
        assertTrue(fast.zoom < slow.zoom)
        assertTrue(fast.lookAheadM > slow.lookAheadM)
    }

    @Test fun cameraTargetProjectsForwardAlongHeading() {
        val east = DrivingCameraPolicy.targetPoint(21.0, 106.0, 90.0, 100.0)
        assertTrue(east.second > 106.0)
        assertTrue(kotlin.math.abs(east.first - 21.0) < 0.0001)
    }

    @Test fun lookAheadIsBoundedAtExtremeSpeed() {
        val target = DrivingCameraPolicy.target(300.0, 8)
        assertTrue(target.lookAheadM <= 220.0)
        assertTrue(target.zoom >= 14.2)
    }
}
