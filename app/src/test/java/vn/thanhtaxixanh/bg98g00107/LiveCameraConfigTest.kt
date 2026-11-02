package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.camera.LiveCameraConfig
import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardLayouts
import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardPanel

class LiveCameraConfigTest {
    @Test fun rtspOnlyAndHostRequired() {
        assertTrue(LiveCameraConfig.isValidRtsp("rtsp://192.168.0.1/"))
        assertTrue(LiveCameraConfig.isValidRtsp("rtsp://192.168.0.1:554/liveRTSP/v1"))
        assertTrue(LiveCameraConfig.isValidRtsp("rtsp://cam.local:8554/live"))
        assertFalse(LiveCameraConfig.isValidRtsp("https://192.168.0.1/live"))
        assertFalse(LiveCameraConfig.isValidRtsp("rtsp:///stream"))
        assertFalse(LiveCameraConfig.isValidRtsp(""))
        assertFalse(LiveCameraConfig.isValidRtsp("rtsp://host:0/"))
        assertFalse(LiveCameraConfig.isValidRtsp("rtsp://host/\n"))
    }

    @Test fun stackedPresetKeepsQrAboveCameraTile() {
        val tiles = DashboardLayouts.byId("stacked")!!.layout
        val qr = tiles.tile(DashboardPanel.QR)
        val camera = tiles.tile(DashboardPanel.SIGNS)
        assertEquals("Camera trực tiếp", DashboardPanel.SIGNS.title)
        assertEquals(qr.x, camera.x)
        assertEquals(qr.width, camera.width)
        assertEquals(qr.y + qr.height, camera.y)
        assertEquals(12, camera.y + camera.height)
    }
}
