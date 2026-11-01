package vn.thanhtaxixanh.bg98g00107.navigation

import vn.thanhtaxixanh.bg98g00107.signs.RegionalOsmStore
import vn.thanhtaxixanh.bg98g00107.signs.SignItem
import vn.thanhtaxixanh.bg98g00107.signs.UpcomingSign

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Conservative offline camera warnings constrained to an active BRouter route.
 *
 * Directionless nationwide OSM markers never change the legal speed limit.
 * Only camera/red-light-camera points close to the route polyline can become
 * route warnings.
 */
internal class RouteTrafficMarkerMatcher {
    private data class Projection(
        val segment: Int,
        val fraction: Double,
        val distanceM: Double,
        val progressM: Double,
        val bearing: Double
    )

    private var route: RoutePlan? = null
    private var cumulative = DoubleArray(0)
    private var lastSegment = -1

    fun setRoute(plan: RoutePlan) {
        route = plan
        cumulative = DoubleArray(plan.points.size)
        for (i in 1 until plan.points.size) {
            cumulative[i] = cumulative[i - 1] +
                routeDistanceMeters(plan.points[i - 1], plan.points[i])
        }
        lastSegment = -1
    }

    fun clear() {
        route = null
        cumulative = DoubleArray(0)
        lastSegment = -1
    }

    fun upcoming(
        lat: Double,
        lon: Double,
        headingDegrees: Double,
        moving: Boolean,
        accuracyM: Double,
        markers: List<RegionalOsmStore.Marker>,
        maxLookaheadM: Int = 600
    ): List<UpcomingSign> {
        val plan = route ?: return emptyList()
        if (!moving || !headingDegrees.isFinite() ||
            !lat.isFinite() || !lon.isFinite() ||
            !accuracyM.isFinite() || accuracyM < 0.0 || accuracyM > 25.0 ||
            markers.isEmpty()
        ) return emptyList()

        val here = RoutePoint(lat, lon)
        val current = nearestCurrent(plan, here, headingDegrees)
        val currentGate = min(40.0, max(12.0, accuracyM * 2.0 + 8.0))
        if (current.distanceM > currentGate) return emptyList()
        if (angleDiff(current.bearing, headingDegrees) > 60.0) return emptyList()
        lastSegment = current.segment

        val lookahead = maxLookaheadM.coerceIn(200, 1200).toDouble()
        val maxProgress = current.progressM + lookahead + 50.0
        var endSegment = current.segment
        while (endSegment + 1 < plan.points.lastIndex &&
            cumulative[endSegment] <= maxProgress) {
            endSegment++
        }
        val startSegment = (current.segment - 2).coerceAtLeast(0)

        val out = ArrayList<UpcomingSign>()
        val seen = HashSet<String>()
        for (marker in markers) {
            if (marker.type != "camera" && marker.type != "redlight_camera") continue
            if (!seen.add(marker.osmId)) continue
            if (localDistance(lat, lon, marker.lat, marker.lon) >
                lookahead + 100.0) continue

            val projection = projectMarker(
                plan, marker.lat, marker.lon, startSegment, endSegment
            ) ?: continue
            if (projection.distanceM > 12.0) continue

            val along = projection.progressM - current.progressM
            if (along !in 20.0..lookahead) continue

            val nodeId = marker.osmId
                .takeIf { it.startsWith("node/") }
                ?.substringAfter('/')
                ?.toLongOrNull()
            val item = SignItem(
                lat = marker.lat,
                lon = marker.lon,
                type = marker.type,
                label = "CAM",
                name = if (marker.type == "redlight_camera") {
                    "Camera giám sát đèn đỏ phía trước (OSM offline)"
                } else {
                    "Camera tốc độ phía trước (OSM offline)"
                },
                value = 0,
                osmNodeId = nodeId
            )
            out.add(UpcomingSign(item, along.roundToInt().coerceAtLeast(1)))
        }
        return out.sortedBy { it.distM }.take(3)
    }

    private fun nearestCurrent(
        plan: RoutePlan,
        here: RoutePoint,
        heading: Double
    ): Projection {
        if (lastSegment >= 0) {
            val local = bestInRange(
                plan,
                here.lat,
                here.lon,
                (lastSegment - 40).coerceAtLeast(0),
                (lastSegment + 300).coerceAtMost(plan.points.lastIndex - 1)
            )
            if (local.distanceM <= 80.0) return local
        }

        val count = plan.points.size - 1
        val stride = (count / 2_000).coerceAtLeast(1)
        var coarse = 0
        var bestScore = Double.POSITIVE_INFINITY
        var i = 0
        while (i < count) {
            val p = project(plan, here.lat, here.lon, i)
            val score = p.distanceM + headingPenalty(p.bearing, heading)
            if (score < bestScore) {
                bestScore = score
                coarse = i
            }
            i += stride
        }
        return bestInRange(
            plan,
            here.lat,
            here.lon,
            (coarse - stride * 2).coerceAtLeast(0),
            (coarse + stride * 2).coerceAtMost(count - 1)
        )
    }

    private fun projectMarker(
        plan: RoutePlan,
        lat: Double,
        lon: Double,
        start: Int,
        end: Int
    ): Projection? {
        var best: Projection? = null
        var second: Projection? = null
        for (i in start..end) {
            val p = project(plan, lat, lon, i)
            if (best == null || p.distanceM < best.distanceM) {
                second = best
                best = p
            } else if (second == null || p.distanceM < second.distanceM) {
                second = p
            }
        }
        val winner = best ?: return null
        if (second != null &&
            second.distanceM - winner.distanceM <= 4.0 &&
            abs(second.progressM - winner.progressM) > 30.0
        ) return null
        return winner
    }

    private fun bestInRange(
        plan: RoutePlan,
        lat: Double,
        lon: Double,
        start: Int,
        end: Int
    ): Projection {
        var best: Projection? = null
        for (i in start..end) {
            val p = project(plan, lat, lon, i)
            if (best == null || p.distanceM < best.distanceM) best = p
        }
        return requireNotNull(best)
    }

    private fun project(
        plan: RoutePlan,
        lat: Double,
        lon: Double,
        segment: Int
    ): Projection {
        val a = plan.points[segment]
        val b = plan.points[segment + 1]
        val lonScale = 111_320.0 *
            cos(Math.toRadians((a.lat + b.lat + lat) / 3.0))
        val ax = (a.lon - lon) * lonScale
        val ay = (a.lat - lat) * 111_320.0
        val bx = (b.lon - lon) * lonScale
        val by = (b.lat - lat) * 111_320.0
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val fraction = if (len2 <= 0.01) 0.0
            else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + fraction * dx
        val py = ay + fraction * dy
        val length = kotlin.math.sqrt(len2.coerceAtLeast(0.0))
        val bearing = if (length > 0.1) {
            (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
        } else 0.0
        return Projection(
            segment,
            fraction,
            hypot(px, py),
            cumulative[segment] + length * fraction,
            bearing
        )
    }

    private fun headingPenalty(routeHeading: Double, gpsHeading: Double): Double {
        val d = angleDiff(routeHeading, gpsHeading)
        return when {
            d > 120.0 -> 120.0
            d > 80.0 -> 45.0
            d > 50.0 -> 15.0
            else -> 0.0
        }
    }

    private fun localDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val x = (lon2 - lon1) * 111_320.0 *
            cos(Math.toRadians((lat1 + lat2) / 2.0))
        val y = (lat2 - lat1) * 111_320.0
        return hypot(x, y)
    }

    private fun angleDiff(a: Double, b: Double): Double =
        abs(((a - b + 540.0) % 360.0) - 180.0)
}
