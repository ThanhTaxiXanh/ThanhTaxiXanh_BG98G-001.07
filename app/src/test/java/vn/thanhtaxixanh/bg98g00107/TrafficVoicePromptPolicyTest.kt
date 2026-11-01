package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.TrafficVoicePromptPolicy
import vn.thanhtaxixanh.bg98g00107.signs.SignItem
import vn.thanhtaxixanh.bg98g00107.signs.SignsState
import vn.thanhtaxixanh.bg98g00107.signs.UpcomingSign

class TrafficVoicePromptPolicyTest {
    @Test fun estimatedSpeedLimitIsNeverSpokenAsLegalLimit() {
        val state = SignsState(60, true, emptyList(), "estimated")
        assertNull(TrafficVoicePromptPolicy.limitChanged(state, null))
        assertNull(TrafficVoicePromptPolicy.overspeed(state, 90, 4))
    }

    @Test fun overspeedHonorsConfiguredTolerance() {
        val state = SignsState(60, false, emptyList(), "verified")
        assertNull(TrafficVoicePromptPolicy.overspeed(state, 64, 4))
        assertTrue(TrafficVoicePromptPolicy.overspeed(state, 65, 4)!!.text.contains("60"))
    }

    @Test fun routeFilteredCameraProducesDistancePrompt() {
        val camera = SignItem(
            lat = 21.0, lon = 106.0, type = "camera",
            label = "CAM", name = "Camera", value = 0, osmNodeId = 123L
        )
        val state = SignsState(60, false, listOf(UpcomingSign(camera, 420)), "ok")
        val prompt = TrafficVoicePromptPolicy.camera(state)!!
        assertEquals("camera:123:far", prompt.token)
        assertTrue(prompt.text.contains("420 mét"))
    }    @Test fun cameraStagesAndTypeFiltersAreConfigurable() {
        val speed = SignItem(
            lat = 21.0, lon = 106.0, type = "camera",
            label = "CAM", name = "Camera", value = 0, osmNodeId = 10L
        )
        val red = SignItem(
            lat = 21.0, lon = 106.001, type = "redlight_camera",
            label = "CAM", name = "Red", value = 0, osmNodeId = 11L
        )
        val far = SignsState(
            60, false, listOf(UpcomingSign(speed, 500)), "ok"
        )
        assertEquals(
            "camera:10:far",
            TrafficVoicePromptPolicy.camera(far, 600, 180, 70)!!.token
        )
        val near = SignsState(
            60, false, listOf(UpcomingSign(speed, 150)), "ok"
        )
        assertEquals(
            "camera:10:near",
            TrafficVoicePromptPolicy.camera(near, 600, 180, 70)!!.token
        )
        val final = SignsState(
            60, false, listOf(UpcomingSign(speed, 60)), "ok"
        )
        assertEquals(
            "camera:10:final",
            TrafficVoicePromptPolicy.camera(final, 600, 180, 70)!!.token
        )
        val onlyRed = SignsState(
            60, false,
            listOf(UpcomingSign(speed, 100), UpcomingSign(red, 120)),
            "ok"
        )
        assertEquals(
            "camera:11:near",
            TrafficVoicePromptPolicy.camera(
                onlyRed, 600, 180, 70,
                warnSpeedCameras = false,
                warnRedLightCameras = true
            )!!.token
        )
        assertNull(
            TrafficVoicePromptPolicy.camera(
                onlyRed, 600, 180, 70,
                warnSpeedCameras = false,
                warnRedLightCameras = false
            )
        )
    }

    @Test fun invalidDistanceOrderIsSafelyNormalized() {
        val camera = SignItem(
            lat = 21.0, lon = 106.0, type = "camera",
            label = "CAM", name = "Camera", value = 0, osmNodeId = 99L
        )
        val state = SignsState(
            60, false, listOf(UpcomingSign(camera, 140)), "ok"
        )
        val prompt = TrafficVoicePromptPolicy.camera(
            state,
            farDistanceM = 200,
            nearDistanceM = 500,
            finalDistanceM = 150
        )
        assertEquals("camera:99:near", prompt!!.token)
    }


}
