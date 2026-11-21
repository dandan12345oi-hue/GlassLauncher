package com.glass.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/** Desenha o papel de parede pela posicao real na tela (correto em janelas, DeX e tela dividida). */
class WallView(ctx: Context) : View(ctx) {
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()
    private val loc = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        val b = Backdrop.wall ?: return
        getLocationOnScreen(loc)
        val sx = b.width.toFloat() / Backdrop.w
        val sy = b.height.toFloat() / Backdrop.h
        src.set(
            (loc[0] * sx).toInt(), (loc[1] * sy).toInt(),
            ((loc[0] + width) * sx).toInt(), ((loc[1] + height) * sy).toInt()
        )
        dst.set(0, 0, width, height)
        if (src.intersect(0, 0, b.width, b.height)) canvas.drawBitmap(b, src, dst, bp)
    }
}
