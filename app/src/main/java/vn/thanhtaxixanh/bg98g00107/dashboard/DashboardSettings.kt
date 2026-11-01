package vn.thanhtaxixanh.bg98g00107.dashboard



import android.content.Context
import org.json.JSONObject

/** Phase 5: safe, versioned settings model, shared by phone and car surfaces. */
enum class MapThemeMode(val title: String) {
    AUTO("Tự động theo ngày/đêm"),
    LIGHT("Luôn dùng giao diện sáng"),
    DARK("Luôn dùng giao diện tối")
}

data class DashboardSettings(
    val showCalendar: Boolean = true,
    val showQr: Boolean = true,
    val showSigns: Boolean = true,
    val showStreetName: Boolean = true,
    val mapTheme: MapThemeMode = MapThemeMode.AUTO,
    val map3d: Boolean = true,
    val rotateMapWithHeading: Boolean = true,
    val autoZoomBySpeed: Boolean = true,
    val cameraLookAheadSeconds: Int = 4,
    val cameraTiltDegrees: Int = 58,
    val autoRecenterSeconds: Int = 15,
    val speedWarningToleranceKmh: Int = 4,
    val recordTrips: Boolean = true,
    val showCurrentTrip: Boolean = true,
    val avoidMotorways: Boolean = false,
    val avoidTollRoads: Boolean = false,
    val avoidUnpavedRoads: Boolean = true,
    /** Personal notes only, never authoritative traffic signs. */
    val showPersonalReportsOnMap: Boolean = false,
    // Fresh installs default to three full-width utility rows on the right.
    // Version-1 payloads that predate layout selection still decode as "classic".
    val layoutPresetId: String = "stacked",
    val useCustomLayout: Boolean = false,
    val customLayout: DashboardLayout? = null,
    /** Development-only Android Auto custom tile preview. Never used for release. */
    val experimentalCarDashboard: Boolean = false
) {
    init {
        require(DashboardLayouts.byId(layoutPresetId) != null) {
            "Preset Dashboard không hợp lệ."
        }
        require(!useCustomLayout || customLayout != null) {
            "Không thể sử dụng bố cục tùy chỉnh chưa lưu."
        }
        require(cameraLookAheadSeconds in 1..8)
        require(cameraTiltDegrees in 0..70)
        require(autoRecenterSeconds in 5..60)
        require(speedWarningToleranceKmh in 0..20)
    }
}

internal object DashboardSettingsCodec {
    const val VERSION = 1

    /**
     * Only the personal-report visibility setting has a legacy equivalent.
     * Legacy cfg/events and filesDir/qr.png remain untouched.
     */
    fun read(raw: String?, legacyReportsVisible: Boolean): DashboardSettings {
        if (raw == null) return DashboardSettings(
            showPersonalReportsOnMap = legacyReportsVisible
        )
        require(raw.isNotBlank()) { "Cấu hình Dashboard trống." }
        val obj = JSONObject(raw)
        require(obj.getInt("version") == VERSION) {
            "Phiên bản cấu hình chưa hỗ trợ."
        }
        fun bool(key: String, default: Boolean): Boolean {
            if (!obj.has(key)) return default
            val value = obj.get(key)
            require(value is Boolean) { "Trường cấu hình $key không hợp lệ." }
            return value
        }
        fun int(key: String, default: Int, range: IntRange): Int {
            if (!obj.has(key)) return default
            val value = obj.getInt(key)
            require(value in range) { "Trường cấu hình $key ngoài giới hạn." }
            return value
        }
        return DashboardSettings(
            showCalendar = bool("showCalendar", true),
            showQr = bool("showQr", true),
            showSigns = bool("showSigns", true),
            showStreetName = bool("showStreetName", true),
            mapTheme = MapThemeMode.valueOf(obj.optString("mapTheme", "AUTO")),
            map3d = bool("map3d", true),
            rotateMapWithHeading = bool("rotateMapWithHeading", true),
            autoZoomBySpeed = bool("autoZoomBySpeed", true),
            cameraLookAheadSeconds = int("cameraLookAheadSeconds", 4, 1..8),
            cameraTiltDegrees = int("cameraTiltDegrees", 58, 0..70),
            autoRecenterSeconds = int("autoRecenterSeconds", 15, 5..60),
            speedWarningToleranceKmh = int("speedWarningToleranceKmh", 4, 0..20),
            recordTrips = bool("recordTrips", true),
            showCurrentTrip = bool("showCurrentTrip", true),
            avoidMotorways = bool("avoidMotorways", false),
            avoidTollRoads = bool("avoidTollRoads", false),
            avoidUnpavedRoads = bool("avoidUnpavedRoads", true),
            showPersonalReportsOnMap = bool("showPersonalReportsOnMap", false),
            layoutPresetId = obj.optString("layoutPresetId", "classic"),
            useCustomLayout = bool("useCustomLayout", false),
            experimentalCarDashboard = bool("experimentalCarDashboard", false),
            customLayout = if (obj.has("customLayout") && !obj.isNull("customLayout"))
                DashboardLayoutCodec.read(obj.getJSONArray("customLayout")) else null
        )
    }

    fun encode(value: DashboardSettings): String = JSONObject()
        .put("version", VERSION)
        .put("showCalendar", value.showCalendar)
        .put("showQr", value.showQr)
        .put("showSigns", value.showSigns)
        .put("showStreetName", value.showStreetName)
        .put("mapTheme", value.mapTheme.name)
        .put("map3d", value.map3d)
        .put("rotateMapWithHeading", value.rotateMapWithHeading)
        .put("autoZoomBySpeed", value.autoZoomBySpeed)
        .put("cameraLookAheadSeconds", value.cameraLookAheadSeconds)
        .put("cameraTiltDegrees", value.cameraTiltDegrees)
        .put("autoRecenterSeconds", value.autoRecenterSeconds)
        .put("speedWarningToleranceKmh", value.speedWarningToleranceKmh)
        .put("recordTrips", value.recordTrips)
        .put("showCurrentTrip", value.showCurrentTrip)
        .put("avoidMotorways", value.avoidMotorways)
        .put("avoidTollRoads", value.avoidTollRoads)
        .put("avoidUnpavedRoads", value.avoidUnpavedRoads)
        .put("showPersonalReportsOnMap", value.showPersonalReportsOnMap)
        .put("layoutPresetId", value.layoutPresetId)
        .put("useCustomLayout", value.useCustomLayout)
        .put("experimentalCarDashboard", value.experimentalCarDashboard)
        .apply {
            value.customLayout?.let {
                put("customLayout", DashboardLayoutCodec.write(it))
            }
        }
        .toString()
}

/**
 * One central preference for all new dashboard options.
 * Does not overwrite legacy cfg/events, report files, or the QR image.
 * Read errors propagate for phone recovery UI and are safely handled in car.
 */
class DashboardSettingsRepository(private val context: Context) {
    companion object {
        private const val NAME = "dashboard_settings"
        private const val VALUE_KEY = "payload_v1"
    }

    @Synchronized fun read(): DashboardSettings {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(VALUE_KEY, null)
        val oldReportsVisible = context.getSharedPreferences(
            "traffic_report_privacy", Context.MODE_PRIVATE
        ).getBoolean("show_map", false)
        return DashboardSettingsCodec.read(raw, oldReportsVisible)
    }

    @Synchronized fun save(value: DashboardSettings) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        // A broken/future schema must not be silently replaced by normal edits.
        if (prefs.contains(VALUE_KEY)) read()
        check(prefs.edit().putString(
            VALUE_KEY, DashboardSettingsCodec.encode(value)
        ).commit()) { "Không lưu được cài đặt." }
    }

    @Synchronized fun change(block: (DashboardSettings) -> DashboardSettings): DashboardSettings {
        val updated = block(read())
        save(updated)
        return updated
    }

    /** Explicit recovery action only; never automatic after a parse failure. */
    @Synchronized fun reset() {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        check(prefs.edit().putString(
            VALUE_KEY, DashboardSettingsCodec.encode(DashboardSettings())
        ).commit()) { "Không thể khôi phục cài đặt mặc định." }
    }
}
