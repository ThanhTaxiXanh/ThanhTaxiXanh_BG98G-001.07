package vn.thanhtaxixanh.bg98g00107.navigation

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

class NavigationVoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private var ready = false
    private var pendingText: String? = null
    private var lastToken: String? = null
    private var lastOffRouteMs = Long.MIN_VALUE

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

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ttx-nav-guidance")
    }
}
