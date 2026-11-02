package vn.thanhtaxixanh.bg98g00107.camera

import android.content.Context
import java.net.URI

/**
 * Local, opt-in RTSP source settings; no assumptions about 70mai firmware paths.
 * Credentials are not logged. The user must supply a path that actually streams.
 */
object LiveCameraConfig {
    const val DEFAULT_URL = "rtsp://192.168.0.1/livestream/12"
    private const val PREFS = "ttx_live_camera"
    private const val KEY_URL = "stream_url"
    private const val KEY_ENABLED = "stream_enabled"
    private const val KEY_FORCE_TCP = "force_rtsp_tcp"

    fun isValidRtsp(raw: String): Boolean = try {
        val uri = URI(raw.trim())
        uri.scheme.equals("rtsp", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.port in -1..65535 && uri.port != 0 &&
            raw.length <= 512 && raw.none { it.code == 10 || it.code == 13 }
    } catch (_: Exception) {
        false
    }

    fun url(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL).orEmpty()

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun forceTcp(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FORCE_TCP, false)

    fun save(context: Context, raw: String, enabled: Boolean, forceTcp: Boolean = false) {
        val url = raw.trim()
        require(isValidRtsp(url)) { "URL RTSP không hợp lệ." }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url)
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_FORCE_TCP, forceTcp).commit()) {
            "Không thể lưu cấu hình Camera."
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).commit()) {
            "Không thể lưu trạng thái Camera."
        }
    }
}
