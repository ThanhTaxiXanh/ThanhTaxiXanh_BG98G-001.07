package vn.thanhtaxixanh.bg98g00107.navigation

import kotlin.math.abs
import kotlin.math.roundToInt

class RouteProgressTracker {
    private var plan: RoutePlan? = null
    private var cumulative = DoubleArray(0)
    private var lastIndex = -1

    fun setRoute(route: RoutePlan) {
        plan = route
        cumulative = DoubleArray(route.points.size)
        for (i in 1 until route.points.size) {
            cumulative[i] = cumulative[i - 1] +
                routeDistanceMeters(route.points[i - 1], route.points[i])
        }
        lastIndex = -1
    }

    fun clear() {
        plan = null
        cumulative = DoubleArray(0)
        lastIndex = -1
    }

    fun update(lat: Double, lon: Double): RouteGuidanceState {
        val route = plan ?: return RouteGuidanceState.EMPTY
        val here = RoutePoint(lat, lon)
        val nearest = nearestIndex(route, here)
        val deviation = routeDistanceMeters(here, route.points[nearest])
        if (deviation > 90.0) {
            return RouteGuidanceState(
                active = true,
                maneuver = ManeuverType.OFF_ROUTE,
                instruction = "Đã lệch tuyến",
                roadName = "",
                distanceMeters = deviation.roundToInt(),
                etaMinutes = remainingEta(route, nearest)
            )
        }
        lastIndex = nearest

        val maneuver = route.maneuvers.firstOrNull { it.trackIndex >= nearest }
        if (maneuver == null) {
            val remain = (cumulative.lastOrNull() ?: 0.0) -
                cumulative.getOrElse(nearest) { 0.0 }
            return RouteGuidanceState(
                active = true,
                maneuver = ManeuverType.ARRIVE,
                instruction = if (remain <= 35.0) "Đã đến điểm đến"
                    else "Tiếp tục đến điểm đến",
                roadName = "",
                distanceMeters = remain.roundToInt().coerceAtLeast(0),
                etaMinutes = remainingEta(route, nearest)
            )
        }

        val distance = (cumulative[maneuver.trackIndex] - cumulative[nearest])
            .roundToInt().coerceAtLeast(0)
        return RouteGuidanceState(
            active = true,
            maneuver = maneuver.type,
            instruction = instruction(maneuver),
            roadName = "",
            distanceMeters = distance,
            etaMinutes = remainingEta(route, nearest)
        )
    }

    private fun nearestIndex(route: RoutePlan, here: RoutePoint): Int {
        val start: Int
        val end: Int
        if (lastIndex < 0) {
            start = 0
            end = route.points.lastIndex
        } else {
            start = (lastIndex - 35).coerceAtLeast(0)
            end = (lastIndex + 260).coerceAtMost(route.points.lastIndex)
        }
        var best = start
        var bestD = Double.POSITIVE_INFINITY
        for (i in start..end) {
            val d = routeDistanceMeters(here, route.points[i])
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (lastIndex >= 0 && bestD > 140.0) {
            best = 0
            bestD = Double.POSITIVE_INFINITY
            for (i in route.points.indices) {
                val d = routeDistanceMeters(here, route.points[i])
                if (d < bestD) {
                    bestD = d
                    best = i
                }
            }
        }
        return best
    }

    private fun remainingEta(route: RoutePlan, index: Int): Int? {
        if (route.totalTimeSec <= 0) return null
        val seconds = if (route.timesSec.size == route.points.size) {
            (route.totalTimeSec - route.timesSec[index]).coerceAtLeast(0.0)
        } else {
            val total = cumulative.lastOrNull()?.takeIf { it > 1.0 } ?: return null
            val remain = (total - cumulative[index]).coerceAtLeast(0.0)
            route.totalTimeSec * (remain / total)
        }
        return (seconds / 60.0).roundToInt().coerceAtLeast(0)
    }

    private fun instruction(m: RouteManeuver): String = when (m.type) {
        ManeuverType.STRAIGHT -> "Đi thẳng"
        ManeuverType.LEFT -> "Rẽ trái"
        ManeuverType.RIGHT -> "Rẽ phải"
        ManeuverType.SLIGHT_LEFT -> "Chếch trái"
        ManeuverType.SLIGHT_RIGHT -> "Chếch phải"
        ManeuverType.SHARP_LEFT -> "Rẽ gắt trái"
        ManeuverType.SHARP_RIGHT -> "Rẽ gắt phải"
        ManeuverType.KEEP_LEFT -> "Giữ bên trái"
        ManeuverType.KEEP_RIGHT -> "Giữ bên phải"
        ManeuverType.U_TURN -> "Quay đầu"
        ManeuverType.ROUNDABOUT -> if (m.exitNumber != 0)
            "Vòng xuyến · lối " + abs(m.exitNumber) else "Vào vòng xuyến"
        ManeuverType.EXIT_LEFT -> "Ra lối bên trái"
        ManeuverType.EXIT_RIGHT -> "Ra lối bên phải"
        ManeuverType.OFF_ROUTE -> "Đã lệch tuyến"
        ManeuverType.ARRIVE -> "Đến điểm đến"
    }
}
