package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardSettings
import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardSettingsCodec
import vn.thanhtaxixanh.bg98g00107.dashboard.MapThemeMode
import vn.thanhtaxixanh.bg98g00107.dashboard.MapLineColor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DashboardSettingsTest {
    @Test fun freshInstallUsesSafeDefaults() {
        val settings = DashboardSettingsCodec.read(null, false)
        assertEquals(DashboardSettings(), settings)
        assertTrue(settings.showCalendar)
        assertTrue(settings.showQr)
        assertTrue(settings.showSigns)
        assertTrue(settings.showStreetName)
        assertEquals(MapThemeMode.AUTO, settings.mapTheme)
        assertFalse(settings.showPersonalReportsOnMap)
    }

    @Test fun legacyReportPrivacyPreferenceMigratesOnFirstReadOnly() {
        val migrated = DashboardSettingsCodec.read(null, true)
        assertTrue(migrated.showPersonalReportsOnMap)
        assertEquals(DashboardSettings().copy(showPersonalReportsOnMap = true), migrated)
        // Stored Phase 5 choice is authoritative after the initial migration.
        val newConfig = DashboardSettingsCodec.encode(
            migrated.copy(showPersonalReportsOnMap = false)
        )
        assertFalse(DashboardSettingsCodec.read(newConfig, true).showPersonalReportsOnMap)
    }

    @Test fun allSettingsRoundTripWithoutLoss() {
        for (theme in MapThemeMode.entries) {
            val settings = DashboardSettings(
                showCalendar = false, showQr = false,
                showSigns = false, showStreetName = false,
                mapTheme = theme, showPersonalReportsOnMap = true
            )
            assertEquals(settings, DashboardSettingsCodec.read(
                DashboardSettingsCodec.encode(settings), false
            ))
        }
    }

    @Test fun phase53MapAndRerouteSettingsRoundTrip() {
        val settings = DashboardSettings(
            autoZoomReferenceMeters = 320,
            rerouteDeviationM = 65,
            routeLineColor = MapLineColor.ORANGE,
            tripLineColor = MapLineColor.MAGENTA
        )
        val restored = DashboardSettingsCodec.read(
            DashboardSettingsCodec.encode(settings),
            false
        )
        assertEquals(320, restored.autoZoomReferenceMeters)
        assertEquals(65, restored.rerouteDeviationM)
        assertEquals(MapLineColor.ORANGE, restored.routeLineColor)
        assertEquals(MapLineColor.MAGENTA, restored.tripLineColor)
    }

    @Test fun partialVersionOnePayloadDefaultsMissingOptionalKeys() {
        val partial = """{"version":1,"mapTheme":"DARK","showQr":false}"""
        val restored = DashboardSettingsCodec.read(partial, false)
        assertEquals(MapThemeMode.DARK, restored.mapTheme)
        assertFalse(restored.showQr)
        assertTrue(restored.showCalendar)
        assertTrue(restored.showSigns)
        assertFalse(restored.showPersonalReportsOnMap)
    }

    @Test fun invalidNewerVersionNeverSilentlyFallsBack() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardSettingsCodec.read("""{"version":2,"showCalendar":false}""", false)
        }
        assertThrows(Exception::class.java) {
            DashboardSettingsCodec.read("{invalid json", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardSettingsCodec.read("", false)
        }
    }

    @Test fun invalidBooleanAndUnknownMapThemeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardSettingsCodec.read(
                """{"version":1,"showCalendar":"false"}""", false
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardSettingsCodec.read(
                """{"version":1,"mapTheme":"MOON"}""", false
            )
        }
    }

    @Test fun serializationDoesNotContainPrivateUserDataOrLegacyKeys() {
        val json = DashboardSettingsCodec.encode(
            DashboardSettings().copy(showPersonalReportsOnMap = true)
        )
        val root = JSONObject(json)
        assertEquals(1, root.getInt("version"))
        assertFalse(root.has("events"))
        assertFalse(root.has("qr.png"))
        assertFalse(root.has("latitude"))
        assertFalse(root.has("longitude"))
        assertFalse(root.has("notes"))
        assertTrue(root.getBoolean("showPersonalReportsOnMap"))
    }

    @Test fun settingsAreImmutableAndCanBeUpdatedIndependently() {
        val original = DashboardSettings()
        val updated = original.copy(showQr = false)
        val next = updated.copy(mapTheme = MapThemeMode.DARK)
        assertEquals(MapThemeMode.AUTO, original.mapTheme)
        assertTrue(original.showQr)
        assertFalse(updated.showQr)
        assertEquals(MapThemeMode.DARK, next.mapTheme)
        assertTrue(next.showSigns)
    }
}
