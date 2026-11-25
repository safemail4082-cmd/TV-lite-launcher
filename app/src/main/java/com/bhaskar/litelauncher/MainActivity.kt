package com.bhaskar.litelauncher

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class AppItem(val pkg: String, val label: String, val ri: ResolveInfo, val intent: Intent)

class MainActivity : Activity() {
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val KEEP = listOf("settings", "vending", "youtube", "chrome", "documentsui", "files",
        "browser", "netflix", "hotstar", "primevideo", "kodi", "vlc")

    private lateinit var sp: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var bg: ImageView
    private lateinit var scrim: View
    private var statusTv: TextView? = null
    private var query = ""
    private var everything = listOf<AppItem>()
    private var vis = listOf<AppItem>()
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { updateStatus(); h.postDelayed(this, 15000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sp = getSharedPreferences("ll", 0)
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        FileServer.start(this)

        val frame = FrameLayout(this)
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        scrim = View(this)
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(24), dp(40), dp(32))
            clipChildren = false; clipToPadding = false
        }
        sv.addView(content)
        frame.addView(bg, FrameLayout.LayoutParams(MP, MP))
        frame.addView(scrim, FrameLayout.LayoutParams(MP, MP))
        frame.addView(sv, FrameLayout.LayoutParams(MP, MP))
        setContentView(frame)
        loadWallpaper()
    }

    override fun onResume() {
        super.onResume()
        render()
        h.removeCallbacks(tick); h.postDelayed(tick, 15000)
    }

    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    override fun onRequestPermissionsResult(code: Int, ps: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, ps, res)
        loadWallpaper()
    }

    override fun onBackPressed() { /* stay on home */ }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun list(k: String) = sp.getString(k, "")!!.split(",").filter { it.isNotEmpty() }
    private fun save(k: String, l: List<String>) { sp.edit().putString(k, l.joinToString(",")).apply() }
    private fun dlg(t: String) = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t)

    private fun go(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("Not available on this TV") }
    }

    private fun ip(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    } catch (e: Exception) { null }

    private fun freeRam(): Long {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.availMem shr 20
    }

    private fun updateStatus() {
        val free = StatFs(Environment.getDataDirectory().path).availableBytes / 1073741824.0
        statusTv?.text = "Wi-Fi: ${ip() ?: "not connected"}   |   RAM free: ${freeRam()} MB   |   Storage free: ${"%.1f".format(free)} GB"
    }

    private fun loadWallpaper() {
        var bmp: Bitmap? = null
        try {
            val dirs = listOf(FileServer.targetDir(),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FromPhone"))
            val f = dirs.flatMap { d -> listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png").map { File(d, it) } }
                .firstOrNull { it.exists() }
            if (f != null) {
                val o = BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = Bitmap.Config.RGB_565 }
                bmp = BitmapFactory.decodeFile(f.path, o)
            }
        } catch (e: Exception) { }
        if (bmp != null) {
            bg.setImageBitmap(bmp); scrim.setBackgroundColor(0x99000000.toInt())
        } else {
            bg.setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0B1220.toInt(), 0xFF14305A.toInt(), 0xFF0B1220.toInt())))
            scrim.setBackgroundColor(0)
        }
    }

    // ---------- apps ----------
    private fun loadApps() {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (r in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val pkg = r.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                out.add(AppItem(pkg, r.loadLabel(pm).toString(), r,
                    Intent(Intent.ACTION_MAIN).setClassName(pkg, r.activityInfo.name)))
            }
        }
        out.sortBy { it.label.lowercase() }
        everything = out
        val hid = list("hid")
        val clean = sp.getBoolean("clean", false)
        vis = out.filter { it.pkg !in hid && !(clean && isBloat(it)) }
    }

    private fun isBloat(a: AppItem): Boolean {
        val f = a.ri.activityInfo.applicationInfo.flags
        val sys = (f and ApplicationInfo.FLAG_SYSTEM) != 0 && (f and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        return sys && KEEP.none { a.pkg.contains(it) }
    }

    private fun open(a: AppItem) {
        val r = list("rec").toMutableList()
        r.remove(a.pkg); r.add(0, a.pkg)
        save("rec", r.take(8))
        go(a.intent)
    }

    // ---------- UI ----------
    private fun render(focusKey: String? = null) {
        content.removeAllViews()
        content.addView(header())
        loadApps()
        val byPkg = vis.associateBy { it.pkg }
        content.addView(title("Quick access"))
        content.addView(row(tiles()))
        val favs = list("fav").mapNotNull { byPkg[it] }
        if (favs.isNotEmpty()) {
            content.addView(title("Favorites"))
            content.addView(row(favs.map { appCard(it, "f") }))
        }
        val rec = list("rec").mapNotNull { byPkg[it] }
        if (rec.isNotEmpty()) {
            content.addView(title("Recent"))
            content.addView(row(rec.map { appCard(it, "r") }))
        }
        val shown = if (query.isEmpty()) vis else vis.filter { it.label.contains(query, true) }
        content.addView(title(if (query.isEmpty()) "All apps (${shown.size})" else "Search: \"$query\" (${shown.size})"))
        val views = ArrayList<View>()
        if (query.isNotEmpty()) views.add(tile("Clear search", R.drawable.t_search) { query = ""; render() })
        shown.forEach { views.add(appCard(it, "a")) }
        content.addView(row(views))
        content.post { content.findViewWithTag<View>(focusKey ?: "t:Settings")?.requestFocus() }
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), dp(12))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"; textSize = 52f
            setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        left.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM yyyy"; format24Hour = "EEEE, d MMMM yyyy"; textSize = 18f
            setTextColor(0xFF9FB3C8.toInt())
        })
        statusTv = TextView(this).apply { textSize = 15f; setTextColor(0xFF9FB3C8.toInt()); gravity = Gravity.END }
        row.addView(left, LinearLayout.LayoutParams(0, WC, 1f))
        row.addView(statusTv, LinearLayout.LayoutParams(WC, WC))
        updateStatus()
        return row
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFE6EDF5.toInt())
        setPadding(dp(8), dp(18), 0, dp(4))
    }

    private fun row(views: List<View>): View {
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; clipChildren = false
            setPadding(0, dp(8), 0, dp(8))
        }
        val ll = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        views.forEach { ll.addView(it) }
        hs.addView(ll)
        return hs
    }

    private fun shape(focus: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(if (focus) 0x55FFFFFF else 0x26FFFFFF)
        if (focus) setStroke(dp(3), 0xFF4FA3FF.toInt())
    }

    private fun card(key: String, label: String, img: Drawable?, fill: Boolean,
                     click: () -> Unit, onLong: (() -> Unit)? = null): View {
        val w = dp(168)
        val frame = FrameLayout(this).apply {
            background = shape(false)
            outlineProvider = ViewOutlineProvider.BACKGROUND; clipToOutline = true
        }
        frame.addView(ImageView(this).apply {
            setImageDrawable(img)
            scaleType = if (fill) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.CENTER_INSIDE
            if (!fill) setPadding(dp(34), dp(14), dp(34), dp(14))
        }, FrameLayout.LayoutParams(MP, MP))
        val tv = TextView(this).apply {
            text = label; textSize = 14f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER; setTextColor(0xFFB8C4D2.toInt()); setPadding(0, dp(6), 0, 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = key; isFocusable = true; isClickable = true
            layoutParams = LinearLayout.LayoutParams(w, WC).apply { setMargins(dp(8), 0, dp(8), 0) }
            addView(frame, LinearLayout.LayoutParams(w, dp(94)))
            addView(tv, LinearLayout.LayoutParams(w, WC))
            setOnClickListener { click() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
            setOnFocusChangeListener { _, f ->
                frame.background = shape(f)
                tv.setTextColor(if (f) Color.WHITE else 0xFFB8C4D2.toInt())
                animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(120).start()
            }
        }
    }

    private fun appCard(a: AppItem, prefix: String): View {
        val pm = packageManager
        val ai = a.ri.activityInfo
        val banner: Drawable? = try { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) } catch (e: Exception) { null }
        return card("$prefix:${a.pkg}", a.label, banner ?: a.ri.loadIcon(pm), banner != null,
            { open(a) }, { menu(a, prefix) })
    }

    private fun tile(name: String, res: Int, f: () -> Unit) = card("t:$name", name, getDrawable(res), false, f)

    private fun tiles(): List<View> = listOf(
        tile("Settings", R.drawable.t_settings) { go(Intent(Settings.ACTION_SETTINGS)) },
        tile("Wi-Fi", R.drawable.t_wifi) { go(Intent(Settings.ACTION_WIFI_SETTINGS)) },
        tile("USB Files", R.drawable.t_usb) { files() },
        tile("Send Files", R.drawable.t_send) { sendDialog() },
        tile("Clear RAM", R.drawable.t_ram) { clearRam() },
        tile("Search", R.drawable.t_search) { searchDialog() },
        tile("Options", R.drawable.t_options) { options() }
    )

    // ---------- actions ----------
    private fun files() {
        val a = everything.firstOrNull { it.label.contains("file", true) || it.label.contains("explorer", true) }
        if (a != null) { open(a); return }
        val i = packageManager.getLaunchIntentForPackage("com.android.documentsui")
        if (i != null) go(i) else toast("No file manager found. Install one (for example X-plore) from a pendrive.")
    }

    private fun clearRam() {
        val before = freeRam()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName != packageName) am.killBackgroundProcesses(ai.packageName)
        }
        h.postDelayed({ updateStatus(); toast("RAM free: ${freeRam()} MB (was $before MB)") }, 900)
    }

    private fun qr(text: String): Bitmap {
        val n = 480
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, n, n)
        val px = IntArray(n * n) { if (m.get(it % n, it / n)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.RGB_565)
    }

    private fun sendDialog() {
        FileServer.start(this)
        val ip = ip()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(12), dp(24), dp(4))
        }
        val msg = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        if (ip != null && FileServer.port > 0) {
            val url = "http://$ip:${FileServer.port}"
            col.addView(ImageView(this).apply { setImageBitmap(qr(url)) }, LinearLayout.LayoutParams(dp(220), dp(220)))
            msg.text = "Scan with your phone camera, or open\n$url\n(phone and TV on the same Wi-Fi)\n\nSaved to: ${FileServer.targetDir().absolutePath}"
        } else msg.text = if (ip == null) "Connect the TV to Wi-Fi first." else "File server could not start. Restart the TV and try again."
        col.addView(msg)
        dlg("Send files from phone").setView(col).setPositiveButton("Close", null).show()
    }

    private fun searchDialog() {
        val et = EditText(this).apply { setSingleLine(); hint = "App name"; setText(query) }
        dlg("Search apps").setView(et)
            .setPositiveButton("Search") { _, _ -> query = et.text.toString().trim(); render() }
            .setNegativeButton("Clear") { _, _ -> query = ""; render() }.show()
    }

    private fun options() {
        fun on(k: String) = sp.getBoolean(k, false)
        fun st(k: String) = if (on(k)) "ON" else "OFF"
        val items = arrayOf(
            "Clean list (hide most system apps): ${st("clean")}",
            "Start at boot: ${st("boot")}",
            "Unhide apps (${list("hid").size} hidden)",
            "Reload wallpaper (send wallpaper.jpg with Send Files)")
        dlg("Options").setItems(items) { _, i ->
            when (i) {
                0 -> { sp.edit().putBoolean("clean", !on("clean")).apply(); render(); options() }
                1 -> { sp.edit().putBoolean("boot", !on("boot")).apply(); options() }
                2 -> unhide()
                3 -> { loadWallpaper(); toast("Wallpaper reloaded") }
            }
        }.setPositiveButton("Close", null).show()
    }

    private fun unhide() {
        val hid = list("hid")
        if (hid.isEmpty()) { toast("No hidden apps"); return }
        val labels = hid.map { p -> everything.firstOrNull { it.pkg == p }?.label ?: p }
        dlg("Tap an app to unhide").setItems(labels.toTypedArray()) { _, i ->
            save("hid", hid.filter { it != hid[i] }); render()
        }.show()
    }

    private fun menu(a: AppItem, prefix: String) {
        val fav = list("fav")
        val isFav = a.pkg in fav
        val names = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        fun add(t: String, f: () -> Unit) { names.add(t); acts.add(f) }
        add("Open") { open(a) }
        add(if (isFav) "Remove from favorites" else "Add to favorites") {
            save("fav", if (isFav) fav.filter { it != a.pkg } else fav + a.pkg); render()
        }
        if (isFav) {
            add("Move left") { moveFav(a.pkg, -1) }
            add("Move right") { moveFav(a.pkg, 1) }
        }
        add("Hide from list") { save("hid", list("hid") + a.pkg); render() }
        add("App info") {
            go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
        }
        add("Uninstall") { go(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))) }
        dlg(a.label).setItems(names.toTypedArray()) { _, i -> acts[i]() }.show()
    }

    private fun moveFav(pkg: String, d: Int) {
        val l = list("fav").toMutableList()
        val i = l.indexOf(pkg)
        val j = i + d
        if (i >= 0 && j in l.indices) { l.removeAt(i); l.add(j, pkg); save("fav", l) }
        render("f:$pkg")
    }
}
