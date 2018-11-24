package se.locutus.sl.realtidhem

import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.locutus.sl.realtidhem.events.WidgetTouchHandler
import android.appwidget.AppWidgetManager
import androidx.test.core.app.ApplicationProvider
import android.widget.TextView
import org.hamcrest.CoreMatchers.`is`
import org.robolectric.Shadows.shadowOf
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.widget.storeWidgetConfig
import java.lang.Exception
import org.robolectric.shadows.ShadowAppWidgetManager
import se.locutus.sl.realtidhem.widget.StandardWidgetProvider
import java.net.SocketTimeoutException


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

    @Test
    fun testTouchWidgetAndLoadData() {
        val widgetId = createWidgetAndConfigFor()
        val touchHandler = WidgetTouchHandler(context, testNetwork)

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        assertViewText(widgetId, R.id.widgetline1, R.string.updating)

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
    fun testTouchWidgetAndErrorLoadingData() {
        val widgetId = createWidgetAndConfigFor()
        val touchHandler = WidgetTouchHandler(context, testNetwork)

        // Touch the widget.
        touchHandler.widgetTouched(widgetId, null)

        // It failed!
        testNetwork.sendResponse(Ng.ResponseData.getDefaultInstance(), SocketTimeoutException())

        assertViewText(widgetId, R.id.widgetline1, R.string.error_timeout)
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
