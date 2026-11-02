package com.terrariaesp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class EspView(c: Context) : View(c) {
    @Volatile private var list: List<Target> = emptyList()

    // cores saturadas e raras no jogo: o detector as ignora para nao se auto-detectar
    private val MAGENTA = 0xFFFF00FF.toInt()  // Humano
    private val CIANO = 0xFF00FFFF.toInt()    // Bicho

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; isFakeBoldText = true }

    fun update(l: List<Target>) { list = l; postInvalidate() }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        for (t in list) {
            if ((t.kind == 0 && !Cfg.humano) || (t.kind == 1 && !Cfg.bicho)) continue
            val col = if (t.kind == 0) MAGENTA else CIANO
            stroke.color = col
            text.color = col
            fill.color = col
            if (Cfg.line) cv.drawLine(cx, cy, t.cx, t.cy, stroke)
            if (Cfg.box) cv.drawRect(t.l, t.t, t.r, t.b, stroke)
            var label = if (t.kind == 0) "Humano" else "Bicho"
            if (Cfg.dist) label += " ${t.dist.toInt()}m"
            cv.drawText(label, t.l, t.b + 30f, text)
            if (Cfg.hp && t.hp >= 0f) {
                // barra de vida abaixo do alvo (o detector le a barra do jogo, que fica acima)
                val w = t.r - t.l
                cv.drawRect(t.l, t.b + 36f, t.r, t.b + 46f, stroke)
                cv.drawRect(t.l, t.b + 36f, t.l + w * t.hp, t.b + 46f, fill)
            }
        }
    }
}
