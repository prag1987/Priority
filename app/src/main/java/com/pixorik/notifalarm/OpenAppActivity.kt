package com.pixorik.notifalarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Invisible activity: alarm band karo aur source app (original chat tak) khol do. */
class OpenAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = AlarmController.last
        AlarmController.stop(this)

        if (info != null) {
            var opened = false
            try {
                info.pi?.send()   // original notification ka deep link (same chat/job)
                opened = info.pi != null
            } catch (_: Exception) {
            }
            if (!opened) {
                val launch = packageManager.getLaunchIntentForPackage(info.pkg)
                if (launch != null) {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    Toast.makeText(this, "App nahi khul paya", Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }
}
