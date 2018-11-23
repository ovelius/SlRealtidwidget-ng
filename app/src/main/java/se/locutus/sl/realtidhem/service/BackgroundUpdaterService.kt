package se.locutus.sl.realtidhem.service

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.content.IntentFilter
import se.locutus.sl.realtidhem.events.WakeLockReceiever
import java.util.logging.Logger


class BackgroundUpdaterService : Service() {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    private var wakeLockReceiver: WakeLockReceiever = WakeLockReceiever()
    internal lateinit var appWidgetManager: AppWidgetManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.fine("Received intent $intent")
        if (intent?.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) == true) {
            handleWidgetTouched(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        }
        return START_STICKY
    }

    fun handleWidgetTouched(widgetId : Int) {
        LOG.info("Registered widget touch $widgetId")
    }

    override fun onCreate() {
        LOG.info("OnCreate")
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(wakeLockReceiver, intentFilter)
        appWidgetManager = AppWidgetManager.getInstance(this)
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
