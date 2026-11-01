package vn.thanhtaxixanh.bg98g00107.navigation

import org.json.JSONObject
import kotlin.math.roundToInt

object BRouterGeoJsonParser {
    private const val MAX_JSON_CHARS = 16 * 1024 * 1024

    fun parse(raw: String): RoutePlan {
        require(raw.isNotBlank() && raw.length <= MAX_JSON_CHARS) {
            "Kết quả BRouter trống hoặc quá lớn."
        }
        val root = JSONObject(raw)
        require(root.optString("type") == "FeatureCollection")
        val features = root.getJSONArray("features")
        require(features.length() >= 1)
        val feature = features.getJSONObject(0)
        val geometry = feature.getJSONObject("geometry")
        require(geometry.getString("type") == "LineString")
        val coords = geometry.getJSONArray("coordinates")
        require(coords.length() in 2..250_000)
        val points = ArrayList<RoutePoint>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            points.add(RoutePoint(c.getDouble(1), c.getDouble(0)))
        }

        val props = feature.getJSONObject("properties")
        val totalDistance = props.optString("track-length", "0")
            .toDoubleOrNull()?.roundToInt()?.coerceAtLeast(0) ?: 0
        val totalTime = props.optString("total-time", "0")
            .toDoubleOrNull()?.roundToInt()?.coerceAtLeast(0) ?: 0

        val times = ArrayList<Double>()
        props.optJSONArray("times")?.let { arr ->
            require(arr.length() == points.size) {
                "BRouter times không khớp geometry."
            }
            for (i in 0 until arr.length()) {
                val value = arr.getDouble(i)
                require(value.isFinite() && value >= 0.0)
                times.add(value)
            }
        }

        val maneuvers = ArrayList<RouteManeuver>()
        props.optJSONArray("voicehints")?.let { hints ->
            require(hints.length() <= 20_000)
            for (i in 0 until hints.length()) {
                val h = hints.getJSONArray(i)
                require(h.length() >= 5)
                val index = h.getInt(0)
                if (index !in points.indices) continue
                maneuvers.add(
                    RouteManeuver(
                        trackIndex = index,
                        type = command(h.getInt(1)),
                        exitNumber = h.getInt(2),
                        angleDegrees = h.getInt(4)
                    )
                )
            }
        }
        maneuvers.sortBy { it.trackIndex }

        return RoutePlan(
            points = points,
            timesSec = times,
            maneuvers = maneuvers,
            totalDistanceM = totalDistance,
            totalTimeSec = totalTime
        )
    }

    private fun command(code: Int): ManeuverType = when (code) {
        1 -> ManeuverType.STRAIGHT
        2 -> ManeuverType.LEFT
        3 -> ManeuverType.SLIGHT_LEFT
        4 -> ManeuverType.SHARP_LEFT
        5 -> ManeuverType.RIGHT
        6 -> ManeuverType.SLIGHT_RIGHT
        7 -> ManeuverType.SHARP_RIGHT
        8 -> ManeuverType.KEEP_LEFT
        9 -> ManeuverType.KEEP_RIGHT
        10, 11, 15 -> ManeuverType.U_TURN
        12 -> ManeuverType.OFF_ROUTE
        13, 14 -> ManeuverType.ROUNDABOUT
        17 -> ManeuverType.EXIT_LEFT
        18 -> ManeuverType.EXIT_RIGHT
        else -> ManeuverType.STRAIGHT
    }
}
