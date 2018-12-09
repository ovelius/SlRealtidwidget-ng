package se.locutus.sl.realtidhem.service

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.content.IntentFilter
import se.locutus.sl.realtidhem.events.WakeLockReceiever
import java.util.logging.Logger
import android.app.PendingIntent
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.view.KeyEventDispatcher
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.STALE_MILLIS
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.widget.StandardWidgetProvider
import se.locutus.sl.realtidhem.widget.getAllWidgetIds
import java.util.*
import kotlin.concurrent.timerTask


class BackgroundUpdaterService : Service() {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    private var wakeLockReceiver: WakeLockReceiever = WakeLockReceiever(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerTask : TimerTask? = null
    private val timer = Timer()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.info("Received intent $intent")
        return START_STICKY
    }

    private fun updateOnce() {
        val touchHandler = WidgetBroadcastReceiver.getTouchHandler(this)
        for (i in getAllWidgetIds(this)) {
            touchHandler.widgetTouched(i, "", false)
        }
    }

    fun startAutoUpdateSequence() {
        LOG.info("Starting automatic updates")
        val context = this
        timerTask = timerTask{
            mainHandler.post {
                updateOnce()
            }
        }
        timer.schedule(timerTask, 200, STALE_MILLIS - 10000L)
    }

    fun stopAutoUpdateSequence() {
        LOG.info("Stopping automatic updates")
        if (timerTask != null) {
            timerTask!!.cancel()
        }
    }

    override fun onCreate() {
        LOG.info("OnCreate")
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        intentFilter.addAction(Intent.ACTION_USER_PRESENT)
        registerReceiver(wakeLockReceiver, intentFilter)

        val disableI = Intent(this, BackgroundUpdaterService::class.java).apply { action = "test" }
        val pdisable = PendingIntent.getService(
            this, 0,
            disableI, 0
        )

        val enableI = Intent(this, BackgroundUpdaterService::class.java).apply { action = "test2" }
        val penable = PendingIntent.getService(
            this, 0,
            enableI, 0
        )

        val notification = NotificationCompat.Builder(this, "123")
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(R.mipmap.ic_launcher, "Disable", pdisable)
            .addAction(R.mipmap.ic_launcher, "Enable", penable)
            .setContentTitle(getString(R.string.auto_updates_running))
                // .setContentIntent(pendingIntent)
            .build()

        startForeground(1337, notification)

        return super.onCreate()
    }

    override fun onDestroy() {
        LOG.info("OnDestroy")
        unregisterReceiver(wakeLockReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        throw IllegalStateException("not implemented!")
    }

}
