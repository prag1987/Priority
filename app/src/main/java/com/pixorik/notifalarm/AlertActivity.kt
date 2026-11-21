package com.pixorik.notifalarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Lock screen / screen-off par full-screen alert. */
class AlertActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val info = AlarmController.last
        if (info == null || !AlarmController.isRinging()) {
            finish(); return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setPadding(48, 48, 48, 48)
        }
        fun tv(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 16)
        }
        root.addView(tv("🔔 ${info.appName}", 28f, true))
        if (info.title.isNotBlank()) root.addView(tv(info.title, 22f, true))
        root.addView(tv(info.text.ifBlank { "Naya message aaya hai" }, 18f))

        val open = Button(this).apply {
            text = "OPEN ${info.appName}"
            textSize = 20f
            setOnClickListener {
                startActivity(Intent(this@AlertActivity, OpenAppActivity::class.java))
                finish()
            }
        }
        val stop = Button(this).apply {
            text = "STOP ALARM"
            textSize = 18f
            setOnClickListener {
                AlarmController.stop(this@AlertActivity)
                finish()
            }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 }
        root.addView(open, lp)
        root.addView(stop, lp)
        setContentView(root)
    }
}
