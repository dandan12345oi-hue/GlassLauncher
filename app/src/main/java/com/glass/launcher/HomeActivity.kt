package com.glass.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.ViewFlipper
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
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.DragEvent
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
    private var homeFlipper: ViewFlipper? = null
    private var pageDots: LinearLayout? = null
    private var homePageIndex = 0
    private var widgetBox: LinearLayout? = null
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private var pendingWidgetId = -1
    private var pendingWidgetProvider: ComponentName? = null
    private var drawerOpen = false
    private var touchStartX = 0f
    private var touchStartY = 0f

    companion object {
        private const val WIDGET_HOST_ID = 0x474C
        private const val REQUEST_BIND_WIDGET = 4101
        private const val REQUEST_CONFIGURE_WIDGET = 4102
    }


    // ---------- ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        icons = Icons(this, p)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        try { widgetHost.stopListening() } catch (_: Exception) {}
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        apps = AppRepo.load(this)
        seedDock()
        seedDesk()
        seedHome()
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
        if (menuOpen()) closeMenu()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!desktop && !drawerOpen) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = ev.x
                    touchStartY = ev.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - touchStartX
                    val dy = ev.y - touchStartY
                    if (kotlin.math.abs(dx) > dp(72) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) {
                        showHomePage(homePageIndex + if (dx < 0) 1 else -1)
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIND_WIDGET) {
            if (resultCode == RESULT_OK && pendingWidgetId > 0) {
                configureOrSaveWidget(pendingWidgetId)
            } else {
                discardPendingWidget()
            }
            return
        }
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK && pendingWidgetId > 0) {
                saveWidget(pendingWidgetId)
            } else {
                discardPendingWidget()
            }
        }
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

    private fun seedHome() {
        if (p.homeSeeded || apps.isEmpty()) return
        p.home = (p.dock + apps.take(8).map { it.key }).distinct().take(16)
        p.homeSeeded = true
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
        drawerOpen = false
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

        val tools = LinearLayout(this)
        tools.orientation = LinearLayout.HORIZONTAL
        tools.gravity = Gravity.CENTER_VERTICAL
        tools.setPadding(dp(18), dp(4), dp(18), dp(8))
        val caption = TextView(this)
        caption.text = "TELA INICIAL"
        caption.textSize = 11f
        caption.typeface = Typeface.DEFAULT_BOLD
        caption.setTextColor(withAlpha(p.fg, 165))
        tools.addView(caption, lp(0, -2, weight = 1f))
        val widgetButton = TextView(this)
        widgetButton.text = "+ Widget"
        widgetButton.textSize = 13f
        widgetButton.setTextColor(p.accent)
        widgetButton.setPadding(dp(10), dp(8), dp(10), dp(8))
        widgetButton.setOnClickListener { showWidgetPicker() }
        tools.addView(widgetButton)
        val organizeButton = TextView(this)
        organizeButton.text = "Organizar"
        organizeButton.textSize = 13f
        organizeButton.setTextColor(p.accent)
        organizeButton.setPadding(dp(10), dp(8), dp(10), dp(8))
        organizeButton.setOnClickListener { showHomeManager() }
        tools.addView(organizeButton)
        col.addView(tools, lp(-1, -2))

        widgetBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        renderWidgets()
        if (p.widgets.isNotEmpty()) col.addView(widgetBox, lp(-1, -2, b = 6))

        val panel = GlassView(this, p)
        val flipper = ViewFlipper(this)
        val entries = availableHomeEntries()
        val capacity = (p.cols * 4).coerceAtLeast(8)
        val pages = entries.chunked(capacity).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { pageIndex, pageEntries ->
            val g = newGrid(p.cols)
            g.setPadding(dp(8), dp(12), dp(8), dp(12))
            g.clipToPadding = false
            g.adapter = HomePageAdapter(pageEntries)
            g.setOnItemClickListener { _, _, position, _ ->
                pageEntries.getOrNull(position)?.let { openHomeEntry(it) }
            }
            g.setOnItemLongClickListener { _, v, position, _ ->
                val entry = pageEntries.getOrNull(position) ?: return@setOnItemLongClickListener true
                val clip = ClipData.newPlainText("glass-home-entry", entry)
                v.startDragAndDrop(clip, View.DragShadowBuilder(v), null, 0)
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }
            g.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    DragEvent.ACTION_DROP -> {
                        val dragged = event.clipData?.getItemAt(0)?.text?.toString()
                        val pos = g.pointToPosition(event.x.toInt(), event.y.toInt())
                        if (!dragged.isNullOrBlank()) {
                            val target = pageEntries.getOrNull(pos)
                            if (target != null && target != dragged) mergeHomeEntries(dragged, target)
                            else if (target == null || target == dragged) moveHomeEntryToPagePosition(dragged, pageIndex, pos)
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> true
                    else -> true
                }
            }
            flipper.addView(g, fp(-1, -1))
        }
        homePageIndex = homePageIndex.coerceIn(0, pages.lastIndex)
        flipper.displayedChild = homePageIndex
        homeFlipper = flipper
        panel.addView(flipper, fp(-1, -1))
        col.addView(panel, lp(-1, 0, 14, 0, 14, 2, 1f))

        val dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        dots.setPadding(0, dp(4), 0, dp(6))
        pageDots = dots
        col.addView(dots, lp(-1, -2))
        updatePageDots(pages.size)

        val dock = GlassView(this, p, 2f)
        dock.glassOn = p.dockGlass
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        box.setPadding(dp(10), dp(8), dp(10), dp(8))
        dock.addView(box, fp(-1, -2))
        dockBox = box
        col.addView(dock, lp(-1, -2, 14, 0, 14, 10))
        fillDock()

        val sc = View(this)
        sc.visibility = View.GONE
        sc.setBackgroundColor(Color.argb(125, 0, 0, 0))
        sc.setOnClickListener { closeMenu() }
        root.addView(sc, fp(-1, -1))
        scrim = sc
        val drawer = buildStartMenu()
        drawer.visibility = View.GONE
        val drawerLp = fp(-1, -1)
        drawerLp.setMargins(dp(12), dp(if (compact) 24 else 42), dp(12), dp(12))
        root.addView(drawer, drawerLp)
        menu = drawer
        return root
    }

    private fun availableHomeEntries(): List<String> {
        val appKeys = apps.map { it.key }.toSet()
        return p.home.filter { entry ->
            if (HomeEntries.isFolder(entry)) HomeEntries.apps(entry).any { it in appKeys }
            else entry in appKeys
        }
    }

    private fun showHomeManager() {
        val entries = availableHomeEntries()
        if (entries.isEmpty()) {
            Toast.makeText(this, "Adicione apps pela gaveta de aplicativos", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = entries.map { entry ->
            if (HomeEntries.isFolder(entry)) "📁 " + HomeEntries.name(entry)
            else apps.firstOrNull { it.key == entry }?.label ?: entry
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Organizar tela inicial")
            .setItems(labels) { _, which ->
                val anchor: View = pageDots ?: window.decorView
                homeEntryMenu(anchor, entries[which])
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun updatePageDots(count: Int) {
        val row = pageDots ?: return
        row.removeAllViews()
        for (i in 0 until count) {
            val dot = TextView(this)
            dot.text = if (i == homePageIndex) "●" else "○"
            dot.textSize = 11f
            dot.setTextColor(if (i == homePageIndex) p.accent else withAlpha(p.fg, 120))
            dot.setPadding(dp(7), 0, dp(7), 0)
            dot.setOnClickListener { showHomePage(i) }
            row.addView(dot)
        }
    }

    private fun showHomePage(index: Int) {
        val flipper = homeFlipper ?: return
        if (index !in 0 until flipper.childCount) return
        homePageIndex = index
        flipper.displayedChild = index
        updatePageDots(flipper.childCount)
    }

    private fun renderWidgets() {
        val box = widgetBox ?: return
        box.removeAllViews()
        if (!::widgetHost.isInitialized || !::widgetManager.isInitialized) return
        p.widgets.forEach { id ->
            try {
                val info = widgetManager.getAppWidgetInfo(id) ?: return@forEach
                val hostView = widgetHost.createView(this, id, info)
                hostView.setAppWidget(id, info)
                box.addView(hostView, lp(-1, -2, b = 8))
                hostView.setOnLongClickListener { v ->
                    popup(v, listOf("Remover widget" to { removeWidget(id) }))
                    true
                }
            } catch (_: Exception) {
                // Um widget removido pelo sistema não deve impedir a tela inicial de abrir.
            }
        }
    }

    private fun showWidgetPicker() {
        val providers = widgetManager.installedProviders.sortedBy {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it.provider.packageName, 0)).toString() }
            catch (_: Exception) { it.provider.packageName }
        }
        if (providers.isEmpty()) {
            Toast.makeText(this, "Nenhum widget disponível", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = providers.map { info ->
            val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.provider.packageName, 0)).toString() }
            catch (_: Exception) { info.provider.packageName }
            app + " — " + (info.loadLabel(packageManager) ?: "Widget")
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Adicionar widget")
            .setItems(labels) { _, which -> requestWidget(providers[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestWidget(info: android.appwidget.AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingWidgetProvider = info.provider
        try {
            val bound = widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
            if (bound) {
                configureOrSaveWidget(id)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                startActivityForResult(intent, REQUEST_BIND_WIDGET)
            }
        } catch (e: Exception) {
            discardPendingWidget()
            Toast.makeText(this, "Não foi possível adicionar esse widget", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureOrSaveWidget(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        val configure = info?.configure
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            try {
                startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET)
            } catch (_: Exception) {
                saveWidget(id)
            }
        } else {
            saveWidget(id)
        }
    }

    private fun saveWidget(id: Int) {
        if (id > 0) p.widgets = (p.widgets + id).distinct()
        pendingWidgetId = -1
        pendingWidgetProvider = null
        rebuild()
    }

    private fun discardPendingWidget() {
        if (pendingWidgetId > 0) {
            try { widgetHost.deleteAppWidgetId(pendingWidgetId) } catch (_: Exception) {}
        }
        pendingWidgetId = -1
        pendingWidgetProvider = null
    }

    private fun removeWidget(id: Int) {
        p.widgets = p.widgets.filter { it != id }
        try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
        rebuild()
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
        drawerOpen = !desktop
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
        drawerOpen = false
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
                iv.setOnLongClickListener { v -> appMenu(v, a, 2); true }
                iv.isContextClickable = true
                iv.setOnContextClickListener { v -> appMenu(v, a, 2); true }
                hover(iv)
                box.addView(iv, lp(px + dp(14), px + dp(14), 2, 0, 2, 0))
            }
        } else {
            if (list.isEmpty()) {
                val t = TextView(this)
                t.text = "Fixe seus apps favoritos"
                t.setTextColor(p.fg)
                t.textSize = 12f
                t.gravity = Gravity.CENTER
                box.addView(t, lp(0, dp(46), weight = 1f))
            } else {
                val px = dp(p.iconSize)
                for (a in list) {
                    val iv = ImageView(this)
                    iv.scaleType = ImageView.ScaleType.CENTER
                    iv.setImageBitmap(icons.get(a, px))
                    iv.contentDescription = a.label
                    iv.setOnClickListener { launch(a) }
                    iv.setOnLongClickListener { v -> appMenu(v, a, 2); true }
                    box.addView(iv, lp(0, px + dp(8), weight = 1f))
                }
            }
            val appsButton = TextView(this)
            appsButton.text = "▦"
            appsButton.textSize = 24f
            appsButton.gravity = Gravity.CENTER
            appsButton.setTextColor(Color.WHITE)
            appsButton.contentDescription = "Abrir gaveta de aplicativos"
            val bg = GradientDrawable()
            bg.cornerRadius = dpf(15f)
            bg.setColor(p.accent)
            appsButton.background = bg
            appsButton.setOnClickListener { openMenu() }
            box.addView(appsButton, lp(dp(48), dp(48), l = 6))
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
        if (!desktop && kind == 0 && a.key !in p.home) l.add("Adicionar à tela inicial" to { pinHome(a) })
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

    private inner class HomePageAdapter(private val entries: List<String>) : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = entries[position].hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = (convertView as? LinearLayout) ?: makeCell()
            val icon = cell.getChildAt(0) as ImageView
            val label = cell.getChildAt(1) as TextView
            val entry = entries[position]
            if (HomeEntries.isFolder(entry)) {
                val members = HomeEntries.apps(entry).mapNotNull { key -> apps.firstOrNull { it.key == key } }
                icon.setImageBitmap(folderBitmap(members, dp(p.iconSize)))
                label.text = HomeEntries.name(entry)
            } else {
                val app = apps.firstOrNull { it.key == entry }
                if (app != null) {
                    icon.setImageBitmap(icons.get(app, dp(p.iconSize)))
                    label.text = app.label
                } else {
                    icon.setImageDrawable(null)
                    label.text = ""
                }
            }
            label.visibility = if (p.showLabels) View.VISIBLE else View.GONE
            cell.isContextClickable = true
            cell.setOnContextClickListener { v ->
                homeEntryMenu(v, entry)
                true
            }
            return cell
        }
    }

    private fun folderBitmap(members: List<AppInfo>, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = withAlpha(p.accent, if (p.dark) 105 else 65)
        canvas.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.24f, px * 0.24f, bg)
        val inset = px * 0.13f
        val size = px * 0.32f
        val spots = listOf(
            RectF(inset, inset, inset + size, inset + size),
            RectF(px - inset - size, inset, px - inset, inset + size),
            RectF(inset, px - inset - size, inset + size, px - inset),
            RectF(px - inset - size, px - inset - size, px - inset, px - inset)
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        members.take(4).forEachIndexed { index, app ->
            canvas.drawBitmap(icons.get(app, (size * 2).toInt().coerceAtLeast(1)), null, spots[index], paint)
        }
        return bitmap
    }

    private fun openHomeEntry(entry: String) {
        if (!HomeEntries.isFolder(entry)) {
            apps.firstOrNull { it.key == entry }?.let { launch(it) }
            return
        }
        val members = HomeEntries.apps(entry).mapNotNull { key -> apps.firstOrNull { it.key == key } }
        if (members.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(HomeEntries.name(entry))
            .setItems(members.map { it.label }.toTypedArray()) { _, which -> launch(members[which]) }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun homeEntryMenu(v: View, entry: String) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("Abrir" to { openHomeEntry(entry) })
        if (HomeEntries.isFolder(entry)) {
            items.add("Renomear pasta" to { renameFolder(entry) })
            items.add("Desagrupar pasta" to {
                val index = p.home.indexOf(entry)
                if (index >= 0) {
                    val expanded = HomeEntries.apps(entry)
                    val list = p.home.toMutableList()
                    list.removeAt(index)
                    list.addAll(index, expanded)
                    p.home = list.distinct()
                    rebuild()
                }
            })
        } else {
            val app = apps.firstOrNull { it.key == entry }
            if (app != null) {
                items.add("Fixar no dock" to { pinDock(app) })
                items.add("Informações do app" to {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)))
                })
            }
        }
        items.add("Remover da tela inicial" to { removeHomeEntry(entry) })
        popup(v, items)
    }

    private fun renameFolder(entry: String) {
        val input = EditText(this)
        input.setText(HomeEntries.name(entry))
        AlertDialog.Builder(this)
            .setTitle("Nome da pasta")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val renamed = HomeEntries.folder(input.text.toString(), HomeEntries.apps(entry))
                p.home = p.home.map { if (it == entry) renamed else it }
                rebuild()
            }
            .show()
    }

    private fun mergeHomeEntries(dragged: String, target: String) {
        val list = p.home.toMutableList()
        if (dragged !in list || target !in list || dragged == target) return
        val combined = (HomeEntries.apps(dragged) + HomeEntries.apps(target)).distinct()
        val label = if (HomeEntries.isFolder(target)) HomeEntries.name(target) else "Pasta"
        val folder = HomeEntries.folder(label, combined)
        val targetIndex = list.indexOf(target)
        list.remove(dragged)
        val adjustedIndex = list.indexOf(target)
        if (adjustedIndex >= 0) list[adjustedIndex] = folder
        else list.add(targetIndex.coerceIn(0, list.size), folder)
        p.home = list
        rebuild()
    }

    private fun moveHomeEntryToPagePosition(entry: String, page: Int, position: Int) {
        val list = p.home.toMutableList()
        if (entry !in list) return
        list.remove(entry)
        val capacity = (p.cols * 4).coerceAtLeast(8)
        val target = (page * capacity + position.coerceAtLeast(0)).coerceIn(0, list.size)
        list.add(target, entry)
        p.home = list
        rebuild()
    }

    private fun pinHome(a: AppInfo) {
        if (a.key !in p.home) p.home = p.home + a.key
        rebuild()
    }

    private fun removeHomeEntry(entry: String) {
        p.home = p.home.filter { it != entry }
        rebuild()
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
