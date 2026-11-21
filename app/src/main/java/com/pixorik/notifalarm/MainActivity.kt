package com.pixorik.notifalarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Spinner
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var rulesBox: LinearLayout
    private var apps: List<Pair<String, String>> = emptyList()
    private val pkgs = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 16)
        }

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            panel.addView(this)
        }

        panel.addView(TextView(this).apply {
            text = "High Priority"
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply { setPadding(0, 8, 0, 16) }
        panel.addView(status)

        panel.addView(Switch(this).apply {
            text = "Alerts ON"
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setEnabled(this@MainActivity, v) }
        })
        panel.addView(CheckBox(this).apply {
            text = "App me notification hat jaye (padh li) to alarm band"
            isChecked = Prefs.autoStop(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setAutoStop(this@MainActivity, v) }
        })
        panel.addView(CheckBox(this).apply {
            text = "Alarm volume full kar do"
            isChecked = Prefs.maxVolume(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setMaxVolume(this@MainActivity, v) }
        })

        btn("1. Notification access allow karein") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("2. Notification permission allow karein") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        btn("3. Battery restriction hatayein (zaroori)") {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        if (Build.VERSION.SDK_INT >= 34) {
            btn("4. Lock screen full-screen alert allow karein") {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        btn("Test alarm") {
            AlarmController.trigger(
                this, "test",
                AlarmController.Info(packageName, "Test", "Test alarm", "Ye ek test alert hai", null)
            )
        }
        btn("Alarm band karein") { AlarmController.stop(this) }

        panel.addView(TextView(this).apply {
            text = "\nRules (optional): kisi app me sirf khaas naam/keyword par alarm"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        rulesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(rulesBox)
        btn("+ Rule add karein (jaise WhatsApp → Piyoo)") { showAddRule() }

        panel.addView(TextView(this).apply {
            text = "\nJin apps ki notification par alarm bajana hai unhe tick karein:"
            textSize = 16f
        })

        // Installed launchable apps
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val watched = Prefs.watched(this)
        apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.first !in watched }, { it.second.lowercase() }))
        apps.forEach { pkgs.add(it.first) }

        list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            addHeaderView(panel, null, false)
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_list_item_multiple_choice,
                apps.map { "${it.second}\n${it.first}" }
            )
            pkgs.forEachIndexed { i, p -> setItemChecked(i + headerViewsCount, p in watched) }
            setOnItemClickListener { _, _, _, _ -> saveSelection() }
        }
        setContentView(list)
        refreshRules()
    }

    private fun appLabel(pkg: String) = apps.firstOrNull { it.first == pkg }?.second ?: pkg

    private fun refreshRules() {
        rulesBox.removeAllViews()
        val rules = Prefs.rules(this)
        if (rules.isEmpty()) {
            rulesBox.addView(TextView(this).apply {
                text = "Koi rule nahi - tick kiye hue apps ki har notification par alarm bajega."
            })
        }
        rules.forEach { r ->
            rulesBox.addView(TextView(this).apply {
                text = "• ${appLabel(r.pkg)} → \"${r.keyword}\"   (hatane ke liye tap karein)"
                textSize = 16f
                setPadding(0, 12, 0, 12)
                setOnClickListener {
                    Prefs.removeRule(this@MainActivity, r)
                    refreshRules()
                }
            })
        }
    }

    private fun showAddRule() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, apps.map { it.second }
            )
            // WhatsApp pehle se select
            val w = apps.indexOfFirst { it.first == "com.whatsapp" }
            if (w >= 0) setSelection(w)
        }
        val input = EditText(this).apply { hint = "Naam ya keyword (jaise Piyoo)" }
        box.addView(spinner)
        box.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Naya rule")
            .setMessage("Is app me sender/group/message me ye likha ho to hi alarm bajega.")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val kw = input.text.toString().trim()
                val sel = spinner.selectedItemPosition
                if (kw.isNotEmpty() && sel in apps.indices) {
                    val pkg = apps[sel].first
                    Prefs.addRule(this, Prefs.Rule(pkg, kw))
                    // App tick bhi kar do, warna rule kaam nahi karega
                    Prefs.setWatched(this, Prefs.watched(this) + pkg)
                    val idx = pkgs.indexOf(pkg)
                    if (idx >= 0) list.setItemChecked(idx + list.headerViewsCount, true)
                    refreshRules()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSelection() {
        val off = list.headerViewsCount
        val sel = pkgs.filterIndexed { i, _ -> list.isItemChecked(i + off) }.toSet()
        Prefs.setWatched(this, sel)
    }

    override fun onResume() {
        super.onResume()
        val access = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        status.text = "Notification access: ${if (access) "✅" else "❌"}\n" +
            "Notification permission: ${if (notif) "✅" else "❌"}\n" +
            "Battery unrestricted: ${if (battery) "✅" else "❌"}"
        status.visibility = View.VISIBLE
    }
}
