package vn.thanhtaxixanh.bg98g00107.dashboard

import vn.thanhtaxixanh.bg98g00107.signs.UpcomingSign

/**
 * Select route-filtered OSM camera warnings for the former speed-limit tile.
 * This intentionally does not invent cameras from missing/unmatched data.
 */
internal object CameraPanelPolicy {
    fun visible(
        upcoming: List<UpcomingSign>,
        showSpeed: Boolean,
        showRedLight: Boolean
    ): List<UpcomingSign> = upcoming.asSequence()
        .filter { it.distM >= 0 }
        .filter { warning ->
            when (warning.item.type) {
                "camera" -> showSpeed
                "redlight_camera" -> showRedLight
                else -> false
            }
        }
        .sortedBy { it.distM }
        .distinctBy { warning ->
            warning.item.osmNodeId?.let { "osm:$it" }
                ?: "${warning.item.type}:${warning.item.lat}:${warning.item.lon}"
        }
        .take(3)
        .toList()
}
