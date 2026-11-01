package vn.thanhtaxixanh.bg98g00107.trips

import android.content.Context
import android.location.Location
import kotlin.math.roundToInt

class TripRecorder(context: Context) {
    private val app = context.applicationContext
    private val store = TripHistoryStore(app)
    private val prefs = app.getSharedPreferences("trip_recording_state", Context.MODE_PRIVATE)

    fun onLocation(
        location: Location,
        enabled: Boolean,
        roadLabel: String = ""
    ) {
        if (!location.hasAccuracy() || location.accuracy > 35f) return
        val timeMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val speed = if (location.hasSpeed() && location.speed >= 0f) {
            location.speed.toDouble()
        } else 0.0

        synchronized(lock) {
            var active = store.activeTrip()
            if (!enabled) {
                active?.let { store.finishTrip(it.id, timeMs) }
                clearState()
                return
            }

            val lastFix = prefs.getLong("last_fix_ms", 0L)
            if (active != null && lastFix > 0L &&
                TripRecordingPolicy.shouldFinishForGap(timeMs - lastFix)
            ) {
                store.finishTrip(active.id, lastFix)
                clearState()
                active = null
            }

            if (active == null && TripRecordingPolicy.shouldStart(speed)) {
                active = store.startTrip(
                    timeMs,
                    location.latitude,
                    location.longitude,
                    roadLabel
                )
            }

            if (active != null) {
                store.appendPoint(
                    active.id,
                    timeMs,
                    location.latitude,
                    location.longitude,
                    (speed * 3.6).roundToInt(),
                    roadLabel
                )

                val stoppedSince = prefs.getLong("stopped_since_ms", 0L)
                val nextStopped = if (speed <= TripRecordingPolicy.STOP_SPEED_MPS) {
                    if (stoppedSince > 0L) stoppedSince else timeMs
                } else 0L

                if (nextStopped > 0L &&
                    TripRecordingPolicy.shouldFinishForStop(timeMs - nextStopped)
                ) {
                    store.finishTrip(active.id, timeMs)
                    clearState()
                    return
                }

                prefs.edit()
                    .putLong("last_fix_ms", timeMs)
                    .putLong("stopped_since_ms", nextStopped)
                    .apply()
            }
        }
    }

    fun close() {
        store.close()
    }

    private fun clearState() {
        prefs.edit()
            .remove("last_fix_ms")
            .remove("stopped_since_ms")
            .apply()
    }

    companion object {
        private val lock = Any()
    }
}
