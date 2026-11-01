package vn.thanhtaxixanh.bg98g00107.navigation

import kotlin.math.cos
import kotlin.math.sqrt

data class RoutePoint(val lat: Double, val lon: Double) {
    init {
        require(lat.isFinite() && lon.isFinite())
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
    }
}

data class RoutePreferences(
    val avoidMotorways: Boolean = false,
    val avoidTollRoads: Boolean = false,
    val avoidUnpavedRoads: Boolean = true
) {
    fun brouterExtraParams(): String = listOf(
        "avoid_motorways=" + if (avoidMotorways) "1" else "0",
        "avoid_toll=" + if (avoidTollRoads) "1" else "0",
        "avoid_unpaved=" + if (avoidUnpavedRoads) "1" else "0"
    ).joinToString("&")
}

data class RouteRequest(
    val start: RoutePoint,
    val destination: RoutePoint,
    val headingDegrees: Double? = null,
    val preferences: RoutePreferences = RoutePreferences()
)

data class RouteManeuver(
    val trackIndex: Int,
    val type: ManeuverType,
    val exitNumber: Int = 0,
    val angleDegrees: Int = 0
)

data class RoutePlan(
    val points: List<RoutePoint>,
    val timesSec: List<Double>,
    val maneuvers: List<RouteManeuver>,
    val totalDistanceM: Int,
    val totalTimeSec: Int
) {
    init {
        require(points.size in 2..250_000)
        require(timesSec.isEmpty() || timesSec.size == points.size)
        require(maneuvers.size <= 20_000)
        require(maneuvers.all { it.trackIndex in points.indices })
        require(totalDistanceM >= 0 && totalTimeSec >= 0)
    }
}

internal fun routeDistanceMeters(a: RoutePoint, b: RoutePoint): Double {
    val dx = (b.lon - a.lon) * 111_320.0 *
        cos(Math.toRadians((a.lat + b.lat) / 2.0))
    val dy = (b.lat - a.lat) * 111_320.0
    return sqrt(dx * dx + dy * dy)
}
