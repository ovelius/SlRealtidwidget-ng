package se.locutus.sl.realtidhem.net

import android.content.Context
import android.support.annotation.GuardedBy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import se.locutus.proto.Ng
import se.locutus.proto.Ng.ResponseData
import se.locutus.proto.Ng.RequestData
import java.lang.Exception


const val URL = "http://anka.locutus.se/NG"

class NetworkManager(var context : Context) {
    val requestQueue = Volley.newRequestQueue(context)
    private var requestId = 0

    fun doStopDataRequest(request : Ng.StopDataRequest, callBack : (ResponseData, Exception?) -> Unit) {
        val api = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        val requestId = (this.requestId++);
        doRequest(RequestData.newBuilder().setStopDataRequest(request)
            .setRequestHeader(Ng.RequestHeader.newBuilder()
                .setApi(api)
                .setId(requestId)).build(),  callBack);
    }

    fun doRequest(request : RequestData, callBack : (ResponseData, Exception?) -> Unit) {
        val protoRequest = ProtoRequest(request,
            Response.Listener { response ->
                callBack(response, null)
            },
            Response.ErrorListener { error ->
                callBack(ResponseData.getDefaultInstance(), error)
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
        headers["Content-Type"] = "application/octet-stream";
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
        if (response.statusCode == 200) {

        }
        val responseData = ResponseData.parseFrom(response.data)
        return Response.success(responseData, HttpHeaderParser.parseCacheHeaders(response))
    }
}