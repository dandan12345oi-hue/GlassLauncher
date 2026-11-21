package com.glass.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import kotlin.math.max

/** Guarda o papel de parede (nitido ou borrado) e a versao muito borrada usada pelo "vidro". */
object Backdrop {
    var wall: Bitmap? = null
    var glass: Bitmap? = null
    var w = 1
    var h = 1

    fun build(ctx: Context, p: Prefs, sw: Int, sh: Int) {
        w = sw
        h = sh
        wall = null
        glass = null
        if (p.wallMode == 1) return // papel de parede do sistema: sem bitmap proprio
        var full: Bitmap? = null
        if (p.wallMode == 2 && p.wallUri.isNotEmpty()) full = loadCropped(ctx, Uri.parse(p.wallUri), sw, sh)
        if (full == null) full = gradient(p, sw, sh)
        val small = Bitmap.createScaledBitmap(full, max(1, sw / 4), max(1, sh / 4), true)
        val ws = if (p.wallBlur > 0) Blur.blur(small, p.wallBlur) else small
        wall = if (p.wallBlur > 0) ws else full
        glass = Blur.blur(ws, max(1, p.glassBlur))
    }

    private fun loadCropped(ctx: Context, uri: Uri, sw: Int, sh: Int): Bitmap? {
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= sw && o.outHeight / (s * 2) >= sh) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return null
            val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val sc = max(sw.toFloat() / src.width, sh.toFloat() / src.height)
            val m = Matrix()
            m.postScale(sc, sc)
            m.postTranslate((sw - src.width * sc) / 2f, (sh - src.height * sc) / 2f)
            Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        } catch (e: Exception) {
            return null
        }
    }

    private fun gradient(p: Prefs, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = p.accent
        val base = if (p.dark) Color.parseColor("#0B1020") else Color.parseColor("#EEF2FF")
        val top = mix(base, a, if (p.dark) 0.35f else 0.25f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w * 0.4f, h.toFloat(), top, base, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val blobs = arrayOf(
            floatArrayOf(0.2f, 0.25f, 0.45f),
            floatArrayOf(0.85f, 0.55f, 0.4f),
            floatArrayOf(0.35f, 0.9f, 0.5f)
        )
        val hsv = FloatArray(3)
        Color.colorToHSV(a, hsv)
        for (i in blobs.indices) {
            val b = blobs[i]
            val col = Color.HSVToColor(120, floatArrayOf((hsv[0] + 40f * i) % 360f, 0.7f, 1f))
            val rad = b[2] * w * 1.2f
            paint.shader = RadialGradient(b[0] * w, b[1] * h, rad, col, withAlpha(col, 0), Shader.TileMode.CLAMP)
            c.drawCircle(b[0] * w, b[1] * h, rad, paint)
        }
        return bmp
    }
}
