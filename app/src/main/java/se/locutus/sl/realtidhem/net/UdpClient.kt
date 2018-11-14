package se.locutus.sl.realtidhem.net

import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import java.net.*
import java.util.logging.Logger

class UpdClient {
    companion object {
        val LOG = Logger.getLogger(BackgroundUpdaterService::class.java.name)
    }
    val PORT = 1196
    val BUFFER = 8096
    // Nothing should take longer than 5000 millis, right?
    val READ_TIMEOUT_MILLIS = 5000

    var udpSocket : DatagramSocket = DatagramSocket().apply {
        soTimeout = READ_TIMEOUT_MILLIS
    }
    var address : InetAddress = Inet4Address.getByName("anka.locutus.se")
    var responsive : Boolean = true

    fun send(message : Ng.RequestData) {
        var bytes = message.toByteArray()
        var p = DatagramPacket(bytes, bytes.size, address, PORT)
        LOG.fine("Sending UDP message of $message size ${bytes.size}")
        udpSocket.send(p)
    }

    fun receive() : Ng.ResponseData? {
        var bytes = ByteArray(BUFFER)
        var p = DatagramPacket(bytes, bytes.size)
        try {
            udpSocket.receive(p)
        } catch (e : SocketTimeoutException) {
            LOG.warning("Got socket timeout, marking as not responsive")
            responsive = false
            return null
        }
        var message = Ng.ResponseData.parseFrom(p.data)
        LOG.fine("Got UDP message of $message size ${p.length}")
        return message
    }

}