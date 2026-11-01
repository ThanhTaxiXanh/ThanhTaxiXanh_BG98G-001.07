package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.*
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.*

class NavigationVoicePromptPolicyTest {
    @Test fun turnPromptUsesDistanceStages() {
        val state = RouteGuidanceState(
            active = true,
            maneuver = ManeuverType.RIGHT,
            instruction = "Rẽ phải",
            roadName = "",
            distanceMeters = 180,
            etaMinutes = 5,
            maneuverKey = 42
        )
        val p = requireNotNull(NavigationVoicePromptPolicy.prompt(state, 40))
        assertEquals("42:mid", p.token)
        assertTrue(p.text.contains("Rẽ phải", ignoreCase = true))
    }

    @Test fun roundaboutSpeaksExitNumber() {
        val state = RouteGuidanceState(
            active = true,
            maneuver = ManeuverType.ROUNDABOUT,
            instruction = "Vòng xuyến · lối 2",
            roadName = "",
            distanceMeters = 60,
            etaMinutes = 1,
            maneuverKey = 7,
            exitNumber = 2
        )
        val p = requireNotNull(NavigationVoicePromptPolicy.prompt(state, 30))
        assertTrue(p.text.contains("lối thứ 2"))
    }

    @Test fun farStraightPromptIsSuppressed() {
        val state = RouteGuidanceState(
            active = true,
            maneuver = ManeuverType.STRAIGHT,
            instruction = "Đi thẳng",
            roadName = "",
            distanceMeters = 450,
            etaMinutes = 2,
            maneuverKey = 3
        )
        assertNull(NavigationVoicePromptPolicy.prompt(state, 30))
    }

    @Test fun offRouteHasDedicatedPrompt() {
        val state = RouteGuidanceState(
            active = true,
            maneuver = ManeuverType.OFF_ROUTE,
            instruction = "Đã lệch tuyến",
            roadName = "",
            distanceMeters = 100,
            etaMinutes = 3
        )
        val p = requireNotNull(NavigationVoicePromptPolicy.prompt(state, 50))
        assertTrue(p.offRoute)
    }
}
