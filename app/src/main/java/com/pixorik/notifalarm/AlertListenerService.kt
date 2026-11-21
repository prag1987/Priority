package com.pixorik.notifalarm

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * System is service ko background me zinda rakhta hai jab tak
 * "Notification access" ON hai. Watched app ki notification aate hi alarm shuru.
 */
class AlertListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.enabled(this)) return
        val pkg = sbn.packageName
        if (pkg == packageName) return
        if (pkg !in Prefs.watched(this)) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return // calls/music/sync jaisi ongoing notifications ignore

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        if (!Prefs.shouldAlert(this, pkg, title, text)) return

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }

        AlarmController.trigger(
            this, sbn.key,
            AlarmController.Info(pkg, appName, title, text, n.contentIntent)
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Prefs.autoStop(this)) AlarmController.onSourceRemoved(this, sbn.key)
    }

    override fun onListenerDisconnected() {
        // System ne unbind kiya to wapas bind karwao
        requestRebind(ComponentName(this, AlertListenerService::class.java))
    }
}
