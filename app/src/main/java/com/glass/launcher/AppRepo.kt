package com.glass.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class AppInfo(val label: String, val pkg: String, val cls: String) {
    val key: String get() = "$pkg/$cls"
}

object AppRepo {
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    /** Pacotes de icones instalados (formato Nova / ADW / Apex). Retorna pares pacote -> nome. */
    fun packs(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, String>()
        for (a in listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME")) {
            for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                out[ri.activityInfo.packageName] = ri.loadLabel(pm).toString()
            }
        }
        return out.map { it.key to it.value }
    }
}

class Icons(private val ctx: Context, private val p: Prefs) {
    private val cache = HashMap<String, Bitmap>()
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null
    private var packPkg = ""
    private var loaded = false

    fun clear() {
        cache.clear()
        loaded = false
    }

    fun get(a: AppInfo, px: Int): Bitmap = cache.getOrPut(a.key + "@" + px) { render(a, px) }

    private fun loadPack() {
        loaded = true
        map = emptyMap()
        res = null
        val pkg = p.iconPack
        if (pkg.isEmpty()) return
        try {
            val pc = ctx.createPackageContext(pkg, 0)
            val r = pc.resources
            val m = HashMap<String, String>()
            val id = r.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser? = if (id != 0) r.getXml(id) else try {
                val xp = Xml.newPullParser()
                xp.setInput(pc.assets.open("appfilter.xml"), "utf-8")
                xp
            } catch (e: Exception) {
                null
            }
            if (parser != null) {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val dr = parser.getAttributeValue(null, "drawable")
                        if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                            val s = comp.substring(14, comp.length - 1).split("/")
                            if (s.size == 2) {
                                val cls = if (s[1].startsWith(".")) s[0] + s[1] else s[1]
                                val k = s[0] + "/" + cls
                                if (!m.containsKey(k)) m[k] = dr
                            }
                        }
                    }
                    ev = parser.next()
                }
            }
            map = m
            res = r
            packPkg = pkg
        } catch (e: Exception) {
        }
    }

    private fun packDrawable(a: AppInfo): Drawable? {
        if (!loaded) loadPack()
        val r = res ?: return null
        val name = map[a.key] ?: return null
        val id = r.getIdentifier(name, "drawable", packPkg)
        if (id == 0) return null
        return try {
            r.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun render(a: AppInfo, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pack = packDrawable(a)
        if (pack != null) {
            pack.setBounds(0, 0, px, px)
            pack.draw(c)
            return bmp
        }
        val pm = ctx.packageManager
        val d: Drawable = try {
            pm.getActivityIcon(ComponentName(a.pkg, a.cls))
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        val shape = p.iconShape
        if (shape == 0) {
            d.setBounds(0, 0, px, px)
            d.draw(c)
            return bmp
        }
        if (d is AdaptiveIconDrawable) {
            val o = (px * 0.25f).toInt()
            d.background?.let {
                it.setBounds(-o, -o, px + o, px + o)
                it.draw(c)
            }
            d.foreground?.let {
                it.setBounds(-o, -o, px + o, px + o)
                it.draw(c)
            }
        } else {
            c.drawColor(Color.argb(255, 240, 240, 244))
            val i = (px * 0.14f).toInt()
            d.setBounds(i, i, px - i, px - i)
            d.draw(c)
        }
        val s = px.toFloat()
        val r = if (shape == 1) s / 2f else s * 0.28f
        val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawRoundRect(0f, 0f, s, s, r, r, mask)
        if (shape == 3) {
            val g = Paint(Paint.ANTI_ALIAS_FLAG)
            g.shader = LinearGradient(
                0f, 0f, 0f, s * 0.6f,
                Color.argb(90, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
            c.drawRoundRect(0f, 0f, s, s, r, r, g)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG)
            rim.style = Paint.Style.STROKE
            rim.strokeWidth = s * 0.035f
            rim.shader = LinearGradient(
                0f, 0f, s, s,
                intArrayOf(
                    Color.argb(230, 255, 255, 255), Color.argb(30, 255, 255, 255),
                    Color.argb(30, 255, 255, 255), Color.argb(200, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
            val h = rim.strokeWidth / 2f
            c.drawRoundRect(h, h, s - h, s - h, r, r, rim)
        }
        return bmp
    }
}
