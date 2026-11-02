package vn.thanhtaxixanh.bg98g00107.ai

import kotlin.math.max
import kotlin.math.min

internal data class AiDetection(
    val classIndex: Int, val label: String, val confidence: Float,
    val left: Float, val top: Float, val right: Float, val bottom: Float
)

/**
 * YOLOv8 raw output decoder: [1, 4+classes, N] or [1, N, 4+classes].
 * Requires an exported model with raw outputs (no built-in NMS).
 */
internal object YoloV8Postprocess {
    fun decode(
        values: FloatArray,
        rows: Int,
        columns: Int,
        labels: List<String>,
        imageSize: Int,
        threshold: Float = 0.62f
    ): List<AiDetection> {
        require(imageSize > 0 && rows > 0 && columns > 0)
        require(values.size == rows * columns)
        val channelsFirst = rows == labels.size + 4
        val channelsLast = columns == labels.size + 4
        require(channelsFirst || channelsLast) {
            "Model output is not raw YOLOv8 [4+classes,N] or [N,4+classes]"
        }
        val count = if (channelsFirst) columns else rows
        val output = ArrayList<AiDetection>()
        fun value(channel: Int, box: Int) =
            if (channelsFirst) values[channel * columns + box]
            else values[box * columns + channel]
        for (i in 0 until count) {
            var classId = -1
            var best = threshold
            for (c in labels.indices) {
                val score = value(c + 4, i)
                if (score.isFinite() && score > best) { best = score; classId = c }
            }
            if (classId == -1) continue
            val cx = value(0,i); val cy = value(1,i)
            val w = value(2,i); val h = value(3,i)
            if (!cx.isFinite() || !cy.isFinite() || !w.isFinite() || !h.isFinite() ||
                w <= 0f || h <= 0f) continue
            val left = max(0f, cx - w/2f)
            val top = max(0f, cy - h/2f)
            val right = min(imageSize.toFloat(), cx + w/2f)
            val bottom = min(imageSize.toFloat(), cy + h/2f)
            if (right <= left || bottom <= top) continue
            output.add(AiDetection(classId, labels[classId], best, left, top, right, bottom))
        }
        val kept = ArrayList<AiDetection>()
        for (d in output.sortedByDescending { it.confidence }) {
            if (kept.size >= 30) break
            if (kept.none { it.classIndex == d.classIndex && iou(it,d) > .45f }) kept.add(d)
        }
        return kept
    }

    private fun iou(a: AiDetection,b: AiDetection): Float {
        val w = max(0f,min(a.right,b.right) - max(a.left,b.left))
        val h = max(0f,min(a.bottom,b.bottom) - max(a.top,b.top))
        val intersect = w*h
        val areaA = (a.right-a.left)*(a.bottom-a.top)
        val areaB = (b.right-b.left)*(b.bottom-b.top)
        return if (areaA+areaB-intersect > 0) intersect/(areaA+areaB-intersect) else 0f
    }
}
