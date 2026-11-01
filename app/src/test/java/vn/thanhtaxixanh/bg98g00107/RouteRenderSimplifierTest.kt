package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.navigation.RoutePoint
import vn.thanhtaxixanh.bg98g00107.navigation.RouteRenderSimplifier
import org.junit.Assert.*
import org.junit.Test

class RouteRenderSimplifierTest {
    @Test fun smallRouteIsReturnedUnchanged() {
        val p = listOf(RoutePoint(21.0, 106.0), RoutePoint(21.1, 106.1))
        assertSame(p, RouteRenderSimplifier.reduce(p, 10))
    }

    @Test fun longRouteIsBoundedAndKeepsEndpoints() {
        val points = (0 until 10_000).map {
            RoutePoint(20.0 + it * 0.000001, 106.0 + it * 0.000001)
        }
        val reduced = RouteRenderSimplifier.reduce(points, 500)
        assertTrue(reduced.size <= 500)
        assertEquals(points.first(), reduced.first())
        assertEquals(points.last(), reduced.last())
    }
}
