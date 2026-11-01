package vn.thanhtaxixanh.bg98g00107.trips

internal object TripRecordingPolicy {
    const val START_SPEED_MPS = 1.2
    const val STOP_SPEED_MPS = 0.8
    const val FINISH_STOPPED_MS = 15L * 60L * 1_000L
    const val MAX_GAP_MS = 30L * 60L * 1_000L

    fun shouldStart(speedMps: Double): Boolean =
        speedMps.isFinite() && speedMps >= START_SPEED_MPS

    fun shouldFinishForStop(stoppedDurationMs: Long): Boolean =
        stoppedDurationMs >= FINISH_STOPPED_MS

    fun shouldFinishForGap(gapMs: Long): Boolean =
        gapMs >= MAX_GAP_MS
}
