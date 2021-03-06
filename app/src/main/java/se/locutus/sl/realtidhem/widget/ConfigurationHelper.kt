package se.locutus.sl.realtidhem.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.util.Base64
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.WIDGET_CONFIG_UPDATED
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import java.lang.IllegalArgumentException

fun widgetKey(widgetId : Int) : String {
    return "widget_$widgetId"
}
fun widgetKeyLastData(widgetId : Int) : String {
    return "widget_last_load$widgetId"
}
fun widgetKeySelectedStop(widgetId : Int) : String {
    return "widget_selected_stop$widgetId"
}

fun widgetKeyStopSelectedAt(widgetId : Int) : String {
    return "widget_stop_selected_at$widgetId"
}

fun widgetKeyLayout(widgetId : Int) : String {
    return "widget_key_layout$widgetId"
}

fun loadWidgetConfigOrDefault(prefs : SharedPreferences, widgetId : Int) : Ng.WidgetConfiguration {
    val widgetKey = widgetKey(widgetId)
    if (prefs.contains(widgetKey)) {
        val bytes = Base64.decode(prefs.getString(widgetKey, ""), 0)
        return Ng.WidgetConfiguration.parseFrom(bytes)
    }
    return Ng.WidgetConfiguration.newBuilder().setWidgetId(widgetId.toLong()).build()
}

fun deleteWidget(prefs : SharedPreferences, widgetId : Int) {
    val edit = prefs.edit()
    edit.remove(widgetKey(widgetId))
        .remove(widgetKeyLastData(widgetId))
        .remove(widgetKeySelectedStop(widgetId))
        .remove(widgetKeyLayout(widgetId))
        .remove(widgetKeyStopSelectedAt(widgetId))
        .apply()
}

fun putLastLoadData(prefs : SharedPreferences, widgetId: Int, response : Ng.WidgetLoadResponseData) {
    val widgetKey = widgetKeyLastData(widgetId)
    val edit = prefs.edit()
    val data = Base64.encodeToString(response.toByteArray(), 0)
    edit.putString(widgetKey, data).apply()
}

fun getWidgetLayoutId(prefs : SharedPreferences, widgetId: Int) : Int {
    return prefs.getInt(widgetKeyLayout(widgetId), R.layout.widgetlayout_base)
}

fun getLastLoadData(prefs : SharedPreferences, widgetId: Int) : Ng.WidgetLoadResponseData? {
    val widgetKey = widgetKeyLastData(widgetId)
    if (prefs.contains(widgetKey)) {
        val bytes = Base64.decode(prefs.getString(widgetKey, ""), 0)
        return Ng.WidgetLoadResponseData.parseFrom(bytes)
    }
    return null
}

fun setSelectedStopIndex(prefs : SharedPreferences, widgetId: Int, selected : Int) {
    prefs.edit().putInt(widgetKeySelectedStop(widgetId),selected).apply()
}

fun widgetConfigToString(config : Ng.WidgetConfiguration) : String {
    return Base64.encodeToString(config.toByteArray(), 0)
}

fun storeWidgetConfig(prefs : SharedPreferences, config : Ng.WidgetConfiguration) {
    val widgetKey = widgetKey(config.widgetId.toInt())
    val edit = prefs.edit()
    edit.putString(widgetKey, widgetConfigToString(config)).commit()
}

fun getStopClosestToLocation(config : Ng.WidgetConfiguration, location : Location) : Int {
    var closestLocationIndex : Int = 0
    var closestLocationDistance : Float = Float.MAX_VALUE
    for (i in config.stopConfigurationList.indices) {
        val stopData = config.stopConfigurationList[i].stopData
        val stopLocation = Location("StopLocation").apply {
            latitude = stopData.lat
            longitude = stopData.lng
        }
        val distance = location.distanceTo(stopLocation)
        if (distance < closestLocationDistance) {
            closestLocationDistance = distance
            closestLocationIndex = i
        }
    }
    return closestLocationIndex
}

fun fromUserInputString(string : String, widgetId : Int) : Ng.WidgetConfiguration {
    var vettedString = string
    if (vettedString.contains(":")) {
        vettedString = vettedString.split(":")[1]
        vettedString = vettedString.trim()
    }
    val bytes = Base64.decode(vettedString, 0)
    return Ng.WidgetConfiguration.parseFrom(bytes).toBuilder().setWidgetId(widgetId.toLong()).build()
}

fun getAllWidgetIds(context : Context) : IntArray {
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, StandardWidgetProvider::class.java)
    return manager.getAppWidgetIds(component)
}

fun sendWidgetUpdateBroadcast(context : Context, widgetId : Int) {
    val intentUpdate = Intent(context, WidgetBroadcastReceiver::class.java).apply {
        action = WIDGET_CONFIG_UPDATED
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    context.sendBroadcast(intentUpdate)
}