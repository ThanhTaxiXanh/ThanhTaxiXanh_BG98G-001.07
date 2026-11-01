package vn.thanhtaxixanh.bg98g00107.navigation

/**
 * Immutable UI contract between the future route engine and the dashboard.
 * The renderer never invents turn instructions; inactive state means no route.
 */
enum class ManeuverType {
    STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, U_TURN, ARRIVE
}

data class RouteGuidanceState(
    val active: Boolean,
    val maneuver: ManeuverType,
    val instruction: String,
    val roadName: String,
    val distanceMeters: Int?,
    val etaMinutes: Int?
) {
    init {
        require(distanceMeters == null || distanceMeters >= 0)
        require(etaMinutes == null || etaMinutes >= 0)
    }

    companion object {
        val EMPTY = RouteGuidanceState(
            active = false,
            maneuver = ManeuverType.STRAIGHT,
            instruction = "",
            roadName = "",
            distanceMeters = null,
            etaMinutes = null
        )
    }
}
