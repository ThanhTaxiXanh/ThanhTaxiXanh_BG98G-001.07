package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.navigation.ManeuverType
import vn.thanhtaxixanh.bg98g00107.road.SpeedWayGeometry
import vn.thanhtaxixanh.bg98g00107.signs.BundledRoadContext
import vn.thanhtaxixanh.bg98g00107.signs.LaneGuidanceResolver
import vn.thanhtaxixanh.bg98g00107.signs.LaneTurn

class LaneGuidanceResolverTest {
    private fun road(
        oneway: Int = 0,
        lanes: Int? = null,
        forwardLanes: Int? = null,
        backwardLanes: Int? = null,
        turn: String? = null,
        turnForward: String? = null,
        turnBackward: String? = null,
        destinationLanes: String? = null,
        destinationLanesForward: String? = null,
        destinationLanesBackward: String? = null
    ) = BundledRoadContext(
        geometry = SpeedWayGeometry(
            99L, 0,
            doubleArrayOf(21.0, 106.0, 21.001, 106.0),
            oneway = oneway
        ),
        highway = "primary",
        name = "Test",
        all = null,
        forward = null,
        backward = null,
        allConditional = null,
        forwardConditional = null,
        backwardConditional = null,
        hasSpeedTag = false,
        lanes = lanes,
        forwardLanes = forwardLanes,
        backwardLanes = backwardLanes,
        turnLanes = turn,
        turnLanesForward = turnForward,
        turnLanesBackward = turnBackward,
        destination = null,
        destinationForward = null,
        destinationBackward = null,
        destinationLanes = destinationLanes,
        destinationLanesForward = destinationLanesForward,
        destinationLanesBackward = destinationLanesBackward
    )

    @Test fun backwardLaneOrderIsAlreadyDriverLeftToRight() {
        val state = LaneGuidanceResolver.resolve(
            road(
                backwardLanes = 2,
                turnBackward = "through|through;right",
                destinationLanesBackward = "Trung tâm|QL1A"
            ),
            forward = false,
            maneuver = ManeuverType.RIGHT
        )
        assertEquals(2, state.lanes.size)
        assertFalse(state.lanes[0].recommended)
        assertTrue(state.lanes[1].recommended)
        assertEquals("Trung tâm", state.lanes[0].destination)
        assertEquals("QL1A", state.lanes[1].destination)
        assertEquals(listOf(LaneTurn.THROUGH), state.lanes[0].turns)
    }

    @Test fun bidirectionalGenericTurnLanesAreNotGuessed() {
        val state = LaneGuidanceResolver.resolve(
            road(lanes = 2, turn = "left|right"),
            forward = true,
            maneuver = ManeuverType.LEFT
        )
        assertFalse(state.visible)
    }

    @Test fun forwardOnewayMayUseGenericLaneTags() {
        val state = LaneGuidanceResolver.resolve(
            road(
                oneway = 1,
                lanes = 3,
                turn = "left|through|right",
                destinationLanes = "A|B|C"
            ),
            forward = true,
            maneuver = ManeuverType.STRAIGHT
        )
        assertEquals(3, state.lanes.size)
        assertTrue(state.lanes[1].recommended)
        assertEquals("B", state.lanes[1].destination)
    }

    @Test fun mismatchedLaneCountIsRejected() {
        val state = LaneGuidanceResolver.resolve(
            road(
                forwardLanes = 3,
                turnForward = "through|right"
            ),
            forward = true,
            maneuver = ManeuverType.RIGHT
        )
        assertFalse(state.visible)
    }

    @Test fun unknownOsmTurnTokenIsRejectedRatherThanGuessed() {
        val state = LaneGuidanceResolver.resolve(
            road(
                forwardLanes = 2,
                turnForward = "through|mystery_turn"
            ),
            forward = true,
            maneuver = ManeuverType.RIGHT
        )
        assertFalse(state.visible)
    }
}
