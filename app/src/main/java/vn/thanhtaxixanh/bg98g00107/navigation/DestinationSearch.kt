package vn.thanhtaxixanh.bg98g00107.navigation

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class DestinationCandidate(
    val label: String,
    val point: RoutePoint
)

/**
 * Phone-only destination lookup. Routing itself remains fully offline in
 * BRouter; Geocoder is only a convenience layer for converting a name/address
 * into coordinates. Direct lat,lon input always remains available.
 */
class DestinationSearch(context: Context) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)

    fun search(
        query: String,
        near: RoutePoint?,
        callback: (Result<List<DestinationCandidate>>) -> Unit
    ) {
        if (closed.get()) {
            callback(Result.failure(IllegalStateException("Tìm kiếm đã đóng.")))
            return
        }
        worker.execute {
            val result = runCatching {
                parseCoordinates(query)?.let {
                    return@runCatching listOf(
                        DestinationCandidate("Tọa độ đã nhập", it)
                    )
                }
                if (!Geocoder.isPresent()) {
                    throw IllegalStateException(
                        "Thiết bị không có dịch vụ tìm địa chỉ. " +
                            "Hãy nhập vĩ độ,kinh độ."
                    )
                }
                val geocoder = Geocoder(app, Locale("vi", "VN"))
                val normalized = if (
                    query.contains("việt nam", ignoreCase = true) ||
                    query.contains("vietnam", ignoreCase = true)
                ) query else query + ", Việt Nam"

                val local = if (near != null) {
                    runCatching {
                        geocoder.getFromLocationName(
                            normalized,
                            6,
                            (near.lat - 1.5).coerceAtLeast(-90.0),
                            (near.lon - 1.5).coerceAtLeast(-180.0),
                            (near.lat + 1.5).coerceAtMost(90.0),
                            (near.lon + 1.5).coerceAtMost(180.0)
                        )
                    }.getOrNull().orEmpty()
                } else emptyList()

                val addresses = if (local.isNotEmpty()) local else
                    geocoder.getFromLocationName(normalized, 6).orEmpty()

                addresses
                    .asSequence()
                    .filter { it.hasLatitude() && it.hasLongitude() }
                    .map {
                        val point = RoutePoint(it.latitude, it.longitude)
                        val label = buildString {
                            for (i in 0..it.maxAddressLineIndex) {
                                val line = it.getAddressLine(i)
                                if (!line.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(line)
                                }
                            }
                            if (isEmpty()) {
                                append(it.featureName ?: it.locality ?: "Điểm đến")
                            }
                        }
                        DestinationCandidate(label, point)
                    }
                    .distinctBy {
                        val latKey = (it.point.lat * 100_000).toInt()
                        val lonKey = (it.point.lon * 100_000).toInt()
                        latKey to lonKey
                    }
                    .take(6)
                    .toList()
            }
            main.post {
                if (!closed.get()) callback(result)
            }
        }
    }

    private fun parseCoordinates(raw: String): RoutePoint? {
        val parts = raw.trim()
            .split(',', ';', ' ', '\n', '\t')
            .filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return RoutePoint(lat, lon)
    }

    fun close() {
        if (closed.compareAndSet(false, true)) worker.shutdownNow()
    }
}
