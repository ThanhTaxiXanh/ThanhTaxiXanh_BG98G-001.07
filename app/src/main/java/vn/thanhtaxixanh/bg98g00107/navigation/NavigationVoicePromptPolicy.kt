package vn.thanhtaxixanh.bg98g00107.navigation

import kotlin.math.max
import kotlin.math.roundToInt

data class NavigationVoicePrompt(
    val token: String,
    val text: String,
    val offRoute: Boolean = false
)

object NavigationVoicePromptPolicy {
    fun prompt(
        state: RouteGuidanceState,
        speedKmh: Int
    ): NavigationVoicePrompt? {
        if (!state.active) return null

        if (state.maneuver == ManeuverType.OFF_ROUTE) {
            return NavigationVoicePrompt(
                token = "off-route",
                text = "Bạn đã lệch tuyến. Đang tính lại tuyến.",
                offRoute = true
            )
        }

        if (state.maneuver == ManeuverType.ARRIVE) {
            val d = state.distanceMeters ?: return null
            if (d > 45) return null
            return NavigationVoicePrompt(
                token = "arrive:" + state.maneuverKey,
                text = "Bạn đã đến điểm đến."
            )
        }

        val distance = state.distanceMeters ?: return null
        val thresholds = when {
            speedKmh >= 80 -> intArrayOf(900, 400, 140)
            speedKmh >= 50 -> intArrayOf(700, 300, 100)
            speedKmh >= 25 -> intArrayOf(500, 200, 70)
            else -> intArrayOf(300, 120, 50)
        }
        val stage = when {
            distance <= thresholds[2] -> "near"
            distance <= thresholds[1] -> "mid"
            distance <= thresholds[0] -> "far"
            else -> return null
        }

        // Avoid noisy "go straight" prompts far away.
        if (state.maneuver == ManeuverType.STRAIGHT && stage == "far") return null

        val instruction = spokenInstruction(state)
        val text = if (stage == "near") {
            instruction
        } else {
            "Còn " + spokenDistance(distance) + ", " +
                instruction.replaceFirstChar { it.lowercase() }
        }
        return NavigationVoicePrompt(
            token = state.maneuverKey.toString() + ":" + stage,
            text = text
        )
    }

    private fun spokenInstruction(state: RouteGuidanceState): String = when (
        state.maneuver
    ) {
        ManeuverType.ROUNDABOUT -> {
            if (state.exitNumber != 0) {
                "Đi vào vòng xuyến, ra ở lối thứ " +
                    kotlin.math.abs(state.exitNumber) + "."
            } else {
                "Đi vào vòng xuyến."
            }
        }
        ManeuverType.EXIT_LEFT -> "Ra lối bên trái."
        ManeuverType.EXIT_RIGHT -> "Ra lối bên phải."
        else -> state.instruction.trim().trimEnd('.') + "."
    }

    private fun spokenDistance(distanceM: Int): String {
        if (distanceM >= 1000) {
            val km10 = (distanceM / 100.0).roundToInt()
            val whole = km10 / 10
            val tenth = km10 % 10
            return if (tenth == 0) {
                whole.toString() + " ki lô mét"
            } else {
                whole.toString() + " phẩy " + tenth + " ki lô mét"
            }
        }
        val step = when {
            distanceM >= 500 -> 50
            distanceM >= 100 -> 20
            else -> 10
        }
        val rounded = max(step, ((distanceM + step / 2) / step) * step)
        return rounded.toString() + " mét"
    }
}
