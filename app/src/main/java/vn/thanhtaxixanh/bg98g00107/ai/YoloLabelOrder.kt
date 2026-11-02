package vn.thanhtaxixanh.bg98g00107.ai

/**
 * Ultralytics ONNX exports usually embed their exact detection class ordering
 * under metadata key "names": {0: 'P-102', 1: 'R-303', ...}.
 *
 * Never infer ordering from a dictionary of sign descriptions: a silent one-off
 * shift would label a STOP sign as a speed limit.
 */
internal object YoloLabelOrder {
    private val entry = Regex("""(?:^|[,{]\s*)(\d{1,3})\s*:\s*['"]([^'"]{1,80})['"]""")
    private val simpleEntry = Regex("""^\s*(\d{1,3})\s*:\s*['"]?([A-Za-z][\w.*\-/ ]{0,78})['"]?\s*$""")

    fun fromOnnxMetadata(raw: String?, expected: Int): List<String>? {
        if (raw.isNullOrBlank() || expected !in 2..512) return null
        val text = raw.substringAfter("names:",raw)
        val matches=entry.findAll(text).map {
            it.groupValues[1].toInt() to it.groupValues[2].trim()
        }.toList().ifEmpty {
            text.lines().mapNotNull { line ->
                simpleEntry.matchEntire(line)?.let {
                    it.groupValues[1].toInt() to it.groupValues[2].trim()
                }
            }
        }
        if (matches.size != expected) return null
        val output = arrayOfNulls<String>(expected)
        for ((id,name) in matches) {
            if (id !in 0 until expected || output[id]!=null ||
                name.isBlank()) return null
            output[id]=name
        }
        return if (output.all { it!=null }) output.map { it!! } else null
    }

    fun fromLabelsFile(text: String, expected: Int): List<String>? {
        if (expected !in 2..512) return null
        val entries=text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() }.toList()
        return entries.takeIf { it.size==expected &&
            it.all { item -> item.length <= 80 } && it.toSet().size==expected
        }
    }
}
