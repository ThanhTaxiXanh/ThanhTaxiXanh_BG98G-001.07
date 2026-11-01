package vn.thanhtaxixanh.bg98g00107.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import btools.routingapp.IBRouterService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class BRouterRouteResult(val plan: RoutePlan, val rawJson: String)

class BRouterServiceClient(context: Context) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun requestRoute(
        request: RouteRequest,
        callback: (Result<BRouterRouteResult>) -> Unit
    ) {
        if (closed) {
            callback(Result.failure(IllegalStateException("BRouter client đã đóng.")))
            return
        }
        val intent = Intent().setComponent(
            ComponentName("btools.routingapp", "btools.routingapp.BRouterService")
        )
        if (app.packageManager.resolveService(intent, 0) == null) {
            callback(Result.failure(IllegalStateException(
                "Chưa cài BRouter. Hãy cài BRouter và tải RD5 trước."
            )))
            return
        }

        val delivered = AtomicBoolean(false)
        lateinit var connection: ServiceConnection
        fun finish(result: Result<BRouterRouteResult>) {
            if (!delivered.compareAndSet(false, true)) return
            runCatching { app.unbindService(connection) }
            main.post { if (!closed) callback(result) }
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = IBRouterService.Stub.asInterface(binder)
                worker.execute {
                    val result = runCatching {
                        val params = Bundle().apply {
                            putDoubleArray(
                                "lats",
                                doubleArrayOf(
                                    request.start.lat,
                                    request.destination.lat
                                )
                            )
                            putDoubleArray(
                                "lons",
                                doubleArrayOf(
                                    request.start.lon,
                                    request.destination.lon
                                )
                            )
                            putString("v", "motorcar")
                            putString("fast", "1")
                            putString("trackFormat", "json")
                            putString("timode", "9")
                            putString("maxRunningTime", "45")
                            request.headingDegrees
                                ?.takeIf { it.isFinite() }
                                ?.let {
                                    val heading = (((it % 360.0) + 360.0) % 360.0)
                                        .roundToInt() % 360
                                    putString("heading", heading.toString())
                                }
                        }
                        val raw = service.getTrackFromParams(params)
                            ?: throw IllegalStateException("BRouter không trả dữ liệu.")
                        if (!raw.trimStart().startsWith("{")) {
                            throw IllegalStateException("BRouter: " + raw.take(240))
                        }
                        BRouterRouteResult(BRouterGeoJsonParser.parse(raw), raw)
                    }
                    finish(result)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                finish(Result.failure(
                    IllegalStateException("BRouter service bị ngắt.")
                ))
            }

            override fun onBindingDied(name: ComponentName) {
                finish(Result.failure(
                    IllegalStateException("BRouter binding không còn hợp lệ.")
                ))
            }

            override fun onNullBinding(name: ComponentName) {
                finish(Result.failure(
                    IllegalStateException("BRouter từ chối kết nối.")
                ))
            }
        }

        val ok = runCatching {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse {
            finish(Result.failure(it))
            false
        }
        if (!ok) {
            finish(Result.failure(
                IllegalStateException("Không bind được BRouter service.")
            ))
        }
    }

    fun close() {
        closed = true
        worker.shutdownNow()
    }
}
