package com.pixorik.notifalarm

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/**
 * Rule banane/edit karne ka screen:
 *  1) "When notification is from" - app picker (search + grid)
 *  2) "When I get a notification from <App> that contains <anything> then alarm..." - sentence builder
 */
class RuleEditorActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ctx: Context get() = this

    private var ruleId: String? = null
    private var pkg: String? = null
    private var keyword = ""
    private var enabled = true

    private lateinit var root: FrameLayout
    private lateinit var apps: List<AppCatalog.AppItem>
    private lateinit var chatSet: Set<String>
    private var chatOnly = true
    private var query = ""
    private var picked: String? = null
    private var shown: List<AppCatalog.AppItem> = emptyList()
    private var gridAdapter: BaseAdapter? = null
    private var pickBtn: TextView? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BAR

        apps = AppCatalog.launchable(this)
        chatSet = AppCatalog.chatPkgs(this, apps)

        ruleId = intent.getStringExtra("rule_id")
        val existing = Prefs.rules(this).firstOrNull { it.id == ruleId }
        if (existing != null) {
            pkg = existing.pkg
            keyword = existing.keyword
            enabled = existing.enabled
        }

        root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        setContentView(root)

        if (pkg == null) showPicker() else showBuilder()
    }

    // ---------------------------------------------------------------- picker

    private fun showPicker() {
        root.removeAllViews()
        picked = pkg

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(28), dp(20), dp(16))

        val title = SpannableString("When notification is from")
        val s = title.indexOf("is from")
        title.setSpan(ForegroundColorSpan(Ui.YELLOW), s, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.setSpan(UnderlineSpan(), s, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        col.addView(Ui.tv(this, title, 28f, Color.WHITE, true))

        val search = EditText(this)
        search.hint = "Search…"
        search.setHintTextColor(Ui.MUTED)
        search.setTextColor(Color.WHITE)
        search.isSingleLine = true
        search.background = Ui.round(Ui.CARD, dp(20))
        search.setPadding(dp(20), dp(14), dp(20), dp(14))
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                query = e?.toString().orEmpty().trim()
                refreshGrid()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val sp = LinearLayout.LayoutParams(MATCH, WRAP)
        sp.topMargin = dp(20)
        col.addView(search, sp)

        val chip = Ui.tv(this, "", 14f, Ui.YELLOW, true)
        chip.setPadding(0, dp(12), 0, dp(12))
        fun chipText() {
            chip.text = if (chatOnly) "☑ Sirf chat apps  (tap: sab apps dikhayein)"
            else "☐ Sab apps  (tap: sirf chat apps)"
        }
        chipText()
        chip.setOnClickListener {
            chatOnly = !chatOnly
            chipText()
            refreshGrid()
        }
        col.addView(chip)

        val grid = GridView(this)
        grid.numColumns = 2
        grid.horizontalSpacing = dp(12)
        grid.verticalSpacing = dp(12)
        grid.selector = ColorDrawable(Color.TRANSPARENT)
        grid.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) {
                picked = shown[pos].pkg
                gridAdapter?.notifyDataSetChanged()
                updatePickBtn()
            }
        }
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = shown.size
            override fun getItem(position: Int): Any = shown[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
                appTile(shown[position])
        }
        gridAdapter = adapter
        grid.adapter = adapter
        col.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val btn = Ui.pillButton(this, "Pick an app") {
            val p = picked
            if (p != null) {
                pkg = p
                showBuilder()
            }
        }
        pickBtn = btn
        val bp = LinearLayout.LayoutParams(MATCH, WRAP)
        bp.topMargin = dp(12)
        col.addView(btn, bp)

        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        refreshGrid()
        updatePickBtn()
    }

    private fun refreshGrid() {
        shown = apps
            .filter { !chatOnly || it.pkg in chatSet || it.pkg == picked }
            .filter {
                query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                    it.pkg.contains(query, ignoreCase = true)
            }
            .sortedWith(compareBy({ it.pkg !in chatSet }, { it.label.lowercase() }))
        gridAdapter?.notifyDataSetChanged()
    }

    private fun updatePickBtn() {
        val b = pickBtn ?: return
        val p = picked
        if (p == null) {
            b.text = "Pick an app"
            b.alpha = 0.5f
        } else {
            b.text = "Pick " + AppCatalog.label(this, p)
            b.alpha = 1f
        }
    }

    private fun appTile(a: AppCatalog.AppItem): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.background = Ui.round(if (a.pkg == picked) Ui.BLUE else Ui.CARD, dp(22))
        tile.setPadding(dp(18), dp(18), dp(18), dp(18))
        val icon = Ui.appIcon(this, a.pkg)
        if (icon != null) tile.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        val label = Ui.tv(this, a.label, 16f, Color.WHITE, true)
        label.maxLines = 2
        label.setPadding(0, dp(12), 0, 0)
        tile.addView(label)
        return tile
    }

    // --------------------------------------------------------------- builder

    private fun showBuilder() {
        root.removeAllViews()
        val p = pkg
        if (p == null) {
            showPicker()
            return
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(48), dp(24), dp(24))

        val icon = Ui.appIcon(this, p)
        if (icon != null) {
            val ip = LinearLayout.LayoutParams(dp(56), dp(56))
            ip.bottomMargin = dp(20)
            col.addView(icon, ip)
        }

        val sb = SpannableStringBuilder()
        fun plain(t: String) {
            sb.append(t)
        }

        fun link(t: String, action: () -> Unit) {
            val st = sb.length
            sb.append(t)
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    action()
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Ui.YELLOW
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }, st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        plain("When I get a notification from ")
        link(AppCatalog.label(this, p)) { showPicker() }
        plain(" that contains ")
        link(if (keyword.isBlank()) "anything" else "\"$keyword\"") { askKeyword() }
        plain(" then alarm immediately with fire alarm sound and vibration")

        val sentence = Ui.tv(this, sb, 26f, Color.WHITE, true)
        sentence.movementMethod = LinkMovementMethod.getInstance()
        sentence.highlightColor = Color.TRANSPARENT
        sentence.setLineSpacing(0f, 1.25f)
        col.addView(sentence)

        val save = Ui.pillButton(this, "Save rule") { save() }
        val svp = LinearLayout.LayoutParams(MATCH, WRAP)
        svp.topMargin = dp(36)
        col.addView(save, svp)

        val cancel = Ui.tv(this, "Cancel", 15f, Ui.MUTED)
        cancel.gravity = Gravity.CENTER
        cancel.setPadding(0, dp(20), 0, dp(20))
        cancel.setOnClickListener { finish() }
        col.addView(cancel, LinearLayout.LayoutParams(MATCH, WRAP))

        val tip = Ui.tv(
            this,
            "Tip: app ya keyword par tap karke badal sakte hain. Naam/keyword comma se alag likh sakte hain " +
                "(Piyoo, Vineet). \"anything\" ka matlab us app ki har notification par alarm.",
            13f, Ui.MUTED
        )
        col.addView(tip)

        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun askKeyword() {
        val wrap = LinearLayout(this)
        wrap.setPadding(dp(20), dp(12), dp(20), 0)
        val input = EditText(this)
        input.setText(keyword)
        input.hint = "Piyoo, Vineet  (khaali = anything)"
        input.setSingleLine()
        wrap.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))

        AlertDialog.Builder(this)
            .setTitle("Notification me ye likha ho")
            .setView(wrap)
            .setPositiveButton("OK") { _, _ ->
                keyword = input.text.toString().trim()
                showBuilder()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save() {
        val p = pkg ?: return
        val id = ruleId ?: UUID.randomUUID().toString()
        Prefs.upsertRule(this, Prefs.Rule(id, p, keyword.trim(), enabled))
        finish()
    }
}
