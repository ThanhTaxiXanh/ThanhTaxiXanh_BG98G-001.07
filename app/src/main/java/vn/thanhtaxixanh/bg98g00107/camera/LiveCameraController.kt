package vn.thanhtaxixanh.bg98g00107.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
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
    fun connect(context: Context, url: String) {
        require(Looper.myLooper() == Looper.getMainLooper())
        LiveCameraConfig.save(context, url, true)
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
        val active = CameraFrameSink(
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

    private fun startPlayer(context: Context, url: String, surface: Surface) {
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
                    // Do not expose RTSP URLs, credentials or device details in UI logs.
                    state = "Không nhận được RTSP (" + error.errorCodeName + "). " +
                        "Kiểm tra Wi-Fi 70mai, URL và bước bắt tay của camera."
                    notifyClients()
                }
            })
            instance.setMediaItem(MediaItem.fromUri(url))
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
