package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePreferences

class RoutePreferencesTest {
    @Test fun brouterExtraParamsMatchCarVarioVariables() {
        val params = RoutePreferences(
            avoidMotorways = true,
            avoidTollRoads = false,
            avoidUnpavedRoads = true
        ).brouterExtraParams()
        assertEquals(
            "avoid_motorways=1&avoid_toll=0&avoid_unpaved=1",
            params
        )
    }
}
