package vn.thanhtaxixanh.bg98g00107.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Read-only interoperability probe for 70mai A5xx Wi-Fi.
 * xADAS 0.2.7's G5XX profile starts at rtsp://192.168.0.1/.
 *
 * This test does not register with or control the camera, and does not
 * assume that an RTSP server will answer before the vendor handshake.
 */
internal object SeventyMaiWifiProbe {
    private const val HOST = "192.168.0.1"
    private const val PORT = 554

    fun probe(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return "Không tìm thấy ConnectivityManager"
        val network = cm.allNetworks.firstOrNull { candidate ->
            cm.getNetworkCapabilities(candidate)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return "Điện thoại chưa có mạng Wi-Fi. Hãy kết nối Wi-Fi 70mai trước."
        val caps = cm.getNetworkCapabilities(network)
        val ssid = caps?.transportInfo?.toString().orEmpty()
        val hint = if (ssid.contains("70mai", ignoreCase=true)) {
            "Đang dùng mạng có dấu hiệu 70mai. "
        } else {
            "Đang dùng Wi-Fi; chưa xác nhận SSID 70mai. "
        }
        return try {
            network.socketFactory.createSocket().use { socket ->
                socket.soTimeout = 2000
                socket.connect(InetSocketAddress(HOST, PORT), 2000)
                val out = socket.getOutputStream()
                out.write(
                    ("OPTIONS rtsp://$HOST/ RTSP/1.0\r\n" +
                     "CSeq: 1\r\n" +
                     "User-Agent: TTX-AICamera-Probe\r\n\r\n")
                        .toByteArray(Charsets.US_ASCII)
                )
                out.flush()
                val input = socket.getInputStream()
                val line = StringBuilder()
                while (line.length < 160) {
                    val ch = input.read()
                    if (ch < 0 || ch == '\r'.code || ch == '\n'.code) break
                    line.append(ch.toChar())
                }
                val reply = line.toString()
                if (reply.startsWith("RTSP/")) {
                    hint + "RTSP phản hồi: " + reply.take(100) +
                        ". Có thể thử tiếp bước lấy video."
                } else {
                    hint + "Kết nối tới $HOST:$PORT thành công nhưng chưa thấy RTSP. " +
                        "Có thể cần bước bắt tay của firmware."
                }
            }
        } catch (e: Exception) {
            hint + "Chưa nhận được RTSP tại $HOST:$PORT (" +
                e.javaClass.simpleName +
                "). Không kết luận camera không hỗ trợ: xADAS còn handshake."
        }
    }
}
