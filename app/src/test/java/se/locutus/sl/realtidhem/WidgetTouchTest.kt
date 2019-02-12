package se.locutus.sl.realtidhem

import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.locutus.sl.realtidhem.events.WidgetTouchHandler
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import android.widget.TextView
import org.hamcrest.CoreMatchers.*
import org.robolectric.Shadows.shadowOf
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.widget.storeWidgetConfig
import java.lang.Exception
import org.robolectric.shadows.ShadowAppWidgetManager
import org.robolectric.shadows.ShadowContextWrapper
import se.locutus.sl.realtidhem.widget.StandardWidgetProvider
import java.lang.RuntimeException
import java.net.SocketTimeoutException
import org.robolectric.shadows.ShadowPowerManager
import se.locutus.sl.realtidhem.activity.WidgetConfigureActivity
import se.locutus.sl.realtidhem.events.CYCLE_STOP_RIGHT
import se.locutus.sl.realtidhem.events.InMemoryState
import se.locutus.sl.realtidhem.widget.getLastLoadData
import se.locutus.sl.realtidhem.widget.widgetKeySelectedStop


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
class WidgetTouchTest {
    private val testNetwork = TestNetworkInterface()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    private val stop1 = Ng.StopConfiguration.newBuilder()
        .setStopData(Ng.StoredStopData.newBuilder().setSiteId(123L).setDisplayName("Stop1"))
        .setDeparturesFilter(Ng.DeparturesFilter.newBuilder().addDepartures("123 Bla"))
        .build()
    private val stop2 = Ng.StopConfiguration.newBuilder()
        .setStopData(Ng.StoredStopData.newBuilder().setSiteId(321L).setDisplayName("Stop2"))
        .addLineFilter(Ng.LineFilter.newBuilder().setDirectionId(1).setGroupOfLineId(1))
        .build()
    private val shadowAppWidgetManager: ShadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(context))
    private val shadowPowerManager: ShadowPowerManager = shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
    private val shadowContext : ShadowContextWrapper = shadowOf(context)

    fun createTouchHandler() : WidgetTouchHandler {
        return WidgetTouchHandler(context, testNetwork)
    }

    @Test
    fun testTouchWidgetAndLoadData() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        assertViewText(widgetId, R.id.widgetline1, R.string.updating)
        assertViewText(widgetId, R.id.widgetline2, R.string.updating)

        // Be pesky.
        touchHandler.widgetTouched(widgetId, null)

        assertViewText(widgetId, R.id.widgetline1, R.string.message_again_line1)
        assertViewText(widgetId, R.id.widgetline2, R.string.message_again)

        testNetwork.sendResponse(Ng.ResponseData.newBuilder()
            .setLoadResponse(widgetLoadResponse("123 Hej", "1 min", "Mooore"))
            .build(), null)

        // Scroller is active.
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(true))
        // It worked no need to retry.
        assertThat(touchHandler.inMemoryState.shouldRetry(widgetId, 2000L), `is`(false))
        // Kill the thread.
        touchHandler.inMemoryState.replaceAndStartThread(null)!!.join()
        // No longer active.
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(false))

        assertViewText(widgetId, R.id.widgetline1, "123 Hej")
        assertViewText(widgetId, R.id.widgetmin, "1 min")
        assertViewText(widgetId, R.id.widgetline2, "Mooore")

        assertThat(testNetwork.dataRequestCounts, `is`(1))
        val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        assertThat(getLastLoadData(prefs, widgetId), notNullValue())

        // Touch it again!
        touchHandler.widgetTouched(widgetId, null)
        assertViewText(widgetId, R.id.widgetline1, "123 Hej")
        assertViewText(widgetId, R.id.widgetmin, "1 min")
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(true))
        touchHandler.inMemoryState.replaceAndStartThread(null)!!.join()

        // No additional network requests.
        assertThat(testNetwork.dataRequestCounts, `is`(1))
    }

    @Test
    fun testWidgetConfigUpdatedWithIdleMessage() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)
        testNetwork.sendResponse(Ng.ResponseData.newBuilder()
            .setLoadResponse(widgetLoadResponse("123 Hej", "1 min", "Mooore", "IdleHello"))
            .build(), null)
        // Scroller is active.
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(true))

        touchHandler.configUpdated(widgetId, false)
        assertThat(prefs.getInt(widgetKeySelectedStop(widgetId), 0), `is`(0))

        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1)
        assertViewText(widgetId, R.id.widgetline2, "IdleHello")

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)
        testNetwork.sendResponse(Ng.ResponseData.newBuilder()
            .setLoadResponse(widgetLoadResponse("123 Hej", "1 min", "Mooore", ""))
            .build(), null)

        touchHandler.configUpdated(widgetId, false)
        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1)
        assertViewText(widgetId, R.id.widgetline2,  R.string.idle_line2)
    }

    @Test
    fun testWidgetChangeStop() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()
        touchHandler.configUpdated(widgetId, false)
        assertThat(prefs.getInt(widgetKeySelectedStop(widgetId), 0), `is`(0))
        assertViewText(widgetId, R.id.widgetline1, R.string.idle_line1)
        assertViewText(widgetId, R.id.widgetline2, R.string.idle_line2)
        assertViewText(widgetId, R.id.widgettag,  "Stop1")

        touchHandler.widgetTouched(widgetId, CYCLE_STOP_RIGHT)
        assertThat(prefs.getInt(widgetKeySelectedStop(widgetId), 0), `is`(1))
        assertViewText(widgetId, R.id.widgettag,  "Stop2")
    }

    @Test
    fun testTouchWidgetAndNoDepartures() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        testNetwork.sendResponse(
            Ng.ResponseData.newBuilder()
                .setLoadResponse(widgetLoadResponse("", "", ""))
                .build(), null
        )

        assertViewText(widgetId, R.id.widgetline1, R.string.no_data)
        assertViewText(widgetId, R.id.widgetline2, R.string.no_data_detail)
    }

    @Test
    fun testTouchWidgetAndExceptionLoadingData() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        // It failed!
        testNetwork.sendResponse(Ng.ResponseData.getDefaultInstance(), SocketTimeoutException())
        assertThat(touchHandler.inMemoryState.shouldRetry(widgetId, 2000L), `is`(true))

        assertViewText(widgetId, R.id.widgetline1, R.string.error_timeout)
        // Trigger config change to clear state.
        touchHandler.configUpdated(widgetId, false)

        // Touch again.
        touchHandler.widgetTouched(widgetId, null)
        // Generic garbage.
        testNetwork.sendResponse(Ng.ResponseData.getDefaultInstance(), RuntimeException())
        assertThat(touchHandler.inMemoryState.shouldRetry(widgetId, 2000L), `is`(true))
        assertViewText(widgetId, R.id.widgetline1, R.string.error)
        assertViewText(widgetId, R.id.widgetline2, R.string.error_details_try_again)
    }

    @Test
    fun testTouchWidgetAndErrorLoadingData() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()
        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        // It failed with a specific error.
        testNetwork.sendResponse(Ng.ResponseData.newBuilder().setErrorResponse(Ng.LoadErrorResponse.newBuilder()
            .setErrorType(Ng.ErrorType.SL_API_ERROR).setMessage("ooga")).build(), null)
        // TODO: This should not be retried..
        assertThat(touchHandler.inMemoryState.shouldRetry(widgetId, 2000L), `is`(true))

        assertViewText(widgetId, R.id.widgetline1, R.string.sl_api_error)
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(true))
        touchHandler.inMemoryState.replaceAndStartThread(null)!!.join()
        assertViewText(widgetId, R.id.widgetline2, context.getString(R.string.sl_api_error_detail, "ooga"))
    }

    @Test
    fun testTouchToConfig() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()
        // Touch the widget once.
        touchHandler.widgetTouched(widgetId, null)
        // Nothing
        assertThat(shadowContext.nextStartedActivity, nullValue())
        // Got some failure yada yada.
        testNetwork.sendResponse(Ng.ResponseData.newBuilder().setErrorResponse(Ng.LoadErrorResponse.newBuilder()
            .setErrorType(Ng.ErrorType.SL_API_ERROR).setMessage("ooga")).build(), null)
        // Again
        touchHandler.widgetTouched(widgetId, null)
        // Nothing
        assertThat(shadowContext.nextStartedActivity, nullValue())
        // Third time.
        touchHandler.widgetTouched(widgetId, null)
        val gotIntent = shadowContext.nextStartedActivity
        assertThat(gotIntent, notNullValue())
        val expectedIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        assertThat(gotIntent.component, `is`(expectedIntent.component))
        assertThat(gotIntent.flags, `is`(expectedIntent.flags))
        assertThat(gotIntent.extras[AppWidgetManager.EXTRA_APPWIDGET_ID], `is`(expectedIntent.extras[AppWidgetManager.EXTRA_APPWIDGET_ID]))
    }

    @Test
    fun testTouchWidgetPowerSave() {
        val widgetId = createWidgetConfig()
        val touchHandler = createTouchHandler()
        // Touch the widget in powersave mode.
        shadowPowerManager.setIsPowerSaveMode(true)
        touchHandler.widgetTouched(widgetId, null)

        assertViewText(widgetId, R.id.widgetline1, R.string.power_save_mode)
        assertViewText(widgetId, R.id.widgetline2, context.getString(R.string.power_save_mode_no_whitelist))

        // Starts into settings.
        touchHandler.widgetTouched(widgetId, null)

        val intent = shadowContext.nextStartedActivity
        assertThat(intent, notNullValue())
        assertEquals("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS", intent.action)
    }

    fun assertViewText(widgetId : Int, viewId : Int, expectedText : String) {
        val view = shadowAppWidgetManager.getViewFor(widgetId)
        val actualText = view.findViewById<TextView>(viewId).text
        assertEquals(expectedText, actualText)
    }

    fun assertViewText(widgetId : Int, viewId : Int, text : Int) {
        assertViewText(widgetId, viewId, context.getString(text))
    }

    fun widgetLoadResponse(line1 : String, min : String, line2 : String, idleMessage : String = "") : Ng.WidgetLoadResponseData {
        return Ng.WidgetLoadResponseData.newBuilder()
            .setLine1(line1)
            .setLine2(line2)
            .setMinutes(min)
            .setIdleMessage(idleMessage)
            .build()
    }

    private fun createWidgetConfig() : Int{
        val widgetId = createWidgetId(shadowAppWidgetManager)
        val config = Ng.WidgetConfiguration.newBuilder()
            .setWidgetId(widgetId.toLong())
            .addStopConfiguration(stop1)
            .addStopConfiguration(stop2)
            .build()
        storeWidgetConfig(prefs, config)
        return widgetId
    }
}
