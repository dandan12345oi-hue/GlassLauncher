package com.glass.launcher

import android.graphics.Bitmap

object Blur {
    private fun cl(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v

    fun blur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxH(px, w, h, radius)
            boxV(px, w, h, radius)
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxH(px: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val tmp = IntArray(w)
        for (y in 0 until h) {
            val o = y * w
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = px[o + cl(i, 0, w - 1)]
                sa += p ushr 24
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (x in 0 until w) {
                tmp[x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val a = px[o + cl(x - r, 0, w - 1)]
                val b = px[o + cl(x + r + 1, 0, w - 1)]
                sa += (b ushr 24) - (a ushr 24)
                sr += ((b shr 16) and 255) - ((a shr 16) and 255)
                sg += ((b shr 8) and 255) - ((a shr 8) and 255)
                sb += (b and 255) - (a and 255)
            }
            System.arraycopy(tmp, 0, px, o, w)
        }
    }

    private fun boxV(px: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val tmp = IntArray(h)
        for (x in 0 until w) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = px[cl(i, 0, h - 1) * w + x]
                sa += p ushr 24
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (y in 0 until h) {
                tmp[y] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val a = px[cl(y - r, 0, h - 1) * w + x]
                val b = px[cl(y + r + 1, 0, h - 1) * w + x]
                sa += (b ushr 24) - (a ushr 24)
                sr += ((b shr 16) and 255) - ((a shr 16) and 255)
                sg += ((b shr 8) and 255) - ((a shr 8) and 255)
                sb += (b and 255) - (a and 255)
            }
            for (y in 0 until h) px[y * w + x] = tmp[y]
        }
    }
}
