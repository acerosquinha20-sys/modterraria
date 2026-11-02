package com.terrariaesp

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** kind: 0 = Player, 1 = Animal, 2 = NPC/Mob */
class Target(var l: Float, var t: Float, var r: Float, var b: Float) {
    var ttl = 6
    var kind = 2
    var hp = -1f
    var dist = 0f
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
    fun copy() = Target(l, t, r, b).also { it.kind = kind; it.hp = hp; it.dist = dist; it.ttl = ttl }
}

/** Detecta movimento comparando frames e agrupa as areas que mudaram. */
class Detector {
    private var prev: IntArray? = null
    private val tracks = ArrayList<Target>()

    private fun lum(c: Int) = (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10

    private fun snapshot(): List<Target> = tracks.filter { it.ttl > 0 }.map { it.copy() }

    fun process(
        buf: ByteBuffer, rowStride: Int, pxStride: Int,
        sw: Int, sh: Int, sx: Float, sy: Float, tile: Float
    ): List<Target> {
        val cur = IntArray(sw * sh)
        for (y in 0 until sh) {
            var o = y * rowStride
            for (x in 0 until sw) {
                val r = buf.get(o).toInt() and 255
                val g = buf.get(o + 1).toInt() and 255
                val b = buf.get(o + 2).toInt() and 255
                cur[y * sw + x] = (r shl 16) or (g shl 8) or b
                o += pxStride
            }
        }
        val p = prev
        prev = cur
        if (p == null) return emptyList()

        val cs = 3
        val gw = sw / cs
        val gh = sh / cs
        val cnt = IntArray(gw * gh)
        var changed = 0
        val top = (sh * 0.10f).toInt() // ignora o topo (HUD)
        for (y in top until gh * cs) for (x in 0 until gw * cs) {
            val i = y * sw + x
            if (abs(lum(cur[i]) - lum(p[i])) > 40) {
                changed++
                cnt[(y / cs) * gw + x / cs]++
            }
        }
        // camera se movendo: tela inteira mudou, nao atualiza
        if (changed / (sw * sh).toFloat() > 0.35f) return snapshot()

        val vis = BooleanArray(gw * gh)
        val stack = IntArray(gw * gh)
        val boxes = ArrayList<FloatArray>()
        for (s in cnt.indices) {
            if (cnt[s] < 4 || vis[s]) continue
            var sp = 0
            stack[sp++] = s
            vis[s] = true
            var minx = gw; var maxx = 0; var miny = gh; var maxy = 0; var n = 0
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % gw
                val cy = c / gw
                n++
                minx = min(minx, cx); maxx = max(maxx, cx)
                miny = min(miny, cy); maxy = max(maxy, cy)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                    val ni = ny * gw + nx
                    if (cnt[ni] >= 4 && !vis[ni]) { vis[ni] = true; stack[sp++] = ni }
                }
            }
            if (n >= 3) boxes.add(
                floatArrayOf(minx * cs * sx, miny * cs * sy, (maxx + 1) * cs * sx, (maxy + 1) * cs * sy)
            )
        }

        for (t in tracks) t.ttl--
        for (bx in boxes) {
            val cx = (bx[0] + bx[2]) / 2
            val cy = (bx[1] + bx[3]) / 2
            var best: Target? = null
            var bd = tile * 3
            for (t in tracks) {
                val d = hypot(t.cx - cx, t.cy - cy)
                if (d < bd) { bd = d; best = t }
            }
            if (best != null) {
                best.l += (bx[0] - best.l) * 0.6f
                best.t += (bx[1] - best.t) * 0.6f
                best.r += (bx[2] - best.r) * 0.6f
                best.b += (bx[3] - best.b) * 0.6f
                best.ttl = 6
            } else tracks.add(Target(bx[0], bx[1], bx[2], bx[3]))
        }
        tracks.removeAll { it.ttl <= 0 }

        val scx = sw * sx / 2
        val scy = sh * sy / 2
        for (t in tracks) {
            val w = t.r - t.l
            val h = t.b - t.t
            t.dist = hypot(t.cx - scx, t.cy - scy) / tile
            t.kind = when {
                t.l - tile < scx && scx < t.r + tile && t.t - tile < scy && scy < t.b + tile -> 0
                h / tile < 1.8f && w / tile < 2.6f -> 1
                else -> 2
            }
            t.hp = health(cur, sw, sh, t, sx, sy)
        }
        return snapshot()
    }

    /** Experimental: procura a barra de vida colorida acima do alvo (so aparece se o inimigo levou dano). */
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
