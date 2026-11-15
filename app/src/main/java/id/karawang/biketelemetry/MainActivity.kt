package id.karawang.biketelemetry

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var tvEngineStatus: TextView
    private lateinit var tvGpsPrecision: TextView
    private lateinit var tvSatCount: TextView
    private lateinit var tvNetMetrics: TextView
    private lateinit var pingGraph: PingGraphView
    private lateinit var swFloatingHud: Switch
    private lateinit var tvShiftStrategy: TextView
    private lateinit var tvDailyStats: TextView
    private lateinit var tvDailyNet: TextView
    private lateinit var etArgoInput: EditText

    private var tripCount = 0
    private var totalNet = 0

    private val telemetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val acc = intent.getFloatExtra("acc", -1f)
            val usedSats = intent.getIntExtra("usedSats", 0)
            val totalSats = intent.getIntExtra("totalSats", 0)
            val ping = intent.getLongExtra("ping", -1L)
            val jitter = intent.getLongExtra("jitter", 0L)

            if (acc > 0f) {
                if (acc <= 15f) {
                    tvGpsPrecision.text = "● Presisi GPS: ±${acc.toInt()} meter (TERKUNCI AKURAT)"
                    tvGpsPrecision.setTextColor(Color.parseColor("#10B981"))
                } else {
                    tvGpsPrecision.text = "● Presisi GPS: ±${acc.toInt()} meter (GESER KELUAR ATAP!)"
                    tvGpsPrecision.setTextColor(Color.parseColor("#EF4444"))
                }
            }
            tvSatCount.text = "Satelit GNSS Aktif: $usedSats terkunci dari $totalSats terlihat"

            if (ping >= 0L) {
                tvNetMetrics.text = "Latensi: ${ping} ms | Jitter: ${jitter} ms"
                tvNetMetrics.setTextColor(if (ping <= 85L) Color.parseColor("#38BDF8") else Color.parseColor("#FBBF24"))
                pingGraph.addSample(ping)
            } else {
                tvNetMetrics.text = "Sinyal Data: Terputus!"
                tvNetMetrics.setTextColor(Color.parseColor("#EF4444"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1120"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 38, 34, 60)
        }

        val headerTitle = TextView(this).apply {
            text = "BIKE TELEMETRY PRO • KARAWANG"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        }
        val headerSub = TextView(this).apply {
            text = "A-GPS XTRA • Keep-Alive 4G • Floating HUD • 100% Aman PM"
            textSize = 11.5f
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, 4, 0, 22)
        }
        container.addView(headerTitle)
        container.addView(headerSub)

        val teleCard = createCard()
        teleCard.addView(createSectionLabel("1. TELEMETRI GNSS & STABILITAS JARINGAN"))

        tvEngineStatus = TextView(this).apply {
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 6)
        }
        tvGpsPrecision = TextView(this).apply {
            text = "Presisi GPS: Menunggu layanan aktif..."
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
        }
        tvSatCount = TextView(this).apply {
            text = "Satelit GNSS Aktif: --"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 2, 0, 8)
        }
        tvNetMetrics = TextView(this).apply {
            text = "Latensi: -- ms | Jitter: -- ms"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, 8)
        }

        pingGraph = PingGraphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).apply { setMargins(0, 4, 0, 12) }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#080D1A"))
                cornerRadius = 14f
                setStroke(1, Color.parseColor("#1E293B"))
            }
        }

        val btnStartAll = createButton("AKTIFKAN TELEMETRI & BUKA TAXSEE DRIVER", "#10B981") {
            startTelemetryAndLaunchMaxim()
        }
        val btnRefreshAgps = createButton("INJEKSI ULANG A-GPS XTRA (REFRESH SATELIT)", "#0284C7") {
            if (TelemetryBoosterService.isRunning) {
                val i = Intent(this, TelemetryBoosterService::class.java).apply {
                    action = TelemetryBoosterService.ACTION_INJECT_AGPS
                }
                startService(i)
                Toast.makeText(this, "Data orbit satelit A-GPS XTRA disegarkan!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nyalakan layanan telemetri terlebih dahulu.", Toast.LENGTH_SHORT).show()
            }
        }
        val btnStopAll = createButton("MATIKAN LAYANAN (ISTIRAHAT)", "#334155") {
            stopService(Intent(this, TelemetryBoosterService::class.java))
            updateStatusBadge()
            Toast.makeText(this, "Layanan dimatikan.", Toast.LENGTH_SHORT).show()
        }

        teleCard.addView(tvEngineStatus)
        teleCard.addView(tvGpsPrecision)
        teleCard.addView(tvSatCount)
        teleCard.addView(tvNetMetrics)
        teleCard.addView(pingGraph)
        teleCard.addView(btnStartAll)
        teleCard.addView(btnRefreshAgps)
        teleCard.addView(btnStopAll)
        container.addView(teleCard)

        val hudCard = createCard()
        hudCard.addView(createSectionLabel("2. FLOATING HUD DI ATAS LAYAR MAXIM"))

        swFloatingHud = Switch(this).apply {
            text = "Tampilkan HUD Melayang + Tombol Salin Chat"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            isChecked = getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
                .getBoolean("hud_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    isChecked = false
                    requestOverlayPermission()
                    return@setOnCheckedChangeListener
                }
                getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("hud_enabled", checked).apply()
                if (TelemetryBoosterService.isRunning) {
                    val i = Intent(this@MainActivity, TelemetryBoosterService::class.java).apply {
                        action = TelemetryBoosterService.ACTION_TOGGLE_HUD
                        putExtra("enable", checked)
                    }
                    startService(i)
                }
            }
        }
        val hudNote = TextView(this).apply {
            text = "HUD menampilkan presisi GPS, jumlah satelit, ping, dan tombol 1-ketuk salin chat konfirmasi agar order terkunci di server saat sudah diterima."
            textSize = 11.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 8, 0, 0)
        }
        hudCard.addView(swFloatingHud)
        hudCard.addView(hudNote)
        container.addView(hudCard)

        val shiftCard = createCard()
        shiftCard.addView(createSectionLabel("3. STRATEGI SEL PETA & JAM SHIFT KIIC"))
        tvShiftStrategy = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setLineSpacing(6f, 1.0f)
        }
        shiftCard.addView(tvShiftStrategy)
        container.addView(shiftCard)

        val statCard = createCard()
        statCard.addView(createSectionLabel("4. TARGET BIKE HARIAN (JAGA RATA-RATA > 9.56)"))

        tvDailyStats = TextView(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 4)
        }
        tvDailyNet = TextView(this).apply {
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, 0, 0, 10)
        }
        etArgoInput = createEditText(InputType.TYPE_CLASS_NUMBER, "10000")

        val btnAddTrip = createButton("+ CATAT ORDER BIKE SELESAI (POTONGAN 15%)", "#10B981") {
            val gross = etArgoInput.text.toString().toIntOrNull() ?: 10000
            tripCount += 1
            totalNet += (gross * 0.85).toInt()
            saveDailyStats()
            updateDailyStatsUI()
            Toast.makeText(this, "Order Bike tercatat!", Toast.LENGTH_SHORT).show()
        }
        val btnResetTrip = createButton("RESET HITUNGAN HARI BARU", "#334155") {
            tripCount = 0
            totalNet = 0
            saveDailyStats()
            updateDailyStatsUI()
        }

        statCard.addView(tvDailyStats)
        statCard.addView(tvDailyNet)
        statCard.addView(etArgoInput)
        statCard.addView(btnAddTrip)
        statCard.addView(btnResetTrip)
        container.addView(statCard)

        val sysCard = createCard()
        sysCard.addView(createSectionLabel("5. PERINTAH PRIORITAS RAM & SINYAL (SHIZUKU / ADB)"))
        val adbScript =
            "dumpsys deviceidle whitelist +com.taxsee.driver\n" +
            "dumpsys deviceidle whitelist +id.karawang.biketelemetry\n" +
            "cmd appops set com.taxsee.driver RUN_ANY_IN_BACKGROUND allow\n" +
            "cmd appops set id.karawang.biketelemetry RUN_ANY_IN_BACKGROUND allow\n" +
            "settings put global mobile_data_always_on 1"

        val tvAdb = TextView(this).apply {
            text = adbScript
            textSize = 11f
            setTextColor(Color.parseColor("#93C5FD"))
            setPadding(18, 16, 18, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#080D1A"))
                cornerRadius = 12f
            }
        }
        val btnCopyAdb = createButton("SALIN PERINTAH SHIZUKU / ADB", "#0284C7") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ADB Script", adbScript))
            Toast.makeText(this, "Perintah Shizuku/ADB tersalin!", Toast.LENGTH_SHORT).show()
        }
        sysCard.addView(tvAdb)
        sysCard.addView(btnCopyAdb)
        container.addView(sysCard)

        scroll.addView(container)
        setContentView(scroll)

        loadDailyStats()
        updateDailyStatsUI()
        updateShiftAdvice()
        checkAndRequestPermissions()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        updateStatusBadge()
        val filter = IntentFilter(TelemetryBoosterService.ACTION_TELEMETRY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(telemetryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(telemetryReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(telemetryReceiver)
        } catch (_: Exception) {}
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 303)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' untuk HUD melayang.", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startTelemetryAndLaunchMaxim() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            checkAndRequestPermissions()
            Toast.makeText(this, "Izinkan akses Lokasi terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        if (swFloatingHud.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }

        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0)
        } catch (_: Exception) {}

        val svc = Intent(this, TelemetryBoosterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
        updateStatusBadge()

        val maximIntent = packageManager.getLaunchIntentForPackage("com.taxsee.driver")
        if (maximIntent != null) {
            startActivity(maximIntent)
        } else {
            Toast.makeText(this, "Telemetri aktif! Silakan buka Taxsee Driver.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusBadge() {
        if (TelemetryBoosterService.isRunning) {
            tvEngineStatus.text = "● STATUS: AKTIF (GNSS 1Hz + 4G KEEP-ALIVE)"
            tvEngineStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            tvEngineStatus.text = "● STATUS: NONAKTIF (Ketuk tombol hijau di bawah)"
            tvEngineStatus.setTextColor(Color.parseColor("#EF4444"))
        }
    }

    private fun updateShiftAdvice() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvShiftStrategy.text = when (h) {
            in 5..8 ->
                "• SHIFT 1 PAGI: Parkir 2-3 meter di luar kanopi/atap seng kost karyawan (Pinayungan, Sukaluyu, Wadas, Sirnabaya, Perumnas Telukjambe) agar satelit GNSS mengunci ±5-10m tepat di depan penumpang."
            in 14..18 ->
                "• SHIFT 2 & PULANG NORMAL KIIC: Geser 150-200m antar-gerbang pabrik agar memegang kotak koordinat peta tersendiri, lalu langsung tekan 'Sudah di Tempat' saat memasuki radius jemput."
            in 19..23 ->
                "• LEMBUR KIIC & SHIFT 3: Fokus jalur utama KIIC – Galuh Mas – Tuparev – Karawang Barat. Hindari tujuan pinggiran/tanggul gelap di jam malam."
            else ->
                "• JAM TENGAH HARI: Fokus titik kampus (Unsika/UBP), Stasiun Karawang, KCP, dan pusat kota."
        }
    }

    private fun loadDailyStats() {
        val p = getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE)
        tripCount = p.getInt("trips", 0)
        totalNet = p.getInt("net", 0)
    }

    private fun saveDailyStats() {
        getSharedPreferences("telemetry_prefs", Context.MODE_PRIVATE).edit().apply {
            putInt("trips", tripCount)
            putInt("net", totalNet)
            apply()
        }
    }

    private fun updateDailyStatsUI() {
        val sisa = (10 - tripCount).coerceAtLeast(0)
        tvDailyStats.text = "Order Bike Hari Ini: $tripCount trip (Kurang $sisa dari target 10)"
        tvDailyNet.text = "Estimasi Bersih: Rp$totalNet"
    }

    private fun createCard(): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#162032"))
            cornerRadius = 24f
            setStroke(2, Color.parseColor("#293548"))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(30, 26, 30, 26)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 22) }
        }
    }

    private fun createSectionLabel(textStr: String): TextView {
        return TextView(this).apply {
            text = textStr
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, 8)
        }
    }

    private fun createEditText(type: Int, defaultVal: String): EditText {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#0B1120"))
            cornerRadius = 16f
            setStroke(2, Color.parseColor("#3B4B63"))
        }
        return EditText(this).apply {
            inputType = type
            setText(defaultVal)
            background = bg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(22, 18, 22, 18)
        }
    }

    private fun createButton(label: String, hexColor: String, onClick: () -> Unit): Button {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(hexColor))
            cornerRadius = 18f
        }
        return Button(this).apply {
            text = label
            background = bg
            setTextColor(if (hexColor == "#10B981") Color.parseColor("#022C22") else Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(22, 22, 22, 22)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 14, 0, 0) }
            setOnClickListener { onClick() }
        }
    }
}
