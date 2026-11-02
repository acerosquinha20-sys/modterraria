package com.terrariaesp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var esp: EspView? = null
    private var menu: View? = null
    private val thread = HandlerThread("capture").apply { start() }
    private val handler by lazy { Handler(thread.looper) }
    private val detector = Detector()
    private var lastRun = 0L

    override fun onBind(i: Intent?): IBinder? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (projection != null || intent == null) return START_NOT_STICKY
        val code = intent.getIntExtra("code", 0)
        val data = intent.getParcelableExtra<Intent>("data") ?: run { stopSelf(); return START_NOT_STICKY }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        val w = max(dm.widthPixels, dm.heightPixels) // jogo em paisagem
        val h = min(dm.widthPixels, dm.heightPixels)
        val sw = w / 4
        val sh = h / 4
        val scaleX = w.toFloat() / sw
        val scaleY = h.toFloat() / sh

        reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastRun < 50) return@setOnImageAvailableListener
                lastRun = now
                val pl = img.planes[0]
                val res = detector.process(pl.buffer, pl.rowStride, pl.pixelStride, sw, sh, scaleX, scaleY, h / Cfg.tiles)
                esp?.update(res)
            } finally { img.close() }
        }, handler)
        display = projection!!.createVirtualDisplay(
            "esp", sw, sh, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
        )

        addEspLayer()
        addMenu()
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("esp", "ESP", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "esp")
            .setContentTitle("Terraria ESP ativo")
            .setContentText("Toque no botao flutuante para abrir o menu")
            .setSmallIcon(R.mipmap.ic_launcher).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
    }

    private fun addEspLayer() {
        val v = EspView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
        esp = v
    }

    private fun addMenu() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply { setColor(0xDD101418.toInt()); cornerRadius = dp(10).toFloat() }
        }
        fun row(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            val tv = TextView(this).apply { textSize = 12f; setPadding(dp(6), dp(5), dp(6), dp(5)) }
            fun paint() {
                tv.text = (if (get()) "● " else "○ ") + label
                tv.setTextColor(if (get()) 0xFF4CFF7A.toInt() else 0xFF8A8A8A.toInt())
            }
            paint()
            tv.setOnClickListener { set(!get()); paint() }
            panel.addView(tv)
        }
        row("Linha (topo)", { Cfg.line }, { Cfg.line = it })
        row("Box ESP", { Cfg.box }, { Cfg.box = it })
        row("Vida", { Cfg.hp }, { Cfg.hp = it })
        row("Distancia", { Cfg.dist }, { Cfg.dist = it })
        fun stepper(name: String, show: () -> String, minus: () -> Unit, plus: () -> Unit) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val lbl = TextView(this).apply { textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(6), dp(5), dp(6), dp(5)) }
            fun paint() { lbl.text = "$name: ${show()}" }
            fun btn(t: String, f: () -> Unit) = TextView(this).apply {
                text = t; textSize = 16f; setTextColor(0xFF4CFF7A.toInt()); setPadding(dp(10), dp(2), dp(10), dp(2))
                setOnClickListener { f(); paint() }
            }
            paint()
            r.addView(btn("−", minus)); r.addView(lbl); r.addView(btn("+", plus))
            panel.addView(r)
        }
        stepper("Bloco", { Cfg.tiles.toInt().toString() },
            { Cfg.tiles = (Cfg.tiles + 2f).coerceIn(10f, 48f) }, { Cfg.tiles = (Cfg.tiles - 2f).coerceIn(10f, 48f) })
        stepper("Filtro", { Cfg.sens.toString() },
            { Cfg.sens = (Cfg.sens - 5).coerceIn(20, 80) }, { Cfg.sens = (Cfg.sens + 5).coerceIn(20, 80) })
        panel.addView(TextView(this).apply {
            text = "✕ Sair"; textSize = 12f; setTextColor(0xFFFF5252.toInt())
            setPadding(dp(6), dp(8), dp(6), dp(4))
            setOnClickListener { stopSelf() }
        })

        val btn = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC101418.toInt()) }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        root.addView(btn)
        root.addView(panel)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(80) }

        var sx = 0; var sy = 0; var dx = 0f; var dy = 0f; var moved = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; dx = e.rawX; dy = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - dx
                    val my = e.rawY - dy
                    if (abs(mx) > 10 || abs(my) > 10) moved = true
                    if (moved) { lp.x = (sx + mx).toInt(); lp.y = (sy + my).toInt(); wm.updateViewLayout(root, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE
                    true
                }
                else -> false
            }
        }
        wm.addView(root, lp)
        menu = root
    }

    override fun onDestroy() {
        runCatching { esp?.let { wm.removeView(it) } }
        runCatching { menu?.let { wm.removeView(it) } }
        display?.release()
        reader?.close()
        projection?.stop()
        thread.quitSafely()
        super.onDestroy()
    }
}
