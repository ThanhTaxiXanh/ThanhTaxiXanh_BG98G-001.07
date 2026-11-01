package vn.thanhtaxixanh.bg98g00107.ai

import java.util.UUID

/**
 * Evidence is not a legal speed limit until explicitly reviewed.
 * Camera video frames MUST NOT inherit the phone's current GPS coordinates.
 */
internal enum class AiEvidenceKind { SIGN_DETECTION, ROAD_CLASS_SUGGESTION, MANUAL }
internal enum class AiReviewStatus { PENDING, APPROVED_LOCAL, REJECTED }
internal data class AiObservation(
    val id: String = UUID.randomUUID().toString(),
    val detectedAtMs: Long = System.currentTimeMillis(),
    val videoOffsetMs: Long? = null,
    val kind: AiEvidenceKind,
    val label: String,
    val confidence: Float = 0f,
    val proposedSpeedKph: Int? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val roadWayId: Long? = null,
    val evidenceFile: String? = null,
    val source: String = "70mai_import",
    val reviewStatus: AiReviewStatus = AiReviewStatus.PENDING
) {
    fun canApprove(): Boolean =
        reviewStatus == AiReviewStatus.PENDING &&
        lat != null && lon != null &&
        lat.isFinite() && lon.isFinite() &&
        lat in -90.0..90.0 && lon in -180.0..180.0 &&
        (proposedSpeedKph == null || (proposedSpeedKph?.let { it in AiObservationRules.SPEEDS } == true))

    fun appliesToRoadSegment(): Boolean =
        reviewStatus == AiReviewStatus.APPROVED_LOCAL &&
        roadWayId != null && roadWayId > 0 &&
        (proposedSpeedKph?.let { it in AiObservationRules.SPEEDS } == true) &&
        kind != AiEvidenceKind.ROAD_CLASS_SUGGESTION

    fun isSpeedPoint(): Boolean =
        reviewStatus == AiReviewStatus.APPROVED_LOCAL &&
        (proposedSpeedKph?.let { it in AiObservationRules.SPEEDS } == true) &&
        kind != AiEvidenceKind.ROAD_CLASS_SUGGESTION
}

internal object AiObservationRules {
    val SPEEDS = listOf(20,30,40,50,60,70,80,90,100,110,120)
    private val SPEED_SIGN = Regex("(?i)^(?:P[.\\-]?127\\*|speed[_ ]?limit[_ :]*)(20|30|40|50|60|70|80|90|100|110|120)(?:\\s*km/?h)?$")
    fun speedFromLabel(label: String): Int? =
        SPEED_SIGN.matchEntire(label.trim())?.groupValues?.get(1)?.toIntOrNull()
    fun approvedSpeed(observation: AiObservation): Int? =
        observation.proposedSpeedKph?.takeIf {
            observation.isSpeedPoint() && it in SPEEDS
        }
}
