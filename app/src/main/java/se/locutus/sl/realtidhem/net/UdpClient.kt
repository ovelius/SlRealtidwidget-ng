package se.locutus.sl.realtidhem.net

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.google.protobuf.ByteString
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import java.lang.Exception
import java.net.*
import java.util.logging.Logger

const val NEW_PORT = 1701
const val BUFFER = 8096
const val READ_TIMEOUT_MILLIS = 5000

class UpdClient(val context : Context, private val prefs : SharedPreferences
) : HandlerThread("UdpHandler") {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    val mainHandler = Handler(Looper.getMainLooper())
    var selfHandler : Handler? = null
    lateinit var udpSocket : DatagramSocket
    private var address : InetAddress? = null

    var responsive : Boolean = true

    override fun run() {
        udpSocket = DatagramSocket().apply {
            soTimeout = READ_TIMEOUT_MILLIS
        }
        tryResolveAddr()
        LOG.info("Created UPD socket on ${udpSocket.port} to $address")
        super.run()
    }

    private fun tryResolveAddr() {
        try {
            address = Inet4Address.getByName(getBackendIp(prefs))
            responsive = true
        } catch (e : java.net.UnknownHostException) {
            LOG.severe("Failed to resolve address, marking UDP socket as not responsive")
            responsive = false
        }
    }

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        selfHandler = Handler(looper)
    }

    fun ready() : Boolean {
        return selfHandler != null
    }

    fun sendRequest(message : Ng.RequestData, callback : (Int, Ng.ResponseData, Exception?) -> Unit) {
        selfHandler!!.post(RequestRunnable(this, message, callback))
        checkBackendIp()
    }

    private fun checkBackendIp() {
        if (shouldUpdateBackendIp(prefs)) {
            selfHandler!!.post {
                LOG.info("Refreshing backend IP record to ${ updateBackendIp(prefs)}")
            }
        }
    }

    internal class RequestRunnable(var udpClient : UpdClient, var message : Ng.RequestData, var callBack : (Int, Ng.ResponseData, Exception?) -> Unit) : Runnable {
        override fun run() {
            var running = true
            val isPing = message.pingRequest.localTimestampMillis != 0L
            udpClient.send(message)
            try {
                // Continue until timeout or we receive the request we expected.
                while (running) {
                    val response = udpClient.receive(isPing)
                    if (response != null && response.responseHeader.id == message.requestHeader.id) {
                        udpClient.mainHandler.post {
                            callBack(message.requestHeader.id, response, null)
                        }
                        running = false
                    }
                }
            } catch (e : Exception) {
                udpClient.mainHandler.post {
                    callBack(message.requestHeader.id, Ng.ResponseData.getDefaultInstance(), e)
                }
            }

        }
    }

    fun send(message : Ng.RequestData) {
        val bytes = message.toByteArray()
        val p = DatagramPacket(bytes, bytes.size, address, NEW_PORT)
        LOG.fine("Sending UDP message of $message size ${bytes.size}")
        try {
            udpSocket.send(p)
        } catch (e : java.io.IOException) {
            LOG.severe("Caught $e , marking as not responsive")
            responsive = false
        }
    }

    fun receive(isPing : Boolean) : Ng.ResponseData? {
        val bytes = ByteArray(BUFFER)
        val p = DatagramPacket(bytes, bytes.size)
        try {
            udpSocket.receive(p)
        } catch (e : SocketTimeoutException) {
            LOG.warning("Got socket timeout, marking as not responsive")
            if (!isPing) {
                responsive = false
                bringBackToLife()
            }
            throw e
        }
        val message = Ng.ResponseData.parseFrom(ByteString.copyFrom(bytes, 0, p.length))
        LOG.fine("Got UDP message of $message size ${p.length}")
        return message
    }

    fun bringBackToLife() {
        // Is the backend IP old?
        checkBackendIp()
        // We failed to resolve address, try it again.
        if (address == null) {
            selfHandler!!.post {
                tryResolveAddr()
            }
            return
        }
        val time = System.currentTimeMillis()
        val api = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        val pingMessage = Ng.RequestData.newBuilder()
            .setRequestHeader(Ng.RequestHeader.newBuilder()
                .setId(time.toInt())
                .setApi(api))
            .setPingRequest(Ng.PingRequestResponse.newBuilder().setLocalTimestampMillis(time))
            .build()
        selfHandler!!.post(RequestRunnable(this, pingMessage) {
            _, response : Ng.ResponseData, e : Exception? ->
            if (e == null) {
                val newTime = System.currentTimeMillis() - response.pingResponse.localTimestampMillis
                responsive = true
                LOG.info("UDP socket alive with response time $newTime ms")
            }
        })
    }
}