package id.karawang.biketelemetry

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingHudManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hudView: LinearLayout? = null
    private var tvGpsHud: TextView? = null
    private var tvNetHud: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = hudView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (hudView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#E6090F1E"))
            cornerRadius = 28f
            setStroke(2, Color.parseColor("#38BDF8"))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(22, 14, 22, 14)
        }

        tvGpsHud = TextView(context).apply {
            text = "GPS: --m | Sat: --"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
        }

        tvNetHud = TextView(context).apply {
            text = "Ping: --ms | Jitter: --"
            textSize = 11f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 2, 0, 8)
        }

        val btnQuickCopy = TextView(context).apply {
            text = "📋 SALIN CHAT KUNCI ORDER"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#022C22"))
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 14f
            }
            setOnClickListener {
                val msg = "Halo Kak, pesanan Bike sudah masuk ke saya. Saya segera meluncur ke titik jemput ya Kak."
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Kunci Order", msg))
                Toast.makeText(context, "Chat konfirmasi tersalin! Tempel di obrolan.", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(tvGpsHud)
        root.addView(tvNetHud)
        root.addView(btnQuickCopy)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = initialX + (event.rawX - initialTouchX).toInt()
                        p.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(root, p)
                        } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(root, params)
            hudView = root
        } catch (_: Exception) {}
    }

    fun updateHud(accuracy: Float, usedSats: Int, totalSats: Int, pingMs: Long, jitterMs: Long) {
        val gpsText = if (accuracy > 0f) "GPS: ±${accuracy.toInt()}m | Sat: $usedSats/$totalSats" else "GPS: Mencari satelit..."
        tvGpsHud?.text = gpsText
        tvGpsHud?.setTextColor(
            when {
                accuracy in 0.1f..15f -> Color.parseColor("#10B981")
                accuracy > 15f -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#F59E0B")
            }
        )

        val netText = if (pingMs >= 0L) "Ping: ${pingMs}ms | Jitter: ${jitterMs}ms" else "Sinyal: Terputus!"
        tvNetHud?.text = netText
        tvNetHud?.setTextColor(
            when {
                pingMs in 0L..85L -> Color.parseColor("#38BDF8")
                pingMs > 85L -> Color.parseColor("#FBBF24")
                else -> Color.parseColor("#EF4444")
            }
        )
    }

    fun hide() {
        hudView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        hudView = null
    }
}
