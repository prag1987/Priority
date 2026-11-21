package com.pixorik.notifalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/** Alarm (sound + vibration + ongoing notification) yahin se control hota hai. */
object AlarmController {

    data class Info(
        val pkg: String,
        val appName: String,
        val title: String,
        val text: String,
        val pi: PendingIntent?
    )

    private const val CHANNEL = "alarm_channel"
    private const val NOTIF_ID = 4242
    private const val MAX_RING_MS = 15 * 60 * 1000L // safety: 15 min baad apne aap band

    private val active = LinkedHashMap<String, Info>()
    @Volatile var last: Info? = null
        private set

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop(appCtx!!) }
    private var appCtx: Context? = null

    @Synchronized
    fun trigger(ctx: Context, key: String, info: Info) {
        appCtx = ctx.applicationContext
        active[key] = info
        last = info
        startSound(ctx.applicationContext)
        postNotification(ctx.applicationContext)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, MAX_RING_MS)
    }

    @Synchronized
    fun onSourceRemoved(ctx: Context, key: String) {
        if (active.remove(key) == null) return
        if (active.isEmpty()) stop(ctx) else postNotification(ctx.applicationContext)
    }

    @Synchronized
    fun stop(ctx: Context) {
        active.clear()
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(vibLoop)          // vibration loop pehle band
        val p = player
        player = null                              // isRinging() turant false
        try { p?.stop() } catch (_: Exception) { }
        try { p?.release() } catch (_: Exception) { }
        try { vibrator(ctx).cancel() } catch (_: Exception) { }
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        } catch (_: Exception) { }
    }

    private fun vibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else
            ctx.getSystemService(Vibrator::class.java)

    // Infinite repeat ki jagah chhoti vibration baar-baar: stop hote hi loop band, vibration atakti nahi
    private val vibLoop = object : Runnable {
        override fun run() {
            val c = appCtx ?: return
            if (player == null) return
            try {
                vibrator(c).vibrate(VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) { }
            handler.postDelayed(this, 1500)
        }
    }

    fun isRinging() = player != null

    private fun startSound(ctx: Context) {
        if (player != null) return

        if (Prefs.maxVolume(ctx)) {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }

        // Pehle user ki chuni hui sound (system tone / phone file), na chale to built-in fire siren
        val saved = Prefs.soundUri(ctx)
        var mp: MediaPlayer? = null
        if (saved != null) {
            mp = try {
                buildPlayer(ctx) { it.setDataSource(ctx, Uri.parse(saved)) }
            } catch (e: Exception) {
                null
            }
        }
        if (mp == null) {
            mp = buildPlayer(ctx) { m ->
                ctx.resources.openRawResourceFd(R.raw.fire_alarm).use {
                    m.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
        }
        mp.start()
        player = mp

        handler.removeCallbacks(vibLoop)
        handler.post(vibLoop)
    }

    private fun buildPlayer(ctx: Context, source: (MediaPlayer) -> Unit): MediaPlayer {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // silent/vibrate mode me bhi bajta hai
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            source(mp)
            mp.isLooping = true
            mp.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            mp.prepare()
            return mp
        } catch (e: Exception) {
            mp.release()
            throw e
        }
    }

    private fun postNotification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "High Priority alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)       // sound hum khud MediaPlayer se bajate hain
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openPi = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, OpenAppActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val fullPi = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val stopPi = PendingIntent.getBroadcast(ctx, 3, Intent(ctx, StopReceiver::class.java), flags)

        val info = last ?: return
        val count = active.size
        val title = if (count > 1) "🔔 $count naye messages" else "🔔 ${info.appName}: ${info.title}"

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(info.text.ifBlank { "Naya message aaya hai" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(openPi)
            .addAction(0, "OPEN ${info.appName}", openPi)
            .addAction(0, "STOP", stopPi)
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
