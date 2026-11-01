package vn.thanhtaxixanh.bg98g00107.car

import vn.thanhtaxixanh.bg98g00107.calendar.EventLine
import vn.thanhtaxixanh.bg98g00107.calendar.Events
import vn.thanhtaxixanh.bg98g00107.core.logErr
import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardSettings
import vn.thanhtaxixanh.bg98g00107.dashboard.DashboardSettingsRepository
import vn.thanhtaxixanh.bg98g00107.dashboard.LauncherRenderer
import vn.thanhtaxixanh.bg98g00107.dashboard.UiState
import vn.thanhtaxixanh.bg98g00107.map.NativeMap
import vn.thanhtaxixanh.bg98g00107.navigation.BRouterServiceClient
import vn.thanhtaxixanh.bg98g00107.navigation.ManeuverType
import vn.thanhtaxixanh.bg98g00107.navigation.NavigationVoiceAnnouncer
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePlanStore
import vn.thanhtaxixanh.bg98g00107.navigation.RoutePoint
import vn.thanhtaxixanh.bg98g00107.navigation.RouteRequest
import vn.thanhtaxixanh.bg98g00107.signs.SignsState

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

class CarService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // Do not allow arbitrary binders to control the release navigation service.
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session {
        val appCtx: Context = this
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen =
                LauncherScreen(carContext, appCtx)
        }
    }
}

/** Host-supported menu. No forms, unrestricted app launch or moving vehicle editing. */
internal class CarStatusMenu(
    ctx: CarContext,
    private val gpsStatus: CarGpsState,
    private val mapStatus: String,
    private val refreshMap: () -> Unit
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val entries = ItemList.Builder()
            .addItem(Row.Builder()
                .setTitle("Trở lại bản đồ")
                .setOnClickListener {
                    carContext.getCarService(ScreenManager::class.java).pop()
                }.build())
            .addItem(Row.Builder()
                .setTitle("GPS trước menu: ${gpsStatus.title}")
                .build())
            .addItem(Row.Builder()
                .setTitle("Bản đồ: ${mapStatus.take(70)}")
                .build())
            .addItem(Row.Builder()
                .setTitle("Làm mới bản đồ")
                .setOnClickListener {
                    refreshMap()
                    carContext.getCarService(ScreenManager::class.java).pop()
                }.build())
            .addItem(Row.Builder()
                .setTitle("Cấu hình Dashboard trên điện thoại")
                .addText("Thao tác chỉnh sửa khi đã dừng xe")
                .build())
            .build()
        return ListTemplate.Builder()
            .setTitle("Trạng thái & điều khiển")
            .setHeaderAction(Action.BACK)
            .setSingleList(entries)
            .build()
    }
}

class LauncherScreen(ctx: CarContext, private val appCtx: Context) : Screen(ctx) {
    private val surfaceState = CarSurfaceState()
    private var surface: Surface? = null
    private var speedKmh: Int? = null
    private var lastFixMs = 0L
    private var selected = 0
    private var lastDraw = 0L
    private var evKey = ""
    private var evLines: List<EventLine> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appCtx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private val configStore = DashboardSettingsRepository(appCtx)
    private var currentSettings = DashboardSettings()
    private val qrFile = File(appCtx.filesDir, "qr.png")
    private val maps = NativeMap(appCtx, handler)
    private val renderer = LauncherRenderer()
    private var gpsRegistered = false
    private var gnssRegistered = false
    private var locationPermission = false
    private var tickerRunning = false
    private var mapRefreshRequested = false
    private val isDebuggable =
        (appCtx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private var cachedBatteryPct = -1
    private var lastBatteryReadMs = Long.MIN_VALUE
    private var rerouteClient: BRouterServiceClient? = null
    private val routeStore by lazy { RoutePlanStore(appCtx) }
    private var rerouteInFlight = false
    private var lastRerouteMs = Long.MIN_VALUE
    private val voiceAnnouncer = NavigationVoiceAnnouncer(appCtx)

    private fun hasFineLocation(): Boolean =
        appCtx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun gpsState(): CarGpsState =
        CarGpsPolicy.state(surfaceState.canRun(), locationPermission,
            SystemClock.uptimeMillis(), lastFixMs)

    private val ticker = object : Runnable {
        override fun run() {
            if (!surfaceState.canRun()) {
                tickerRunning = false
                return
            }
            locationPermission = hasFineLocation()
            // A revoked permission immediately stops GPS callbacks.
            if (!locationPermission && (gpsRegistered || gnssRegistered)) {
                unregisterLocation()
                lastFixMs = 0L
                speedKmh = null
            } else if (locationPermission && !gpsRegistered) {
                registerLocation()
            }
            currentSettings = try {
                configStore.read()
            } catch (e: Exception) {
                logErr("CarService:dashboardSettings", e)
                DashboardSettings()
            }
            refreshBatteryIfDue()
            refreshEventsIfNeeded()
            draw()
            if (mapRefreshRequested) {
                mapRefreshRequested = false
                maps.stop()
            }
            ensureMaps()
            maps.update(System.currentTimeMillis())
            handler.postDelayed(this, 1000L)
        }
    }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!surfaceState.canRun() || !locationPermission) return
            val ageNs = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (ageNs < 0L || ageNs > 10_000_000_000L) return
            lastFixMs = SystemClock.uptimeMillis()
            speedKmh = if (location.hasSpeed() && location.hasAccuracy() &&
                location.accuracy <= 35f && location.speed >= 0f) {
                if (location.speed < 0.6f) 0
                else (location.speed * 3.6f).roundToInt()
            } else null
            maps.onLocation(location)
            voiceAnnouncer.onGuidance(
                maps.guidanceState,
                speedKmh ?: 0,
                SystemClock.uptimeMillis()
            )
            maybeReroute(location)
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            if (!surfaceState.canRun()) return
            var used = 0
            var sum = 0.0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) {
                    used++
                    sum += status.getCn0DbHz(i)
                }
            }
            maps.setGnss(used, if (used > 0) sum / used else 0.0)
        }
    }

    init {
        maps.onFrame = {
            if (surfaceState.canRun() &&
                SystemClock.uptimeMillis() - lastDraw >= 40L) draw()
        }
        // Screen may STOP while a menu/another app is on top even if Surface
        // stays allocated. Suspend GPS, map VirtualDisplay and ticker then.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    surfaceState.onStart()
                    startUpdates()
                }
                Lifecycle.Event.ON_STOP -> {
                    surfaceState.onStop()
                    stopUpdates()
                    maps.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    surfaceState.onDestroy()
                    stopUpdates()
                    rerouteClient?.close()
                    rerouteClient = null
                    voiceAnnouncer.shutdown()
                    maps.dispose()
                    surface?.release()
                    surface = null
                }
                else -> Unit
            }
        })
        ctx.getCarService(AppManager::class.java).setSurfaceCallback(
            object : SurfaceCallback {
                override fun onSurfaceAvailable(container: SurfaceContainer) {
                    val candidate = container.surface ?: return
                    val oldWidth = surfaceState.width
                    val oldHeight = surfaceState.height
                    if (!surfaceState.onAvailable(
                            candidate, container.width, container.height
                        )) {
                        if (candidate !== surface) candidate.release()
                        return
                    }
                    val previous = surface
                    if (previous != null && previous !== candidate) {
                        stopUpdates()
                        maps.stop()
                        previous.release()
                    } else if (previous != null &&
                        (oldWidth != container.width || oldHeight != container.height)
                    ) {
                        // Resize without dropping the actual host surface.
                        maps.stop()
                    }
                    surface = candidate
                    startUpdates()
                }

                override fun onSurfaceDestroyed(container: SurfaceContainer) {
                    if (!surfaceState.onDestroyed(container.surface)) return
                    stopUpdates()
                    maps.stop()
                    surface?.release()
                    surface = null
                }

                override fun onClick(x: Float, y: Float) {
                    if (surfaceState.canRun()) handleClick(x, y)
                }

                override fun onScroll(distanceX: Float, distanceY: Float) {
                    if (surfaceState.canRun()) maps.scrollBy(distanceX, distanceY)
                }

                override fun onFling(velocityX: Float, velocityY: Float) {
                    if (surfaceState.canRun()) maps.fling(velocityX, velocityY)
                }

                override fun onScale(
                    focusX: Float, focusY: Float, scaleFactor: Float
                ) {
                    if (!surfaceState.canRun()) return
                    val p = renderer.hitMapPoint(focusX, focusY)
                    if (p != null) maps.pinch(scaleFactor, p[0], p[1])
                }
            }
        )
    }

    private fun handleClick(x: Float, y: Float) {
        val i = renderer.hitIcon(x, y)
        if (i >= 0) {
            selected = i
            draw()
            return
        }
        renderer.hitMapPoint(x, y)?.let { maps.tap(it[0], it[1]) }
    }


    private fun maybeReroute(location: Location) {
        if (maps.guidanceState.maneuver != ManeuverType.OFF_ROUTE) return
        if (rerouteInFlight) return
        if (!location.hasAccuracy() || location.accuracy > 30f) return
        val destination = maps.routeDestination() ?: return
        val now = SystemClock.uptimeMillis()
        if (lastRerouteMs != Long.MIN_VALUE && now - lastRerouteMs < 30_000L) return

        rerouteInFlight = true
        lastRerouteMs = now
        val client = rerouteClient ?: BRouterServiceClient(appCtx).also {
            rerouteClient = it
        }
        val heading = if (location.hasBearing()) location.bearing.toDouble() else null
        client.requestRoute(
            RouteRequest(
                start = RoutePoint(location.latitude, location.longitude),
                destination = destination,
                headingDegrees = heading
            )
        ) { result ->
            rerouteInFlight = false
            result.onSuccess { route ->
                try {
                    routeStore.saveRaw(route.rawJson)
                    voiceAnnouncer.resetForNewRoute()
                    maps.setRoute(route.plan)
                    draw()
                } catch (e: Exception) {
                    logErr("CarService:rerouteSave", e)
                }
            }.onFailure { e ->
                logErr("CarService:reroute", e)
            }
        }
    }

    private fun refreshBatteryIfDue() {
        val now = SystemClock.uptimeMillis()
        if (lastBatteryReadMs != Long.MIN_VALUE &&
            now - lastBatteryReadMs < 60_000L) return
        lastBatteryReadMs = now
        val manager = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        cachedBatteryPct = manager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: -1
    }

    private fun refreshEventsIfNeeded() {
        val raw = prefs.getString("events", "") ?: ""
        val today = LocalDate.now()
        val key = raw + today.toString()
        if (key != evKey) {
            evKey = key
            evLines = try {
                Events.lines(raw, today)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun ensureMaps() {
        if (!surfaceState.canRun()) return
        val r = renderer.mapRect() ?: return
        if (r.width() < 64f || r.height() < 64f) return
        val wantW = r.width().toInt()
        val wantH = r.height().toInt()
        if (maps.running() &&
            (kotlin.math.abs(maps.frameW - wantW) >= 3 ||
                kotlin.math.abs(maps.frameH - wantH) >= 3)) {
            maps.stop()
        }
        if (!maps.running()) maps.start(wantW, wantH)
    }

    private fun startUpdates() {
        if (!surfaceState.canRun()) return
        locationPermission = hasFineLocation()
        if (!tickerRunning) {
            tickerRunning = true
            handler.post(ticker)
        }
        if (maps.running()) maps.resume()
        registerLocation()
    }

    @SuppressLint("MissingPermission")
    private fun registerLocation() {
        if (!surfaceState.canRun() || !locationPermission) return
        try {
            val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!gpsRegistered) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L,
                    0f, locListener, Looper.getMainLooper())
                gpsRegistered = true
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    locListener.onLocationChanged(it)
                }
            }
            if (!gnssRegistered) {
                lm.registerGnssStatusCallback(appCtx.mainExecutor, gnssCallback)
                gnssRegistered = true
            }
        } catch (e: Exception) {
            logErr("CarService:registerLocation", e)
        }
    }

    private fun unregisterLocation() {
        try {
            val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (gpsRegistered) lm.removeUpdates(locListener)
            if (gnssRegistered) lm.unregisterGnssStatusCallback(gnssCallback)
        } catch (e: Exception) {
            logErr("CarService:unregisterLocation", e)
        } finally {
            gpsRegistered = false
            gnssRegistered = false
            maps.setGnss(0, 0.0)
        }
    }

    private fun stopUpdates() {
        voiceAnnouncer.stop()
        tickerRunning = false
        handler.removeCallbacks(ticker)
        unregisterLocation()
        lastFixMs = 0L
        speedKmh = null
    }

    private fun requestMapRefresh() { mapRefreshRequested = true }

    private fun draw() {
        if (!surfaceState.canRun()) return
        val drawBeginNs = if (isDebuggable) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            0L
        }
        val s = surface ?: return
        if (!s.isValid) return
        lastDraw = SystemClock.uptimeMillis()
        val canvas = try {
            s.lockCanvas(null)
        } catch (e: Exception) {
            logErr("CarService:lockCanvas", e)
            return
        }
        try {
            val gpsReady = gpsState() == CarGpsState.READY
            maps.withFrame { mapFrame ->
                val st = UiState(
                    speedKmh = speedKmh.takeIf { gpsReady },
                    gpsFix = gpsReady,
                    satellites = maps.satUsed,
                    batteryPct = cachedBatteryPct,
                    qrFile = qrFile,
                    nowMs = System.currentTimeMillis(),
                    selectedIcon = selected,
                    events = evLines,
                    mapFrame = mapFrame,
                    mapFrameW = maps.frameW,
                    mapFrameH = maps.frameH,
                    mapsStatus = maps.status,
                    streetName = maps.streetName.takeIf { gpsReady } ?: "",
                    signs = if (gpsReady) maps.signsState else SignsState.EMPTY,
                    settings = currentSettings,
                    guidance = maps.guidanceState
                )
                if (isDebuggable) {
                    // Personal/debug Android Auto mode: always keep the full dashboard
                    // (map + driver data + right-side utility rows) on one surface.
                    renderer.render(
                        canvas,
                        surfaceState.width,
                        surfaceState.height,
                        st
                    )
                } else {
                    renderer.renderNavigationSurface(
                        canvas,
                        surfaceState.width,
                        surfaceState.height,
                        st
                    )
                }
            }
        } catch (e: Exception) {
            logErr("CarService:draw", e)
        } finally {
            try {
                s.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                logErr("CarService:unlockCanvas", e)
            }
            if (isDebuggable) {
                maps.recordHostDrawNs(
                    SystemClock.elapsedRealtimeNanos() - drawBeginNs
                )
            }
        }
    }

    override fun onGetTemplate(): Template =
        NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Menu")
                            .setOnClickListener {
                                carContext.getCarService(ScreenManager::class.java)
                                    .push(CarStatusMenu(
                                        carContext,
                                        gpsStatus = gpsState(),
                                        mapStatus = maps.status,
                                        refreshMap = { requestMapRefresh() }
                                    ))
                            }
                            .build()
                    ).build()
            ).build()
}
