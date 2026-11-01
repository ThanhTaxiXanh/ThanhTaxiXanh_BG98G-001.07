package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.ManeuverType
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePlan
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePoint
import vn.thanhtaxixanh.bg98g00107.navigation.RouteProgressTracker

class ConfigurableRerouteThresholdTest {
    private val route = RoutePlan(
        points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.01)),
        timesSec = emptyList(),
        maneuvers = emptyList(),
        totalDistanceM = 1113,
        totalTimeSec = 90
    )

    @Test fun fiftyMeterSettingStillRequiresThreeBadGpsFixes() {
        val tracker = RouteProgressTracker()
        tracker.setRoute(route)
        val latAbout61m = 0.00055
        repeat(2) {
            val state = tracker.update(
                latAbout61m, 0.005,
                accuracyM = 3.0,
                offRouteThresholdM = 50.0
            )
            assertNotEquals(ManeuverType.OFF_ROUTE, state.maneuver)
        }
        val third = tracker.update(
            latAbout61m, 0.005,
            accuracyM = 3.0,
            offRouteThresholdM = 50.0
        )
        assertEquals(ManeuverType.OFF_ROUTE, third.maneuver)
    }

    @Test fun gpsAccuracyCanSafelyWidenConfiguredThreshold() {
        val tracker = RouteProgressTracker()
        tracker.setRoute(route)
        val latAbout61m = 0.00055
        repeat(3) {
            val state = tracker.update(
                latAbout61m, 0.005,
                accuracyM = 30.0,
                offRouteThresholdM = 50.0
            )
            assertNotEquals(ManeuverType.OFF_ROUTE, state.maneuver)
        }
    }
}
