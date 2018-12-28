package se.locutus.sl.realtidhem.service

import android.app.Service
import android.appwidget.AppWidgetManager
import android.os.IBinder
import se.locutus.sl.realtidhem.events.WakeLockReceiever
import java.util.logging.Logger
import android.app.PendingIntent
import android.content.*
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.events.STALE_MILLIS
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.widget.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timerTask

const val DEFAULT_ALWAYS_UPDATE_TIMEOUT_MILLIS = 60 * 1000 * 5
const val SERVICE_NOTIFICATION_ID = 1337

class BackgroundUpdaterService : Service() {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    private var wakeLockReceiver: WakeLockReceiever = WakeLockReceiever(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerTask : TimerTask? = null
    private val timer = Timer()
    private lateinit var prefs : SharedPreferences
    private lateinit var powerManager : PowerManager
    private var autoUpdateSequenceEndTime = ConcurrentHashMap<Int, Long>()
    private val selfLearningTimeouts = ConcurrentHashMap<Int, Long>()

    // https://github.com/robolectric/robolectric/issues/3763
    internal var widgetIdProvider : () -> IntArray = { getAllWidgetIds(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.info("Received intent $intent")
        if (intent?.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) == true) {
            // Typical case is a widget update intent alarm triggering.
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            LOG.info("Received extra widgetId $id")
            selfLearningTimeouts[id] = System.currentTimeMillis() + UPDATE_TIME_MILLIS
        }
        val allWidgetIds = widgetIdProvider()
        LOG.info("Widget Ids ${allWidgetIds.toList()}")
        var updateAny = false
        for (widgetId in allWidgetIds) {
            if (shouldUpdate(widgetId)) {
                updateAny = true
            }
        }
        if (!updateAny) {
            LOG.warning("Service start without Widgets to update, stopping")
            stopSelf(startId)
            return START_STICKY
        }

        // Start automatic updates right away.
        if (powerManager.isInteractive) {
            startAutoUpdateSequence()
        }

        createForeGroundNotification()

        return START_STICKY
    }

    private fun createForeGroundNotification() {
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

        startForeground(SERVICE_NOTIFICATION_ID, notification)
    }

    private fun updateOnce() {
        val touchHandler = WidgetBroadcastReceiver.getTouchHandler(this)
        var updatedWidget = false
        for (widgetId in getAllWidgetIds(this)) {
            if (shouldUpdate(widgetId)) {
                LOG.info("Automatic update of widget $widgetId")
                touchHandler.widgetTouched(widgetId, "", false)
                updatedWidget = true
            }
        }
        if (!updatedWidget) {
            LOG.info("No widgets left to update")
        }
    }

    private fun shouldUpdate(widgetId : Int) : Boolean {
        val updateMode = prefs.getInt(widgetKeyUpdateMode(widgetId), LEARNING_UPDATE_MODE)
        if (updateMode == MANUAL_UPDATE_MODE) {
            return false
        }
        if (updateMode == LEARNING_UPDATE_MODE) {
            LOG.info("learning mode $widgetId ${selfLearningTimeouts}")
            return selfLearningTimeouts.containsKey(widgetId)
                    && System.currentTimeMillis() <= selfLearningTimeouts[widgetId]!!
        }
        if (updateMode == ALWAYS_UPDATE_MODE) {
            if (!autoUpdateSequenceEndTime.containsKey(widgetId)) {
                autoUpdateSequenceEndTime[widgetId] =
                        System.currentTimeMillis() + prefs.getInt(
                    widgetKeyAlwaysUpdateEndTime(widgetId), DEFAULT_ALWAYS_UPDATE_TIMEOUT_MILLIS)
            }
            return System.currentTimeMillis() <= autoUpdateSequenceEndTime[widgetId]!!
        }
        return false
    }

    fun startAutoUpdateSequence() {
        LOG.info("Starting automatic updates")
        timerTask = timerTask{
            mainHandler.post {
                updateOnce()
            }
        }
        // TODO: Make these constants?
        timer.schedule(timerTask, 200, STALE_MILLIS + 1000L)
    }

    fun hasAutoUpdatesRunning() : Boolean {
        return timerTask != null
    }

    fun stopAutoUpdateSequence() {
        LOG.info("Stopping automatic updates")
        if (timerTask != null) {
            timerTask!!.cancel()
            timerTask = null
        }
    }

    override fun onCreate() {
        LOG.info("OnCreate")
        prefs = getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        intentFilter.addAction(Intent.ACTION_USER_PRESENT)
        registerReceiver(wakeLockReceiver, intentFilter)

        return super.onCreate()
    }

    override fun onDestroy() {
        LOG.info("OnDestroy")
        unregisterReceiver(wakeLockReceiver)
        stopAutoUpdateSequence()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        throw IllegalStateException("not implemented!")
    }

}
