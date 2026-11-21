package com.glass.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.widget.FrameLayout

/**
 * Painel "Liquid Glass": recorta a parte do papel de parede borrado que fica atras dele,
 * soma um tom translucido, brilho especular no topo e uma borda de luz.
 * [extra] soma (ou subtrai) dp ao arredondamento global; valores grandes viram "pilula".
 */
class GlassView(ctx: Context, private val p: Prefs, private val extra: Float = 0f) : FrameLayout(ctx) {
    var glassOn = true

    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val src = Rect()
    private val loc = IntArray(2)

    init {
        setWillNotDraw(false)
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = ctx.dpf(1.3f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val white = Color.WHITE
        val ga = (p.glassGloss * 2.55f * (if (p.dark) 0.45f else 1f)).toInt()
        gloss.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f, withAlpha(white, ga), withAlpha(white, 0), Shader.TileMode.CLAMP
        )
        val k = p.glassRim / 100f
        rim.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                withAlpha(white, (230 * k).toInt()), withAlpha(p.accent, (70 * k).toInt()),
                withAlpha(white, (45 * k).toInt()), withAlpha(white, (190 * k).toInt())
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!glassOn) return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(maxOf(0f, context.dpf(p.glassRadius + extra)), minOf(w, h) / 2f)
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        val g = Backdrop.glass
        if (g != null) {
            getLocationOnScreen(loc)
            val sx = g.width.toFloat() / Backdrop.w
            val sy = g.height.toFloat() / Backdrop.h
            src.set(
                (loc[0] * sx).toInt(), (loc[1] * sy).toInt(),
                ((loc[0] + width) * sx).toInt() + 1, ((loc[1] + height) * sy).toInt() + 1
            )
            if (src.intersect(0, 0, g.width, g.height)) canvas.drawBitmap(g, src, rect, bp)
        }
        val boost = if (g == null) 1.8f else 1f
        val a = (p.glassTint * 2.55f * (if (p.dark) 0.6f else 1f) * boost).toInt().coerceIn(0, 255)
        val base = if (p.glassAccent) mix(Color.WHITE, p.accent, 0.5f)
        else if (p.dark) mix(Color.WHITE, p.accent, 0.12f) else Color.WHITE
        tint.color = withAlpha(base, a)
        canvas.drawRect(rect, tint)
        if (p.glassGloss > 0) canvas.drawRect(rect, gloss)
        canvas.restore()
        if (p.glassRim > 0) {
            val s = rim.strokeWidth / 2f
            canvas.drawRoundRect(s, s, w - s, h - s, r, r, rim)
        }
    }
}
