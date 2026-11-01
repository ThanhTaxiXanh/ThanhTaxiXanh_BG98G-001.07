package vn.thanhtaxixanh.bg98g00107.navigation

import kotlin.math.ceil

/**
 * Keeps the full BRouter geometry for GPS progress matching but bounds the
 * number of coordinates handed to MapLibre. This is a visual-only reduction.
 */
object RouteRenderSimplifier {
    const val DEFAULT_MAX_POINTS = 12_000

    fun reduce(
        points: List<RoutePoint>,
        maxPoints: Int = DEFAULT_MAX_POINTS
    ): List<RoutePoint> {
        require(maxPoints >= 2)
        if (points.size <= maxPoints) return points
        val stride = ceil((points.size - 1).toDouble() / (maxPoints - 1)).toInt()
            .coerceAtLeast(1)
        val out = ArrayList<RoutePoint>(maxPoints)
        var i = 0
        while (i < points.lastIndex) {
            out.add(points[i])
            i += stride
        }
        if (out.lastOrNull() != points.last()) out.add(points.last())
        return out
    }
}
