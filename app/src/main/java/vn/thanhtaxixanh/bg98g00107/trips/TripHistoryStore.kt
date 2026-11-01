package vn.thanhtaxixanh.bg98g00107.trips

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.cos
import kotlin.math.sqrt

data class SavedTrip(
    val id: String,
    val startedMs: Long,
    val endedMs: Long?,
    val distanceM: Double,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val favorite: Boolean,
    val active: Boolean
)

data class SavedTripPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Int
)

class TripHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "ttx_trips_v1.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE trips(
                id TEXT PRIMARY KEY,
                started_ms INTEGER NOT NULL,
                ended_ms INTEGER,
                distance_m REAL NOT NULL,
                start_lat REAL NOT NULL,
                start_lon REAL NOT NULL,
                end_lat REAL NOT NULL,
                end_lon REAL NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1
            )"""
        )
        db.execSQL(
            """CREATE TABLE trip_points(
                trip_id TEXT NOT NULL,
                time_ms INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                speed_kmh INTEGER NOT NULL,
                PRIMARY KEY(trip_id,time_ms)
            )"""
        )
        db.execSQL(
            "CREATE INDEX idx_trip_points_trip_time ON trip_points(trip_id,time_ms)"
        )
        db.execSQL(
            "CREATE INDEX idx_trips_started ON trips(started_ms DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Kho chuyến đi cần nâng cấp thủ công.")
    }

    fun activeTrip(): SavedTrip? = readableDatabase.rawQuery(
        """SELECT id,started_ms,ended_ms,distance_m,start_lat,start_lon,
            end_lat,end_lon,favorite,active
            FROM trips WHERE active=1 ORDER BY started_ms DESC LIMIT 1""",
        null
    ).use { c ->
        if (!c.moveToFirst()) null else row(c)
    }

    fun startTrip(timeMs: Long, lat: Double, lon: Double): SavedTrip {
        require(timeMs > 0L)
        require(valid(lat, lon))
        val id = "trip_$timeMs"
        val v = ContentValues().apply {
            put("id", id)
            put("started_ms", timeMs)
            putNull("ended_ms")
            put("distance_m", 0.0)
            put("start_lat", lat)
            put("start_lon", lon)
            put("end_lat", lat)
            put("end_lon", lon)
            put("favorite", 0)
            put("active", 1)
        }
        writableDatabase.insertWithOnConflict(
            "trips", null, v, SQLiteDatabase.CONFLICT_IGNORE
        )
        return requireNotNull(activeTrip())
    }

    fun appendPoint(
        tripId: String,
        timeMs: Long,
        lat: Double,
        lon: Double,
        speedKmh: Int
    ): Boolean {
        require(tripId.isNotBlank() && timeMs > 0L && valid(lat, lon))
        val db = writableDatabase
        db.beginTransaction()
        try {
            var lastLat: Double? = null
            var lastLon: Double? = null
            db.rawQuery(
                """SELECT lat,lon FROM trip_points
                   WHERE trip_id=? ORDER BY time_ms DESC LIMIT 1""",
                arrayOf(tripId)
            ).use { c ->
                if (c.moveToFirst()) {
                    lastLat = c.getDouble(0)
                    lastLon = c.getDouble(1)
                }
            }
            val point = ContentValues().apply {
                put("trip_id", tripId)
                put("time_ms", timeMs)
                put("lat", lat)
                put("lon", lon)
                put("speed_kmh", speedKmh.coerceIn(0, 250))
            }
            val inserted = db.insertWithOnConflict(
                "trip_points", null, point, SQLiteDatabase.CONFLICT_IGNORE
            ) != -1L
            if (!inserted) {
                db.setTransactionSuccessful()
                return false
            }
            val delta = if (lastLat != null && lastLon != null) {
                distanceM(lastLat!!, lastLon!!, lat, lon)
                    .takeIf { it in 0.0..500.0 } ?: 0.0
            } else 0.0
            db.execSQL(
                """UPDATE trips SET distance_m=distance_m+?,
                   end_lat=?,end_lon=? WHERE id=? AND active=1""",
                arrayOf(delta, lat, lon, tripId)
            )
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun finishTrip(tripId: String, endedMs: Long) {
        writableDatabase.execSQL(
            "UPDATE trips SET active=0,ended_ms=? WHERE id=? AND active=1",
            arrayOf(endedMs, tripId)
        )
    }

    fun setFavorite(tripId: String, favorite: Boolean) {
        writableDatabase.execSQL(
            "UPDATE trips SET favorite=? WHERE id=?",
            arrayOf(if (favorite) 1 else 0, tripId)
        )
    }

    fun list(limit: Int = 80, favoritesOnly: Boolean = false): List<SavedTrip> {
        val safe = limit.coerceIn(1, 200)
        val where = if (favoritesOnly) "WHERE favorite=1" else ""
        val out = ArrayList<SavedTrip>()
        readableDatabase.rawQuery(
            """SELECT id,started_ms,ended_ms,distance_m,start_lat,start_lon,
               end_lat,end_lon,favorite,active FROM trips
               $where ORDER BY started_ms DESC LIMIT $safe""",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(row(c))
        }
        return out
    }

    fun activePoints(limit: Int = 2_500): List<SavedTripPoint> {
        val trip = activeTrip() ?: return emptyList()
        val safe = limit.coerceIn(2, 10_000)
        val out = ArrayList<SavedTripPoint>()
        readableDatabase.rawQuery(
            """SELECT time_ms,lat,lon,speed_kmh FROM trip_points
               WHERE trip_id=? ORDER BY time_ms DESC LIMIT $safe""",
            arrayOf(trip.id)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SavedTripPoint(
                        c.getLong(0), c.getDouble(1), c.getDouble(2), c.getInt(3)
                    )
                )
            }
        }
        out.reverse()
        return out
    }

    private fun row(c: android.database.Cursor): SavedTrip = SavedTrip(
        id = c.getString(0),
        startedMs = c.getLong(1),
        endedMs = if (c.isNull(2)) null else c.getLong(2),
        distanceM = c.getDouble(3),
        startLat = c.getDouble(4),
        startLon = c.getDouble(5),
        endLat = c.getDouble(6),
        endLon = c.getDouble(7),
        favorite = c.getInt(8) != 0,
        active = c.getInt(9) != 0
    )

    private fun valid(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0

    private fun distanceM(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dx = (lon2 - lon1) * 111_320.0 *
            cos(Math.toRadians((lat1 + lat2) / 2.0))
        val dy = (lat2 - lat1) * 111_320.0
        return sqrt(dx * dx + dy * dy)
    }
}
