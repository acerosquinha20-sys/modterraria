package com.terrariaesp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class EspView(c: Context) : View(c) {
    @Volatile private var list: List<Target> = emptyList()
    @Volatile private var snap: NetSnapshot? = null
    @Volatile var packetAgeMs: Long = Long.MAX_VALUE

    private val COR = 0xFFFF00FF.toInt()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = COR }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = COR }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; isFakeBoldText = true; color = COR }

    fun update(l: List<Target>) { list = l; postInvalidate() }
    fun updateNetwork(s: NetSnapshot?) { snap = s; postInvalidate() }

    override fun onDraw(cv: Canvas) {
        if (Cfg.wifi && packetAgeMs < 1500L) drawNetwork(cv)
        else if (!Cfg.wifi) drawLegacy(cv)
    }

    /** Desenha os alvos do mod. As posicoes sao previstas ate 40 ms a frente (velocidade do ultimo pacote). */
    private fun drawNetwork(cv: Canvas) {
        val s = snap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val ahead = (System.nanoTime() / 1_000_000L - s.atMs).coerceIn(0L, 40L).toFloat()
        val centerX = w / 2f
        for (t in s.targets) {
            if (t.kind == 2 && !Cfg.self) continue
            if (t.kind == 1 && !Cfg.humano) continue
            val l = (t.x + t.vx * ahead) * w
            val top = (t.y + t.vy * ahead) * h
            val r = l + t.w * w
            val b = top + t.h * h
            if (Cfg.line && t.kind != 2) cv.drawLine(centerX, 0f, (l + r) / 2f, top, stroke)
            if (Cfg.box) cv.drawRect(l, top, r, b, stroke)
            var label = if (t.kind == 2) "Eu" else if (t.kind == 0) "Player" else "NPC"
            if (Cfg.names && t.name.isNotBlank()) label += " ${t.name}"
            if (Cfg.dist && t.kind != 2) label += " ${t.dist}m"
            cv.drawText(label, l, b + 30f, text)
            if (Cfg.hp && t.maxLife > 0) {
                val ratio = (t.life.toFloat() / t.maxLife).coerceIn(0f, 1f)
                cv.drawRect(l, b + 36f, r, b + 46f, stroke)
                cv.drawRect(l, b + 36f, l + (r - l) * ratio, b + 46f, fill)
            }
        }
    }

    private fun drawLegacy(cv: Canvas) {
        val topX = width / 2f
        for (t in list) {
            if (t.self && !Cfg.self) continue
            if (!t.self && !Cfg.humano) continue
            if (Cfg.line && !t.self) cv.drawLine(topX, 0f, t.cx, t.t, stroke)
            if (Cfg.box) cv.drawRect(t.l, t.t, t.r, t.b, stroke)
            var label = if (t.self) "Player" else "Humano"
            if (Cfg.dist && !t.self) label += " ${t.dist.toInt()}m"
            cv.drawText(label, t.l, t.b + 30f, text)
            if (Cfg.hp && t.hp >= 0f) {
                val w = t.r - t.l
                cv.drawRect(t.l, t.b + 36f, t.r, t.b + 46f, stroke)
                cv.drawRect(t.l, t.b + 36f, t.l + w * t.hp, t.b + 46f, fill)
            }
        }
    }
}
