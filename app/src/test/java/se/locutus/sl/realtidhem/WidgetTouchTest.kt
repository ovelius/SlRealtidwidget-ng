package se.locutus.sl.realtidhem

import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.locutus.sl.realtidhem.events.WidgetTouchHandler
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import android.widget.TextView
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
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
        .setStopData(Ng.StoredStopData.newBuilder().setSiteId(123L))
        .setDeparturesFilter(Ng.DeparturesFilter.newBuilder().addDepartures("123 Bla"))
        .build()
    private val shadowAppWidgetManager: ShadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(context))
    private val shadowPowerManager: ShadowPowerManager = shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
    private val shadowContext : ShadowContextWrapper = shadowOf(context)

    @Test
    fun testTouchWidgetAndLoadData() {
        val widgetId = createWidgetAndConfigFor()
        val touchHandler = WidgetTouchHandler(context, testNetwork)

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        assertViewText(widgetId, R.id.widgetline1, R.string.updating)
        assertViewText(widgetId, R.id.widgetline2, R.string.updating)

        // Be pesky.
        touchHandler.widgetTouched(widgetId, null)
        touchHandler.widgetTouched(widgetId, null)

        assertViewText(widgetId, R.id.widgetline1, R.string.message_again_line1)
        assertViewText(widgetId, R.id.widgetline2, R.string.message_again)

        testNetwork.sendResponse(Ng.ResponseData.newBuilder()
            .setLoadResponse(widgetLoadResponse("123 Hej", "1 min", "Mooore"))
            .build(), null)

        // Scroller is active.
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(true))
        // Kill the thread.
        touchHandler.inMemoryState.replaceThread(null)!!.join()
        // No longer active.
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(false))

        assertViewText(widgetId, R.id.widgetline1, "123 Hej")
        assertViewText(widgetId, R.id.widgetmin, "1 min")
        assertViewText(widgetId, R.id.widgetline2, "Mooore")

        assertThat(testNetwork.dataRequestCounts, `is`(1))

        // Touch it again!
        touchHandler.widgetTouched(widgetId, null)
        assertViewText(widgetId, R.id.widgetline1, "123 Hej")
        assertViewText(widgetId, R.id.widgetmin, "1 min")
        assertThat(touchHandler.inMemoryState.hasRunningThread(widgetId), `is`(true))
        touchHandler.inMemoryState.replaceThread(null)!!.join()

        // No additional network requests.
        assertThat(testNetwork.dataRequestCounts, `is`(1))
    }

    @Test
    fun testTouchWidgetAndExceptionLoadingData() {
        val widgetId = createWidgetAndConfigFor()
        val touchHandler = WidgetTouchHandler(context, testNetwork)

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        // It failed!
        testNetwork.sendResponse(Ng.ResponseData.getDefaultInstance(), SocketTimeoutException())

        assertViewText(widgetId, R.id.widgetline1, R.string.error_timeout)
        // Trigger config change to clear state.
        touchHandler.configUpdated(widgetId, false)

        // Touch again.
        touchHandler.widgetTouched(widgetId, null)
        // Generic garbage.
        testNetwork.sendResponse(Ng.ResponseData.getDefaultInstance(), RuntimeException())
        assertViewText(widgetId, R.id.widgetline1, R.string.error)
        assertViewText(widgetId, R.id.widgetline2, R.string.error_details_try_again)
    }

    @Test
    fun testTouchWidgetAndErrorLoadingData() {
        val widgetId = createWidgetAndConfigFor()
        val touchHandler = WidgetTouchHandler(context, testNetwork)
        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        // It failed with a specific error.
        testNetwork.sendResponse(Ng.ResponseData.newBuilder().setErrorResponse(Ng.LoadErrorResponse.newBuilder()
            .setErrorType(Ng.ErrorType.SL_API_ERROR).setMessage("ooga")).build(), null)

        assertViewText(widgetId, R.id.widgetline1, R.string.sl_api_error)
        assertViewText(widgetId, R.id.widgetline2, context.getString(R.string.sl_api_error_detail, "ooga"))
    }

    @Test
    fun testTouchWidgetPowerSave() {
        val widgetId = createWidgetAndConfigFor()
        val touchHandler = WidgetTouchHandler(context, testNetwork)
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

    fun widgetLoadResponse(line1 : String, min : String, line2 : String) : Ng.WidgetLoadResponseData {
        return Ng.WidgetLoadResponseData.newBuilder().setLine1(line1).setLine2(line2).setMinutes(min).build()
    }

    fun createWidgetAndConfigFor() : Int{
        val widgetId = shadowAppWidgetManager.createWidget(StandardWidgetProvider::class.java, R.layout.widgetlayout_base)
        val config = Ng.WidgetConfiguration.newBuilder()
            .setWidgetId(widgetId.toLong())
            .addStopConfiguration(stop1)
            .build()
        storeWidgetConfig(prefs, config)
        return widgetId
    }

    internal class TestNetworkInterface : NetworkInterface {
        var dataRequestCounts = 0
        var request : Ng.StopDataRequest? = null
        var callback : ((Int, Ng.ResponseData, Exception?) -> Unit) = {_, _, _ -> }
        override fun doStopDataRequest(
            request: Ng.StopDataRequest,
            forceHttp : Boolean,
            callback: (Int, Ng.ResponseData, Exception?) -> Unit
        ): Int {
            this.request = request
            this.callback = callback
            dataRequestCounts++
            return 0
        }
        fun sendResponse(response : Ng.ResponseData, e : Exception?) {
            callback(0, response, e)
        }
    }
}
