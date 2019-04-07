package se.locutus.sl.realtidhem.service

import android.app.*
import android.appwidget.AppWidgetManager
import java.util.logging.Logger
import android.content.*
import android.graphics.Color
import android.os.*
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

const val SERVICE_NOTIFICATION_ID = 1337
// Update once every -1 second of being stale.
const val UPDATE_PERIOD_MILLIS = STALE_MILLIS + 1000L
const val EXTRA_MANUAL_TOUCH = "manual_touch"
const val ACTION_STOP_UPATE_SEQUENCE = "stop_update_sequence"
const val ACTION_STOP_UPATE_SEQUENCE_NEVER_UPDATE = "stop_update_sequence_never_update"

const val DISTANCE_NO_AUTO_UPDATES_METERS = 5 * 1000
const val POSITION_AGE_NO_AUTO_UPDATES = 3600*1000

class BackgroundUpdaterService : Service() {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    internal var wakeLockReceiver: WakeLockReceiever =
        WakeLockReceiever(this)
    private lateinit var timeTracker : TimeTracker
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerTask : TimerTask? = null
    private val timer = Timer()
    private lateinit var prefs : SharedPreferences
    private lateinit var powerManager : PowerManager
    internal val autoUpdateSequenceEndTime = ConcurrentHashMap<Int, Long>()
    internal val selfLearningTimeouts = ConcurrentHashMap<Int, Long>()
    internal val selfLearningKey = ConcurrentHashMap<Int, String>()
    // These members are exposed for testing.
    // https://github.com/robolectric/robolectric/issues/3763
    internal var widgetIdProvider : () -> IntArray = { getAllWidgetIds(this) }
    internal var widgetTouchProvider : () -> TouchHandlerInterface = {
        WidgetBroadcastReceiver.getTouchHandler(this)
    }
    internal var unscheduleAlarmFunction : (context: Context, alarmKey : String) -> Unit = { context, alarmKey -> deleteAlarmKey(context, alarmKey) }
    internal var updateTimePeriodMillis = UPDATE_PERIOD_MILLIS

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.info("Received intent $intent")
        var widgetId = -1
        if (intent?.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) == true) {
            widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        }
        var delay = false
        if (widgetId != -1) {
            // Typical case is a widget update intent alarm triggering.
            val inMemoryState = widgetTouchProvider().getInMemoryState()
            val config = inMemoryState.getWidgetConfig(widgetId, prefs, true)
            if (ACTION_STOP_UPATE_SEQUENCE == intent?.action
                || ACTION_STOP_UPATE_SEQUENCE_NEVER_UPDATE == intent?.action) {
                inMemoryState.replaceAndStartThread(null, true)
                selfLearningTimeouts.remove(widgetId)
                autoUpdateSequenceEndTime.remove(widgetId)
                LOG.info("Manually stopping sequence for $widgetId")
                if (ACTION_STOP_UPATE_SEQUENCE_NEVER_UPDATE == intent?.action) {
                    val alarmKey = selfLearningKey[widgetId]
                    if (alarmKey != null) {
                        unscheduleAlarmFunction(this, alarmKey)
                    } else {
                        LOG.warning("Missing alarm key for $widgetId, can not unschedule alarm")
                    }
                }
            } else if (config.updateSettings.updateMode == Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE) {
                val alarmKey = intent!!.getStringExtra(EXTRA_UPDATE_TIME_KEY)
                val alarmMinCount = config.updateSettings.interactionsToLearn
                val alarmKeyValue = timeTracker.getAlarmKeyValue(alarmKey)
                if (alarmKeyValue >= alarmMinCount) {
                    val triggerTime = intent!!.getLongExtra(EXTRA_UPDATE_TIME, System.currentTimeMillis())
                    val overtTime = System.currentTimeMillis() - triggerTime
                    LOG.info("Received extra widgetId $widgetId for learning widget with overtime $overtTime")
                    if (verifyLocationSanity(widgetId)) {
                        selfLearningTimeouts[widgetId] = System.currentTimeMillis() + UPDATE_TIME_MILLIS - overtTime
                        selfLearningKey[widgetId] = alarmKey
                    }
                } else {
                    LOG.warning("Received alarm with missing/too low alarm key for $widgetId - not scheduling.")
                }
            } else if (config.updateSettings.updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
                LOG.info("Received extra widgetId $widgetId for auto update widget manual extra ${intent?.hasExtra(EXTRA_MANUAL_TOUCH)}")
                setAutoUpdateSequence(widgetId, config.updateSettings.updateSequenceLength)
                if (intent!!.hasExtra(EXTRA_MANUAL_TOUCH)) {
                    if (inMemoryState.maybeIncrementTouchCountAndOpenConfig(widgetId)) {
                        openWidgetConfig(this, null, widgetId)
                    }
                    // Trigger the update right away
                    touchWidgetOnce(widgetId, intent.action, true)
                    if (!hasAutoUpdatesRunning()) {
                        delay = true
                    }
                }
            }
        }
        val allWidgetIds = widgetIdProvider()
        var hasAlwaysUpdateWidgetRequiringScreenOn = false
        var updateAny = false
        for (widgetId in allWidgetIds) {
            val config = widgetTouchProvider().getInMemoryState().getWidgetConfig(widgetId, prefs)
            val updateSetting = config.updateSettings
            if (updateSetting.updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE
                && updateSetting.updateWhenScreenOn) {
                hasAlwaysUpdateWidgetRequiringScreenOn = true
            }
            if (shouldUpdate(widgetId, config.updateSettings)) {
                updateAny = true
            }
        }
        if (!updateAny && !hasAlwaysUpdateWidgetRequiringScreenOn) {
            LOG.warning("Service start without Widgets to update, stopping")
            stopSelf(startId)
            return START_STICKY
        }

        // Start automatic updates right away.
        if (powerManager.isInteractive) {
            startAutoUpdateSequence(false, delay)
        }

        createForeGroundNotification(widgetId,
            widgetTouchProvider().getInMemoryState().getWidgetConfig(widgetId, prefs))

        return START_STICKY
    }

    private fun setAutoUpdateSequence(widgetId: Int, sequenceLengthMinutes : Int) {
        autoUpdateSequenceEndTime[widgetId] = System.currentTimeMillis() +
                sequenceLengthMinutes * 60 * 1000L
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

    private fun createForeGroundNotification(widgetId: Int?, widgetConfig : Ng.WidgetConfiguration?,
            contentTitle : String = getString(R.string.auto_updates_running),
                                             contentInfo : String? = null,
                                             contentText : String? = null) {
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel("my_service", "My Background Service")
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(contentTitle)

        if (widgetId != null) {
            val stopSequenceIntent = Intent(this, BackgroundUpdaterService::class.java).apply {
                action = ACTION_STOP_UPATE_SEQUENCE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val stopSequence = PendingIntent.getService(
                this, widgetId,
                stopSequenceIntent, 0
            )
            builder.addAction(R.mipmap.ic_launcher, getString(R.string.stop_sequence), stopSequence)
            if (widgetConfig!!.updateSettings.updateMode == Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE) {
                val stopSequenceIntent = Intent(this, BackgroundUpdaterService::class.java).apply {
                    action = ACTION_STOP_UPATE_SEQUENCE_NEVER_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                val stopSequence = PendingIntent.getService(
                    this, widgetId * 10000,
                    stopSequenceIntent, 0
                )
                builder.addAction(R.mipmap.ic_launcher, getString(R.string.stop_sequence_time), stopSequence)
            }
        }

        if (contentInfo != null) {
            builder.setSubText(contentInfo)
        }
        if (contentText != null) {
            builder.setContentText(contentText)
        }

        startForeground(SERVICE_NOTIFICATION_ID, builder.build())
    }

    private fun updateOnce() {
        var updatedAnyWidget = false
        var hasAlwaysUpdateWidgetRequiringScreenOn = false
        LOG.info("Update once at ${System.currentTimeMillis()}")
        for (widgetId in widgetIdProvider()) {
            val config = widgetTouchProvider().getInMemoryState().getWidgetConfig(widgetId, prefs)
            val updateSetting = config.updateSettings
            if (updateSetting.updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE
                && updateSetting.updateWhenScreenOn) {
                hasAlwaysUpdateWidgetRequiringScreenOn = true
            }
            if (shouldUpdate(widgetId, config.updateSettings)) {
                LOG.info("Update of widget $widgetId with type ${config.updateSettings.updateMode} at ${System.currentTimeMillis()}")
                touchWidgetOnce(widgetId, "", false)
                updatedAnyWidget = true
            } else {
                setStaleMessages(widgetId)
            }
        }
        if (!updatedAnyWidget) {
            LOG.info("No widgets left to update")
            stopAutoUpdateSequence()
            if (!hasAlwaysUpdateWidgetRequiringScreenOn) {
                // Stop the service. Wait for next alarm to fire to start it again.
                LOG.info("Stopping self, no screen on event to listen for")
                stopSelf()
            } else {
                // Reset to generic notification.
                createForeGroundNotification(null, null)
            }
        }
    }

    private fun touchWidgetOnce(widgetId : Int, action : String?, userTouch : Boolean) {
        val touchHandler = widgetTouchProvider()
        val config = touchHandler.getInMemoryState().getWidgetConfig(widgetId, prefs)
        touchHandler.widgetTouched(widgetId, action, userTouch) { line1 : String, min : String, line2 : String ->
            val stopConfig = config.getStopConfiguration(getWidgetSelectedStopIndex(widgetId, prefs))
            createForeGroundNotification(widgetId, config,"$line1 $min", stopConfig.stopData.displayName, line2)
        }
    }

    private fun setStaleMessages(widgetId : Int) {
        val manager = AppWidgetManager.getInstance(this)
        setWidgetViews(this, widgetTouchProvider().getInMemoryState().getWidgetConfig(widgetId, prefs),
                           manager, prefs, widgetId)
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
                return false
            }
            return System.currentTimeMillis() <= autoUpdateSequenceEndTime[widgetId]!!
        }
        return false
    }

    fun startAutoUpdateSequence(fromScreenOn : Boolean, delay : Boolean) {
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
        if (fromScreenOn) {
            // Clear widgets triggering from screen turning on.
            for (widgetId in widgetIdProvider()) {
                val config = widgetTouchProvider().getInMemoryState().getWidgetConfig(widgetId, prefs)
                val updateSetting = config.updateSettings
                if (updateSetting.updateWhenScreenOn && updateSetting.updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
                    // This widget should update from screen turning on!
                    // But only if location makes sense...
                    if (verifyLocationSanity(widgetId)) {
                        LOG.info("Starting screen on sequence of length ${config.updateSettings.updateSequenceLength} for $widgetId")
                        setAutoUpdateSequence(widgetId, config.updateSettings.updateSequenceLength)
                    } else {
                        LOG.info("Not scheduling update for $widgetId as location data is too far off")
                    }
                }
            }
        }
        LOG.info("Scheduling automatic updates with $updateTimePeriodMillis millis period, initial update delayed $delay")
        timer.schedule(timerTask, if (delay) updateTimePeriodMillis else 0, updateTimePeriodMillis)
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
            val config = widgetTouchProvider().getInMemoryState().getWidgetConfig(widgetId, prefs)
            val updateMode = config.updateSettings.updateMode
            if (updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE ||
                    updateMode == Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE) {
                // Set something else here.
                setStaleMessages(widgetId)
            }
        }
    }

    override fun onCreate() {
        LOG.info("OnCreate")
        prefs = getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        timeTracker = TimeTracker(this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(wakeLockReceiver, intentFilter)
        createForeGroundNotification(null, null)
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

    fun runTimerTaskForTest() {
        timerTask!!.run()
    }

    private fun verifyLocationSanity(widgetId: Int) : Boolean {
        val distanceMeters = prefs.getFloat(widgetKeyLastDistance(widgetId), 1.0f)
        val ageMillis = (System.currentTimeMillis() -  prefs.getLong(widgetKeyLastLocation(widgetId), 0))
        // If a recent position says we are more than 30km away, don't update.
        if (ageMillis < POSITION_AGE_NO_AUTO_UPDATES && distanceMeters > DISTANCE_NO_AUTO_UPDATES_METERS) {
            return false
        }
        return true
    }
}
