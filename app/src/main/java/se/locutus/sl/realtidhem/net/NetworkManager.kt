package se.locutus.sl.realtidhem.net

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import se.locutus.proto.Ng
import se.locutus.proto.Ng.RequestData
import se.locutus.proto.Ng.ResponseData
import se.locutus.proto.Ng.UpdateMode
import java.util.logging.Logger

interface NetworkInterface {
    fun doStopDataRequest(request : Ng.StopDataRequest, updateMode: UpdateMode, forceHttp : Boolean = false, callBack : (Int, ResponseData, Exception?) -> Unit) : Int
    fun doGenericRequest(request : Ng.RequestData, forceHttp : Boolean = false, callBack : (Int, ResponseData, Exception?) -> Unit) : Int
}

class NetworkManager(var context : Context,
                     val requestQueue : RequestQueue = Volley.newRequestQueue(context)) : NetworkInterface {
    companion object {
        val LOG = Logger.getLogger(NetworkManager::class.java.name)
    }
    val prefs = context.getSharedPreferences(null, 0)
    val udpSocket = UpdClient(context, prefs).apply { start() }
    private var requestId = 1

    fun buildHeader(updateMode: UpdateMode) : Ng.RequestHeader {
        val api = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

        return Ng.RequestHeader.newBuilder()
            .setApi(api)
            .setId(requestId)
            .setUpdateMode(updateMode)
            .build()
    }
    override fun doStopDataRequest(request : Ng.StopDataRequest, updateMode: UpdateMode, forceHttp : Boolean, callBack : (Int, ResponseData, Exception?) -> Unit) : Int {
        doRequest(RequestData.newBuilder().setStopDataRequest(request)
            .setRequestHeader(buildHeader(updateMode)).build(),  forceHttp, callBack)
        return requestId++
    }

    override fun doGenericRequest(
        request: RequestData,
        forceHttp: Boolean,
        callBack: (Int, ResponseData, Exception?) -> Unit
    ): Int {
        val requestBuilder = request.toBuilder().setRequestHeader(buildHeader(UpdateMode.UPDATE_MODE_UNKNOWN))
        doRequest(requestBuilder.build(),  forceHttp, callBack)
        return requestId++
    }

    private fun doRequest(request : RequestData, forceHttp : Boolean, callback : (Int, ResponseData, Exception?) -> Unit) {
        if (udpSocket.ready() && udpSocket.responsive && !forceHttp) {
            sendRequestWithUDP(request, callback)
        } else {
            sendRequestWithHTTP(request, callback)
            if (udpSocket.ready() && !forceHttp) {
                udpSocket.bringBackToLife()
            }
        }
    }

    private fun sendRequestWithUDP(request : RequestData, callback : (Int, ResponseData, Exception?) -> Unit) {
       udpSocket.sendRequest(request, callback)
    }

    private fun sendRequestWithHTTP(request : RequestData, callback : (Int, ResponseData, Exception?) -> Unit) {
        val protoRequest = ProtoRequest(request,
            { response ->
                LOG.fine("Got data $response")
                callback(request.requestHeader.id, response, null)
            },
            { error ->
                callback(request.requestHeader.id, ResponseData.getDefaultInstance(), error)
            })
        protoRequest.setRetryPolicy(
            DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        )
        requestQueue.add(protoRequest)
    }
}

class ProtoRequest
    (val requestData : RequestData,
     var mListener: Response.Listener<ResponseData>?,
    errorListener: Response.ErrorListener?
) : Request<ResponseData>(Request.Method.POST, URL, errorListener) {


    override fun getHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/octet-stream"
        return headers
    }

    override fun getBody(): ByteArray {
        return requestData.toByteArray()
    }

    private val mLock = Any()

    override fun deliverResponse(response: ResponseData?) {
        var listener: Response.Listener<ResponseData>? = null
        synchronized(mLock) {
            listener = mListener
        }
        if (listener != null) {
            listener!!.onResponse(response)
        }
    }

    override fun cancel() {
        super.cancel()
        synchronized(mLock) {
            mListener = null
        }
    }

    override fun parseNetworkResponse(response: NetworkResponse): Response<ResponseData> {
        if (response.statusCode != 200) {
            NetworkManager.LOG.warning("HTTP error: ${response.statusCode} - response ${response}")
        }
        val responseData = ResponseData.parseFrom(response.data)
        return Response.success(responseData, HttpHeaderParser.parseCacheHeaders(response))
    }
}