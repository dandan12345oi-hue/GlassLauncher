package com.glass.launcher

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SettingsActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var content: LinearLayout
    private lateinit var cur: LinearLayout
    private var scroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        render()
    }

    private fun render() {
        val y = scroll?.scrollY ?: 0
        val bg = if (p.dark) Color.parseColor("#0E1220") else Color.parseColor("#F3F5FB")
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility =
            if (p.dark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        val sv = ScrollView(this)
        sv.setBackgroundColor(bg)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(12), dp(16), dp(32))
        sv.addView(content)
        cur = content
        scroll = sv

        val h = TextView(this)
        h.text = "Ajustes"
        h.textSize = 32f
        h.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        h.setTextColor(p.fg)
        h.setPadding(dp(4), 0, 0, 0)
        content.addView(h)

        title("ESTILO")
        choices("Predefini\u00e7\u00e3o", listOf("Cristal", "Fosco", "Neon", "Minimalista"), -1) {
            p.preset(it)
            render()
        }

        title("TEMA")
        choices("Modo", listOf("Escuro", "Claro", "Autom\u00e1tico"), p.themeMode) {
            p.themeMode = it
            render()
        }
        label("Cor de destaque")
        swatches()
        val hsv = FloatArray(3)
        Color.colorToHSV(p.accent, hsv)
        slider("Matiz personalizado", 0, 360, hsv[0].toInt(), "\u00b0") {
            p.accent = Color.HSVToColor(floatArrayOf(it.toFloat(), 0.62f, 1f))
            render()
        }

        title("PAPEL DE PAREDE")
        choices("Fonte", listOf("Gradiente", "Sistema", "Imagem"), p.wallMode) {
            p.wallMode = it
            if (it == 2 && p.wallUri.isEmpty()) pickImage()
            render()
        }
        if (p.wallMode == 2) button("Escolher imagem...") { pickImage() }
        slider("Desfoque do papel de parede", 0, 20, p.wallBlur) { p.wallBlur = it }
        note("Com \"Sistema\", o desfoque s\u00f3 funciona no Android 12 ou mais novo.")

        title("LIQUID GLASS")
        slider("Desfoque do vidro", 1, 16, p.glassBlur) { p.glassBlur = it }
        slider("Opacidade do vidro", 0, 60, p.glassTint, "%") { p.glassTint = it }
        slider("Arredondamento", 8, 44, p.glassRadius, " dp") { p.glassRadius = it }
        slider("Brilho da borda", 0, 100, p.glassRim, "%") { p.glassRim = it }
        slider("Reflexo do vidro", 0, 100, p.glassGloss, "%") { p.glassGloss = it }
        toggle("Vidro com tom da cor de destaque", p.glassAccent) { p.glassAccent = it }

        title("\u00cdCONES")
        choices("Formato", listOf("Original", "C\u00edrculo", "Arredondado", "Vidro"), p.iconShape) {
            p.iconShape = it
            render()
        }
        val packs = AppRepo.packs(this)
        val sel = packs.indexOfFirst { it.first == p.iconPack } + 1
        choices("Pacote de \u00edcones", listOf("Nenhum") + packs.map { it.second }, sel) {
            p.iconPack = if (it == 0) "" else packs[it - 1].first
            render()
        }
        slider("Tamanho do \u00edcone", 40, 72, p.iconSize, " dp") { p.iconSize = it }
        slider("Tamanho do texto", 9, 14, p.labelSize, " sp") { p.labelSize = it }
        toggle("Mostrar nomes dos apps", p.showLabels) { p.showLabels = it }

        title("TELA INICIAL (CELULAR)")
        choices("Rel\u00f3gio", listOf("Fino", "Negrito", "Compacto"), p.clockStyle) {
            p.clockStyle = it
            render()
        }
        toggle("Mostrar data", p.showDate) { p.showDate = it }
        toggle("Barra de busca na gaveta", p.showSearch) { p.showSearch = it }
        slider("Colunas da grade", 3, 6, p.cols) { p.cols = it }
        slider("Espa\u00e7amento da grade", 0, 24, p.gridGap, " dp") { p.gridGap = it }
        slider("\u00cdcones no dock", 3, 6, p.dockCount) { p.dockCount = it }
        toggle("Dock em vidro", p.dockGlass) { p.dockGlass = it }
        note("Deslize horizontalmente para trocar de página. Segure um atalho e solte sobre outro para criar uma pasta. Use + Widget na tela inicial para adicionar widgets instalados.")
        button("Restaurar atalhos da tela inicial") {
            p.home = emptyList()
            p.homeSeeded = false
            render()
        }

        title("MODO DESKTOP / DEX")
        choices("Ativa\u00e7\u00e3o", listOf("Autom\u00e1tico", "Sempre", "Nunca"), p.desktopMode) {
            p.desktopMode = it
            render()
        }
        note("Autom\u00e1tico: liga no DeX ou em telas grandes (1000 dp ou mais). Use \"Sempre\" para testar.")
        toggle("Abrir apps em janelas", p.winMode) { p.winMode = it }
        toggle("Barra de tarefas flutuante", p.taskbarFloat) { p.taskbarFloat = it }
        toggle("Barra de tarefas centralizada", p.taskbarCenter) { p.taskbarCenter = it }
        slider("Altura da barra de tarefas", 44, 76, p.taskbarH, " dp") { p.taskbarH = it }
        slider("\u00cdcones da \u00e1rea de trabalho", 40, 88, p.deskIcon, " dp") { p.deskIcon = it }
        button("Restaurar atalhos da \u00e1rea de trabalho") {
            p.desk = emptyList()
            p.deskSeeded = false
            render()
        }

        title("SISTEMA")
        button("Definir como launcher padr\u00e3o") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        button("Voltar") { finish() }

        setContentView(sv)
        sv.post { sv.scrollTo(0, y) }
    }

    // ---------- componentes ----------

    private fun title(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 13f
        tv.setTextColor(p.accent)
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(dp(6), dp(22), 0, dp(8))
        content.addView(tv)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(10), dp(16), dp(14))
        val cb = GradientDrawable()
        cb.cornerRadius = dpf(22f)
        cb.setColor(withAlpha(p.fg, 16))
        cb.setStroke(dp(1), withAlpha(p.fg, 26))
        card.background = cb
        content.addView(card, LinearLayout.LayoutParams(-1, -2))
        cur = card
    }

    private fun label(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 15f
        tv.setTextColor(p.fg)
        tv.setPadding(0, dp(8), 0, dp(4))
        cur.addView(tv)
    }

    private fun note(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 12f
        tv.setTextColor(withAlpha(p.fg, 150))
        tv.setPadding(0, dp(2), 0, dp(6))
        cur.addView(tv)
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, unit: String = "", onSet: (Int) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, dp(6), 0, dp(6))
        val tv = TextView(this)
        tv.setTextColor(p.fg)
        tv.textSize = 15f
        tv.text = "$label: $value$unit"
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = (value - min).coerceIn(0, max - min)
        sb.progressTintList = ColorStateList.valueOf(p.accent)
        sb.thumbTintList = ColorStateList.valueOf(p.accent)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                tv.text = "$label: ${v + min}$unit"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {
                onSet((s?.progress ?: 0) + min)
            }
        })
        row.addView(tv)
        row.addView(sb)
        cur.addView(row)
    }

    private fun toggle(label: String, value: Boolean, onSet: (Boolean) -> Unit) {
        val s = Switch(this)
        s.text = label
        s.textSize = 15f
        s.setTextColor(p.fg)
        s.isChecked = value
        s.thumbTintList = ColorStateList.valueOf(p.accent)
        s.setPadding(0, dp(8), 0, dp(8))
        s.setOnCheckedChangeListener { _, checked -> onSet(checked) }
        cur.addView(s)
    }

    private fun choices(label: String, opts: List<String>, sel: Int, onSet: (Int) -> Unit) {
        label(label)
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        for (i in opts.indices) {
            val b = TextView(this)
            b.text = opts[i]
            b.textSize = 14f
            b.setPadding(dp(14), dp(8), dp(14), dp(8))
            val g = GradientDrawable()
            g.cornerRadius = dpf(20f)
            if (i == sel) {
                g.setColor(p.accent)
                b.setTextColor(Color.WHITE)
            } else {
                g.setColor(withAlpha(p.fg, 28))
                b.setTextColor(p.fg)
            }
            b.background = g
            b.setOnClickListener { onSet(i) }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.rightMargin = dp(8)
            row.addView(b, lp)
        }
        hs.addView(row)
        cur.addView(hs)
    }

    private fun swatches() {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.setPadding(0, dp(4), 0, dp(4))
        val colors = ArrayList<Int>()
        if (Build.VERSION.SDK_INT >= 31) colors.add(getColor(android.R.color.system_accent1_400))
        for (hex in listOf("#4F8CFF", "#FF5C8A", "#34D399", "#FFB020", "#A78BFA", "#22D3EE", "#FF6B35", "#E5E7EB")) {
            colors.add(Color.parseColor(hex))
        }
        for (c in colors) {
            val v = View(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(c)
            if (c == p.accent) g.setStroke(dp(3), p.fg)
            v.background = g
            v.setOnClickListener {
                p.accent = c
                render()
            }
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.rightMargin = dp(10)
            row.addView(v, lp)
        }
        hs.addView(row)
        cur.addView(hs)
    }

    private fun button(label: String, onClick: () -> Unit) {
        val b = TextView(this)
        b.text = label
        b.textSize = 15f
        b.gravity = Gravity.CENTER
        b.setTextColor(Color.WHITE)
        b.setPadding(dp(16), dp(12), dp(16), dp(12))
        val g = GradientDrawable()
        g.cornerRadius = dpf(16f)
        g.setColor(p.accent)
        b.background = g
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(10)
        cur.addView(b, lp)
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, 7)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data
        if (requestCode == 7 && resultCode == RESULT_OK && u != null) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            p.wallUri = u.toString()
            p.wallMode = 2
            render()
        }
    }
}