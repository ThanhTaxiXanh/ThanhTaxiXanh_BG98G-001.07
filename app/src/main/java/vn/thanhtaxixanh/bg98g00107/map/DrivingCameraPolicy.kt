package vn.thanhtaxixanh.bg98g00107.map

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln

internal data class DrivingCameraTarget(
    val zoom: Double,
    val lookAheadM: Double
)

internal object DrivingCameraPolicy {
    fun target(
        speedKmh: Double,
        lookAheadSeconds: Int,
        referenceMeters: Int = 120
    ): DrivingCameraTarget {
        val speed = speedKmh.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val baseZoom = when {
            speed <= 10.0 -> 18.0 - speed / 10.0 * 0.25
            speed <= 30.0 -> 17.75 - (speed - 10.0) / 20.0 * 0.65
            speed <= 60.0 -> 17.10 - (speed - 30.0) / 30.0 * 0.90
            speed <= 90.0 -> 16.20 - (speed - 60.0) / 30.0 * 0.80
            speed <= 120.0 -> 15.40 - (speed - 90.0) / 30.0 * 0.65
            else -> (14.75 - (speed - 120.0) / 60.0 * 0.55).coerceAtLeast(14.2)
        }
        val reference = referenceMeters.coerceIn(20, 800).toDouble()
        val zoomBias = ln(120.0 / reference) / ln(2.0)
        val zoom = (baseZoom + zoomBias).coerceIn(13.8, 19.7)
        val seconds = lookAheadSeconds.coerceIn(1, 8)
        val lookAhead = (22.0 + speed / 3.6 * seconds)
            .coerceIn(22.0, 220.0)
        return DrivingCameraTarget(zoom, lookAhead)
    }

    fun targetPoint(
        lat: Double,
        lon: Double,
        headingDegrees: Double,
        lookAheadM: Double
    ): Pair<Double, Double> {
        if (!lat.isFinite() || !lon.isFinite() ||
            !headingDegrees.isFinite() || lookAheadM <= 0.0
        ) return lat to lon
        val br = Math.toRadians(headingDegrees)
        val nextLat = lat + cos(br) * lookAheadM / 111_320.0
        val lonScale = 111_320.0 *
            cos(Math.toRadians(lat)).coerceAtLeast(0.05)
        val nextLon = lon + sin(br) * lookAheadM / lonScale
        return nextLat to nextLon
    }
}
