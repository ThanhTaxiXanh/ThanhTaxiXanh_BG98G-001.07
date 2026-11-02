package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.ai.*
import org.junit.Assert.*
import org.junit.Test

class AiCameraReviewTest {
    @Test fun speedNeedsHumanLocationAndConsent() {
        val raw=AiObservation(kind=AiEvidenceKind.SIGN_DETECTION,
            label="P-127",proposedSpeedKph=50)
        assertFalse(raw.canApprove())
        assertFalse(raw.appliesToRoadSegment())
        val approved=raw.copy(lat=21.0,lon=106.0,roadWayId=1234,
            reviewStatus=AiReviewStatus.APPROVED_LOCAL)
        assertTrue(approved.appliesToRoadSegment())
        assertEquals(50,AiObservationRules.approvedSpeed(approved))
    }

    @Test fun roadTypeCannotBecomeLegalSpeedByDefault() {
        val raw=AiObservation(kind=AiEvidenceKind.ROAD_CLASS_SUGGESTION,
            label="Two lane street",proposedSpeedKph=60,lat=21.0,lon=106.0,
            roadWayId=10,reviewStatus=AiReviewStatus.APPROVED_LOCAL)
        assertNull(AiObservationRules.approvedSpeed(raw))
        assertFalse(raw.appliesToRoadSegment())
    }

    @Test fun yoloDecoderUsesClassOrderAndNms() {
        // [4+2 classes, 2 boxes], both same label and overlapping.
        val data=floatArrayOf(
            200f,202f,200f,202f,100f,100f,100f,100f,
            .95f,.90f,.05f,.10f)
        val detections=YoloV8Postprocess.decode(data,6,2,
            listOf("P-127","STOP"),640,.50f)
        assertEquals(1,detections.size)
        assertEquals("P-127",detections[0].label)
    }
}
