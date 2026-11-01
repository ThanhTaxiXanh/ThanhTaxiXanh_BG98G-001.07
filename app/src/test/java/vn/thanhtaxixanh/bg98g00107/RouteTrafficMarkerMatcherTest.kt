package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePlan
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePoint
import vn.thanhtaxixanh.bg98g00107.navigation.RouteTrafficMarkerMatcher
import vn.thanhtaxixanh.bg98g00107.signs.RegionalOsmStore

class RouteTrafficMarkerMatcherTest {
    private val meter = 111_320.0

    private fun route() = RoutePlan(
        points = listOf(
            RoutePoint(0.0, -300.0 / meter),
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 300.0 / meter),
            RoutePoint(0.0, 700.0 / meter)
        ),
        timesSec = emptyList(),
        maneuvers = emptyList(),
        totalDistanceM = 1_000,
        totalTimeSec = 100
    )

    private fun marker(
        eastM: Double,
        northM: Double = 0.0,
        type: String = "camera",
        id: Long = 1L
    ) = RegionalOsmStore.Marker(
        id = "node/$id",
        region = "vn",
        lat = northM / meter,
        lon = eastM / meter,
        type = type,
        label = "CAM",
        value = 0,
        source = "OpenStreetMap",
        osmId = "node/$id"
    )

    @Test fun cameraOnActiveRouteAheadIsWarnedByRouteDistance() {
        val m = RouteTrafficMarkerMatcher()
        m.setRoute(route())
        val result = m.upcoming(
            0.0, 0.0, 90.0, true, 3.0,
            listOf(marker(120.0))
        )
        assertEquals(1, result.size)
        assertEquals(120, result.single().distM)
    }

    @Test fun parallelRoadCameraIsRejected() {
        val m = RouteTrafficMarkerMatcher()
        m.setRoute(route())
        assertTrue(m.upcoming(
            0.0, 0.0, 90.0, true, 3.0,
            listOf(marker(120.0, northM = 20.0))
        ).isEmpty())
    }

    @Test fun cameraBehindOrNonCameraMarkerIsIgnored() {
        val m = RouteTrafficMarkerMatcher()
        m.setRoute(route())
        assertTrue(m.upcoming(
            0.0, 0.0, 90.0, true, 3.0,
            listOf(marker(-80.0), marker(100.0, type = "speed", id = 2L))
        ).isEmpty())
    }

    @Test fun badGpsOrStoppedVehicleProducesNoOfflineWarning() {
        val m = RouteTrafficMarkerMatcher()
        m.setRoute(route())
        assertTrue(m.upcoming(
            0.0, 0.0, 90.0, false, 3.0,
            listOf(marker(100.0))
        ).isEmpty())
        assertTrue(m.upcoming(
            0.0, 0.0, 90.0, true, 30.0,
            listOf(marker(100.0))
        ).isEmpty())
    }    @Test fun longRangeAlertStillRejectsParallelRoadCamera() {
        val m = RouteTrafficMarkerMatcher()
        m.setRoute(RoutePlan(
            points = listOf(
                RoutePoint(0.0, 0.0),
                RoutePoint(0.0, 1300.0 / meter)
            ),
            timesSec = emptyList(),
            maneuvers = emptyList(),
            totalDistanceM = 1300,
            totalTimeSec = 100
        ))
        val result = m.upcoming(
            0.0, 0.0, 90.0, true, 3.0,
            listOf(marker(900.0, northM = 20.0)),
            maxLookaheadM = 1000
        )
        assertTrue(result.isEmpty())
    }

    @Test fun configuredLongerLookaheadCanIncludeFarRouteCamera() {
        val m = RouteTrafficMarkerMatcher()
        m.setRoute(RoutePlan(
            points = listOf(
                RoutePoint(0.0, 0.0),
                RoutePoint(0.0, 1300.0 / meter)
            ),
            timesSec = emptyList(),
            maneuvers = emptyList(),
            totalDistanceM = 1300,
            totalTimeSec = 100
        ))
        val result = m.upcoming(
            0.0, 0.0, 90.0, true, 3.0,
            listOf(marker(900.0)),
            maxLookaheadM = 1000
        )
        assertEquals(1, result.size)
        assertEquals(900, result.single().distM)
    }


}
