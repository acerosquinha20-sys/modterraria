package com.terrariaesp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class EspView(c: Context) : View(c) {
    @Volatile private var list: List<Target> = emptyList()

    // magenta saturado e raro no jogo: o detector ignora essa cor para nao se auto-detectar
    private val COR = 0xFFFF00FF.toInt()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = COR }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = COR }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; isFakeBoldText = true; color = COR }

    fun update(l: List<Target>) { list = l; postInvalidate() }

    override fun onDraw(cv: Canvas) {
        val topX = width / 2f
        for (t in list) {
            // linha do TOPO da tela ate o inimigo
            if (Cfg.line) cv.drawLine(topX, 0f, t.cx, t.t, stroke)
            if (Cfg.box) cv.drawRect(t.l, t.t, t.r, t.b, stroke)
            var label = "Humano"
            if (Cfg.dist) label += " ${t.dist.toInt()}m"
            cv.drawText(label, t.l, t.b + 30f, text)
            if (Cfg.hp && t.hp >= 0f) {
                val w = t.r - t.l
                cv.drawRect(t.l, t.b + 36f, t.r, t.b + 46f, stroke)
                cv.drawRect(t.l, t.b + 36f, t.l + w * t.hp, t.b + 46f, fill)
            }
        }
    }
}
