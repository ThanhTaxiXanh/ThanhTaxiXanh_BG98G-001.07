package vn.thanhtaxixanh.bg98g00107.map

import kotlin.math.roundToInt

internal data class MapRenderResolution(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val scale: Double
)

internal object MapRenderResolutionPolicy {
    fun resolve(
        requestedWidth: Int,
        requestedHeight: Int,
        baseDpi: Int
    ): MapRenderResolution {
        require(baseDpi > 0)
        val w = requestedWidth.coerceAtLeast(64)
        val h = requestedHeight.coerceAtLeast(64)
        val pixels = w.toLong() * h.toLong()
        val scale = when {
            pixels <= 700_000L -> 1.0
            pixels <= 1_300_000L -> 0.90
            else -> 0.82
        }

        fun even(value: Double): Int {
            val rounded = value.roundToInt().coerceAtLeast(64)
            return if (rounded % 2 == 0) rounded else rounded - 1
        }

        return MapRenderResolution(
            width = even(w * scale),
            height = even(h * scale),
            dpi = (baseDpi * scale).roundToInt().coerceAtLeast(120),
            scale = scale
        )
    }
}
