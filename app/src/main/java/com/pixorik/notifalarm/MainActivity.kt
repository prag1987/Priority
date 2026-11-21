package com.pixorik.notifalarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.media.RingtoneManager
import android.provider.OpenableColumns
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import java.util.Date

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ctx: Context get() = this

    private lateinit var root: LinearLayout
    private var content: FrameLayout? = null
    private var barView: LinearLayout? = null
    private var tab = 0

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BAR
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun hasAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun notifGranted() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    // ------------------------------------------------------------------ root

    private fun render() {
        root.removeAllViews()
        if (!hasAccess()) {
            root.addView(welcome(), LinearLayout.LayoutParams(MATCH, MATCH))
            return
        }
        val c = FrameLayout(this)
        content = c
        root.addView(c, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val b = buildBar()
        barView = b
        root.addView(b, LinearLayout.LayoutParams(MATCH, WRAP))
        showTab()
    }

    private fun showTab() {
        val c = content ?: return
        c.removeAllViews()
        c.addView(
            when (tab) {
                0 -> rulesView()
                1 -> historyView()
                else -> settingsView()
            }
        )
        updateBar()
    }

    private fun buildBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.BAR)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val items = listOf("🔔" to "Rules", "🕘" to "History", "⚙️" to "Settings")
        items.forEachIndexed { i, pair ->
            val t = Ui.tv(this, pair.first + "\n" + pair.second, 13f, Color.WHITE, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(8), 0, dp(8))
            t.setOnClickListener {
                tab = i
                showTab()
            }
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.setMargins(dp(4), 0, dp(4), 0)
            bar.addView(t, p)
        }
        return bar
    }

    private fun updateBar() {
        val b = barView ?: return
        for (i in 0 until b.childCount) {
            val t = b.getChildAt(i) as TextView
            if (i == tab) {
                t.background = Ui.round(Ui.YELLOW, dp(24))
                t.setTextColor(Ui.DARK)
            } else {
                t.background = null
                t.setTextColor(Color.WHITE)
            }
        }
    }

    // --------------------------------------------------------------- welcome

    private fun welcome(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        col.addView(Ui.tv(this, "Welcome to High Priority", 28f, Color.WHITE, true))
        val intro = Ui.tv(
            this,
            "Chat apps ki zaroori notifications miss na hon: rules banayein, aur match hone par " +
                "fire-alarm tab tak bajta rahega jab tak aap app na kholein. Aapka data sirf phone me " +
                "rehta hai, app ke paas internet permission hi nahi hai.",
            16f, Ui.MUTED
        )
        intro.setPadding(0, dp(16), 0, dp(32))
        col.addView(intro)

        col.addView(
            stepCard(1, "Allow notifications", "Alarm alert dikhane ke liye.", notifGranted()) {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }, lp()
        )
        col.addView(
            stepCard(2, "Allow to run in background", "Battery restriction hatayein taaki app band na ho.", batteryOk()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }, lp(12)
        )
        col.addView(
            stepCard(
                3, "Enable High Priority",
                "Notification access allow karein. Switch grey ho to: Settings → Apps → High Priority → ⋮ → Allow restricted settings.",
                false
            ) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }, lp(12)
        )
        return ScrollView(this).apply { addView(col) }
    }

    private fun stepCard(n: Int, title: String, desc: String, done: Boolean, onClick: () -> Unit): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = Ui.round(Ui.YELLOW, dp(24))
        card.setPadding(dp(20), dp(20), dp(20), dp(20))

        val badge = Ui.tv(this, if (done) "✓" else n.toString(), 18f, Ui.DARK, true)
        badge.gravity = Gravity.CENTER
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setStroke(dp(2), Ui.DARK)
        badge.background = ring
        card.addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.setPadding(dp(16), 0, 0, 0)
        texts.addView(Ui.tv(this, title, 17f, Ui.DARK, true))
        texts.addView(Ui.tv(this, desc, 14f, Ui.DARK))
        card.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        card.setOnClickListener { onClick() }
        return card
    }

    // ----------------------------------------------------------------- rules

    private fun rulesView(): View {
        val frame = FrameLayout(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(120))
        }
        val title = Ui.tv(this, "Notification rules", 26f, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        col.addView(title)
        val sub = Ui.tv(this, "Jab koi notification inme se kisi rule se match kare, tab alarm bajega.", 14f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(8), 0, dp(24))
        col.addView(sub)

        val rules = Prefs.rules(this)
        if (rules.isEmpty()) {
            val empty = Ui.tv(
                this,
                "Abhi koi rule nahi.\n\"Create rule\" dabakar pehla rule banayein (jaise WhatsApp ya Fiverr).",
                16f, Ui.MUTED
            )
            empty.gravity = Gravity.CENTER
            col.addView(empty)
        }
        rules.forEach { col.addView(ruleCard(it), lp(0).apply { bottomMargin = dp(16) }) }

        val scroll = ScrollView(this)
        scroll.addView(col)
        frame.addView(scroll)

        val fab = Ui.pillButton(this, "＋  Create rule") {
            startActivity(Intent(this, RuleEditorActivity::class.java))
        }
        val fp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        fp.setMargins(0, 0, dp(20), dp(20))
        frame.addView(fab, fp)
        return frame
    }

    private fun editRule(id: String?) {
        val i = Intent(this, RuleEditorActivity::class.java)
        if (id != null) i.putExtra("rule_id", id)
        startActivity(i)
    }

    private fun ruleCard(r: Prefs.Rule): View {
        val label = AppCatalog.label(this, r.pkg)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.round(if (r.enabled) Ui.GREEN else Ui.GREY, dp(30))
        card.setPadding(dp(8), dp(4), dp(8), dp(8))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val dots = Ui.tv(this, "⋮", 26f, Ui.DARK, true)
        dots.setPadding(dp(14), dp(4), dp(14), dp(4))
        dots.setOnClickListener { v ->
            val pm = PopupMenu(this, v)
            pm.menu.add(0, 1, 0, "Edit")
            pm.menu.add(0, 2, 1, "Delete")
            pm.setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    editRule(r.id)
                } else {
                    Prefs.deleteRule(this, r.id)
                    showTab()
                }
                true
            }
            pm.show()
        }
        top.addView(dots)
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val state = Ui.tv(this, if (r.enabled) "Active  " else "Off  ", 15f, Ui.DARK, true)
        top.addView(state)
        val sw = Switch(this)
        sw.isChecked = r.enabled
        Ui.styleSwitch(this, sw, Color.WHITE, Ui.DARK, Color.parseColor("#D0D0D0"), Color.parseColor("#55565B"))
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.upsertRule(this, r.copy(enabled = on))
            showTab()
        }
        top.addView(sw)
        card.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.background = Ui.round(Color.parseColor("#0F1012"), dp(24))
        inner.setPadding(dp(20), dp(20), dp(20), dp(24))
        val icon = Ui.appIcon(this, r.pkg)
        if (icon != null) {
            val ip = LinearLayout.LayoutParams(dp(44), dp(44))
            ip.bottomMargin = dp(10)
            inner.addView(icon, ip)
        }
        val sentence = Ui.tv(this, Ui.ruleSentence(label, r.keyword), 20f, Color.WHITE, true)
        sentence.setLineSpacing(0f, 1.15f)
        inner.addView(sentence)
        inner.setOnClickListener { editRule(r.id) }
        card.addView(inner, LinearLayout.LayoutParams(MATCH, WRAP))
        return card
    }

    // --------------------------------------------------------------- history

    private fun historyView(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(24))
        }
        val title = Ui.tv(this, "History", 26f, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        col.addView(title)
        val sub = Ui.tv(this, "Jin notifications par alarm baja (latest 50).", 14f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(8), 0, dp(16))
        col.addView(sub)

        val hits = Prefs.hits(this)
        if (hits.isEmpty()) {
            val empty = Ui.tv(this, "Abhi tak koi alarm nahi baja.", 16f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            col.addView(empty)
        } else {
            col.addView(
                Ui.pillButton(this, "Clear history", Ui.GREY, Color.WHITE) {
                    Prefs.clearHits(this)
                    showTab()
                }, lp(0).apply { bottomMargin = dp(16) }
            )
            hits.forEach { h ->
                val card = LinearLayout(this)
                card.orientation = LinearLayout.VERTICAL
                card.background = Ui.round(Ui.CARD, dp(20))
                card.setPadding(dp(18), dp(16), dp(18), dp(16))
                card.addView(Ui.tv(this, h.appName, 17f, Ui.YELLOW, true))
                if (h.title.isNotBlank()) card.addView(Ui.tv(this, h.title, 16f, Color.WHITE, true))
                if (h.text.isNotBlank()) card.addView(Ui.tv(this, h.text, 15f, Color.WHITE))
                card.addView(
                    Ui.tv(this, DateFormat.format("dd MMM, hh:mm a", Date(h.time)), 12f, Ui.MUTED)
                        .apply { setPadding(0, dp(6), 0, 0) }
                )
                col.addView(card, lp(0).apply { bottomMargin = dp(12) })
            }
        }
        return ScrollView(this).apply { addView(col) }
    }

    // -------------------------------------------------------------- settings

    private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.round(Ui.CARD, dp(20))
        row.setPadding(dp(18), dp(14), dp(18), dp(14))
        row.addView(Ui.tv(this, label, 16f), LinearLayout.LayoutParams(0, WRAP, 1f))
        val sw = Switch(this)
        sw.isChecked = checked
        Ui.styleSwitch(this, sw, Ui.DARK, Ui.YELLOW, Color.parseColor("#B5B5B5"), Ui.GREY)
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        row.addView(sw)
        return row
    }

    private fun actionRow(label: String, ok: Boolean, button: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.round(Ui.CARD, dp(20))
        row.setPadding(dp(18), dp(14), dp(18), dp(14))
        row.addView(
            Ui.tv(this, (if (ok) "✅ " else "❌ ") + label, 15f),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        val b = Ui.tv(this, button, 14f, Ui.YELLOW, true)
        b.setPadding(dp(12), dp(8), dp(4), dp(8))
        b.setOnClickListener { onClick() }
        row.addView(b)
        return row
    }

    private fun settingsView(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(24))
        }
        val title = Ui.tv(this, "Settings", 26f, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        col.addView(title)

        col.addView(toggleRow("Alerts ON", Prefs.enabled(this)) { Prefs.setEnabled(this, it) }, lp(24))
        col.addView(
            toggleRow("Notification hat jaye (padh li) to alarm band", Prefs.autoStop(this)) {
                Prefs.setAutoStop(this, it)
            }, lp(10)
        )
        col.addView(
            toggleRow("Alarm volume full kar do", Prefs.maxVolume(this)) { Prefs.setMaxVolume(this, it) },
            lp(10)
        )

        col.addView(soundRow(), lp(10))

        val sec = Ui.tv(this, "Permissions", 14f, Ui.MUTED, true)
        sec.setPadding(0, dp(24), 0, dp(8))
        col.addView(sec)
        col.addView(
            actionRow("Notification access", hasAccess(), "Open") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }, lp()
        )
        col.addView(
            actionRow("Notification permission", notifGranted(), "Allow") {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }, lp(10)
        )
        col.addView(
            actionRow("Battery unrestricted", batteryOk(), "Allow") {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }, lp(10)
        )
        if (Build.VERSION.SDK_INT >= 34) {
            col.addView(
                actionRow("Lock screen full-screen alert", true, "Open") {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                    )
                }, lp(10)
            )
        }

        col.addView(
            Ui.pillButton(this, "Test alarm") {
                AlarmController.trigger(
                    this, "test",
                    AlarmController.Info(packageName, "Test", "Test alarm", "Ye ek test alert hai", null)
                )
            }, lp(28)
        )
        col.addView(
            Ui.pillButton(this, "Alarm band karein", Ui.GREY, Color.WHITE) { AlarmController.stop(this) },
            lp(12)
        )
        return ScrollView(this).apply { addView(col) }
    }

    // ----------------------------------------------------------- alarm sound

    private val REQ_RINGTONE = 101
    private val REQ_FILE = 102

    private fun soundRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.round(Ui.CARD, dp(20))
        row.setPadding(dp(18), dp(14), dp(18), dp(14))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(Ui.tv(this, "🔊 Alarm sound", 16f, Color.WHITE, true))
        texts.addView(Ui.tv(this, Prefs.soundName(this), 13f, Ui.MUTED))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        val change = Ui.tv(this, "Change", 14f, Ui.YELLOW, true)
        change.setPadding(dp(12), dp(8), dp(4), dp(8))
        row.addView(change)
        row.setOnClickListener { showSoundDialog() }
        return row
    }

    private fun showSoundDialog() {
        AlertDialog.Builder(this)
            .setTitle("Alarm sound")
            .setItems(
                arrayOf("Fire siren (default)", "System sounds (alarm / ringtone)…", "Phone se audio file…")
            ) { _, which ->
                when (which) {
                    0 -> {
                        Prefs.setSound(this, null, null)
                        showTab()
                    }
                    1 -> pickSystemSound()
                    else -> pickFile()
                }
            }
            .show()
    }

    private fun pickSystemSound() {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound chunein")
        val cur = Prefs.soundUri(this)
        if (cur != null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur))
        startActivityForResult(i, REQ_RINGTONE)
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "audio/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, REQ_FILE)
    }

    private fun displayName(uri: Uri): String {
        val n: String? = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return n ?: "Custom sound"
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == REQ_RINGTONE) {
            val uri: Uri? = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val title: String? = try {
                    RingtoneManager.getRingtone(this, uri)?.getTitle(this)
                } catch (e: Exception) {
                    null
                }
                Prefs.setSound(this, uri.toString(), title ?: "System sound")
            }
        } else if (requestCode == REQ_FILE) {
            val uri = data.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            Prefs.setSound(this, uri.toString(), displayName(uri))
        }
    }
}
