package com.terrariaesp

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Humanoide em movimento (NPC, inimigo ou o proprio player). */
class Target(var l: Float, var t: Float, var r: Float, var b: Float) {
    var miss = 0          // frames seguidos sem ser visto
    var age = 1
    var hp = -1f
    var dist = 0f
    var shown = false
    var self = false
    var x0 = (l + r) / 2
    var y0 = (t + b) / 2
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
    fun copy() = Target(l, t, r, b).also {
        it.hp = hp; it.dist = dist; it.miss = miss; it.age = age; it.shown = shown; it.self = self
    }
}

/**
 * Deteccao de humanoides por movimento:
 * compensa a camera, ignora o proprio overlay, acumula 2 frames para preencher a silhueta,
 * junta partes proximas, filtra por forma/tamanho e confirma por varios frames.
 * O alvo so e exibido enquanto foi visto no frame atual ou no anterior (some na hora).
 */
class Detector {
    private var prevGray: IntArray? = null
    private var prevOvl: BooleanArray? = null
    private var prevAct: BooleanArray? = null
    private val tracks = ArrayList<Target>()

    private fun isOverlay(r: Int, g: Int, b: Int) = r > 170 && b > 170 && g < 120

    private fun snapshot(): List<Target> = tracks.filter { it.shown && it.miss <= 1 }.map { it.copy() }

    fun process(
        buf: ByteBuffer, rowStride: Int, pxStride: Int,
        sw: Int, sh: Int, sx: Float, sy: Float, tile: Float
    ): List<Target> {
        val cur = IntArray(sw * sh)
        val gray = IntArray(sw * sh)
        val ovl = BooleanArray(sw * sh)
        for (y in 0 until sh) {
            var o = y * rowStride
            for (x in 0 until sw) {
                val r = buf.get(o).toInt() and 255
                val g = buf.get(o + 1).toInt() and 255
                val b = buf.get(o + 2).toInt() and 255
                val i = y * sw + x
                cur[i] = (r shl 16) or (g shl 8) or b
                gray[i] = (r * 3 + g * 6 + b) / 10
                ovl[i] = isOverlay(r, g, b)
                o += pxStride
            }
        }
        val pg = prevGray
        val po = prevOvl
        prevGray = gray
        prevOvl = ovl
        if (pg == null || po == null) return emptyList()

        // mascara do overlay (frame atual + anterior), dilatada
        val mask = BooleanArray(sw * sh)
        for (y in 1 until sh - 1) for (x in 1 until sw - 1) {
            val i = y * sw + x
            if (ovl[i] || po[i]) {
                for (dy in -1..1) for (dx in -1..1) mask[i + dy * sw + dx] = true
            }
        }

        val top = max((sh * 0.10f).toInt(), 7)

        // deslocamento global da camera
        var bdx = 0
        var bdy = 0
        var bestScore = Long.MAX_VALUE
        var n = 0
        for (y in top until sh - 7 step 3) for (x in 8 until sw - 8 step 3) n++
        for (dy in -5..5) for (dx in -6..6) {
            var sad = 0L
            var y = top
            while (y < sh - 7) {
                var x = 8
                while (x < sw - 8) {
                    sad += abs(gray[y * sw + x] - pg[(y - dy) * sw + (x - dx)])
                    x += 3
                }
                y += 3
            }
            val score = sad + (abs(dx) + abs(dy)).toLong() * n * 2 / 5
            if (score < bestScore) { bestScore = score; bdx = dx; bdy = dy }
        }

        // diferenca compensada (celulas de 2x2 px)
        val cs = 2
        val gw = sw / cs
        val gh = sh / cs
        val cnt = IntArray(gw * gh)
        var changed = 0
        for (y in top until gh * cs) for (x in 0 until gw * cs) {
            val i = y * sw + x
            if (mask[i]) continue
            val px = x - bdx
            val py = y - bdy
            if (px < 0 || py < 0 || px >= sw || py >= sh) continue
            if (abs(gray[i] - pg[py * sw + px]) > Cfg.sens) {
                changed++
                cnt[(y / cs) * gw + x / cs]++
            }
        }
        if (changed / (sw * sh).toFloat() > 0.22f) {
            for (t in tracks) t.miss++
            tracks.removeAll { it.miss > 10 }
            return snapshot()
        }

        // ativa celulas do frame atual + anterior (preenche a silhueta do personagem)
        val cur0 = BooleanArray(gw * gh) { cnt[it] >= 2 }
        val pa = prevAct
        prevAct = cur0
        val act = BooleanArray(gw * gh) { cur0[it] || (pa != null && pa.size == cur0.size && pa[it]) }

        val vis = BooleanArray(gw * gh)
        val stack = IntArray(gw * gh)
        val boxes = ArrayList<FloatArray>() // l,t,r,b,celulas
        for (s in act.indices) {
            if (!act[s] || vis[s]) continue
            var sp = 0
            stack[sp++] = s
            vis[s] = true
            var minx = gw; var maxx = 0; var miny = gh; var maxy = 0; var k = 0
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % gw
                val cy = c / gw
                k++
                minx = min(minx, cx); maxx = max(maxx, cx)
                miny = min(miny, cy); maxy = max(maxy, cy)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                    val ni = ny * gw + nx
                    if (act[ni] && !vis[ni]) { vis[ni] = true; stack[sp++] = ni }
                }
            }
            if (k >= 5) boxes.add(
                floatArrayOf(
                    minx * cs * sx, miny * cs * sy, (maxx + 1) * cs * sx, (maxy + 1) * cs * sy, k.toFloat()
                )
            )
        }

        // junta pedacos proximos (corpo + pernas + arma)
        val gap = tile * 0.5f
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in 0 until boxes.size) for (j in i + 1 until boxes.size) {
                val a = boxes[i]
                val b = boxes[j]
                if (a[0] - gap < b[2] && b[0] - gap < a[2] && a[1] - gap < b[3] && b[1] - gap < a[3]) {
                    a[0] = min(a[0], b[0]); a[1] = min(a[1], b[1])
                    a[2] = max(a[2], b[2]); a[3] = max(a[3], b[3])
                    a[4] += b[4]
                    boxes.removeAt(j)
                    merged = true
                    break@loop
                }
            }
        }

        // filtro de forma: humanoide (~3 blocos de altura, mais alto que largo)
        val good = ArrayList<FloatArray>()
        for (bx in boxes) {
            val w = bx[2] - bx[0]
            val h = bx[3] - bx[1]
            val tw = w / tile
            val th = h / tile
            if (th !in 2f..4.8f || tw !in 0.7f..3.4f || th / tw !in 1.15f..3.6f) continue
            val cells = (w / (cs * sx)) * (h / (cs * sy))
            if (bx[4] / cells < 0.3f) continue
            good.add(bx)
        }

        // rastreamento
        for (t in tracks) t.miss++
        for (bx in good) {
            val cx = (bx[0] + bx[2]) / 2
            val cy = (bx[1] + bx[3]) / 2
            var best: Target? = null
            var bd = tile * 3
            for (t in tracks) {
                val d = hypot(t.cx - cx, t.cy - cy)
                if (d < bd) { bd = d; best = t }
            }
            if (best != null) {
                best.l += (bx[0] - best.l) * 0.8f
                best.t += (bx[1] - best.t) * 0.8f
                best.r += (bx[2] - best.r) * 0.8f
                best.b += (bx[3] - best.b) * 0.8f
                best.miss = 0
                best.age++
            } else tracks.add(Target(bx[0], bx[1], bx[2], bx[3]))
        }
        tracks.removeAll { it.miss > 10 }

        val scx = sw * sx / 2
        val scy = sh * sy / 2
        for (t in tracks) {
            t.dist = hypot(t.cx - scx, t.cy - scy) / tile
            t.self = abs(t.cx - scx) < tile * 1.5f && abs(t.cy - scy) < tile * 2f
            if (!t.shown && t.age >= 2 && (t.self || hypot(t.cx - t.x0, t.cy - t.y0) >= tile * 0.5f)) t.shown = true
            t.hp = health(cur, sw, sh, t, sx, sy)
        }
        return snapshot()
    }

    /** Experimental: procura a barra de vida do proprio jogo acima do alvo (aparece apos dano). */
    private fun health(c: IntArray, sw: Int, sh: Int, t: Target, sx: Float, sy: Float): Float {
        val x0 = (t.l / sx).toInt().coerceIn(0, sw - 1)
        val x1 = (t.r / sx).toInt().coerceIn(0, sw - 1)
        val y1 = (t.t / sy).toInt().coerceIn(0, sh - 1)
        val y0 = (y1 - 5).coerceAtLeast(0)
        var cols = 0
        for (x in x0..x1) {
            var hit = false
            for (y in y0..y1) {
                val p = c[y * sw + x]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                if (b < 90 && ((r > 150 && g < 110) || (g > 150 && r < 170))) { hit = true; break }
            }
            if (hit) cols++
        }
        val width = x1 - x0 + 1
        return if (cols < 3 || width < 4) -1f else min(1f, cols / width.toFloat())
    }
}
