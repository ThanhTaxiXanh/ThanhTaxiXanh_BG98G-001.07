package vn.thanhtaxixanh.bg98g00107.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * One opt-in, audio-muted RTSP player for car debug and phone preview.
 * Consumers receive snapshots, never take ownership of player or EGL objects.
 *
 * All public methods run on the main thread (Android activities / CarService).
 * The EGL callback runs on its own HandlerThread and publishes immutable bitmaps.
 */
object LiveCameraController {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableMapOf<String, () -> Unit>()
    private var player: ExoPlayer? = null
    private var sink: CameraFrameSink? = null
    private var applicationContext: Context? = null

    @Volatile private var latest: Bitmap? = null
    @Volatile private var latestTimeMs: Long = 0L
    @Volatile private var state: String = "Chưa kết nối"

    data class Snapshot(val bitmap: Bitmap?, val status: String, val ageMs: Long)

    fun snapshot(): Snapshot {
        val elapsed = SystemClock.elapsedRealtime() - latestTimeMs
        val frame = latest?.takeIf { elapsed in 0L..3500L }
        return Snapshot(frame, if (frame == null && latestTimeMs != 0L &&
                elapsed > 3500L && state == "Đang phát trực tiếp") {
            "Luồng camera đang mất hình"
        } else state, elapsed)
    }

    fun attach(context: Context, consumer: String, onChange: () -> Unit) {
        require(Looper.myLooper() == Looper.getMainLooper())
        applicationContext = context.applicationContext
        listeners[consumer] = onChange
        startIfAllowed()
    }

    fun detach(consumer: String) {
        require(Looper.myLooper() == Looper.getMainLooper())
        listeners.remove(consumer)
        if (listeners.isEmpty()) {
            releaseSession()
            state = "Chưa kết nối"
        }
    }

    /** Starts only after the phone user explicitly enables and saves a source. */
    fun connect(context: Context, url: String, forceTcp: Boolean = false) {
        require(Looper.myLooper() == Looper.getMainLooper())
        LiveCameraConfig.save(context, url, true, forceTcp)
        applicationContext = context.applicationContext
        releaseSession()
        state = "Đang kết nối Camera..."
        notifyClients()
        startIfAllowed()
    }

    fun disconnect(context: Context) {
        require(Looper.myLooper() == Looper.getMainLooper())
        LiveCameraConfig.setEnabled(context, false)
        releaseSession()
        state = "Đã ngắt Camera"
        notifyClients()
    }

    private fun startIfAllowed() {
        val context = applicationContext ?: return
        if (listeners.isEmpty() || player != null || sink != null) return
        if (!LiveCameraConfig.enabled(context)) return
        val url = LiveCameraConfig.url(context)
        if (!LiveCameraConfig.isValidRtsp(url)) {
            state = "URL RTSP không hợp lệ"
            notifyClients()
            return
        }
        state = "Đang kết nối Camera..."
        lateinit var active: CameraFrameSink
        active = CameraFrameSink(
            onFrame = { bitmap ->
                latest = bitmap
                latestTimeMs = SystemClock.elapsedRealtime()
                state = "Đang phát trực tiếp"
                main.post { if (sink === active) notifyClients() }
            },
            onFailure = { reason ->
                main.post {
                    if (sink === active) {
                        releaseSession()
                        state = reason
                        notifyClients()
                    }
                }
            }
        )
        sink = active
        active.prepare { videoSurface ->
            main.post {
                if (sink !== active || listeners.isEmpty() ||
                    !LiveCameraConfig.enabled(context)) {
                    if (sink === active) releaseSession()
                } else {
                    startPlayer(context, url, videoSurface)
                }
            }
        }
        notifyClients()
    }

    @OptIn(UnstableApi::class)
    private fun startPlayer(context: Context, url: String, surface: Surface) {
        // Unlike the old default socket, use the same Wi-Fi as the successful
        // 70mai OPTIONS probe. Do not bind the entire app/process to Wi-Fi:
        // maps and non-camera features may require Internet over mobile data.
        val wifi = CameraWifiNetwork.find(context)
        if (wifi == null) {
            releaseSession()
            state = "Không tìm thấy mạng Wi-Fi; hãy kết nối Wi-Fi 70mai."
            notifyClients()
            return
        }
        try {
            val instance = ExoPlayer.Builder(context).build()
            player = instance
            instance.volume = 0f
            instance.setVideoSurface(surface)
            instance.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player !== instance) return
                    if (playbackState == Player.STATE_BUFFERING && latest == null) {
                        state = "Đang nạp luồng RTSP..."
                        notifyClients()
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        releaseSession()
                        state = "Luồng Camera đã kết thúc"
                        notifyClients()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (player !== instance) return
                    releaseSession()
                    // Never expose full exception messages: RTSP links may have
                    // embedded credentials. Display exception *types* only.
                    val failureType = error.cause?.javaClass?.simpleName ?: "Unknown"
                    state = "RTSP qua Wi-Fi: " + error.errorCodeName +
                        " (" + failureType + "). " +
                        "Thử kiểm tra DESCRIBE/SDP hoặc đổi chế độ TCP/UDP."
                    notifyClients()
                }
            })
            val rtspSource = RtspMediaSource.Factory()
                .setSocketFactory(wifi.socketFactory)
                .setForceUseRtpTcp(LiveCameraConfig.forceTcp(context))
                .setTimeoutMs(12000)
                .createMediaSource(MediaItem.fromUri(url))
            instance.setMediaSource(rtspSource)
            instance.prepare()
            instance.playWhenReady = true
        } catch (_: Exception) {
            releaseSession()
            state = "Không khởi tạo được bộ phát RTSP"
            notifyClients()
        }
    }

    private fun releaseSession() {
        player?.release()
        player = null
        latest = null
        latestTimeMs = 0L
        val old = sink
        sink = null
        old?.close()
    }

    private fun notifyClients() {
        // Avoid concurrent modifications when listeners close activities in a callback.
        listeners.values.toList().forEach {
            try { it() } catch (_: Exception) { }
        }
    }
}
