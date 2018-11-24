package se.locutus.sl.realtidhem.net

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import com.google.protobuf.ByteString
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import java.lang.Exception
import java.net.*
import java.nio.ByteBuffer
import java.util.logging.Logger

const val PORT = 1199
const val BUFFER = 8096
const val READ_TIMEOUT_MILLIS = 5000

class UpdClient(var context : Context) : HandlerThread("UdpHandler") {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    val mainHandler = Handler(Looper.getMainLooper())
    var selfHandler : Handler? = null
    lateinit var udpSocket : DatagramSocket
    lateinit var address : InetAddress

    var responsive : Boolean = true

    override fun run() {
        udpSocket = DatagramSocket().apply {
            soTimeout = READ_TIMEOUT_MILLIS
        }
        address = Inet4Address.getByName("anka.locutus.se")
        LOG.fine("Created UPD socket on ${udpSocket.port} to $address")
        super.run()
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
        var bytes = message.toByteArray()
        var p = DatagramPacket(bytes, bytes.size, address, PORT)
        LOG.fine("Sending UDP message of $message size ${bytes.size}")
        udpSocket.send(p)
    }

    fun receive(isPing : Boolean) : Ng.ResponseData? {
        var bytes = ByteArray(BUFFER)
        var p = DatagramPacket(bytes, bytes.size)
        try {
            udpSocket.receive(p)
        } catch (e : SocketTimeoutException) {
            LOG.warning("Got socket timeout, marking as not responsive")
            if (!isPing) {
                responsive = false
                schedulePing()
            }
            throw e
        }
        var message = Ng.ResponseData.parseFrom(ByteString.copyFrom(bytes, 0, p.length))
        LOG.fine("Got UDP message of $message size ${p.length}")
        return message
    }

    fun schedulePing() {
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