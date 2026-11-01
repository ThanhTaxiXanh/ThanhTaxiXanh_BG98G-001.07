package vn.thanhtaxixanh.bg98g00107.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private, bounded review queue. No write to bundled OSM / legal speed database. */
internal class AiReviewStore(context: Context) {
    private val dir = File(context.filesDir, "ai_camera").apply { mkdirs() }
    private val file = File(dir, "observations.json")
    private val maxEntries = 2000

    @Synchronized fun list(): List<AiObservation> {
        if (!file.isFile || file.length() > 2_500_000L) return emptyList()
        return try {
            val rows = JSONArray(file.readText())
            (0 until rows.length()).mapNotNull { index ->
                try { decode(rows.getJSONObject(index)) } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized fun add(item: AiObservation) {
        val current = list().filterNot { it.id == item.id }
        write((listOf(item) + current).take(maxEntries))
    }

    @Synchronized fun review(
        id: String,
        approve: Boolean,
        speedKph: Int?,
        lat: Double?,
        lon: Double?,
        wayId: Long?
    ): AiObservation? {
        val old = list()
        val target = old.find { it.id == id && it.reviewStatus == AiReviewStatus.PENDING }
            ?: return null
        if (approve) {
            if (lat == null || lon == null ||
                !lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
                (speedKph != null && speedKph !in AiObservationRules.SPEEDS) ||
                (wayId != null && wayId <= 0)) return null
        }
        val updated = target.copy(
            proposedSpeedKph = speedKph,
            lat = lat, lon = lon, roadWayId = wayId,
            reviewStatus = if (approve) AiReviewStatus.APPROVED_LOCAL
                else AiReviewStatus.REJECTED
        )
        write(old.map { if (it.id == id) updated else it })
        return updated
    }

    /** Approved evidence is explicitly personal, never represented as official OSM data. */
    @Synchronized fun approvedGeoJson(): String {
        val features = JSONArray()
        list().filter { it.reviewStatus == AiReviewStatus.APPROVED_LOCAL }.forEach { item ->
            val lon = item.lon ?: return@forEach
            val lat = item.lat ?: return@forEach
            val properties = JSONObject().put("source", "ttx_user_review")
                .put("label", item.label)
                .put("kind", item.kind.name)
                .put("confidence", item.confidence.toDouble())
                .put("confirmed_by_user", true)
            if (item.isSpeedPoint()) item.proposedSpeedKph?.let {
                properties.put("speed_kph_user_reviewed", it)
            }
            item.roadWayId?.let { properties.put("osm_way_id", it) }
            features.put(JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject()
                    .put("type", "Point")
                    .put("coordinates", JSONArray().put(lon).put(lat)))
                .put("properties", properties))
        }
        return JSONObject().put("type", "FeatureCollection")
            .put("features", features).toString()
    }

    private fun write(data: List<AiObservation>) {
        val array = JSONArray()
        data.forEach { array.put(encode(it)) }
        val pending = File(dir, "observations.json.tmp")
        pending.writeText(array.toString())
        if (!pending.renameTo(file)) {
            file.delete()
            check(pending.renameTo(file)) { "Cannot save AI review queue" }
        }
    }

    private fun encode(item: AiObservation) = JSONObject().apply {
        put("id", item.id); put("at", item.detectedAtMs)
        item.videoOffsetMs?.let { put("offset", it) }
        put("kind", item.kind.name); put("label", item.label)
        put("confidence", item.confidence.toDouble())
        item.proposedSpeedKph?.let { put("speed", it) }
        item.lat?.let { put("lat", it) }; item.lon?.let { put("lon", it) }
        item.roadWayId?.let { put("way", it) }
        item.evidenceFile?.let { put("file", it) }
        put("source", item.source); put("status", item.reviewStatus.name)
    }

    private fun decode(obj: JSONObject): AiObservation = AiObservation(
        id = obj.getString("id"),
        detectedAtMs = obj.optLong("at", 0),
        videoOffsetMs = if (obj.has("offset")) obj.getLong("offset") else null,
        kind = AiEvidenceKind.valueOf(obj.getString("kind")),
        label = obj.optString("label", ""),
        confidence = obj.optDouble("confidence", 0.0).toFloat(),
        proposedSpeedKph = if (obj.has("speed")) obj.getInt("speed") else null,
        lat = if (obj.has("lat")) obj.getDouble("lat") else null,
        lon = if (obj.has("lon")) obj.getDouble("lon") else null,
        roadWayId = if (obj.has("way")) obj.getLong("way") else null,
        evidenceFile = obj.optString("file", "").takeIf { it.isNotBlank() },
        source = obj.optString("source", "70mai_import"),
        reviewStatus = AiReviewStatus.valueOf(obj.optString("status", "PENDING"))
    )
}
