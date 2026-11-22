package com.bhaskar.litelauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface

class App(val label: String, val icon: Drawable, val intent: Intent)

class MainActivity : Activity() {
    private val apps = ArrayList<App>()
    private lateinit var adapter: A
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)

        FileServer.start(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101418.toInt())
            setPadding(48, 32, 48, 16)
        }
        info = TextView(this).apply {
            setTextColor(0xFF9FB3C8.toInt()); textSize = 18f; setPadding(0, 0, 0, 24)
        }
        adapter = A()
        val grid = GridView(this).apply {
            numColumns = 6
            selector = ColorDrawable(0x55FFFFFF)
            setDrawSelectorOnTop(true)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, i, _ -> startActivity(apps[i].intent) }
        }
        root.addView(info)
        root.addView(grid)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        info.text = "Send files from phone: open http://${ip()}:8080 in phone browser (same Wi-Fi)\nSaves to: ${FileServer.targetDir().absolutePath}"
        loadApps()
        adapter.notifyDataSetChanged()
    }

    private fun loadApps() {
        apps.clear()
        val pm = packageManager
        val seen = HashSet<String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (r in pm.queryIntentActivities(q, 0)) {
                val pkg = r.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val i = Intent(Intent.ACTION_MAIN).setClassName(pkg, r.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                apps.add(App(r.loadLabel(pm).toString(), r.loadIcon(pm), i))
            }
        }
        apps.sortBy { it.label.lowercase() }
    }

    private fun ip(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress ?: "TV-IP"
    } catch (e: Exception) { "TV-IP" }

    override fun onBackPressed() { /* stay on home */ }

    inner class A : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(i: Int) = apps[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val a = apps[i]
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 24, 8, 24)
                addView(ImageView(context).apply {
                    setImageDrawable(a.icon)
                    layoutParams = LinearLayout.LayoutParams(96, 96)
                })
                addView(TextView(context).apply {
                    text = a.label; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
                    gravity = Gravity.CENTER; maxLines = 1; setPadding(0, 8, 0, 0)
                })
            }
        }
    }
}
