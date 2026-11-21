package com.glass.launcher

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color

class Prefs(private val ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    private fun ed(block: SharedPreferences.Editor.() -> Unit) {
        val e = sp.edit()
        e.block()
        e.putInt("rev", sp.getInt("rev", 0) + 1)
        e.apply()
    }

    private fun list(k: String): List<String> =
        (sp.getString(k, "") ?: "").split("|").filter { it.isNotEmpty() }

    val rev: Int get() = sp.getInt("rev", 0)

    /** 0 = escuro, 1 = claro, 2 = automatico (segue o sistema) */
    var themeMode: Int
        get() = sp.getInt("themeMode", 0)
        set(v) = ed { putInt("themeMode", v) }
    val dark: Boolean
        get() = when (themeMode) {
            0 -> true
            1 -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    val fg: Int get() = if (dark) Color.WHITE else Color.parseColor("#111827")

    var accent: Int
        get() = sp.getInt("accent", Color.parseColor("#4F8CFF"))
        set(v) = ed { putInt("accent", v) }

    // Papel de parede: 0 = gradiente, 1 = sistema, 2 = imagem
    var wallMode: Int
        get() = sp.getInt("wallMode", 0)
        set(v) = ed { putInt("wallMode", v) }
    var wallUri: String
        get() = sp.getString("wallUri", "") ?: ""
        set(v) = ed { putString("wallUri", v) }
    var wallBlur: Int
        get() = sp.getInt("wallBlur", 0)
        set(v) = ed { putInt("wallBlur", v) }

    // Liquid Glass
    var glassBlur: Int
        get() = sp.getInt("glassBlur", 5)
        set(v) = ed { putInt("glassBlur", v) }
    var glassTint: Int
        get() = sp.getInt("glassTint", 22)
        set(v) = ed { putInt("glassTint", v) }
    var glassRadius: Int
        get() = sp.getInt("glassRadius", 28)
        set(v) = ed { putInt("glassRadius", v) }
    var glassRim: Int
        get() = sp.getInt("glassRim", 85)
        set(v) = ed { putInt("glassRim", v) }
    var glassGloss: Int
        get() = sp.getInt("glassGloss", 50)
        set(v) = ed { putInt("glassGloss", v) }
    var glassAccent: Boolean
        get() = sp.getBoolean("glassAccent", false)
        set(v) = ed { putBoolean("glassAccent", v) }

    // Icones: 0 original, 1 circulo, 2 arredondado, 3 vidro
    var iconShape: Int
        get() = sp.getInt("iconShape", 3)
        set(v) = ed { putInt("iconShape", v) }
    var iconPack: String
        get() = sp.getString("iconPack", "") ?: ""
        set(v) = ed { putString("iconPack", v) }
    var iconSize: Int
        get() = sp.getInt("iconSize", 52)
        set(v) = ed { putInt("iconSize", v) }
    var labelSize: Int
        get() = sp.getInt("labelSize", 11)
        set(v) = ed { putInt("labelSize", v) }
    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = ed { putBoolean("labels", v) }

    // Tela inicial (celular)
    var cols: Int
        get() = sp.getInt("cols", 4)
        set(v) = ed { putInt("cols", v) }
    var gridGap: Int
        get() = sp.getInt("gridGap", 6)
        set(v) = ed { putInt("gridGap", v) }
    var showSearch: Boolean
        get() = sp.getBoolean("showSearch", true)
        set(v) = ed { putBoolean("showSearch", v) }
    /** 0 fino, 1 negrito, 2 compacto */
    var clockStyle: Int
        get() = sp.getInt("clockStyle", 0)
        set(v) = ed { putInt("clockStyle", v) }
    var showDate: Boolean
        get() = sp.getBoolean("showDate", true)
        set(v) = ed { putBoolean("showDate", v) }
    var dockCount: Int
        get() = sp.getInt("dockCount", 4)
        set(v) = ed { putInt("dockCount", v) }
    var dockGlass: Boolean
        get() = sp.getBoolean("dockGlass", true)
        set(v) = ed { putBoolean("dockGlass", v) }
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = ed { putBoolean("seeded", v) }
    var dock: List<String>
        get() = list("dock")
        set(v) = ed { putString("dock", v.joinToString("|")) }

    // Modo desktop / DeX: 0 automatico, 1 sempre, 2 nunca
    var desktopMode: Int
        get() = sp.getInt("desktopMode", 0)
        set(v) = ed { putInt("desktopMode", v) }
    var winMode: Boolean
        get() = sp.getBoolean("winMode", true)
        set(v) = ed { putBoolean("winMode", v) }
    var taskbarH: Int
        get() = sp.getInt("taskbarH", 60)
        set(v) = ed { putInt("taskbarH", v) }
    var taskbarFloat: Boolean
        get() = sp.getBoolean("taskbarFloat", true)
        set(v) = ed { putBoolean("taskbarFloat", v) }
    var taskbarCenter: Boolean
        get() = sp.getBoolean("taskbarCenter", true)
        set(v) = ed { putBoolean("taskbarCenter", v) }
    var deskIcon: Int
        get() = sp.getInt("deskIcon", 56)
        set(v) = ed { putInt("deskIcon", v) }
    var deskSeeded: Boolean
        get() = sp.getBoolean("deskSeeded", false)
        set(v) = ed { putBoolean("deskSeeded", v) }
    var desk: List<String>
        get() = list("desk")
        set(v) = ed { putString("desk", v.joinToString("|")) }

    /** Predefinicoes de estilo: 0 Cristal, 1 Fosco, 2 Neon, 3 Minimalista */
    fun preset(i: Int) = ed {
        when (i) {
            0 -> {
                putInt("glassBlur", 5); putInt("glassTint", 22); putInt("glassRadius", 28)
                putInt("glassRim", 85); putInt("glassGloss", 50); putInt("iconShape", 3)
                putBoolean("glassAccent", false)
            }
            1 -> {
                putInt("glassBlur", 12); putInt("glassTint", 40); putInt("glassRadius", 24)
                putInt("glassRim", 35); putInt("glassGloss", 25); putInt("iconShape", 2)
                putBoolean("glassAccent", false)
            }
            2 -> {
                putInt("glassBlur", 3); putInt("glassTint", 12); putInt("glassRadius", 34)
                putInt("glassRim", 100); putInt("glassGloss", 70); putInt("iconShape", 1)
                putBoolean("glassAccent", true); putInt("themeMode", 0)
            }
            3 -> {
                putInt("glassBlur", 1); putInt("glassTint", 0); putInt("glassRadius", 16)
                putInt("glassRim", 0); putInt("glassGloss", 0); putInt("iconShape", 0)
                putBoolean("glassAccent", false)
            }
            else -> {}
        }
    }
}
