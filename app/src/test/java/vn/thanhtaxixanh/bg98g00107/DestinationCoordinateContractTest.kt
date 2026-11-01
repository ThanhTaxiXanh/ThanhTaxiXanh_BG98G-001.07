package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.*
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePoint

class DestinationCoordinateContractTest {
    @Test fun routePointRejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            RoutePoint(91.0, 106.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutePoint(21.0, 181.0)
        }
    }

    @Test fun validVietnamCoordinateIsAccepted() {
        val p = RoutePoint(21.2817, 106.1970)
        assertEquals(21.2817, p.lat, 0.0)
        assertEquals(106.1970, p.lon, 0.0)
    }
}
