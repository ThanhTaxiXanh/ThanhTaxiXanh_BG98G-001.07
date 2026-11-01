package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.navigation.*
import org.junit.Assert.*
import org.junit.Test

class RouteProgressTrackerTest {
    private fun route() = RoutePlan(
        points = listOf(
            RoutePoint(21.0, 106.0000),
            RoutePoint(21.0, 106.0005),
            RoutePoint(21.0, 106.0010),
            RoutePoint(21.0, 106.0015)
        ),
        timesSec = listOf(0.0, 30.0, 60.0, 90.0),
        maneuvers = listOf(RouteManeuver(2, ManeuverType.RIGHT)),
        totalDistanceM = 160,
        totalTimeSec = 90
    )

    @Test fun nextTurnComesFromRealRouteIndex() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        val state = t.update(21.0, 106.00045)
        assertTrue(state.active)
        assertEquals(ManeuverType.RIGHT, state.maneuver)
        assertEquals("Rẽ phải", state.instruction)
        assertTrue((state.distanceMeters ?: 0) > 0)
        assertNotNull(state.etaMinutes)
    }

    @Test fun largeDeviationRequiresStableEvidenceBeforeOffRoute() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        t.update(21.0, 106.00045)
        val firstBad = t.update(21.0020, 106.0005)
        assertNotEquals(ManeuverType.OFF_ROUTE, firstBad.maneuver)
        val secondBad = t.update(21.0020, 106.0005)
        assertNotEquals(ManeuverType.OFF_ROUTE, secondBad.maneuver)
        val thirdBad = t.update(21.0020, 106.0005)
        assertEquals(ManeuverType.OFF_ROUTE, thirdBad.maneuver)
        assertEquals("Đã lệch tuyến", thirdBad.instruction)
    }

    @Test fun afterLastManeuverHeadsToDestination() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        val state = t.update(21.0, 106.00145)
        assertEquals(ManeuverType.ARRIVE, state.maneuver)
    }
}
