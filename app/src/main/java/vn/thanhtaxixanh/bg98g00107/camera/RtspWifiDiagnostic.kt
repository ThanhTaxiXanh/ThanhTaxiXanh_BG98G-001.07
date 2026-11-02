package vn.thanhtaxixanh.bg98g00107.camera

import android.content.Context
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Read-only RTSP DESCRIBE/SDP diagnostics for locally attached Wi-Fi cameras.
 * OPTIONS 200 means the RTSP service exists; DESCRIBE SDP is needed to locate
 * a playable video path. This probe never sends SETUP, PLAY, vendor CGI or
 * camera-changing commands.
 */
object RtspWifiDiagnostic {
    data class Result(val details: String, val playableCandidate: String?)

    /** SDP inspection only; never assumes a successful SETUP/PLAY/decoder. */
    data class VideoInfo(
        val codec: String?,
        val contentBasePath: String?,
        val track: String?,
        val hasH264ParameterSets: Boolean
    )

    /**
     * Content-Base from A500S/LIVE555 may be a per-session path such as
     * /00000008/ while the DESCRIBE entry URL remains /livestream/12.
     * Media3 should resolve SETUP using the server's Content-Base + a=control.
     * Do not treat Content-Base as a permanent stream URL.
     */
    fun inspectVideoSdp(body: String, contentBase: String?): VideoInfo? {
        val lines = body.lineSequence().map { it.trim() }.toList()
        val start = lines.indexOfFirst { it.startsWith("m=video ") }
        if (start == -1) return null
        val section = lines.drop(start + 1).takeWhile { !it.startsWith("m=") }
        val codec = section.firstOrNull { it.startsWith("a=rtpmap:") }
            ?.substringAfter(" ")?.substringBefore("/")?.take(32)
        val rawTrack = section.firstOrNull { it.startsWith("a=control:") }
            ?.substringAfter("a=control:")
        val track = rawTrack?.takeIf { it.matches(Regex("track[0-9]{1,3}")) }
        // Strip hostname and possible credentials from remote Content-Base.
        val basePath = try {
            contentBase?.let { java.net.URI(it).path?.take(80) }
        } catch (_: Exception) {
            null
        }
        return VideoInfo(
            codec = codec,
            contentBasePath = basePath,
            track = track,
            hasH264ParameterSets = section.any { it.contains("sprop-parameter-sets=") }
        )
    }


    private val g5xxPaths = listOf(
        "/livestream/12", "/liveRTSP/av1", "/liveRTSP/v1",
        "/liveRTSP/av2", "/liveRTSP/av4"
    )

    fun candidateUrls(url: String): List<String> {
        if (!LiveCameraConfig.isValidRtsp(url)) return emptyList()
        val parsed = URI(url.trim())
        val origin = "rtsp://" + parsed.host +
            (if (parsed.port == -1) "" else ":" + parsed.port)
        return (listOf(url.trim()) +
            if (parsed.host == "192.168.0.1") g5xxPaths.map { origin + it }
            else emptyList()).distinct()
    }

    fun check(context: Context, enteredUrl: String): Result {
        val urls = candidateUrls(enteredUrl)
        if (urls.isEmpty()) return Result("URL RTSP không hợp lệ.", null)
        val wifi = CameraWifiNetwork.find(context)
            ?: return Result("Điện thoại chưa có mạng Wi-Fi. Hãy kết nối với Wi-Fi 70mai.", null)
        val found = ArrayList<String>()
        var best: String? = null
        for (url in urls) {
            val uri = URI(url)
            val path = uri.rawPath?.ifBlank { "/" } ?: "/"
            val description = try {
                // Route the command through Wi-Fi even when Android prefers LTE.
                wifi.socketFactory.createSocket().use { socket ->
                    socket.soTimeout = 2200
                    socket.connect(InetSocketAddress(uri.host,
                        if (uri.port > 0) uri.port else 554), 2200)
                    val request = "DESCRIBE $url RTSP/1.0\r\n" +
                        "CSeq: 1\r\nAccept: application/sdp\r\n" +
                        "User-Agent: TTX-Camera-Diagnostic\r\n\r\n"
                    socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
                    socket.getOutputStream().flush()
                    val input = BufferedInputStream(socket.getInputStream())
                    val status = readLine(input, 400)
                    val headers = HashMap<String, String>()
                    for (index in 0 until 48) {
                        val line = readLine(input, 1000)
                        if (line.isBlank()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) headers[line.substring(0, colon)
                            .lowercase()] = line.substring(colon + 1).trim()
                    }
                    val length = headers["content-length"]?.toIntOrNull()
                        ?.coerceIn(0, 4096) ?: 0
                    val bytes = ByteArray(length)
                    var offset = 0
                    while (offset < bytes.size) {
                        val count = input.read(bytes, offset, bytes.size - offset)
                        if (count < 0) break
                        offset += count
                    }
                    val body = String(bytes, 0, offset, StandardCharsets.US_ASCII)
                    val info = if (status.contains(" 200 ") &&
                        headers["content-type"]?.contains("application/sdp", true) == true) {
                        inspectVideoSdp(body, headers["content-base"])
                    } else null
                    if (info != null) {
                        best = url
                        "DESCRIBE 200 + SDP VIDEO" +
                            (info.codec?.let { " · codec: $it" } ?: "") +
                            (info.contentBasePath?.let { " · Content-Base: $it" } ?: "") +
                            (info.track?.let { " · control: $it" } ?: "") +
                            (if (info.codec.equals("H264", true)) {
                                if (info.hasH264ParameterSets) " · SPS/PPS có sẵn"
                                else " · chưa thấy SPS/PPS trong SDP"
                            } else "")
                    } else {
                        (status.take(72).ifBlank { "Không có phản hồi RTSP" }) +
                            if (status.contains(" 200 ")) " (chưa tìm thấy SDP video)" else ""
                    }
                }
            } catch (e: Exception) {
                "Lỗi kết nối: " + e.javaClass.simpleName
            }
            found.add("$path: $description")
            if (best != null) break
        }
        return Result(
            "Thử DESCRIBE qua Wi-Fi 70mai (không gửi PLAY):\n" +
            found.joinToString("\n") +
            if (best != null) "\nCó SDP video; hãy thử Kết nối RTSP qua Wi-Fi/TCP. " +
                "Đây chưa phải bằng chứng giải mã thành công."
            else "\nChưa xác minh được đường phát. " +
                "Camera có thể cần handshake/đường dẫn của firmware.",
            best
        )
    }

    private fun readLine(input: BufferedInputStream, max: Int): String {
        val bytes = ByteArray(max)
        var length = 0
        while (length < max) {
            val ch = input.read()
            if (ch == -1 || ch == 10) break
            if (ch != 13) bytes[length++] = ch.toByte()
        }
        return String(bytes, 0, length, StandardCharsets.US_ASCII)
    }
}
