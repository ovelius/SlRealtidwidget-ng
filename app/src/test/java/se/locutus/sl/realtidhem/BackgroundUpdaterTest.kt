package se.locutus.sl.realtidhem

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
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
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import se.locutus.sl.realtidhem.service.SERVICE_NOTIFICATION_ID
import se.locutus.sl.realtidhem.widget.storeWidgetConfig

@RunWith(RobolectricTestRunner::class)
class BackgroundUpdaterTest {
    private lateinit var service : BackgroundUpdaterService
    private lateinit var shadow : ShadowService
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var shadowAppWidgetManager: ShadowAppWidgetManager
    private val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    private val shadowPowerManager: ShadowPowerManager = shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)

    @Before
    fun createService() {
        service = Robolectric.setupService(BackgroundUpdaterService::class.java)
        shadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(service))
        shadow = shadowOf(service)
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
    fun testStartSelfLearningWidgets() {
        val widgetId = createWidgetConfig(createUpdateSettings(Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE))
        service.widgetIdProvider = {
            intArrayOf(widgetId)
        }
        val intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }
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
    }

    private fun createUpdateSettings(mode : Ng.UpdateSettings.UpdateMode) : Ng.UpdateSettings {
        return Ng.UpdateSettings.newBuilder().setUpdateMode(mode).build()
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
}