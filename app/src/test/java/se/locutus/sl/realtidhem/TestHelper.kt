package se.locutus.sl.realtidhem

import org.robolectric.shadows.ShadowAppWidgetManager
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.widget.StandardWidgetProvider
import java.lang.Exception

fun createWidgetId(shadowAppWidgetManager : ShadowAppWidgetManager) : Int {
    return shadowAppWidgetManager.createWidget(StandardWidgetProvider::class.java, R.layout.widgetlayout_base)
}

internal class TestNetworkInterface : NetworkInterface {
    var dataRequestCounts = 0
    var request : Ng.StopDataRequest? = null
    var callback : ((Int, Ng.ResponseData, Exception?) -> Unit) = { _, _, _ -> }
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