package vn.thanhtaxixanh.bg98g00107.signs

import vn.thanhtaxixanh.bg98g00107.navigation.ManeuverType

enum class LaneTurn {
    LEFT, SLIGHT_LEFT, SHARP_LEFT,
    THROUGH,
    RIGHT, SLIGHT_RIGHT, SHARP_RIGHT,
    REVERSE,
    MERGE_LEFT, MERGE_RIGHT
}

data class LaneGuidanceLane(
    val turns: List<LaneTurn>,
    val destination: String?,
    val recommended: Boolean
)

data class LaneGuidanceState(
    val lanes: List<LaneGuidanceLane>,
    val destination: String?,
    val roadWayId: Long?,
    val status: String
) {
    val visible: Boolean get() = lanes.isNotEmpty()

    companion object {
        val EMPTY = LaneGuidanceState(
            emptyList(), null, null, "Không có hướng dẫn làn đủ tin cậy"
        )
    }
}

/**
 * Conservative OSM lane interpreter.
 *
 * It never guesses direction. Bidirectional roads require :forward/:backward
 * lane tags. Generic turn:lanes is accepted only for a normal forward oneway.
 * Lane order is preserved exactly as OSM stores it: driver-left to
 * driver-right for the respective traffic direction.
 */
internal object LaneGuidanceResolver {
    private data class Directional(
        val laneCount: Int?,
        val turns: String?,
        val destinations: String?,
        val destination: String?
    )

    fun resolve(
        road: BundledRoadContext,
        forward: Boolean?,
        maneuver: ManeuverType
    ): LaneGuidanceState {
        val direction = forward ?: return LaneGuidanceState.EMPTY
        val raw = selectDirectional(road, direction) ?: return LaneGuidanceState.EMPTY
        val turnValues = raw.turns?.let(::splitPreserve)
            ?: return LaneGuidanceState.EMPTY
        if (turnValues.size !in 1..8) return LaneGuidanceState.EMPTY
        if (raw.laneCount != null && raw.laneCount != turnValues.size) {
            return LaneGuidanceState.EMPTY
        }

        val parsedTurns = ArrayList<List<LaneTurn>>(turnValues.size)
        for (value in turnValues) {
            val parsed = parseTurns(value) ?: return LaneGuidanceState.EMPTY
            parsedTurns.add(parsed)
        }
        if (parsedTurns.all { it.isEmpty() }) return LaneGuidanceState.EMPTY

        val destinations = raw.destinations?.let(::splitPreserve)
            ?.takeIf { it.size == turnValues.size }
        val target = targetTurns(maneuver)
        val lanes = parsedTurns.mapIndexed { index, turns ->
            LaneGuidanceLane(
                turns = turns,
                destination = destinations
                    ?.getOrNull(index)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                recommended = target.isNotEmpty() &&
                    turns.any { it in target }
            )
        }
        return LaneGuidanceState(
            lanes = lanes,
            destination = raw.destination?.trim()?.takeIf { it.isNotEmpty() },
            roadWayId = road.geometry.wayId,
            status = if (lanes.any { it.recommended }) {
                "Hướng dẫn làn từ OpenStreetMap"
            } else {
                "Có dữ liệu làn OSM; chưa xác định làn khuyến nghị"
            }
        )
    }

    private fun selectDirectional(
        road: BundledRoadContext,
        forward: Boolean
    ): Directional? {
        val oneway = road.geometry.oneway
        return when {
            oneway == 1 && forward -> Directional(
                laneCount = road.forwardLanes ?: road.lanes,
                turns = road.turnLanesForward ?: road.turnLanes,
                destinations = road.destinationLanesForward
                    ?: road.destinationLanes,
                destination = road.destinationForward ?: road.destination
            )
            oneway == -1 && !forward -> Directional(
                laneCount = road.backwardLanes ?: road.lanes,
                turns = road.turnLanesBackward,
                destinations = road.destinationLanesBackward,
                destination = road.destinationBackward
            )
            oneway == 0 && forward -> Directional(
                laneCount = road.forwardLanes,
                turns = road.turnLanesForward,
                destinations = road.destinationLanesForward,
                destination = road.destinationForward
            )
            oneway == 0 && !forward -> Directional(
                laneCount = road.backwardLanes,
                turns = road.turnLanesBackward,
                destinations = road.destinationLanesBackward,
                destination = road.destinationBackward
            )
            else -> null
        }
    }

    private fun splitPreserve(raw: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        for (i in raw.indices) {
            if (raw[i] == '|') {
                result.add(raw.substring(start, i))
                start = i + 1
            }
        }
        result.add(raw.substring(start))
        return result
    }

    private fun parseTurns(raw: String): List<LaneTurn>? {
        val value = raw.trim().lowercase()
        if (value.isEmpty() || value == "none") return emptyList()
        val out = ArrayList<LaneTurn>()
        for (part in value.split(';')) {
            val turn = when (part.trim()) {
                "left" -> LaneTurn.LEFT
                "slight_left" -> LaneTurn.SLIGHT_LEFT
                "sharp_left" -> LaneTurn.SHARP_LEFT
                "through" -> LaneTurn.THROUGH
                "right" -> LaneTurn.RIGHT
                "slight_right" -> LaneTurn.SLIGHT_RIGHT
                "sharp_right" -> LaneTurn.SHARP_RIGHT
                "reverse" -> LaneTurn.REVERSE
                "merge_to_left" -> LaneTurn.MERGE_LEFT
                "merge_to_right" -> LaneTurn.MERGE_RIGHT
                else -> return null
            }
            if (turn !in out) out.add(turn)
        }
        return out
    }

    private fun targetTurns(maneuver: ManeuverType): Set<LaneTurn> = when (maneuver) {
        ManeuverType.LEFT -> setOf(
            LaneTurn.LEFT, LaneTurn.SLIGHT_LEFT, LaneTurn.SHARP_LEFT
        )
        ManeuverType.SLIGHT_LEFT,
        ManeuverType.KEEP_LEFT,
        ManeuverType.EXIT_LEFT -> setOf(
            LaneTurn.SLIGHT_LEFT, LaneTurn.LEFT, LaneTurn.SHARP_LEFT
        )
        ManeuverType.SHARP_LEFT -> setOf(LaneTurn.SHARP_LEFT, LaneTurn.LEFT)
        ManeuverType.RIGHT -> setOf(
            LaneTurn.RIGHT, LaneTurn.SLIGHT_RIGHT, LaneTurn.SHARP_RIGHT
        )
        ManeuverType.SLIGHT_RIGHT,
        ManeuverType.KEEP_RIGHT,
        ManeuverType.EXIT_RIGHT -> setOf(
            LaneTurn.SLIGHT_RIGHT, LaneTurn.RIGHT, LaneTurn.SHARP_RIGHT
        )
        ManeuverType.SHARP_RIGHT -> setOf(LaneTurn.SHARP_RIGHT, LaneTurn.RIGHT)
        ManeuverType.STRAIGHT -> setOf(LaneTurn.THROUGH)
        ManeuverType.U_TURN -> setOf(LaneTurn.REVERSE)
        else -> emptySet()
    }
}
