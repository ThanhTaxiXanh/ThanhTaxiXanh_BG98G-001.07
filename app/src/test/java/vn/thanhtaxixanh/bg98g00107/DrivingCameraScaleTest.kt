package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.map.DrivingCameraPolicy

class DrivingCameraScaleTest {
    @Test fun smallerReferenceScaleZoomsCloser() {
        val close = DrivingCameraPolicy.target(50.0, 4, 40)
        val normal = DrivingCameraPolicy.target(50.0, 4, 120)
        val far = DrivingCameraPolicy.target(50.0, 4, 400)
        assertTrue(close.zoom > normal.zoom)
        assertTrue(normal.zoom > far.zoom)
    }

    @Test fun scaleIsBounded() {
        val close = DrivingCameraPolicy.target(0.0, 4, 20)
        val far = DrivingCameraPolicy.target(160.0, 4, 800)
        assertTrue(close.zoom <= 19.7)
        assertTrue(far.zoom >= 13.8)
    }
}
