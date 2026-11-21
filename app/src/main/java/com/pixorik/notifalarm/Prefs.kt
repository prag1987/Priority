package com.pixorik.notifalarm

import android.content.Context

object Prefs {
    // Default watched apps (list me se baad me badal sakte hain)
    private val DEFAULTS = setOf(
        "com.fiverr.fiverr",                 // Fiverr
        "com.upwork.android.apps.main",      // Upwork
        "com.Slack",                         // Slack
        "com.microsoft.teams",               // Microsoft Teams
        "com.whatsapp",                      // WhatsApp
        "org.telegram.messenger"             // Telegram
    )

    private fun sp(c: Context) = c.getSharedPreferences("notif_alarm", Context.MODE_PRIVATE)

    fun watched(c: Context): Set<String> =
        sp(c).getStringSet("watched", DEFAULTS) ?: DEFAULTS

    fun setWatched(c: Context, s: Set<String>) =
        sp(c).edit().putStringSet("watched", HashSet(s)).apply()

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    /** Source notification hat jaye (app me padh liya) to alarm band. */
    fun autoStop(c: Context) = sp(c).getBoolean("auto_stop", true)
    fun setAutoStop(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_stop", v).apply()

    // ---- Rules: (app, keyword). Jis app ka koi rule hai, uski sirf matching notifications par alarm. ----
    data class Rule(val pkg: String, val keyword: String)

    fun rules(c: Context): List<Rule> =
        (sp(c).getStringSet("rules", emptySet()) ?: emptySet()).mapNotNull {
            val i = it.indexOf('|')
            if (i <= 0) null else Rule(it.substring(0, i), it.substring(i + 1))
        }

    fun addRule(c: Context, r: Rule) {
        val s = HashSet(sp(c).getStringSet("rules", emptySet()) ?: emptySet())
        s.add("${r.pkg}|${r.keyword}")
        sp(c).edit().putStringSet("rules", s).apply()
    }

    fun removeRule(c: Context, r: Rule) {
        val s = HashSet(sp(c).getStringSet("rules", emptySet()) ?: emptySet())
        s.remove("${r.pkg}|${r.keyword}")
        sp(c).edit().putStringSet("rules", s).apply()
    }

    /** Title (sender/group) + text me keyword dhundta hai, case-insensitive. */
    fun shouldAlert(c: Context, pkg: String, title: String, text: String): Boolean {
        val mine = rules(c).filter { it.pkg == pkg }
        if (mine.isEmpty()) return true
        val hay = "$title\n$text"
        return mine.any { hay.contains(it.keyword, ignoreCase = true) }
    }

    fun maxVolume(c: Context) = sp(c).getBoolean("max_volume", true)
    fun setMaxVolume(c: Context, v: Boolean) = sp(c).edit().putBoolean("max_volume", v).apply()
}
