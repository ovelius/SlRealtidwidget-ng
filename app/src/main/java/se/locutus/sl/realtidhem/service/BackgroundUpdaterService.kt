package se.locutus.sl.realtidhem.service

import android.app.*
import android.appwidget.AppWidgetManager
import java.util.logging.Logger
import android.content.*
import android.graphics.Color
import android.os.*
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.events.*
import se.locutus.sl.realtidhem.widget.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timerTask

const val DEFAULT_ALWAYS_UPDATE_TIMEOUT_MILLIS = 60 * 1000 * 5
const val SERVICE_NOTIFICATION_ID = 1337
// Update once every -1 second of being stale.
const val UPDATE_PERIOD_MILLIS = STALE_MILLIS + 1000L
const val EXTRA_MANUAL_TOUCH = "manual_touch"

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
    private val autoUpdateSequenceEndTime = ConcurrentHashMap<Int, Long>()
    internal val selfLearningTimeouts = ConcurrentHashMap<Int, Long>()
    private val widgetConfigs = ConcurrentHashMap<Int, Ng.WidgetConfiguration>()
    private val inMemoryState = InMemoryState()

    // These members are exposed for testing.
    // https://github.com/robolectric/robolectric/issues/3763
    internal var widgetIdProvider : () -> IntArray = { getAllWidgetIds(this) }
    internal var widgetTouchProvider : () -> TouchHandlerInterface = {
        WidgetBroadcastReceiver.getTouchHandler(this)
    }
    internal var updateTimePeriodMillis = UPDATE_PERIOD_MILLIS

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        LOG.info("Received intent $intent")
        var updateString : String? = null
        if (intent?.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) == true) {
            // Typical case is a widget update intent alarm triggering.
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            val config = getConfigFor(id, true)
            if (config.updateSettings.updateMode == Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE) {
                updateString = "Learning widget $id"
                val triggerTime = intent.getLongExtra(EXTRA_UPDATE_TIME, System.currentTimeMillis())
                val overtTime = System.currentTimeMillis() - triggerTime
                LOG.info("Received extra widgetId $id for learning widget with overtime $overtTime")
                selfLearningTimeouts[id] = System.currentTimeMillis() + UPDATE_TIME_MILLIS - overtTime
            } else if (config.updateSettings.updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
                LOG.info("Received extra widgetId $id for auto update widget manual extra ${intent.hasExtra(EXTRA_MANUAL_TOUCH)}")
                if (intent.hasExtra(EXTRA_MANUAL_TOUCH)) {
                    if (inMemoryState.maybeIncrementTouchCountAndOpenConfig(id)) {
                        openWidgetConfig(this, null, id)
                    }
                }
                autoUpdateSequenceEndTime.remove(id)
            }
        }
        val allWidgetIds = widgetIdProvider()
        LOG.info("Widget Ids ${allWidgetIds.toList()}")
        var updateAny = false
        for (widgetId in allWidgetIds) {
            val config = getConfigFor(widgetId)
            if (shouldUpdate(widgetId, config.updateSettings)) {
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

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel("my_service", "My Background Service")
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }
        createForeGroundNotification(channelId, updateString)

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun createForeGroundNotification(channelId : String, contentText : String?) {
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

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(R.mipmap.ic_launcher, "Disable", pdisable)
            .addAction(R.mipmap.ic_launcher, "Enable", penable)
            .setContentTitle(getString(R.string.auto_updates_running))
            // .setContentIntent(pendingIntent)
        if (contentText != null) {
            builder.setContentText(contentText)
        }

        startForeground(SERVICE_NOTIFICATION_ID, builder.build())
    }

    private fun updateOnce() {
        val touchHandler = widgetTouchProvider()
        var updatedAnyWidget = false
        var hasAlwaysUpdateWidget = false
        for (widgetId in widgetIdProvider()) {
            val config = getConfigFor(widgetId)
            val updateMode = config.updateSettings.updateMode
            if (updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
                hasAlwaysUpdateWidget = true
            }
            if (shouldUpdate(widgetId, config.updateSettings)) {
                LOG.info("Update of widget $widgetId with type ${config.updateSettings.updateMode}")
                touchHandler.widgetTouched(widgetId, "", false)
                updatedAnyWidget = true
            } else {
                setStaleMessages(widgetId, updateMode)
            }
        }
        if (!updatedAnyWidget) {
            LOG.info("No widgets left to update")
            stopAutoUpdateSequence()
            if (!hasAlwaysUpdateWidget) {
                // Stop the service. Wait for next alarm to fire to start it again.
                stopSelf()
            }
        }
    }

    private fun setStaleMessages(widgetId : Int, updateMode : Ng.UpdateSettings.UpdateMode) {
        val manager = AppWidgetManager.getInstance(this)
        setWidgetViews(this, getConfigFor(widgetId),
                           manager, prefs, widgetId)
    }

    private fun getConfigFor(widgetId: Int, refresh: Boolean = false) : Ng.WidgetConfiguration {
        if (!widgetConfigs.containsKey(widgetId) || refresh) {
            widgetConfigs[widgetId] = loadWidgetConfigOrDefault(prefs, widgetId)
        }
        return widgetConfigs[widgetId]!!
    }

    private fun shouldUpdate(widgetId : Int, updateSettings : Ng.UpdateSettings) : Boolean {
        val updateMode = updateSettings.updateMode
        if (updateMode == Ng.UpdateSettings.UpdateMode.MANUAL_UPDATE_MODE) {
            return false
        }
        if (updateMode == Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE) {
            return selfLearningTimeouts.containsKey(widgetId)
                    && System.currentTimeMillis() <= selfLearningTimeouts[widgetId]!!
        }
        if (updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
            if (!autoUpdateSequenceEndTime.containsKey(widgetId)) {
                autoUpdateSequenceEndTime[widgetId] =
                        System.currentTimeMillis() + updateSettings.updateSequenceLength * 60 * 1000L
            }
            return System.currentTimeMillis() <= autoUpdateSequenceEndTime[widgetId]!!
        }
        return false
    }

    fun startAutoUpdateSequence() {
        LOG.info("Starting automatic updates")
        if (timerTask != null) {
            LOG.warning("Already scheduled automatic updates")
            return
        }
        timerTask = timerTask{
            mainHandler.post {
                updateOnce()
            }
        }
        // Clear automatic update timeouts.
        autoUpdateSequenceEndTime.clear()
        LOG.info("Scheduling automatic updates with $updateTimePeriodMillis millis period")
        timer.schedule(timerTask, 0, updateTimePeriodMillis)
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
        for (widgetId in widgetIdProvider()) {
            val config = getConfigFor(widgetId)
            val updateMode = config.updateSettings.updateMode
            if (updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
                // Set something else here.
                setStaleMessages(widgetId, updateMode)
            }
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
