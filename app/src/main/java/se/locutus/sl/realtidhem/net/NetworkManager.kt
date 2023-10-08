package se.locutus.sl.realtidhem.net

import android.content.Context
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import se.locutus.proto.Ng
import se.locutus.proto.Ng.ResponseData
import se.locutus.proto.Ng.RequestData
import java.lang.Exception
import java.util.logging.Logger

interface NetworkInterface {
    fun doStopDataRequest(request : Ng.StopDataRequest, forceHttp : Boolean = false, callBack : (Int, ResponseData, Exception?) -> Unit) : Int
}

class NetworkManager(var context : Context) : NetworkInterface {
    companion object {
        val LOG = Logger.getLogger(NetworkManager::class.java.name)
    }
    val requestQueue = Volley.newRequestQueue(context)
    val prefs = context.getSharedPreferences(null, 0)
    val udpSocket = UpdClient(context, prefs).apply { start() }
    private var requestId = 1

    override fun doStopDataRequest(request : Ng.StopDataRequest, forceHttp : Boolean, callBack : (Int, ResponseData, Exception?) -> Unit) : Int {
        val api = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        doRequest(RequestData.newBuilder().setStopDataRequest(request)
            .setRequestHeader(Ng.RequestHeader.newBuilder()
                .setApi(api)
                .setId(requestId)).build(),  forceHttp, callBack)
        return requestId++
    }

    private fun doRequest(request : RequestData, forceHttp : Boolean, callback : (Int, ResponseData, Exception?) -> Unit) {
        if (udpSocket.ready() && udpSocket.responsive && !forceHttp) {
            LOG.info("Sending request using UDP")
            sendRequestWithUDP(request, callback)
        } else {
            LOG.info("Sending request using HTTP URL $URL")
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
            Response.Listener { response ->
                LOG.fine("Got data $response")
                callback(request.requestHeader.id, response, null)
            },
            Response.ErrorListener { error ->
                callback(request.requestHeader.id, ResponseData.getDefaultInstance(), error)
            })
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