package id.karawang.biketelemetry

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.math.abs

class TelemetryBoosterService : Service(), LocationListener {

    companion object {
        const val CHANNEL_ID = "bike_telemetry_channel"
        const val NOTIF_ID = 3031
        const val ACTION_TELEMETRY = "id.karawang.biketelemetry.UPDATE"
        const val ACTION_INJECT_AGPS = "id.karawang.biketelemetry.INJECT_AGPS"
        const val ACTION_TOGGLE_HUD = "id.karawang.biketelemetry.TOGGLE_HUD"

        var isRunning = false
    }

    private lateinit var locationManager: LocationManager
    private lateinit var hudManager: FloatingHudManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val netExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var lastAccuracy = -1f
    private var lastSpeedKmh = 0f
    private var usedSatellites = 0
    private var totalSatellites = 0
    private var lastPingMs = -1L
    private var previousPingMs = -1L
    private var jitterMs = 0L

    private var gnssCallback: GnssStatus.Callback? = null

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            runNetworkHeartbeat()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        hudManager = FloatingHudManager(this)

        createNotificationChannel()
        startForegroundServiceNotification("Menyalakan GNSS 1Hz & Jalur 4G...")
        acquireCpuLock()
        injectAgpsAssistanceData()
        startGnssEngine()

        val showHud = getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
            .getBoolean("hud_enabled", true)
        if (showHud) hudManager.show()

        handler.post(keepAliveRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INJECT_AGPS -> {
                injectAgpsAssistanceData()
                startGnssEngine()
            }
            ACTION_TOGGLE_HUD -> {
                val enable = intent.getBooleanExtra("enable", true)
                if (enable) hudManager.show() else hudManager.hide()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification(text: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notif = builder
            .setContentTitle("Bike Telemetry Pro Aktif")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Bike Telemetry & GNSS Booster",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireCpuLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BikeTelemetry::CpuLock")
            wakeLock?.acquire()
        } catch (_: Exception) {}
    }

    fun injectAgpsAssistanceData() {
        try {
            locationManager.sendExtraCommand("gps", "force_xtra_injection", null)
            locationManager.sendExtraCommand("gps", "force_time_injection", null)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startGnssEngine() {
        try {
            locationManager.removeUpdates(this)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, this)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
                gnssCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        var used = 0
                        val total = status.satelliteCount
                        for (i in 0 until total) {
                            if (status.usedInFix(i)) used++
                        }
                        usedSatellites = used
                        totalSatellites = total
                        dispatchTelemetryUpdate()
                    }
                }
                locationManager.registerGnssStatusCallback(gnssCallback!!, handler)
            }
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        lastAccuracy = location.accuracy
        lastSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f
        dispatchTelemetryUpdate()
    }

    private fun runNetworkHeartbeat() {
        netExecutor.execute {
            val start = System.currentTimeMillis()
            val ok = try {
                InetAddress.getByName("driver-id.taxsee.com")
                Socket().use { s ->
                    s.connect(InetSocketAddress("8.8.8.8", 53), 2000)
                    true
                }
            } catch (_: Exception) {
                false
            }
            val elapsed = if (ok) (System.currentTimeMillis() - start) else -1L
            if (elapsed >= 0L) {
                jitterMs = if (previousPingMs >= 0L) abs(elapsed - previousPingMs) else 0L
                previousPingMs = elapsed
            }
            lastPingMs = elapsed
            handler.post { dispatchTelemetryUpdate() }
        }
    }

    private fun dispatchTelemetryUpdate() {
        hudManager.updateHud(lastAccuracy, usedSatellites, totalSatellites, lastPingMs, jitterMs)

        val accStr = if (lastAccuracy > 0f) "±${lastAccuracy.toInt()}m" else "--"
        val pingStr = if (lastPingMs >= 0L) "${lastPingMs}ms" else "Putus"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notif = builder
            .setContentTitle("GPS $accStr ($usedSatellites/$totalSatellites Sat) • Ping $pingStr")
            .setContentText("Jalur Auto-Assign Siaga • Ketuk untuk panel")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        nm.notify(NOTIF_ID, notif)

        val intent = Intent(ACTION_TELEMETRY).apply {
            setPackage(packageName)
            putExtra("acc", lastAccuracy)
            putExtra("speed", lastSpeedKmh)
            putExtra("usedSats", usedSatellites)
            putExtra("totalSats", totalSatellites)
            putExtra("ping", lastPingMs)
            putExtra("jitter", jitterMs)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(keepAliveRunnable)
        hudManager.hide()
        try {
            locationManager.removeUpdates(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                locationManager.unregisterGnssStatusCallback(gnssCallback!!)
            }
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        netExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
