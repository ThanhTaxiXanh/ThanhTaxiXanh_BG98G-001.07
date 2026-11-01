package vn.thanhtaxixanh.bg98g00107.map



import java.util.Locale

/**
 * Debug-only observer for MapLibre frame delivery and hot-path processing costs.
 * A delivered ImageReader frame is NOT a display vsync or Android Auto host FPS.
 * Calls are synchronized because GPS and image callbacks may originate on different threads.
 */
internal class MapPerformanceMonitor(private val sampleWindowMs: Long = 10_000L) {
    init { require(sampleWindowMs > 0L) }

    private var windowStartMs = -1L
    private var mapLoadStartMs: Long? = null
    private var frames = 0
    private var frameCopyTotalNs = 0L
    private var frameCopyMaxNs = 0L
    private var droppedFrames = 0
    private var frameLockWaits = 0
    private var frameLockWaitTotalNs = 0L
    private var frameLockWaitMaxNs = 0L
    private var hostDraws = 0
    private var hostDrawTotalNs = 0L
    private var hostDrawMaxNs = 0L
    private var roadContextQueries = 0
    private var roadContextTotalNs = 0L
    private var roadContextMaxNs = 0L
    private var roadContextCandidates = 0L
    private var staleRoadContexts = 0
    private var gpsUpdates = 0
    private var gpsTotalNs = 0L
    private var gpsMaxNs = 0L
    private var mapSteps = 0
    private var mapStepTotalNs = 0L
    private var mapStepMaxNs = 0L

    @Synchronized fun reset(nowMs: Long) {
        windowStartMs = nowMs
        mapLoadStartMs = null
        frames = 0
        frameCopyTotalNs = 0L
        frameCopyMaxNs = 0L
        droppedFrames = 0
        frameLockWaits = 0
        frameLockWaitTotalNs = 0L
        frameLockWaitMaxNs = 0L
        hostDraws = 0
        hostDrawTotalNs = 0L
        hostDrawMaxNs = 0L
        roadContextQueries = 0
        roadContextTotalNs = 0L
        roadContextMaxNs = 0L
        roadContextCandidates = 0L
        staleRoadContexts = 0
        gpsUpdates = 0
        gpsTotalNs = 0L
        gpsMaxNs = 0L
        mapSteps = 0
        mapStepTotalNs = 0L
        mapStepMaxNs = 0L
    }

    @Synchronized fun startMapLoad(nowMs: Long) {
        mapLoadStartMs = nowMs
    }

    /** Only the first successful style load is measured, not subsequent day/night reloads. */
    @Synchronized fun finishMapLoad(nowMs: Long): Long? {
        val start = mapLoadStartMs ?: return null
        if (nowMs < start) return null
        mapLoadStartMs = null
        return nowMs - start
    }

    @Synchronized fun recordFrame(nowMs: Long, copyNs: Long) {
        if (copyNs < 0L) return
        if (windowStartMs < 0L) windowStartMs = nowMs
        frames++
        frameCopyTotalNs += copyNs
        frameCopyMaxNs = maxOf(frameCopyMaxNs, copyNs)
    }

    @Synchronized fun recordDroppedFrame(nowMs: Long) {
        if (windowStartMs < 0L) windowStartMs = nowMs
        droppedFrames++
    }

    @Synchronized fun recordFrameLockWaitNs(durationNs: Long) {
        if (durationNs < 0L) return
        frameLockWaits++
        frameLockWaitTotalNs += durationNs
        frameLockWaitMaxNs = maxOf(frameLockWaitMaxNs, durationNs)
    }

    @Synchronized fun recordHostDrawNs(durationNs: Long) {
        if (durationNs < 0L) return
        hostDraws++
        hostDrawTotalNs += durationNs
        hostDrawMaxNs = maxOf(hostDrawMaxNs, durationNs)
    }

    @Synchronized fun recordRoadContextQueryNs(
        durationNs: Long,
        candidateCount: Int
    ) {
        if (durationNs < 0L || candidateCount < 0) return
        roadContextQueries++
        roadContextTotalNs += durationNs
        roadContextMaxNs = maxOf(roadContextMaxNs, durationNs)
        roadContextCandidates += candidateCount.toLong()
    }

    @Synchronized fun recordStaleRoadContext() {
        staleRoadContexts++
    }

    @Synchronized fun recordGpsNs(durationNs: Long) {
        if (durationNs < 0L) return
        gpsUpdates++
        gpsTotalNs += durationNs
        gpsMaxNs = maxOf(gpsMaxNs, durationNs)
    }

    @Synchronized fun recordStepNs(durationNs: Long) {
        if (durationNs < 0L) return
        mapSteps++
        mapStepTotalNs += durationNs
        mapStepMaxNs = maxOf(mapStepMaxNs, durationNs)
    }

    /** Returns at most one snapshot per window; does not retain historical samples. */
    @Synchronized fun snapshotIfDue(nowMs: Long): MapPerfSnapshot? {
        if (windowStartMs < 0L) {
            windowStartMs = nowMs
            return null
        }
        val elapsed = nowMs - windowStartMs
        if (elapsed < sampleWindowMs || elapsed <= 0L) return null
        val result = MapPerfSnapshot(
            durationMs = elapsed,
            deliveredFrames = frames,
            observedFrameFps = frames * 1_000.0 / elapsed,
            avgCopyMs = if (frames == 0) null else frameCopyTotalNs / 1_000_000.0 / frames,
            maxCopyMs = if (frames == 0) null else frameCopyMaxNs / 1_000_000.0,
            droppedFrames = droppedFrames,
            frameLockWaits = frameLockWaits,
            avgFrameLockWaitMs = if (frameLockWaits == 0) null
                else frameLockWaitTotalNs / 1_000_000.0 / frameLockWaits,
            maxFrameLockWaitMs = if (frameLockWaits == 0) null
                else frameLockWaitMaxNs / 1_000_000.0,
            hostDraws = hostDraws,
            avgHostDrawMs = if (hostDraws == 0) null
                else hostDrawTotalNs / 1_000_000.0 / hostDraws,
            maxHostDrawMs = if (hostDraws == 0) null
                else hostDrawMaxNs / 1_000_000.0,
            roadContextQueries = roadContextQueries,
            avgRoadContextMs = if (roadContextQueries == 0) null
                else roadContextTotalNs / 1_000_000.0 / roadContextQueries,
            maxRoadContextMs = if (roadContextQueries == 0) null
                else roadContextMaxNs / 1_000_000.0,
            avgRoadCandidates = if (roadContextQueries == 0) null
                else roadContextCandidates.toDouble() / roadContextQueries,
            staleRoadContexts = staleRoadContexts,
            gpsUpdates = gpsUpdates,
            avgGpsMs = if (gpsUpdates == 0) null else gpsTotalNs / 1_000_000.0 / gpsUpdates,
            maxGpsMs = if (gpsUpdates == 0) null else gpsMaxNs / 1_000_000.0,
            mapSteps = mapSteps,
            avgStepMs = if (mapSteps == 0) null else mapStepTotalNs / 1_000_000.0 / mapSteps,
            maxStepMs = if (mapSteps == 0) null else mapStepMaxNs / 1_000_000.0
        )
        // Preserve an explicit load timer until style callback; clear only sample counters.
        val loading = mapLoadStartMs
        reset(nowMs)
        mapLoadStartMs = loading
        return result
    }
}

internal data class MapPerfSnapshot(
    val durationMs: Long,
    val deliveredFrames: Int,
    val observedFrameFps: Double,
    val avgCopyMs: Double?,
    val maxCopyMs: Double?,
    val droppedFrames: Int,
    val frameLockWaits: Int,
    val avgFrameLockWaitMs: Double?,
    val maxFrameLockWaitMs: Double?,
    val hostDraws: Int,
    val avgHostDrawMs: Double?,
    val maxHostDrawMs: Double?,
    val roadContextQueries: Int,
    val avgRoadContextMs: Double?,
    val maxRoadContextMs: Double?,
    val avgRoadCandidates: Double?,
    val staleRoadContexts: Int,
    val gpsUpdates: Int,
    val avgGpsMs: Double?,
    val maxGpsMs: Double?,
    val mapSteps: Int,
    val avgStepMs: Double?,
    val maxStepMs: Double?
) {
    /** No GPS coordinates, location history, trip IDs or personally identifying data. */
    fun logLine(heapUsedBytes: Long): String = String.format(
        Locale.US,
        "window_ms=%d delivered_frame_fps=%.1f frames=%d dropped_frames=%d copy_avg_ms=%s copy_max_ms=%s frame_lock_wait_avg_ms=%s frame_lock_wait_max_ms=%s host_draw_avg_ms=%s host_draw_max_ms=%s road_ctx_queries=%d road_ctx_avg_ms=%s road_ctx_max_ms=%s road_ctx_candidates_avg=%s road_ctx_stale=%d gps_updates=%d gps_avg_ms=%s gps_max_ms=%s map_steps=%d step_avg_ms=%s step_max_ms=%s java_heap_mb=%.1f",
        durationMs, observedFrameFps, deliveredFrames, droppedFrames,
        avgCopyMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        maxCopyMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        avgFrameLockWaitMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        maxFrameLockWaitMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        avgHostDrawMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        maxHostDrawMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        roadContextQueries,
        avgRoadContextMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        maxRoadContextMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        avgRoadCandidates?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
        staleRoadContexts,
        gpsUpdates,
        avgGpsMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        maxGpsMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        mapSteps,
        avgStepMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        maxStepMs?.let { String.format(Locale.US, "%.2f", it) } ?: "-",
        heapUsedBytes / 1_048_576.0
    )
}


/**
 * Adaptive render cadence for the MapLibre -> VirtualDisplay pipeline.
 * Prioritizes smooth motion while avoiding unnecessary bitmap copies when idle.
 */
internal object MapFrameRatePolicy {
    fun delayMs(hasFix: Boolean, moving: Boolean, interactive: Boolean): Long = when {
        interactive -> 33L   // ~30 fps briefly while the user manipulates the map
        moving -> 42L        // ~24 fps while driving
        hasFix -> 80L        // ~12.5 fps while stationary
        else -> 120L         // ~8 fps while waiting for a GPS fix
    }
}
