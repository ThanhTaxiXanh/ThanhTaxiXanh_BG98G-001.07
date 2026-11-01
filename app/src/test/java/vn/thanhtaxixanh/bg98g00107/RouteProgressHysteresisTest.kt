package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.*
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.*

class RouteProgressHysteresisTest {
    private fun route(): RoutePlan = RoutePlan(
        points = listOf(
            RoutePoint(21.0000, 106.0000),
            RoutePoint(21.0000, 106.0010),
            RoutePoint(21.0000, 106.0020),
            RoutePoint(21.0000, 106.0030),
            RoutePoint(21.0000, 106.0040)
        ),
        timesSec = listOf(0.0, 30.0, 60.0, 90.0, 120.0),
        maneuvers = listOf(
            RouteManeuver(2, ManeuverType.RIGHT),
            RouteManeuver(4, ManeuverType.ARRIVE)
        ),
        totalDistanceM = 420,
        totalTimeSec = 120
    )

    @Test fun singleBadGpsSampleDoesNotDeclareOffRoute() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        val good = t.update(21.0, 106.0005, 90.0, 8.0)
        assertTrue(good.active)
        val oneBad = t.update(21.0020, 106.0005, 90.0, 8.0)
        assertNotEquals(ManeuverType.OFF_ROUTE, oneBad.maneuver)
    }

    @Test fun threeBadSamplesLatchOffRouteAndTwoGoodSamplesRejoin() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        t.update(21.0, 106.0005, 90.0, 8.0)
        t.update(21.0020, 106.0005, 90.0, 8.0)
        t.update(21.0020, 106.0005, 90.0, 8.0)
        val off = t.update(21.0020, 106.0005, 90.0, 8.0)
        assertEquals(ManeuverType.OFF_ROUTE, off.maneuver)

        val firstGood = t.update(21.0, 106.0010, 90.0, 8.0)
        assertEquals(ManeuverType.OFF_ROUTE, firstGood.maneuver)
        val secondGood = t.update(21.0, 106.0010, 90.0, 8.0)
        assertNotEquals(ManeuverType.OFF_ROUTE, secondGood.maneuver)
    }

    @Test fun passedManeuverAdvancesToNextInstruction() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        val before = t.update(21.0, 106.0015, 90.0, 5.0)
        assertEquals(ManeuverType.RIGHT, before.maneuver)

        val after = t.update(21.0, 106.0023, 90.0, 5.0)
        assertNotEquals(ManeuverType.RIGHT, after.maneuver)
    }

    @Test fun largeAccuracyExpandsThresholdButRemainsBounded() {
        val t = RouteProgressTracker()
        t.setRoute(route())
        // 100 m accuracy does not create an unbounded off-route threshold.
        repeat(3) {
            t.update(21.0015, 106.0010, 90.0, 100.0)
        }
        val state = t.update(21.0015, 106.0010, 90.0, 100.0)
        assertEquals(ManeuverType.OFF_ROUTE, state.maneuver)
    }
}
