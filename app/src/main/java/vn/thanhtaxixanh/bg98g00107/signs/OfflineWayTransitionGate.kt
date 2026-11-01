package vn.thanhtaxixanh.bg98g00107.signs

/**
 * Conservative speed-limit way-transition confirmation.
 *
 * Evidence is counted by distinct GPS fix timestamps, never by UI/state calls.
 * Offline speed uses three fixes; fresh live OSM may use a shorter gate.
 * Re-rendering the same fix therefore cannot confirm a road transition.
 */
internal class OfflineWayTransitionGate(private val requiredFixes: Int = 3) {
    init { require(requiredFixes >= 1) }

    private var accepted: Long? = null
    private var pending: Long? = null
    private var consecutive = 0
    private var lastEvidenceFixMs = Long.MIN_VALUE

    fun reset() {
        accepted = null
        pending = null
        consecutive = 0
        lastEvidenceFixMs = Long.MIN_VALUE
    }

    fun accept(wayId: Long, gpsFixMs: Long): Boolean {
        if (wayId <= 0L) {
            reset()
            return false
        }

        if (accepted == null) {
            accepted = wayId
            pending = null
            consecutive = 0
            lastEvidenceFixMs = gpsFixMs
            return true
        }

        if (accepted == wayId) {
            pending = null
            consecutive = 0
            lastEvidenceFixMs = gpsFixMs
            return true
        }

        // Same GPS sample may be rendered/evaluated multiple times.
        if (gpsFixMs == lastEvidenceFixMs) return false
        lastEvidenceFixMs = gpsFixMs

        if (pending == wayId) {
            consecutive++
        } else {
            pending = wayId
            consecutive = 1
        }

        if (consecutive < requiredFixes) return false
        accepted = wayId
        pending = null
        consecutive = 0
        return true
    }
}
