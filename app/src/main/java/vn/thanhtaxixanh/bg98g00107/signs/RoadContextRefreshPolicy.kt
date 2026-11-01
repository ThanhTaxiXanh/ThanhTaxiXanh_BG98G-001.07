package vn.thanhtaxixanh.bg98g00107.signs

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class RoadContextQueryPlan(
    val centerLat: Double,
    val centerLon: Double,
    val radiusM: Double,
    val refreshDistanceM: Double,
    val minIntervalMs: Long
)

/**
 * Keeps the offline road-context cache slightly ahead of a moving vehicle.
 *
 * The policy only changes which static OSM candidates are preloaded. Exact
 * SpeedWayMatcher geometry/heading/accuracy checks remain authoritative.
 */
internal object RoadContextRefreshPolicy {
    fun plan(
        lat: Double,
        lon: Double,
        headingDegrees: Double,
        moving: Boolean,
        speedMs: Double
    ): RoadContextQueryPlan {
        val safeSpeed = speedMs.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val lookAheadM = if (moving && headingDegrees.isFinite()) {
            (safeSpeed * 3.5).coerceIn(12.0, 80.0)
        } else 0.0
        val radiusM = when {
            safeSpeed >= 22.0 -> 230.0
            safeSpeed >= 14.0 -> 205.0
            safeSpeed >= 7.0 -> 180.0
            else -> 150.0
        }
        val refreshDistanceM = when {
            safeSpeed >= 22.0 -> 70.0
            safeSpeed >= 14.0 -> 58.0
            safeSpeed >= 7.0 -> 48.0
            else -> 38.0
        }
        val minIntervalMs = when {
            safeSpeed >= 22.0 -> 650L
            safeSpeed >= 14.0 -> 800L
            safeSpeed >= 7.0 -> 1_000L
            else -> 1_500L
        }

        if (lookAheadM <= 0.0) {
            return RoadContextQueryPlan(
                lat, lon, radiusM, refreshDistanceM, minIntervalMs
            )
        }

        val br = Math.toRadians(headingDegrees)
        val centerLat = lat + cos(br) * lookAheadM / 111_320.0
        val lonScale = 111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05)
        val centerLon = lon + sin(br) * lookAheadM / lonScale
        return RoadContextQueryPlan(
            centerLat, centerLon, radiusM, refreshDistanceM, minIntervalMs
        )
    }

    fun shouldRefresh(
        nowMs: Long,
        lastFetchMs: Long,
        lastCenterLat: Double,
        lastCenterLon: Double,
        plan: RoadContextQueryPlan,
        hasCandidates: Boolean
    ): Boolean {
        if (lastFetchMs < 0L || !lastCenterLat.isFinite() ||
            !lastCenterLon.isFinite()) return true

        val elapsed = nowMs - lastFetchMs
        if (elapsed < 0L || elapsed < plan.minIntervalMs) return false

        // Empty/overflow/failed queries are retried, but never every GPS tick.
        if (!hasCandidates && elapsed >= 2_000L) return true

        return distanceM(
            lastCenterLat, lastCenterLon, plan.centerLat, plan.centerLon
        ) >= plan.refreshDistanceM
    }

    fun covers(
        lat: Double,
        lon: Double,
        plan: RoadContextQueryPlan,
        safetyMarginM: Double = 35.0
    ): Boolean {
        if (!lat.isFinite() || !lon.isFinite() ||
            !safetyMarginM.isFinite() || safetyMarginM < 0.0) return false
        val usableRadius = (plan.radiusM - safetyMarginM).coerceAtLeast(25.0)
        return distanceM(lat, lon, plan.centerLat, plan.centerLon) <= usableRadius
    }

    internal fun distanceM(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val meanLat = (lat1 + lat2) / 2.0
        val dx = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians(meanLat))
        val dy = (lat2 - lat1) * 111_320.0
        return hypot(dx, dy)
    }
}
