package com.terrariaesp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

@Suppress("DEPRECATION")
class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
            setBackgroundColor(0xFF101418.toInt())
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams((96 * d).toInt(), (96 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "Terraria ESP"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "Overlay para jogo offline. 1) Permita a sobreposicao. 2) Inicie e aceite a captura de tela. 3) Abra o Terraria."
            textSize = 13f; setTextColor(0xFF9AA4AF.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, (20 * d).toInt())
        })
        root.addView(Button(this).apply {
            text = "1. Permitir sobreposicao"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        root.addView(Button(this).apply {
            text = "2. Iniciar ESP"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    val mpm = getSystemService(MediaProjectionManager::class.java)
                    startActivityForResult(mpm.createScreenCaptureIntent(), 1)
                }
            }
        })
        setContentView(root)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK && data != null) {
            val i = Intent(this, OverlayService::class.java).putExtra("code", res).putExtra("data", data)
            startForegroundService(i)
            moveTaskToBack(true)
        }
    }
}
