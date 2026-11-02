package com.terrariaesp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
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
import android.widget.CheckBox
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
    private var status: TextView? = null
    private val thread = HandlerThread("capture").apply { start() }
    private val handler by lazy { Handler(thread.looper) }
    private val detector = Detector()
    private val udp = UdpReceiver(47831)
    private var lastRun = 0L

    override fun onBind(i: Intent?): IBinder? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg(intent?.getBooleanExtra("networkOnly", false) == true)
        udp.start()
        if (projection != null || intent == null) return START_NOT_STICKY
        val networkOnly = intent.getBooleanExtra("networkOnly", false)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        if (networkOnly) {
            addEspLayer()
            addMenu()
            val tick = object : Runnable {
                var statusAt = 0L
                override fun run() {
                    val nowMs = System.currentTimeMillis()
                    val age = if (udp.lastPacketAt == 0L) Long.MAX_VALUE else nowMs - udp.lastPacketAt
                    esp?.packetAgeMs = age
                    esp?.updateNetwork(udp.snapshot)
                    if (nowMs - statusAt >= 500L) { statusAt = nowMs; setStatus(age) }
                    if (thread.isAlive) handler.postDelayed(this, 16L)
                }
            }
            handler.post(tick)
            return START_STICKY
        }

        val code = intent.getIntExtra("code", 0)
        val data = intent.getParcelableExtra<Intent>("data") ?: run { stopSelf(); return START_NOT_STICKY }
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
                if (now - lastRun < 33) return@setOnImageAvailableListener
                lastRun = now
                val pl = img.planes[0]
                val res = detector.process(pl.buffer, pl.rowStride, pl.pixelStride, sw, sh, scaleX, scaleY, h / Cfg.tiles)
                esp?.update(res)
                esp?.packetAgeMs = System.currentTimeMillis() - udp.lastPacketAt
                if (Cfg.wifi && udp.lastPacketAt > 0L) esp?.updateNetwork(udp.snapshot)
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

    private fun setStatus(age: Long) {
        val v = status ?: return
        val txt = if (age < 1500L) "Mod conectado - ${udp.pps} pacotes/s" else "Aguardando o mod (UDP 47832)..."
        v.post { v.text = txt }
    }

    private fun startFg(networkOnly: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("esp", "ESP", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "esp")
            .setContentTitle("Terraria ESP ativo")
            .setContentText("Toque no botao flutuante para abrir o menu")
            .setSmallIcon(R.mipmap.ic_launcher).build()
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (networkOnly) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(1, n, type)
        } else startForeground(1, n)
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

    private val GREEN = 0xFF4CFF7A.toInt()

    private fun shape(color: Int, radiusDp: Int, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != 0) setStroke(dp(1), strokeColor)
    }

    private fun addMenu() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = shape(0xF2141A21.toInt(), 16, 0xFF2A3440.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        fun section(t: String) = panel.addView(TextView(this).apply {
            text = t; textSize = 10f; setTextColor(0xFF7F8C99.toInt()); letterSpacing = 0.1f
            setPadding(0, dp(10), 0, dp(2))
        })
        fun check(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            panel.addView(CheckBox(this).apply {
                text = label; textSize = 13f; setTextColor(0xFFFFFFFF.toInt())
                minHeight = dp(34); minimumHeight = dp(34)
                buttonTintList = ColorStateList.valueOf(GREEN)
                isChecked = get()
                setOnCheckedChangeListener { _, v -> set(v) }
            })
        }
        fun stepper(name: String, show: () -> String, minus: () -> Unit, plus: () -> Unit) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val lbl = TextView(this).apply {
                textSize = 13f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            fun paint() { lbl.text = "$name: ${show()}" }
            fun btn(t: String, f: () -> Unit) = TextView(this).apply {
                text = t; textSize = 18f; setTextColor(GREEN); gravity = Gravity.CENTER
                background = shape(0xFF232C36.toInt(), 8)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
                setOnClickListener { f(); paint() }
            }
            paint()
            r.addView(btn("−", minus)); r.addView(lbl); r.addView(btn("+", plus))
            r.setPadding(0, dp(3), 0, dp(3))
            panel.addView(r)
        }

        panel.addView(TextView(this).apply {
            text = "Terraria ESP"; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); paint.isFakeBoldText = true
        })
        val st = TextView(this).apply {
            textSize = 11f; setTextColor(0xFF7F8C99.toInt()); text = "Aguardando o mod..."
        }
        status = st
        panel.addView(st)
        section("ESP")
        check("Linha (do topo)", { Cfg.line }, { Cfg.line = it })
        check("Box", { Cfg.box }, { Cfg.box = it })
        check("Vida", { Cfg.hp }, { Cfg.hp = it })
        check("Distancia", { Cfg.dist }, { Cfg.dist = it })
        check("Nomes", { Cfg.names }, { Cfg.names = it })
        check("WiFi / tModLoader", { Cfg.wifi }, { Cfg.wifi = it })
        section("ALVOS")
        check("Humanos / NPCs", { Cfg.humano }, { Cfg.humano = it })
        check("Player (eu)", { Cfg.self }, { Cfg.self = it })
        section("AJUSTES")
        stepper("Bloco", { Cfg.tiles.toInt().toString() },
            { Cfg.tiles = (Cfg.tiles + 2f).coerceIn(10f, 48f) }, { Cfg.tiles = (Cfg.tiles - 2f).coerceIn(10f, 48f) })
        stepper("Filtro", { Cfg.sens.toString() },
            { Cfg.sens = (Cfg.sens - 5).coerceIn(20, 80) }, { Cfg.sens = (Cfg.sens + 5).coerceIn(20, 80) })
        panel.addView(TextView(this).apply {
            text = "Sair"; textSize = 13f; setTextColor(0xFFFF6B6B.toInt()); gravity = Gravity.CENTER
            background = shape(0xFF3A1D1F.toInt(), 10, 0xFF6B2A2E.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(12) }
            setOnClickListener { stopSelf() }
        })

        val btn = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = shape(0xEE141A21.toInt(), 12, GREEN)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
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
        udp.stop()
        thread.quitSafely()
        super.onDestroy()
    }
}
