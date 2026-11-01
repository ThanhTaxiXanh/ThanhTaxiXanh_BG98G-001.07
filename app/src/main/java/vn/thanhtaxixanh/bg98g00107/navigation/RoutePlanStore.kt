package vn.thanhtaxixanh.bg98g00107.navigation

import android.content.Context
import java.io.File
import java.io.FileOutputStream

data class RouteStoreSnapshot(val stamp: Long, val plan: RoutePlan?)

class RoutePlanStore(context: Context) {
    private val file = File(context.filesDir, "active_brouter_route.json")

    @Synchronized fun saveRaw(raw: String): RoutePlan {
        val plan = BRouterGeoJsonParser.parse(raw)
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(raw.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("Không thay được tuyến cũ.")
            }
            check(tmp.renameTo(file)) { "Không ghi được tuyến." }
        } finally {
            tmp.delete()
        }
        return plan
    }

    @Synchronized fun clear() {
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Không xóa được tuyến.")
        }
    }

    @Synchronized fun snapshotIfChanged(knownStamp: Long): RouteStoreSnapshot? {
        if (!file.isFile || file.length() <= 0L) {
            return if (knownStamp == 0L) null else RouteStoreSnapshot(0L, null)
        }
        require(file.length() <= 16L * 1024L * 1024L) { "Tuyến lưu quá lớn." }
        val stamp = file.lastModified() xor file.length()
        if (stamp == knownStamp) return null
        return RouteStoreSnapshot(
            stamp,
            BRouterGeoJsonParser.parse(file.readText(Charsets.UTF_8))
        )
    }
}
