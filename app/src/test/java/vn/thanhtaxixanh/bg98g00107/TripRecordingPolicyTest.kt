package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.trips.TripRecordingPolicy

class TripRecordingPolicyTest {
    @Test fun tripStartsOnlyWhenVehicleActuallyMoves() {
        assertFalse(TripRecordingPolicy.shouldStart(0.7))
        assertTrue(TripRecordingPolicy.shouldStart(1.2))
    }

    @Test fun normalTrafficStopDoesNotFinishTrip() {
        assertFalse(
            TripRecordingPolicy.shouldFinishForStop(5L * 60L * 1_000L)
        )
        assertTrue(
            TripRecordingPolicy.shouldFinishForStop(15L * 60L * 1_000L)
        )
    }

    @Test fun longGpsGapClosesOldTrip() {
        assertFalse(
            TripRecordingPolicy.shouldFinishForGap(10L * 60L * 1_000L)
        )
        assertTrue(
            TripRecordingPolicy.shouldFinishForGap(30L * 60L * 1_000L)
        )
    }
}
