package vn.thanhtaxixanh.bg98g00107.navigation

import vn.thanhtaxixanh.bg98g00107.signs.SignsState
import vn.thanhtaxixanh.bg98g00107.signs.UpcomingSign
import kotlin.math.max
import kotlin.math.roundToInt

data class TrafficVoicePrompt(val token: String, val text: String)

object TrafficVoicePromptPolicy {
    fun limitChanged(state: SignsState, previousLimit: Int?): TrafficVoicePrompt? {
        val limit = state.limit ?: return null
        if (state.estimated || limit == previousLimit) return null
        return TrafficVoicePrompt(
            token = "limit:$limit",
            text = "Giới hạn tốc độ $limit ki lô mét một giờ."
        )
    }

    fun overspeed(
        state: SignsState,
        speedKmh: Int,
        toleranceKmh: Int
    ): TrafficVoicePrompt? {
        val limit = state.limit ?: return null
        if (state.estimated) return null
        if (speedKmh <= limit + toleranceKmh.coerceIn(0, 20)) return null
        return TrafficVoicePrompt(
            token = "overspeed:$limit",
            text = "Bạn đang vượt giới hạn tốc độ $limit ki lô mét một giờ."
        )
    }

    fun camera(
        state: SignsState,
        farDistanceM: Int = 600,
        nearDistanceM: Int = 180,
        finalDistanceM: Int = 70,
        warnSpeedCameras: Boolean = true,
        warnRedLightCameras: Boolean = true
    ): TrafficVoicePrompt? {
        val far = farDistanceM.coerceIn(200, 1200)
        val near = nearDistanceM.coerceIn(80, 500)
            .coerceAtMost((far - 50).coerceAtLeast(80))
        val final = finalDistanceM.coerceIn(30, 150)
            .coerceAtMost((near - 30).coerceAtLeast(30))
        val candidate = state.upcoming.asSequence()
            .filter {
                (it.item.type == "camera" && warnSpeedCameras) ||
                    (it.item.type == "redlight_camera" && warnRedLightCameras)
            }
            .filter { it.distM in 20..far }
            .minByOrNull { it.distM } ?: return null
        val stage = when {
            candidate.distM <= final -> "final"
            candidate.distM <= near -> "near"
            else -> "far"
        }
        val id = cameraIdentity(candidate)
        val kind = if (candidate.item.type == "redlight_camera") {
            "camera giám sát đèn đỏ"
        } else {
            "camera tốc độ"
        }
        val text = when (stage) {
            "final" -> "Sắp tới $kind."
            "near" -> "Còn ${spokenDistance(candidate.distM)} đến $kind."
            else -> kind.replaceFirstChar { it.uppercase() } +
                " phía trước ${spokenDistance(candidate.distM)}."
        }
        return TrafficVoicePrompt("camera:$id:$stage", text)
    }

    private fun cameraIdentity(sign: UpcomingSign): String =
        sign.item.osmNodeId?.toString() ?:
            "${(sign.item.lat * 100000).roundToInt()}:" +
            "${(sign.item.lon * 100000).roundToInt()}"

    private fun spokenDistance(distanceM: Int): String {
        val step = when {
            distanceM >= 500 -> 50
            distanceM >= 100 -> 20
            else -> 10
        }
        val rounded = max(step, ((distanceM + step / 2) / step) * step)
        return "$rounded mét"
    }
}
