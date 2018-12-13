package se.locutus.sl.realtidhem.net

import android.content.SharedPreferences
import java.net.InetAddress

const val IP_ADDR_KEY = "server_ip_addr"
const val IP_ADDR_TIME_KEY = "server_ip_addr_time"
const val HOSTNAME = "anka.locutus.se"
const val URL = "http://$HOSTNAME/NG"
const val DNS_REFRESH_TIME_MILLIS = 3 * 24 * 3600 * 1000

fun getBackendIp(prefs : SharedPreferences) : String {
    return prefs.getString(IP_ADDR_KEY, HOSTNAME)
}

fun shouldUpdateBackendIp(prefs : SharedPreferences): Boolean{
    val time = prefs.getLong(IP_ADDR_TIME_KEY, 0L)
    val now = System.currentTimeMillis()
    return (now - time > DNS_REFRESH_TIME_MILLIS)
}

fun updateBackendIp(prefs : SharedPreferences) : String {
    var addr : InetAddress? = null
    try {
        addr = InetAddress.getByName(HOSTNAME)
    } catch (e : java.net.UnknownHostException) {
        return e.toString()
    }
        prefs.edit()
            .putString(IP_ADDR_KEY, addr.hostAddress)
            .putLong(IP_ADDR_TIME_KEY, System.currentTimeMillis())
            .apply()
    return addr.hostAddress
}