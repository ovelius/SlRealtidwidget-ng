package se.locutus.sl.realtidhem

import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.HttpResponse
import com.android.volley.toolbox.NoCache
import org.apache.http.HttpVersion
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import org.apache.http.message.BasicStatusLine
import org.robolectric.shadows.ShadowAppWidgetManager
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.widget.StandardWidgetProvider
import java.io.ByteArrayInputStream
import java.util.Locale


fun createWidgetId(shadowAppWidgetManager : ShadowAppWidgetManager) : Int {
    return shadowAppWidgetManager.createWidget(StandardWidgetProvider::class.java, R.layout.widgetlayout_base)
}

internal class TestNetworkInterface : NetworkInterface {
    var dataRequestCounts = 0
    var request : Ng.StopDataRequest? = null
    var genericRequest : Ng.RequestData? = null
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

    override fun doGenericRequest(
        request: Ng.RequestData,
        forceHttp: Boolean,
        callBack: (Int, Ng.ResponseData, Exception?) -> Unit
    ): Int {
        this.genericRequest = request
        this.callback = callback
        dataRequestCounts++
        return 0
    }

    fun sendResponse(response : Ng.ResponseData, e : Exception?) {
        callback(0, response, e)
    }
}

class FakeHttpStack : BaseHttpStack() {

    val responses = mutableMapOf<String, ByteArray>()

    override fun executeRequest(
        request: Request<*>?,
        additionalHeaders: MutableMap<String, String>?
    ): HttpResponse {
        val url = request!!.url
        check(responses.containsKey(url)) {
            TODO("Missing faked response for $url")
        }
        val bytes = responses.remove(url)
        val stream = ByteArrayInputStream(bytes)
        val response: HttpResponse =
            HttpResponse(200, listOf(), bytes!!.size, stream)
        return response
    }
}

class FakeRequestQueue(val fakeStack : FakeHttpStack = FakeHttpStack()) :
    RequestQueue(NoCache(), BasicNetwork(fakeStack)) {
}