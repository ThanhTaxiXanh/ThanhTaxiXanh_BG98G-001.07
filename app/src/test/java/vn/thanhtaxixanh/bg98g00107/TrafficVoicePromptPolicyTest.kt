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
    }
}
