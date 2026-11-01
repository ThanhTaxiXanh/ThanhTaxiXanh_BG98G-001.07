package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.navigation.ManeuverType
import vn.thanhtaxixanh.bg98g00107.navigation.RouteGuidanceState
import org.junit.Assert.*
import org.junit.Test

class RouteGuidanceStateTest {
    @Test fun emptyStateNeverPretendsNavigationIsActive() {
        assertFalse(RouteGuidanceState.EMPTY.active)
        assertNull(RouteGuidanceState.EMPTY.distanceMeters)
        assertTrue(RouteGuidanceState.EMPTY.instruction.isEmpty())
    }

    @Test fun activeGuidanceCarriesRealTurnData() {
        val state = RouteGuidanceState(
            active = true,
            maneuver = ManeuverType.RIGHT,
            instruction = "Rẽ phải",
            roadName = "Đường thử nghiệm",
            distanceMeters = 180,
            etaMinutes = 12
        )
        assertTrue(state.active)
        assertEquals(ManeuverType.RIGHT, state.maneuver)
        assertEquals(180, state.distanceMeters)
    }

    @Test fun invalidNegativeMetricsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteGuidanceState(true, ManeuverType.LEFT, "Rẽ trái", "", -1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteGuidanceState(true, ManeuverType.LEFT, "Rẽ trái", "", 10, -1)
        }
    }
}
