package com.pixorik.notifalarm

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Switch
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView

/** Dark + yellow theme helpers. */
object Ui {
    val BG = Color.parseColor("#0A0B0D")
    val BAR = Color.parseColor("#14151A")
    val CARD = Color.parseColor("#1B1C20")
    val YELLOW = Color.parseColor("#FFF08A")
    val GREEN = Color.parseColor("#1DBF73") // Fiverr green
    val BLUE = Color.parseColor("#7B93FF")
    val GREY = Color.parseColor("#3A3B40")
    val MUTED = Color.parseColor("#8A8B90")
    val DARK = Color.parseColor("#1A1A1A")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun round(color: Int, radiusPx: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radiusPx.toFloat()
        return d
    }

    fun tv(c: Context, s: CharSequence, sp: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun pillButton(c: Context, label: String, fill: Int = YELLOW, textColor: Int = DARK, onClick: () -> Unit): TextView {
        val t = tv(c, label, 16f, textColor, true)
        t.gravity = Gravity.CENTER
        t.background = round(fill, dp(c, 30))
        t.setPadding(dp(c, 24), dp(c, 16), dp(c, 24), dp(c, 16))
        t.setOnClickListener { onClick() }
        return t
    }

    /** Switch ko apne colors me: ON/OFF dono state saaf dikhein. */
    fun styleSwitch(c: Context, sw: Switch, onThumb: Int, onTrack: Int, offThumb: Int, offTrack: Int) {
        fun shape(color: Int, w: Int, h: Int, r: Int): GradientDrawable {
            val d = GradientDrawable()
            d.setColor(color)
            d.cornerRadius = r.toFloat()
            d.setSize(w, h)
            return d
        }
        val on = intArrayOf(android.R.attr.state_checked)
        val off = intArrayOf()
        val track = StateListDrawable()
        track.addState(on, shape(onTrack, dp(c, 50), dp(c, 28), dp(c, 14)))
        track.addState(off, shape(offTrack, dp(c, 50), dp(c, 28), dp(c, 14)))
        val thumb = StateListDrawable()
        thumb.addState(on, InsetDrawable(shape(onThumb, dp(c, 20), dp(c, 20), dp(c, 10)), dp(c, 4)))
        thumb.addState(off, InsetDrawable(shape(offThumb, dp(c, 20), dp(c, 20), dp(c, 10)), dp(c, 4)))
        sw.trackDrawable = track
        sw.thumbDrawable = thumb
    }

    fun appIcon(c: Context, pkg: String): ImageView? = try {
        val v = ImageView(c)
        v.setImageDrawable(c.packageManager.getApplicationIcon(pkg))
        v
    } catch (e: Exception) {
        null
    }

    /** "When I get a notification from <App> [that contains "x"] then alarm ..." */
    fun ruleSentence(appLabel: String, keyword: String): CharSequence {
        val sb = SpannableStringBuilder("When I get a notification from ")
        fun hl(s: String) {
            val st = sb.length
            sb.append(s)
            sb.setSpan(ForegroundColorSpan(YELLOW), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        hl(appLabel)
        if (keyword.isNotBlank()) {
            sb.append(" that contains ")
            hl("\"$keyword\"")
        }
        sb.append(" then alarm immediately with alarm sound")
        return sb
    }
}

object AppCatalog {
    val KNOWN_CHAT = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.Slack", "com.microsoft.teams", "com.skype.raider", "com.facebook.orca",
        "com.instagram.android", "com.discord", "org.thoughtcrime.securesms",
        "com.google.android.apps.dynamite", "com.fiverr.fiverr", "com.upwork.android.apps.main",
        "com.linkedin.android", "us.zoom.videomeetings", "com.google.android.apps.tachyon",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.viber.voip",
        "jp.naver.line.android", "com.tencent.mm", "com.snapchat.android", "com.google.android.gm",
        "com.microsoft.office.outlook", "com.facebook.mlite", "com.facebook.katana"
    )

    data class AppItem(val pkg: String, val label: String)

    fun launchable(c: Context): List<AppItem> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != c.packageName }
            .distinctBy { it.pkg }
    }

    fun chatPkgs(c: Context, apps: List<AppItem>): Set<String> {
        val pm = c.packageManager
        return apps.map { it.pkg }.filter { p ->
            p in KNOWN_CHAT || try {
                pm.getApplicationInfo(p, 0).category == ApplicationInfo.CATEGORY_SOCIAL
            } catch (e: Exception) {
                false
            }
        }.toSet()
    }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
