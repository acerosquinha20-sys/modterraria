package com.terrariaesp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding((24*d).toInt(), (24*d).toInt(), (24*d).toInt(), (24*d).toInt())
            setBackgroundColor(0xFF101418.toInt())
        }
        root.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); layoutParams = LinearLayout.LayoutParams((96*d).toInt(), (96*d).toInt()) })
        root.addView(TextView(this).apply { text = "Terraria ESP"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; setPadding(0,(12*d).toInt(),0,(4*d).toInt()) })
        root.addView(TextView(this).apply {
            text = "Modo WiFi: o mod tModLoader envia jogadores/NPCs em coordenadas de tela para este overlay. Porta UDP: 47831."
            textSize = 13f; setTextColor(0xFF9AA4AF.toInt()); gravity = Gravity.CENTER; setPadding(0,0,0,(20*d).toInt())
        })
        root.addView(Button(this).apply { text = "Permitir sobreposição"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text = "Iniciar ESP WiFi"; setOnClickListener { startOverlay() } })
        root.addView(Button(this).apply { text = "Modo captura local"; setOnClickListener { startCapture() } })
        setContentView(root)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        startForegroundService(Intent(this, OverlayService::class.java).putExtra("networkOnly", true))
        moveTaskToBack(true)
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        val mpm = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, OverlayService::class.java).putExtra("code", res).putExtra("data", data))
            moveTaskToBack(true)
        }
    }
}
