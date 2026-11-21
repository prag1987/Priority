package com.pixorik.notifalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {

    private fun sp(c: Context) = c.getSharedPreferences("notif_alarm", Context.MODE_PRIVATE)

    // ---- Global settings ----
    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    /** Source notification hat jaye (app me padh liya) to alarm band. */
    fun autoStop(c: Context) = sp(c).getBoolean("auto_stop", true)
    fun setAutoStop(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_stop", v).apply()

    fun maxVolume(c: Context) = sp(c).getBoolean("max_volume", true)
    fun setMaxVolume(c: Context, v: Boolean) = sp(c).edit().putBoolean("max_volume", v).apply()

    // ---- Alarm sound (null = built-in fire siren) ----
    fun soundUri(c: Context): String? = sp(c).getString("sound_uri", null)
    fun soundName(c: Context): String = sp(c).getString("sound_name", null) ?: "Fire siren (default)"
    fun setSound(c: Context, uri: String?, name: String?) {
        val e = sp(c).edit()
        if (uri == null) e.remove("sound_uri").remove("sound_name")
        else e.putString("sound_uri", uri).putString("sound_name", name ?: "Custom sound")
        e.apply()
    }

    // ---- Rules ----
    /** keyword khaali = us app ki har notification. Comma se kai naam: "Piyoo, Vineet". */
    data class Rule(val id: String, val pkg: String, val keyword: String, val enabled: Boolean)

    fun rules(c: Context): List<Rule> {
        val arr = try {
            JSONArray(sp(c).getString("rules_json", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Rule>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Rule(
                    o.optString("id"),
                    o.optString("pkg"),
                    o.optString("keyword", ""),
                    o.optBoolean("enabled", true)
                )
            )
        }
        return out
    }

    private fun saveRules(c: Context, list: List<Rule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("pkg", it.pkg)
                    .put("keyword", it.keyword)
                    .put("enabled", it.enabled)
            )
        }
        sp(c).edit().putString("rules_json", arr.toString()).apply()
    }

    fun upsertRule(c: Context, r: Rule) {
        val l = rules(c).toMutableList()
        val i = l.indexOfFirst { it.id == r.id }
        if (i >= 0) l[i] = r else l.add(r)
        saveRules(c, l)
    }

    fun deleteRule(c: Context, id: String) = saveRules(c, rules(c).filter { it.id != id })

    /** Title (sender/group) + text me keyword dhundta hai, case-insensitive. */
    fun shouldAlert(c: Context, pkg: String, title: String, text: String): Boolean {
        val hay = "$title\n$text"
        return rules(c).any { r ->
            r.enabled && r.pkg == pkg && (
                r.keyword.isBlank() ||
                    r.keyword.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        .any { hay.contains(it, ignoreCase = true) }
                )
        }
    }

    // ---- Recent apps (jinhone recently notification bheji) ----
    fun recent(c: Context): List<String> {
        val arr = try {
            JSONArray(sp(c).getString("recent_json", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }

    fun addRecent(c: Context, pkg: String) {
        val l = ArrayList<String>()
        l.add(pkg)
        l.addAll(recent(c).filter { it != pkg })
        val arr = JSONArray()
        l.take(15).forEach { arr.put(it) }
        sp(c).edit().putString("recent_json", arr.toString()).apply()
    }

    // ---- History ----
    data class Hit(val time: Long, val appName: String, val title: String, val text: String)

    fun hits(c: Context): List<Hit> {
        val arr = try {
            JSONArray(sp(c).getString("hits_json", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Hit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Hit(o.optLong("t"), o.optString("a"), o.optString("ti"), o.optString("tx")))
        }
        return out
    }

    fun addHit(c: Context, h: Hit) {
        val list = ArrayList<Hit>()
        list.add(h)
        list.addAll(hits(c))
        val arr = JSONArray()
        list.take(50).forEach {
            arr.put(JSONObject().put("t", it.time).put("a", it.appName).put("ti", it.title).put("tx", it.text))
        }
        sp(c).edit().putString("hits_json", arr.toString()).apply()
    }

    fun clearHits(c: Context) = sp(c).edit().remove("hits_json").apply()
}
