package se.locutus.sl.realtidhem

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppWidgetManager
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowService
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.events.InMemoryState
import se.locutus.sl.realtidhem.events.TouchHandlerInterface
import se.locutus.sl.realtidhem.service.*
import se.locutus.sl.realtidhem.widget.storeWidgetConfig
import se.locutus.sl.realtidhem.widget.widgetKeyLastDistance
import se.locutus.sl.realtidhem.widget.widgetKeyLastLocation
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap

const val ALARM_KEY = "alarm_key"
const val ALARM_INTERACTIONS_COUNT = 2

@RunWith(RobolectricTestRunner::class)
class BackgroundUpdaterTest {
    private val TEST_UPDATE_PERIOD = 10000000L
    private lateinit var service : BackgroundUpdaterService
    private lateinit var shadow : ShadowService
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var shadowAppWidgetManager: ShadowAppWidgetManager
    private val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    private val timeTrackerPrefs = context.getSharedPreferences(TIME_PREFS, 0)
    private val shadowLooper = shadowOf(Looper.getMainLooper())
    private val touchHandler = FakeTouchHandler(context)
    private val shadowPowerManager: ShadowPowerManager = shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)

    @Before
    fun createService() {
        service = Robolectric.setupService(BackgroundUpdaterService::class.java)
        service.widgetTouchProvider = {
            touchHandler
        }
        service.updateTimePeriodMillis = TEST_UPDATE_PERIOD
        shadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(service))
        shadow = shadowOf(service)
        // For self learning widgets an alarm key is required.
        // TODO: Verify key is high enough to trigger alarm.
        timeTrackerPrefs.edit().putInt(ALARM_KEY, ALARM_INTERACTIONS_COUNT).commit()
    }

    private fun runOneTask() {
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))
        service.runTimerTaskForTest()
    }

    @Test
    fun testStopOnNoWidgets() {
        assertThat(shadow.lastForegroundNotificationId, `is`(SERVICE_NOTIFICATION_ID))
        assertThat(shadow.isForegroundStopped, `is`(false))

        service.widgetIdProvider = {
            intArrayOf()
        }

        service.onStartCommand(null, 0, 0)

        assertThat(shadow.isStoppedBySelf, `is`(true))
        assertThat(shadow.lastForegroundNotificationId, `is`(SERVICE_NOTIFICATION_ID))
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
    }

    @Test
    fun testStopNoUpdatableWidgets() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.MANUAL_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }
        service.onStartCommand(intent, 0, 0)

        // Still doesn't start.
        assertThat(shadow.isStoppedBySelf, `is`(true))
        assertThat(shadow.lastForegroundNotificationId, `is`(SERVICE_NOTIFICATION_ID))
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
    }


    @Test
    fun testStopAutoUpdateSequence() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE, 2))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }
        service.onStartCommand(intent, 0, 0)
        shadowLooper.runOneTask()
        // Starts the service.
        assertThat(shadow.isStoppedBySelf, `is`(false))
        assertThat(shadow.lastForegroundNotificationId, `is`(SERVICE_NOTIFICATION_ID))
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))

        // Runs update right away.
        shadowLooper.runOneTask()
        shadowLooper.runOneTask()
        assertThat(touchHandler.updateCount, `is`(1))
        runOneTask()
        // Did additional updates.
        assertThat(touchHandler.updateCount, `is`(2))

        service.autoUpdateSequenceEndTime[widgetId] = 0
        runOneTask()
        // Did not update.
        assertThat(touchHandler.updateCount, `is`(2))
        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1_auto)
        assertViewText(widgetId, R.id.widgetline2,  context.getString(R.string.idle_line2_auto, 2))

        // We stopped the service, since no widgets require the screen broadcast listener.
        assertThat(shadow.isStoppedBySelf, `is`(true))
    }

    @Test
    fun testRunSequenceOnScreenOn() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE,2, true))
        val widgetId2 = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE,2, false))
        service.widgetIdProvider = {
            intArrayOf(widgetId, widgetId2)
        }
        val intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }
        service.onStartCommand(intent, 0, 0)
        shadowLooper.runOneTask()

        // Runs update right away.
        shadowLooper.runOneTask()
        assertThat(touchHandler.updateCount, `is`(1))
        assertThat(touchHandler.userTouch, `is`(false))
        assertThat(touchHandler.updateCountPerId, `is`(mapOf(widgetId to 1)))

        // Simulate sequence timeout.
        service.autoUpdateSequenceEndTime[widgetId] = 0
        runOneTask()

        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
        assertThat(shadow.isStoppedBySelf, `is`(false))

        val screenIntent = Intent(Intent.ACTION_SCREEN_ON)
        service.wakeLockReceiver.onReceive(context, screenIntent)
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))
        Thread.sleep(40)
        shadowLooper.runOneTask()
        touchHandler.callback("one", "two", "three")
        var shadowNotification = shadowOf(shadow.lastForegroundNotification)
        assertThat(shadowNotification.contentText.toString(), `is`("three"))
        assertThat(touchHandler.updateCount, `is`(2))
        assertThat(touchHandler.updateCountPerId, `is`(mapOf(widgetId to 2)))

        // Now trigger the other widget from touching it.
        val intent2 = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId2)
            putExtra(EXTRA_MANUAL_TOUCH, true)
        }
        service.onStartCommand(intent2, 0, 0)
        Thread.sleep(40)
        shadowLooper.runOneTask()

        assertThat(touchHandler.userTouch, `is`(true))
        assertThat(touchHandler.updateCount, `is`(3))
        assertThat(touchHandler.updateCountPerId, `is`(mapOf(widgetId to 2, widgetId2 to 1)))

        shadowNotification = shadowOf(shadow.lastForegroundNotification)
        assertThat(shadowNotification.contentText, `is`(null as CharSequence?))
    }

    @Test
    fun testStartSelfLearningWidgets() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = startIntent(widgetId, System.currentTimeMillis())
        // Change update mode.
        shadowPowerManager.setIsInteractive(false)
        service.onStartCommand(intent, 0 ,0)

        assertThat(shadow.isStoppedBySelf, `is`(false))
        assertThat(shadow.lastForegroundNotificationId, `is`(SERVICE_NOTIFICATION_ID))
        // Since we are not interactive.
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))

        // Try again.
        shadowPowerManager.setIsInteractive(true)
        service.onStartCommand(intent, 0 ,0)
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))

        Thread.sleep(40)
        shadowLooper.runOneTask()
        assertThat(touchHandler.updateCount, `is`(1))
        // Send one more start command.
        service.onStartCommand(intent, 0 ,0)
        // Wait for things to run.
        // Run a couple of tasks.
        Thread.sleep(40)
        shadowLooper.runOneTask()
        // We still only made one update.
        assertThat(touchHandler.updateCount, `is`(1))
        // Wait one update period
        runOneTask()
        assertThat(touchHandler.updateCount, `is`(2))
        // Something got loaded
        touchHandler.callback("test1", "min", "test2")
        setWidgetLines(widgetId, "test", "test")
        // Notification was updated.
        val shadowNotification = shadowOf(shadow.lastForegroundNotification)
        assertThat(shadowNotification.contentText.toString(), `is`("test2"))
        assertThat(shadowNotification.contentTitle.toString(), `is`("test1 min"))

        // Simulate manual abort.
        val stopSequenceIntent = Intent(context, BackgroundUpdaterService::class.java).apply {
            action = ACTION_STOP_UPATE_SEQUENCE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        service.onStartCommand(stopSequenceIntent, 0,0)
        runOneTask()
        // No additional updates.
        assertThat(touchHandler.updateCount, `is`(2))
        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1)
        assertViewText(widgetId, R.id.widgetline2,  R.string.idle_line2)
        // We stopped.
        assertThat(shadow.isStoppedBySelf, `is`(true))
    }

    @Test
    fun testStartSelfLearningUnschedule() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = startIntent(widgetId, System.currentTimeMillis())
        service.onStartCommand(intent, 0 ,0)
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))

        Thread.sleep(40)
        shadowLooper.runOneTask()
        assertThat(touchHandler.updateCount, `is`(1))

        // Simulate manual abort.
        val stopSequenceIntent = Intent(context, BackgroundUpdaterService::class.java).apply {
            action = ACTION_STOP_UPATE_SEQUENCE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        service.onStartCommand(stopSequenceIntent, 0,0)
        runOneTask()
        // No additional updates.
        assertThat(touchHandler.updateCount, `is`(1))
        // Alarm was stopped.
        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1)
        assertViewText(widgetId, R.id.widgetline2,  R.string.idle_line2)
        // We stopped.
        assertThat(shadow.isStoppedBySelf, `is`(true))
    }

    @Test
    fun testStartSelfLearningWidgetsLateUpdate() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = startIntent(widgetId, 0L)
        // Try again.
        shadowPowerManager.setIsInteractive(true)
        service.onStartCommand(intent, 0 ,0)
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
    }

    @Test
    fun testTooLowAlarmKeyForSelfLearning() {
        val widgetId = createWidgetConfig(createLearningUpdateSettings(ALARM_INTERACTIONS_COUNT + 1))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = startIntent(widgetId, System.currentTimeMillis())
        shadowPowerManager.setIsInteractive(true)

        // Removing the alarm key will not run the alarm sequence.
       //  timeTrackerPrefs.edit().remove(ALARM_KEY).commit()

        service.onStartCommand(intent, 0 ,0)
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
    }

    @Test
    fun testTooFarAwayForSelfLearning() {
        val widgetId = createWidgetConfig(createLearningUpdateSettings(1))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = startIntent(widgetId, System.currentTimeMillis())
        shadowPowerManager.setIsInteractive(true)

        // Update distance data - we are now too far away for the alarm to make sense.
        prefs.edit().putLong(widgetKeyLastLocation(widgetId), System.currentTimeMillis() - POSITION_AGE_NO_AUTO_UPDATES + 10000)
            .putFloat(widgetKeyLastDistance(widgetId), DISTANCE_NO_AUTO_UPDATES_METERS + 1f).commit()

        service.onStartCommand(intent, 0 ,0)
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
    }

    private fun startIntent(widgetId : Int, startTime : Long) : Intent {
      return Intent().apply {
          putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
          putExtra(EXTRA_UPDATE_TIME, startTime)
          putExtra(EXTRA_UPDATE_TIME_KEY, ALARM_KEY)
      }
    }

    private fun createUpdateSettings(mode : Ng.UpdateSettings.UpdateMode, sequenceMinutes : Int = 1, listenForScreenOn : Boolean = false) : Ng.UpdateSettings {
        return Ng.UpdateSettings.newBuilder()
            .setUpdateMode(mode)
            .setUpdateSequenceLength(sequenceMinutes)
            .setUpdateWhenScreenOn(listenForScreenOn)
            .build()
    }

    private fun createLearningUpdateSettings(minInteractions : Int) : Ng.UpdateSettings {
        return Ng.UpdateSettings.newBuilder()
            .setUpdateMode(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE)
            .setInteractionsToLearn(minInteractions)
            .build()
    }

    private fun createWidgetConfig(updateSettings : Ng.UpdateSettings) : Int{
        val widgetId = createWidgetId(shadowAppWidgetManager)
        val config = Ng.WidgetConfiguration.newBuilder()
            .setUpdateSettings(updateSettings)
            .setWidgetId(widgetId.toLong())
            .addStopConfiguration(Ng.StopConfiguration.newBuilder()
                .setStopData(Ng.StoredStopData.newBuilder().setDisplayName("stop display name")))
            .build()
        storeWidgetConfig(prefs, config)
        return widgetId
    }

    private fun setWidgetLines(widgetId: Int, line1 : String, line2 : String) {
        val manager = AppWidgetManager.getInstance(service)
        val views = RemoteViews(context.packageName,  R.layout.widgetlayout_base)
        views.setTextViewText(R.id.widgetline1, line1)
        views.setTextViewText(R.id.widgetline2, line2)
        manager.updateAppWidget(widgetId, views)
    }

    fun assertViewText(widgetId : Int, viewId : Int, expectedText : String) {
        val view = shadowAppWidgetManager.getViewFor(widgetId)
        val actualText = view.findViewById<TextView>(viewId).text
        Assert.assertEquals(expectedText, actualText)
    }

    fun assertViewText(widgetId : Int, viewId : Int, text : Int) {
        assertViewText(widgetId, viewId, context.getString(text))
    }

    //val context: Context, val networkManager : NetworkInterface, val retryMillis : Long = UPDATE_AUTO_RETRY_MILLIS
    internal class FakeTouchHandler(context : Context) : TouchHandlerInterface {
        override fun getInMemoryState(): InMemoryState {
            return InMemoryState()
        }

        var updateCount = 0
        var updateCountPerId = ConcurrentHashMap<Int, Int>()
        var lastUpdateAction : String? = ""
        var callback : (String, String, String) -> Unit = {
            _,_,_ ->
        }
        var userTouch = false
        override fun widgetTouched(widgetId: Int, action: String?, userTouch: Boolean, loadedLinesCallback : (String, String, String) -> Unit) {
            lastUpdateAction = action
            updateCount++
            if (!updateCountPerId.containsKey(widgetId)) {
                updateCountPerId[widgetId] = 0
            }
            updateCountPerId[widgetId] = updateCountPerId[widgetId]!! + 1
            callback = loadedLinesCallback
            this.userTouch = userTouch
        }
    }
}