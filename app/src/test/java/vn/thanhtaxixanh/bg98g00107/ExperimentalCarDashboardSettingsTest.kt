package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardSettings
import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardSettingsCodec
import org.junit.Assert.*
import org.junit.Test

class ExperimentalCarDashboardSettingsTest {
    @Test fun debugPreviewDefaultsOff() {
        assertFalse(DashboardSettings().experimentalCarDashboard)
    }
    @Test fun optInRoundTripPreservesPresetAndCustomMode() {
        val original = DashboardSettings(layoutPresetId = "wide_map",
            experimentalCarDashboard = true)
        val saved = DashboardSettingsCodec.read(
            DashboardSettingsCodec.encode(original), false)
        assertEquals("wide_map", saved.layoutPresetId)
        assertTrue(saved.experimentalCarDashboard)
    }
    @Test fun oldSettingsRemainMapOnlyOnCar() {
        val old = """{"version":1,"layoutPresetId":"classic"}"""
        assertFalse(DashboardSettingsCodec.read(old, false).experimentalCarDashboard)
    }
}
