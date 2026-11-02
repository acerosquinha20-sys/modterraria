package com.terrariaesp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class EspView(c: Context) : View(c) {
    @Volatile private var list: List<Target> = emptyList()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f; color = Color.WHITE; setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    fun update(l: List<Target>) { list = l; postInvalidate() }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        for (t in list) {
            if ((t.kind == 0 && !Cfg.player) || (t.kind == 1 && !Cfg.animal) || (t.kind == 2 && !Cfg.npc)) continue
            val col = when (t.kind) { 0 -> 0xFF00E5FF.toInt(); 1 -> 0xFF4CFF7A.toInt(); else -> 0xFFFF4040.toInt() }
            stroke.color = col
            if (Cfg.line && t.kind != 0) cv.drawLine(cx, cy, t.cx, t.cy, stroke)
            if (Cfg.box) cv.drawRect(t.l, t.t, t.r, t.b, stroke)
            var label = when (t.kind) { 0 -> "Player"; 1 -> "Animal"; else -> "NPC/Mob" }
            if (Cfg.dist && t.kind != 0) label += " ${t.dist.toInt()}m"
            cv.drawText(label, t.l, t.b + 26f, text)
            if (Cfg.hp && t.hp >= 0f) {
                val w = t.r - t.l
                fill.color = 0xAA000000.toInt()
                cv.drawRect(t.l, t.t - 14f, t.r, t.t - 6f, fill)
                fill.color = if (t.hp > 0.5f) 0xFF4CFF7A.toInt() else if (t.hp > 0.25f) 0xFFFFC107.toInt() else 0xFFFF4040.toInt()
                cv.drawRect(t.l, t.t - 14f, t.l + w * t.hp, t.t - 6f, fill)
            }
        }
    }
}
