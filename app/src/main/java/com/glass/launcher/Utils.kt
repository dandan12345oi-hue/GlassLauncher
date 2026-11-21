package com.glass.launcher

import android.content.Context
import android.graphics.Color

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun withAlpha(c: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

fun mix(a: Int, b: Int, t: Float): Int {
    fun m(x: Int, y: Int): Int = (x + (y - x) * t).toInt().coerceIn(0, 255)
    return Color.rgb(
        m(Color.red(a), Color.red(b)),
        m(Color.green(a), Color.green(b)),
        m(Color.blue(a), Color.blue(b))
    )
}

/** Detecta o Samsung DeX (campo da Samsung na Configuration, lido por reflexao). */
fun isDex(ctx: Context): Boolean {
    return try {
        val c = ctx.resources.configuration
        val enabled = c.javaClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(c)
        val current = c.javaClass.getField("semDesktopModeEnabled").getInt(c)
        enabled == current
    } catch (e: Throwable) {
        false
    }
}
