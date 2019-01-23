package se.locutus.sl.realtidhem

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
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
import se.locutus.sl.realtidhem.events.TouchHandlerInterface
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import se.locutus.sl.realtidhem.service.EXTRA_UPDATE_TIME
import se.locutus.sl.realtidhem.service.SERVICE_NOTIFICATION_ID
import se.locutus.sl.realtidhem.widget.storeWidgetConfig

@RunWith(RobolectricTestRunner::class)
class BackgroundUpdaterTest {
    private val TEST_UPDATE_PERIOD = 10000000L
    private lateinit var service : BackgroundUpdaterService
    private lateinit var shadow : ShadowService
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var shadowAppWidgetManager: ShadowAppWidgetManager
    private val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
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
    }

    private fun runOneTask() {
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))
        service.runTimerTaskForTest()
    }

    @Test
    fun testStopOnNoWidgets() {
        assertThat(shadow.lastForegroundNotificationId, `is`(0))
        assertThat(shadow.isForegroundStopped, `is`(false))

        service.widgetIdProvider = {
            intArrayOf()
        }

        service.onStartCommand(null, 0, 0)

        assertThat(shadow.isStoppedBySelf, `is`(true))
        assertThat(shadow.lastForegroundNotificationId, `is`(0))
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
        assertThat(shadow.lastForegroundNotificationId, `is`(0))
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
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }
        service.onStartCommand(intent, 0, 0)
        shadowLooper.runOneTask()

        // Runs update right away.
        shadowLooper.runOneTask()
        assertThat(touchHandler.updateCount, `is`(1))

        service.autoUpdateSequenceEndTime[widgetId] = 0
        runOneTask()

        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
        assertThat(shadow.isStoppedBySelf, `is`(false))

        val screenIntent = Intent(Intent.ACTION_SCREEN_ON)
        service.wakeLockReceiver.onReceive(context, screenIntent)
        assertThat(service.hasAutoUpdatesRunning(), `is`(true))
    }

    @Test
    fun testStartSelfLearningWidgets() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
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
        // Simulate timeout
        service.selfLearningTimeouts[widgetId] = 0
        runOneTask()
        // No additional updates.
        assertThat(touchHandler.updateCount, `is`(2))
        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1)
        assertViewText(widgetId, R.id.widgetline2,  R.string.idle_line2)
    }

    @Test
    fun testStartSelfLearningWidgetsLateUpdate() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_UPDATE_TIME, 0L)
        }
        // Try again.
        shadowPowerManager.setIsInteractive(true)
        service.onStartCommand(intent, 0 ,0)
        assertThat(service.hasAutoUpdatesRunning(), `is`(false))
    }

    private fun createUpdateSettings(mode : Ng.UpdateSettings.UpdateMode, sequenceMinutes : Int = 1, listenForScreenOn : Boolean = false) : Ng.UpdateSettings {
        return Ng.UpdateSettings.newBuilder()
            .setUpdateMode(mode)
            .setUpdateSequenceLength(sequenceMinutes)
            .setUpdateWhenScreenOn(listenForScreenOn)
            .build()
    }

    private fun createWidgetConfig(updateSettings : Ng.UpdateSettings) : Int{
        val widgetId = createWidgetId(shadowAppWidgetManager)
        val config = Ng.WidgetConfiguration.newBuilder()
            .setUpdateSettings(updateSettings)
            .setWidgetId(widgetId.toLong())
            .build()
        storeWidgetConfig(prefs, config)
        return widgetId
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
        var updateCount = 0
        var lastUpdateAction : String? = ""
        override fun widgetTouched(widgetId: Int, action: String?, userTouch: Boolean) {
            lastUpdateAction = action
            updateCount++
        }
    }
}