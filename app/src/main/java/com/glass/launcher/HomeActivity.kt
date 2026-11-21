package com.glass.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class HomeActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var icons: Icons
    private var apps: List<AppInfo> = emptyList()
    private var shown: List<AppInfo> = emptyList()
    private var query = ""
    private var lastRev = -1
    private var desktop = false
    private var adapter: AppAdapter? = null
    private var grid: GridView? = null
    private var dockBox: LinearLayout? = null
    private var menu: View? = null
    private var scrim: View? = null
    private var searchEt: EditText? = null
    private var winCount = 0

    // ---------- ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        icons = Icons(this, p)
    }

    override fun onResume() {
        super.onResume()
        apps = AppRepo.load(this)
        seedDock()
        seedDesk()
        applyFilter()
        if (p.rev != lastRev) {
            rebuild()
        } else {
            adapter?.notifyDataSetChanged()
            fillDock()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeMenu()
        grid?.setSelection(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (apps.isNotEmpty()) rebuild()
    }

    override fun onBackPressed() {
        closeMenu()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && menuOpen()) {
            closeMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- dados ----------

    private fun applyFilter() {
        val q = query.trim()
        shown = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    private fun seedDock() {
        if (p.seeded || apps.isEmpty()) return
        val pm = packageManager
        val tries = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val keys = ArrayList<String>()
        for (i in tries) {
            val pkg = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: continue
            val a = apps.firstOrNull { it.pkg == pkg } ?: continue
            if (a.key !in keys) keys.add(a.key)
        }
        p.dock = keys
        p.seeded = true
    }

    private fun seedDesk() {
        if (p.deskSeeded || apps.isEmpty()) return
        p.desk = (p.dock + apps.take(8).map { it.key }).distinct().take(8)
        p.deskSeeded = true
    }

    private fun wantDesktop(): Boolean = when (p.desktopMode) {
        1 -> true
        2 -> false
        else -> isDex(this) || resources.configuration.screenWidthDp >= 1000
    }

    // ---------- construcao ----------

    private fun rebuild() {
        icons.clear()
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        Backdrop.build(this, p, dm.widthPixels, dm.heightPixels)
        applyWindow()
        desktop = wantDesktop()
        setUi()
        window.decorView.alpha = 0f
        window.decorView.animate().alpha(1f).setDuration(220).start()
        lastRev = p.rev
    }

    private fun setUi() {
        menu = null
        scrim = null
        searchEt = null
        setContentView(if (desktop) buildDesktop() else buildPhone())
    }

    private fun applyWindow() {
        var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (!p.dark) {
            f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = f
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 31) {
            // com o papel de parede do sistema, o desfoque vem do proprio Android (12+)
            window.setBackgroundBlurRadius(if (p.wallMode == 1) p.wallBlur * 4 else 0)
        }
    }

    private fun lp(
        w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f
    ): LinearLayout.LayoutParams {
        val x = LinearLayout.LayoutParams(w, h, weight)
        x.setMargins(dp(l), dp(t), dp(r), dp(b))
        return x
    }

    private fun fp(w: Int, h: Int, g: Int = Gravity.NO_GRAVITY): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(w, h, g)

    private fun addWall(root: FrameLayout) {
        if (p.wallMode != 1) root.addView(WallView(this), fp(-1, -1))
    }

    private fun pad(root: View, target: View) {
        root.setOnApplyWindowInsetsListener { _, ins ->
            target.setPadding(
                ins.systemWindowInsetLeft, ins.systemWindowInsetTop,
                ins.systemWindowInsetRight, ins.systemWindowInsetBottom
            )
            ins
        }
    }

    private fun gearView(size: Float): TextView {
        val gear = TextView(this)
        gear.text = "\u2699"
        gear.textSize = size
        gear.setTextColor(p.fg)
        gear.setPadding(dp(14), dp(10), dp(14), dp(10))
        gear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        return gear
    }

    private fun clockCard(withGear: Boolean): GlassView {
        val fg = p.fg
        val style = p.clockStyle
        val card = GlassView(this, p)
        val cv = LinearLayout(this)
        cv.orientation = if (style == 2) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        cv.gravity = if (style == 2) Gravity.CENTER_VERTICAL else Gravity.START
        cv.setPadding(dp(20), dp(14), dp(if (withGear) 52 else 20), dp(16))
        val time = TextClock(this)
        time.format24Hour = "HH:mm"
        time.format12Hour = "h:mm"
        time.textSize = if (style == 2) 34f else 54f
        time.setTextColor(fg)
        time.typeface = if (style == 1) Typeface.create("sans-serif", Typeface.BOLD)
        else Typeface.create("sans-serif-light", Typeface.NORMAL)
        cv.addView(time)
        if (p.showDate) {
            val date = TextClock(this)
            date.format24Hour = "EEE, d MMM"
            date.format12Hour = "EEE, d MMM"
            date.textSize = 16f
            date.setTextColor(withAlpha(fg, 200))
            if (style == 2) date.setPadding(dp(14), 0, 0, 0)
            cv.addView(date)
        }
        if (style != 2) {
            val bar = View(this)
            val bg = GradientDrawable()
            bg.cornerRadius = dpf(2f)
            bg.setColor(p.accent)
            bar.background = bg
            cv.addView(bar, lp(dp(36), dp(4), t = 10))
        }
        card.addView(cv, fp(-1, -2))
        if (withGear) card.addView(gearView(22f), fp(-2, -2, Gravity.END or Gravity.TOP))
        return card
    }

    private fun searchBar(withGear: Boolean): GlassView {
        val fg = p.fg
        val bar = GlassView(this, p, 100f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), 0, dp(8), 0)
        val et = EditText(this)
        et.background = null
        et.hint = "Buscar apps"
        et.setHintTextColor(withAlpha(fg, 130))
        et.setTextColor(fg)
        et.textSize = 15f
        et.setSingleLine()
        et.imeOptions = EditorInfo.IME_ACTION_GO
        et.setPadding(0, dp(12), 0, dp(12))
        et.setText(query)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
                adapter?.notifyDataSetChanged()
            }
        })
        et.setOnEditorActionListener { _, _, _ ->
            shown.firstOrNull()?.let { launch(it) }
            true
        }
        row.addView(et, lp(0, -2, weight = 1f))
        if (withGear) row.addView(gearView(20f))
        bar.addView(row, fp(-1, -2))
        searchEt = et
        return bar
    }

    private fun setupGrid(g: GridView) {
        val ad = AppAdapter()
        g.adapter = ad
        g.setOnItemClickListener { _, _, pos, _ -> shown.getOrNull(pos)?.let { launch(it) } }
        g.setOnItemLongClickListener { _, v, pos, _ ->
            shown.getOrNull(pos)?.let { appMenu(v, it, 0) }
            true
        }
        grid = g
        adapter = ad
    }

    private fun newGrid(cols: Int): GridView {
        val g = GridView(this)
        g.numColumns = cols
        g.selector = ColorDrawable(Color.TRANSPARENT)
        g.verticalSpacing = dp(p.gridGap)
        g.isVerticalScrollBarEnabled = false
        g.overScrollMode = View.OVER_SCROLL_NEVER
        g.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        return g
    }

    // ---------- modo celular ----------

    private fun buildPhone(): View {
        val root = FrameLayout(this)
        addWall(root)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, fp(-1, -1))
        pad(root, col)

        val compact = resources.configuration.screenHeightDp < 480
        if (!compact) col.addView(clockCard(true), lp(-1, -2, 14, 10, 14, 8))
        if (p.showSearch) col.addView(searchBar(compact), lp(-1, -2, 14, if (compact) 10 else 0, 14, 8))

        val panel = GlassView(this, p)
        val g = newGrid(p.cols)
        g.setPadding(dp(8), dp(12), dp(8), dp(12))
        g.clipToPadding = false
        setupGrid(g)
        panel.addView(g, fp(-1, -1))
        col.addView(panel, lp(-1, 0, 14, 0, 14, 8, 1f))

        val dock = GlassView(this, p, 2f)
        dock.glassOn = p.dockGlass
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        box.setPadding(dp(10), dp(10), dp(10), dp(10))
        dock.addView(box, fp(-1, -2))
        dockBox = box
        col.addView(dock, lp(-1, -2, 14, 0, 14, 10))
        fillDock()
        return root
    }

    // ---------- modo desktop / DeX ----------

    private fun buildDesktop(): View {
        val root = FrameLayout(this)
        addWall(root)
        val col = FrameLayout(this)
        root.addView(col, fp(-1, -1))
        pad(root, col)
        col.isContextClickable = true
        col.setOnContextClickListener { v ->
            desktopMenu(v)
            true
        }

        // atalhos da area de trabalho (colunas de cima para baixo)
        val map = apps.associateBy { it.key }
        val deskApps = p.desk.mapNotNull { map[it] }
        val iconPx = dp(p.deskIcon)
        val cellH = iconPx + dp(46)
        val rows = maxOf(1, (resources.displayMetrics.heightPixels - dp(p.taskbarH) - dp(150)) / cellH)
        val area = LinearLayout(this)
        area.orientation = LinearLayout.HORIZONTAL
        area.setPadding(dp(16), dp(16), dp(16), dp(16))
        for (chunk in deskApps.chunked(rows)) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            for (a in chunk) c.addView(deskCell(a, iconPx), lp(iconPx + dp(40), cellH))
            area.addView(c, lp(-2, -2))
        }
        col.addView(area, fp(-2, -2, Gravity.TOP or Gravity.START))

        // relogio em vidro, canto superior direito
        val cw = minOf(dp(300), resources.displayMetrics.widthPixels / 2)
        val clockLp = fp(cw, -2, Gravity.TOP or Gravity.END)
        clockLp.setMargins(dp(24), dp(24), dp(24), 0)
        col.addView(clockCard(false), clockLp)

        // fundo clicavel para fechar o menu Iniciar + o proprio menu
        val sc = View(this)
        sc.visibility = View.GONE
        sc.setOnClickListener { closeMenu() }
        col.addView(sc, fp(-1, -1))
        scrim = sc
        val m = buildStartMenu()
        m.visibility = View.GONE
        col.addView(m, menuParams())
        menu = m

        // barra de tarefas
        val floating = p.taskbarFloat
        val tb = GlassView(this, p, if (floating) 40f else -p.glassRadius.toFloat())
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(10), 0, dp(10), 0)
        val sb = dp(p.taskbarH) - dp(20)
        val start = TextView(this)
        start.text = "\u2756"
        start.textSize = 20f
        start.gravity = Gravity.CENTER
        start.setTextColor(Color.WHITE)
        val sg = GradientDrawable()
        sg.cornerRadius = dpf(14f)
        sg.setColor(p.accent)
        start.background = sg
        start.setOnClickListener { toggleMenu() }
        val startGroup = LinearLayout(this)
        startGroup.orientation = LinearLayout.HORIZONTAL
        startGroup.gravity = Gravity.CENTER_VERTICAL
        startGroup.addView(start, lp(sb, sb, 4, 0, 10, 0))
        val dockRow = LinearLayout(this)
        dockRow.orientation = LinearLayout.HORIZONTAL
        dockRow.gravity = Gravity.CENTER_VERTICAL
        startGroup.addView(dockRow)
        dockBox = dockRow

        val left = LinearLayout(this)
        left.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        val mid = LinearLayout(this)
        mid.gravity = Gravity.CENTER_VERTICAL
        val right = LinearLayout(this)
        right.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        if (p.taskbarCenter) mid.addView(startGroup) else left.addView(startGroup)
        val tc = LinearLayout(this)
        tc.orientation = LinearLayout.VERTICAL
        tc.gravity = Gravity.END
        tc.setPadding(dp(8), 0, dp(4), 0)
        val t1 = TextClock(this)
        t1.format24Hour = "HH:mm"
        t1.format12Hour = "h:mm"
        t1.textSize = 15f
        t1.typeface = Typeface.DEFAULT_BOLD
        t1.setTextColor(p.fg)
        val t2 = TextClock(this)
        t2.format24Hour = "d MMM"
        t2.format12Hour = "d MMM"
        t2.textSize = 11f
        t2.setTextColor(withAlpha(p.fg, 190))
        tc.addView(t1)
        tc.addView(t2)
        right.addView(tc)
        right.addView(gearView(20f))
        bar.addView(left, lp(0, -1, weight = 1f))
        bar.addView(mid, lp(-2, -1))
        bar.addView(right, lp(0, -1, weight = 1f))
        tb.addView(bar, fp(-1, -1))
        val tbLp = fp(-1, dp(p.taskbarH), Gravity.BOTTOM)
        if (floating) tbLp.setMargins(dp(16), 0, dp(16), dp(12))
        col.addView(tb, tbLp)
        fillDock()
        return root
    }

    private fun menuParams(): FrameLayout.LayoutParams {
        val dm = resources.displayMetrics
        val mh = minOf(dp(600), dm.heightPixels - dp(p.taskbarH) - dp(110))
        val mw = minOf(dp(440), dm.widthPixels - dp(32))
        val gravity = Gravity.BOTTOM or (if (p.taskbarCenter) Gravity.CENTER_HORIZONTAL else Gravity.START)
        val lpm = fp(mw, mh, gravity)
        lpm.bottomMargin = dp(p.taskbarH) + dp(if (p.taskbarFloat) 24 else 10)
        if (!p.taskbarCenter) lpm.leftMargin = dp(if (p.taskbarFloat) 16 else 8)
        return lpm
    }

    private fun buildStartMenu(): GlassView {
        val m = GlassView(this, p)
        val lay = LinearLayout(this)
        lay.orientation = LinearLayout.VERTICAL
        lay.setPadding(dp(14), dp(14), dp(14), dp(10))
        lay.addView(searchBar(false), lp(-1, -2, b = 8))
        val g = newGrid(4)
        setupGrid(g)
        lay.addView(g, lp(-1, 0, weight = 1f))
        val foot = LinearLayout(this)
        foot.orientation = LinearLayout.HORIZONTAL
        foot.gravity = Gravity.CENTER_VERTICAL
        foot.setPadding(dp(4), dp(8), dp(4), 0)
        val count = TextView(this)
        count.text = "${apps.size} apps"
        count.textSize = 12f
        count.setTextColor(withAlpha(p.fg, 170))
        foot.addView(count, lp(0, -2, weight = 1f))
        val st = TextView(this)
        st.text = "Ajustes"
        st.textSize = 13f
        st.setTextColor(p.accent)
        st.typeface = Typeface.DEFAULT_BOLD
        st.setPadding(dp(10), dp(6), dp(10), dp(6))
        st.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        foot.addView(st)
        lay.addView(foot, lp(-1, -2))
        m.addView(lay, fp(-1, -1))
        return m
    }

    private fun menuOpen(): Boolean = menu?.visibility == View.VISIBLE

    private fun toggleMenu() {
        if (menuOpen()) closeMenu() else openMenu()
    }

    private fun openMenu() {
        val m = menu ?: return
        query = ""
        searchEt?.setText("")
        applyFilter()
        adapter?.notifyDataSetChanged()
        grid?.setSelection(0)
        scrim?.visibility = View.VISIBLE
        m.alpha = 0f
        m.visibility = View.VISIBLE
        m.animate().alpha(1f).setDuration(140).start()
        searchEt?.requestFocus()
    }

    private fun closeMenu() {
        val m = menu ?: return
        if (m.visibility != View.VISIBLE) return
        scrim?.visibility = View.GONE
        m.animate().alpha(0f).setDuration(110).withEndAction { m.visibility = View.GONE }.start()
    }

    private fun deskCell(a: AppInfo, px: Int): View {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER_HORIZONTAL
        ll.setPadding(dp(6), dp(6), dp(6), dp(6))
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER
        iv.setImageBitmap(icons.get(a, px))
        ll.addView(iv, lp(px, px))
        val tv = TextView(this)
        tv.text = a.label
        tv.textSize = 12f
        tv.maxLines = 2
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = Gravity.CENTER
        tv.setTextColor(p.fg)
        tv.setShadowLayer(8f, 0f, 2f, if (p.dark) Color.argb(180, 0, 0, 0) else Color.argb(160, 255, 255, 255))
        ll.addView(tv, lp(-1, -2, t = 4))
        hover(ll)
        ll.setOnClickListener { launch(a) }
        ll.setOnLongClickListener { v ->
            appMenu(v, a, 1)
            true
        }
        ll.isContextClickable = true
        ll.setOnContextClickListener { v ->
            appMenu(v, a, 1)
            true
        }
        return ll
    }

    private fun hover(v: View) {
        v.setOnHoverListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    val g = GradientDrawable()
                    g.cornerRadius = dpf(14f)
                    g.setColor(withAlpha(Color.WHITE, if (p.dark) 40 else 90))
                    view.background = g
                }
                MotionEvent.ACTION_HOVER_EXIT -> view.background = null
            }
            false
        }
    }

    private fun fillDock() {
        val box = dockBox ?: return
        box.removeAllViews()
        val map = apps.associateBy { it.key }
        val list = p.dock.mapNotNull { map[it] }.take(p.dockCount)
        if (desktop) {
            val px = dp(p.taskbarH) - dp(26)
            for (a in list) {
                val iv = ImageView(this)
                iv.scaleType = ImageView.ScaleType.CENTER
                iv.setImageBitmap(icons.get(a, px))
                iv.setOnClickListener { launch(a) }
                iv.setOnLongClickListener { v ->
                    appMenu(v, a, 2)
                    true
                }
                iv.isContextClickable = true
                iv.setOnContextClickListener { v ->
                    appMenu(v, a, 2)
                    true
                }
                hover(iv)
                box.addView(iv, lp(px + dp(14), px + dp(14), 2, 0, 2, 0))
            }
        } else {
            if (list.isEmpty()) {
                val t = TextView(this)
                t.text = "Segure um app na grade para fixar aqui"
                t.setTextColor(p.fg)
                t.textSize = 13f
                t.setPadding(0, dp(18), 0, dp(18))
                box.addView(t)
                return
            }
            val px = dp(p.iconSize)
            for (a in list) {
                val iv = ImageView(this)
                iv.scaleType = ImageView.ScaleType.CENTER
                iv.setImageBitmap(icons.get(a, px))
                iv.setOnClickListener { launch(a) }
                iv.setOnLongClickListener { v ->
                    appMenu(v, a, 2)
                    true
                }
                box.addView(iv, lp(0, px + dp(8), weight = 1f))
            }
        }
    }

    private fun launch(a: AppInfo) {
        closeMenu()
        try {
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory(Intent.CATEGORY_LAUNCHER)
            i.component = ComponentName(a.pkg, a.cls)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (desktop && p.winMode) {
                val dm = resources.displayMetrics
                val w = (dm.widthPixels * 0.6f).toInt()
                val h = (dm.heightPixels * 0.72f).toInt()
                val off = (winCount % 6) * dp(40)
                winCount++
                val left = dp(120) + off
                val top = dp(48) + off
                val options = ActivityOptions.makeBasic()
                options.setLaunchBounds(android.graphics.Rect(left, top, left + w, top + h))
                startActivity(i, options.toBundle())
            } else {
                startActivity(i)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir o app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun popup(v: View, items: List<Pair<String, () -> Unit>>) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val m = PopupMenu(this, v)
        items.forEachIndexed { i, item -> m.menu.add(0, i, i, item.first) }
        m.setOnMenuItemClickListener { item ->
            items[item.itemId].second()
            true
        }
        m.show()
    }

    /** kind: 0 = grade/menu, 1 = área de trabalho, 2 = dock/barra */
    private fun appMenu(v: View, a: AppInfo, kind: Int) {
        val l = ArrayList<Pair<String, () -> Unit>>()
        l.add("Abrir" to { launch(a) })
        if (kind != 2) l.add((if (desktop) "Fixar na barra" else "Fixar no dock") to { pinDock(a) })
        if (kind == 2) l.add("Remover da barra" to { unpinDock(a) })
        if (kind != 1) l.add("Fixar na área de trabalho" to { pinDesk(a) })
        if (kind == 1) l.add("Remover da área de trabalho" to { unpinDesk(a) })
        l.add("Informações do app" to {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)))
        })
        l.add("Desinstalar" to {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)))
        })
        popup(v, l)
    }

    private fun desktopMenu(v: View) {
        popup(v, listOf(
            "Menu Iniciar" to { toggleMenu() },
            "Ajustes do launcher" to { startActivity(Intent(this, SettingsActivity::class.java)) },
            "Atualizar" to { rebuild() }
        ))
    }

    private fun pinDock(a: AppInfo) {
        val l = p.dock.toMutableList()
        if (a.key !in l) l.add(a.key)
        p.dock = l.takeLast(p.dockCount)
        lastRev = p.rev
        fillDock()
    }

    private fun unpinDock(a: AppInfo) {
        p.dock = p.dock.filter { it != a.key }
        lastRev = p.rev
        fillDock()
    }

    private fun pinDesk(a: AppInfo) {
        val l = p.desk.toMutableList()
        if (a.key !in l) l.add(a.key)
        p.desk = l
        lastRev = p.rev
        if (desktop) setUi()
        else Toast.makeText(this, "Adicionado à área de trabalho (modo desktop)", Toast.LENGTH_SHORT).show()
    }

    private fun unpinDesk(a: AppInfo) {
        p.desk = p.desk.filter { it != a.key }
        lastRev = p.rev
        if (desktop) setUi()
    }

    private fun makeCell(): LinearLayout {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER_HORIZONTAL
        ll.setPadding(dp(2), dp(8), dp(2), dp(6))
        val px = dp(p.iconSize)
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER
        ll.addView(iv, lp(px, px))
        val tv = TextView(this)
        tv.textSize = p.labelSize.toFloat()
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = Gravity.CENTER
        tv.setTextColor(p.fg)
        ll.addView(tv, lp(-1, -2, t = 4))
        hover(ll)
        ll.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return ll
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = shown[position]
            val ll = (convertView as? LinearLayout) ?: makeCell()
            val iv = ll.getChildAt(0) as ImageView
            val tv = ll.getChildAt(1) as TextView
            iv.setImageBitmap(icons.get(a, dp(p.iconSize)))
            tv.text = a.label
            tv.visibility = if (p.showLabels) View.VISIBLE else View.GONE
            ll.isContextClickable = true
            ll.setOnContextClickListener { v ->
                appMenu(v, a, 0)
                true
            }
            return ll
        }
    }
}
