package vn.thanhtaxixanh.bg98g00107.navigation

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import vn.thanhtaxixanh.bg98g00107.signs.SignsState
import java.util.Locale

class NavigationVoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private var ready = false
    private var pendingText: String? = null
    private var lastToken: String? = null
    private var lastOffRouteMs = Long.MIN_VALUE
    private var lastTrafficLimit: Int? = null
    private var overspeedActive = false
    private var lastOverspeedMs = Long.MIN_VALUE
    private val spokenCameraTokens = LinkedHashSet<String>()

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val language = engine.setLanguage(Locale("vi", "VN"))
        ready = language != TextToSpeech.LANG_MISSING_DATA &&
            language != TextToSpeech.LANG_NOT_SUPPORTED
        engine.setSpeechRate(0.96f)
        engine.setPitch(1.0f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        if (ready) {
            pendingText?.let {
                pendingText = null
                speak(it)
            }
        }
    }

    fun onGuidance(
        state: RouteGuidanceState,
        speedKmh: Int,
        nowMs: Long = SystemClock.uptimeMillis()
    ) {
        val prompt = NavigationVoicePromptPolicy.prompt(state, speedKmh) ?: return
        if (prompt.offRoute) {
            if (lastOffRouteMs != Long.MIN_VALUE &&
                nowMs - lastOffRouteMs < 30_000L) return
            lastOffRouteMs = nowMs
        } else if (prompt.token == lastToken) {
            return
        }
        lastToken = prompt.token
        if (ready) speak(prompt.text) else pendingText = prompt.text
    }

    fun onTraffic(
        state: SignsState,
        speedKmh: Int,
        toleranceKmh: Int,
        speedWarnings: Boolean,
        cameraWarnings: Boolean,
        announceLimitChanges: Boolean,
        cameraFarWarningM: Int = 600,
        cameraNearWarningM: Int = 180,
        cameraFinalWarningM: Int = 70,
        warnSpeedCameras: Boolean = true,
        warnRedLightCameras: Boolean = true,
        nowMs: Long = SystemClock.uptimeMillis()
    ) {
        if (speedWarnings) {
            if (announceLimitChanges) {
                TrafficVoicePromptPolicy.limitChanged(
                    state,
                    lastTrafficLimit
                )?.let { speak(it.text, TextToSpeech.QUEUE_ADD) }
            }

            val limit = state.limit
            if (!state.estimated) lastTrafficLimit = limit

            val over = TrafficVoicePromptPolicy.overspeed(
                state,
                speedKmh,
                toleranceKmh
            )
            if (over != null) {
                if (!overspeedActive ||
                    lastOverspeedMs == Long.MIN_VALUE ||
                    nowMs - lastOverspeedMs >= 45_000L
                ) {
                    speak(over.text, TextToSpeech.QUEUE_ADD)
                    lastOverspeedMs = nowMs
                }
                overspeedActive = true
            } else {
                val limit = state.limit
                if (limit == null) {
                    overspeedActive = false
                } else {
                    val resetAt = limit +
                        toleranceKmh.coerceIn(0, 20) - 2
                    if (speedKmh <= resetAt) overspeedActive = false
                }
            }
        } else {
            overspeedActive = false
        }

        if (cameraWarnings) {
            TrafficVoicePromptPolicy.camera(
                state = state,
                farDistanceM = cameraFarWarningM,
                nearDistanceM = cameraNearWarningM,
                finalDistanceM = cameraFinalWarningM,
                warnSpeedCameras = warnSpeedCameras,
                warnRedLightCameras = warnRedLightCameras
            )?.let { prompt ->
                if (spokenCameraTokens.add(prompt.token)) {
                    speak(prompt.text, TextToSpeech.QUEUE_ADD)
                    while (spokenCameraTokens.size > 80) {
                        val first = spokenCameraTokens.firstOrNull() ?: break
                        spokenCameraTokens.remove(first)
                    }
                }
            }
        }
    }

    fun resetForNewRoute() {
        lastToken = null
        lastOffRouteMs = Long.MIN_VALUE
    }

    fun stop() {
        pendingText = null
        tts?.stop()
    }

    fun shutdown() {
        pendingText = null
        ready = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ) {
        tts?.speak(
            text,
            queueMode,
            null,
            "ttx-guidance-" + SystemClock.uptimeMillis()
        )
    }
}
