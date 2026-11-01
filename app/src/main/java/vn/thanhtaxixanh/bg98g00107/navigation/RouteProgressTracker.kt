package vn.thanhtaxixanh.bg98g00107.navigation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RouteProgressTracker {
    private data class RouteMatch(
        val segmentIndex: Int,
        val fraction: Double,
        val distanceM: Double,
        val routeMeters: Double,
        val score: Double
    )

    private var plan: RoutePlan? = null
    private var cumulative = DoubleArray(0)
    private var lastSegment = -1
    private var lastProgressM = -1.0
    private var badSamples = 0
    private var goodSamples = 0
    private var offRouteLatched = false
    private var lastStableGuidance = RouteGuidanceState.EMPTY

    fun setRoute(route: RoutePlan) {
        plan = route
        cumulative = DoubleArray(route.points.size)
        for (i in 1 until route.points.size) {
            cumulative[i] = cumulative[i - 1] +
                routeDistanceMeters(route.points[i - 1], route.points[i])
        }
        lastSegment = -1
        lastProgressM = -1.0
        badSamples = 0
        goodSamples = 0
        offRouteLatched = false
        lastStableGuidance = RouteGuidanceState.EMPTY
    }

    fun clear() {
        plan = null
        cumulative = DoubleArray(0)
        lastSegment = -1
        lastProgressM = -1.0
        badSamples = 0
        goodSamples = 0
        offRouteLatched = false
        lastStableGuidance = RouteGuidanceState.EMPTY
    }

    fun update(
        lat: Double,
        lon: Double,
        headingDegrees: Double? = null,
        accuracyM: Double? = null
    ): RouteGuidanceState {
        val route = plan ?: return RouteGuidanceState.EMPTY
        val here = RoutePoint(lat, lon)
        val match = nearestSegment(route, here, headingDegrees)
        val threshold = max(
            90.0,
            min(140.0, (accuracyM ?: 0.0).coerceAtLeast(0.0) * 2.5)
        )

        if (match.distanceM > threshold) {
            badSamples++
            goodSamples = 0
            if (badSamples >= 3) {
                offRouteLatched = true
                return offRouteState(route, match)
            }
            return if (lastStableGuidance.active) {
                lastStableGuidance
            } else {
                RouteGuidanceState.EMPTY
            }
        }

        badSamples = 0
        if (offRouteLatched) {
            goodSamples++
            if (goodSamples < 2) return offRouteState(route, match)
            offRouteLatched = false
        }
        goodSamples = 0

        lastSegment = match.segmentIndex
        val progress = if (lastProgressM < 0.0) {
            match.routeMeters
        } else {
            max(lastProgressM - 12.0, match.routeMeters)
        }
        lastProgressM = max(lastProgressM, match.routeMeters)

        val guidance = guidanceFor(route, match, progress)
        lastStableGuidance = guidance
        return guidance
    }

    private fun offRouteState(
        route: RoutePlan,
        match: RouteMatch
    ): RouteGuidanceState = RouteGuidanceState(
        active = true,
        maneuver = ManeuverType.OFF_ROUTE,
        instruction = "Đã lệch tuyến",
        roadName = "",
        distanceMeters = match.distanceM.roundToInt().coerceAtLeast(0),
        etaMinutes = remainingEta(route, match),
        maneuverKey = -1
    )

    private fun guidanceFor(
        route: RoutePlan,
        match: RouteMatch,
        progressM: Double
    ): RouteGuidanceState {
        val maneuver = route.maneuvers.firstOrNull {
            cumulative[it.trackIndex] >= progressM - 12.0
        }
        if (maneuver == null) {
            val remain = ((cumulative.lastOrNull() ?: 0.0) - progressM)
                .coerceAtLeast(0.0)
            return RouteGuidanceState(
                active = true,
                maneuver = ManeuverType.ARRIVE,
                instruction = if (remain <= 35.0) {
                    "Đã đến điểm đến"
                } else {
                    "Tiếp tục đến điểm đến"
                },
                roadName = "",
                distanceMeters = remain.roundToInt(),
                etaMinutes = remainingEta(route, match),
                maneuverKey = route.points.lastIndex
            )
        }

        val maneuverM = cumulative[maneuver.trackIndex]
        return RouteGuidanceState(
            active = true,
            maneuver = maneuver.type,
            instruction = instruction(maneuver),
            roadName = "",
            distanceMeters = (maneuverM - progressM)
                .roundToInt()
                .coerceAtLeast(0),
            etaMinutes = remainingEta(route, match),
            maneuverKey = maneuver.trackIndex,
            exitNumber = maneuver.exitNumber
        )
    }

    private fun nearestSegment(
        route: RoutePlan,
        here: RoutePoint,
        headingDegrees: Double?
    ): RouteMatch {
        if (lastSegment < 0) return coarseNearest(route, here, headingDegrees)

        val start = (lastSegment - 35).coerceAtLeast(0)
        val end = (lastSegment + 260).coerceAtMost(route.points.lastIndex - 1)
        val local = bestInRange(route, here, headingDegrees, start, end)
        return if (local.distanceM > 140.0) {
            coarseNearest(route, here, headingDegrees)
        } else {
            local
        }
    }

    private fun coarseNearest(
        route: RoutePlan,
        here: RoutePoint,
        headingDegrees: Double?
    ): RouteMatch {
        val segmentCount = route.points.size - 1
        val stride = (segmentCount / 2_000).coerceAtLeast(1)
        var coarse = 0
        var bestScore = Double.POSITIVE_INFINITY
        var i = 0
        while (i < segmentCount) {
            val candidate = project(
                route, here, headingDegrees, i
            )
            if (candidate.score < bestScore) {
                bestScore = candidate.score
                coarse = i
            }
            i += stride
        }
        val start = (coarse - stride * 2).coerceAtLeast(0)
        val end = (coarse + stride * 2).coerceAtMost(segmentCount - 1)
        return bestInRange(route, here, headingDegrees, start, end)
    }

    private fun bestInRange(
        route: RoutePlan,
        here: RoutePoint,
        headingDegrees: Double?,
        start: Int,
        end: Int
    ): RouteMatch {
        var best: RouteMatch? = null
        for (i in start..end) {
            val candidate = project(route, here, headingDegrees, i)
            if (best == null || candidate.score < best.score) best = candidate
        }
        return requireNotNull(best)
    }

    private fun project(
        route: RoutePlan,
        here: RoutePoint,
        headingDegrees: Double?,
        segment: Int
    ): RouteMatch {
        val a = route.points[segment]
        val b = route.points[segment + 1]
        val latScale = 111_320.0
        val lonScale = 111_320.0 *
            cos(Math.toRadians((a.lat + b.lat + here.lat) / 3.0))

        val ax = (a.lon - here.lon) * lonScale
        val ay = (a.lat - here.lat) * latScale
        val bx = (b.lon - here.lon) * lonScale
        val by = (b.lat - here.lat) * latScale
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val fraction = if (len2 <= 0.01) {
            0.0
        } else {
            (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        }
        val px = ax + fraction * dx
        val py = ay + fraction * dy
        val distance = sqrt(px * px + py * py)
        val segLength = sqrt(len2)
        val routeMeters = cumulative[segment] + segLength * fraction

        var score = distance
        if (headingDegrees != null && headingDegrees.isFinite() && segLength > 5.0) {
            val segmentHeading = (
                Math.toDegrees(atan2(dx, dy)) + 360.0
            ) % 360.0
            val diff = angularDifference(segmentHeading, headingDegrees)
            score += when {
                diff > 120.0 -> 120.0
                diff > 80.0 -> 45.0
                diff > 50.0 -> 15.0
                else -> 0.0
            }
        }
        if (lastProgressM >= 0.0 && routeMeters < lastProgressM - 50.0) {
            score += min(150.0, (lastProgressM - routeMeters) * 0.5)
        }

        return RouteMatch(
            segmentIndex = segment,
            fraction = fraction,
            distanceM = distance,
            routeMeters = routeMeters,
            score = score
        )
    }

    private fun remainingEta(route: RoutePlan, match: RouteMatch): Int? {
        if (route.totalTimeSec <= 0) return null
        val seconds = if (route.timesSec.size == route.points.size) {
            val t0 = route.timesSec[match.segmentIndex]
            val t1 = route.timesSec[match.segmentIndex + 1]
            val elapsed = t0 + (t1 - t0) * match.fraction
            (route.totalTimeSec - elapsed).coerceAtLeast(0.0)
        } else {
            val total = cumulative.lastOrNull()?.takeIf { it > 1.0 } ?: return null
            val remain = (total - match.routeMeters).coerceAtLeast(0.0)
            route.totalTimeSec * (remain / total)
        }
        return (seconds / 60.0).roundToInt().coerceAtLeast(0)
    }

    private fun angularDifference(a: Double, b: Double): Double =
        abs(((a - b + 540.0) % 360.0) - 180.0)

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
        ManeuverType.ROUNDABOUT -> if (m.exitNumber != 0) {
            "Vòng xuyến · lối " + abs(m.exitNumber)
        } else {
            "Vào vòng xuyến"
        }
        ManeuverType.EXIT_LEFT -> "Ra lối bên trái"
        ManeuverType.EXIT_RIGHT -> "Ra lối bên phải"
        ManeuverType.OFF_ROUTE -> "Đã lệch tuyến"
        ManeuverType.ARRIVE -> "Đến điểm đến"
    }
}
