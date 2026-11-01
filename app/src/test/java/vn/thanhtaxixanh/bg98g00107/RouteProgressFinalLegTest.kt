package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.*
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.*

class RouteProgressFinalLegTest {
    private val route = RoutePlan(
        points = listOf(
            RoutePoint(21.0, 106.0000),
            RoutePoint(21.0, 106.0010),
            RoutePoint(21.0, 106.0020),
            RoutePoint(21.0, 106.0030)
        ),
        timesSec = listOf(0.0, 30.0, 60.0, 90.0),
        maneuvers = listOf(RouteManeuver(1, ManeuverType.RIGHT)),
        totalDistanceM = 310,
        totalTimeSec = 90
    )

    @Test fun afterLastTurnButFarFromDestinationKeepsStraightGuidance() {
        val tracker = RouteProgressTracker()
        tracker.setRoute(route)
        val state = tracker.update(21.0, 106.0020, 90.0, 5.0)
        assertEquals(ManeuverType.STRAIGHT, state.maneuver)
        assertEquals("Tiếp tục theo tuyến", state.instruction)
        assertTrue((state.distanceMeters ?: 0) > 35)
    }

    @Test fun nearDestinationChangesToArrive() {
        val tracker = RouteProgressTracker()
        tracker.setRoute(route)
        val state = tracker.update(21.0, 106.00295, 90.0, 5.0)
        assertEquals(ManeuverType.ARRIVE, state.maneuver)
    }
}
