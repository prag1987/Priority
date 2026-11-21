package com.pixorik.notifalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmController.stop(context)
    }
}
