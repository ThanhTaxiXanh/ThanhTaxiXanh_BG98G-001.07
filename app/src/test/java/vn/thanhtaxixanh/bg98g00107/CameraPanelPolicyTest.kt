package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.dashboard.CameraPanelPolicy
import vn.thanhtaxixanh.bg98g00107.signs.SignItem
import vn.thanhtaxixanh.bg98g00107.signs.UpcomingSign

class CameraPanelPolicyTest {
    private fun marker(type: String, distance: Int, id: Long = distance.toLong()) =
        UpcomingSign(
            SignItem(21.0, 105.0 + id / 1000000.0, type,
                "", type, 0, osmNodeId = id),
            distance
        )

    @Test fun cameraTileShowsOnlyForwardCameraWarningsNearestFirst() {
        val warnings = listOf(
            marker("speed", 10),
            marker("camera", 350),
            marker("redlight_camera", 180),
            marker("stop", 25),
            marker("camera", 90),
            marker("camera", 600)
        )
        val result = CameraPanelPolicy.visible(warnings, true, true)
        assertEquals(listOf(90, 180, 350), result.map { it.distM })
        assertEquals(listOf("camera", "redlight_camera", "camera"),
            result.map { it.item.type })
    }

    @Test fun respectsIndependentCameraAlertSettings() {
        val warnings = listOf(marker("camera", 90), marker("redlight_camera", 120))
        assertEquals(listOf("redlight_camera"),
            CameraPanelPolicy.visible(warnings, false, true).map { it.item.type })
        assertEquals(listOf("camera"),
            CameraPanelPolicy.visible(warnings, true, false).map { it.item.type })
        assertTrue(CameraPanelPolicy.visible(warnings, false, false).isEmpty())
    }

    @Test fun doesNotDuplicateOrShowBehindMarkers() {
        val first = marker("camera", 150, 56)
        val duplicate = marker("camera", 151, 56)
        val behind = marker("camera", -15, 57)
        assertEquals(listOf(150),
            CameraPanelPolicy.visible(listOf(duplicate, first, behind), true, true)
                .map { it.distM })
    }
}
