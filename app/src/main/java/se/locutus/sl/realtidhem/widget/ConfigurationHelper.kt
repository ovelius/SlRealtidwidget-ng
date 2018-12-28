package se.locutus.sl.realtidhem.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Base64
import se.locutus.proto.Ng

const val LEARNING_UPDATE_MODE = 0
const val MANUAL_UPDATE_MODE = 1
const val ALWAYS_UPDATE_MODE = 2

fun widgetKey(widgetId : Int) : String {
    return "widget_$widgetId"
}
fun widgetKeyLastData(widgetId : Int) : String {
    return "widget_last_load$widgetId"
}
fun widgetKeySelectedStop(widgetId : Int) : String {
    return "widget_selected_stop$widgetId"
}

// TODO This should be in proto?
fun widgetKeyUpdateMode(widgetId : Int) : String {
    return "widget_update_mode$widgetId"
}

fun widgetKeyAlwaysUpdateEndTime(widgetId : Int) : String {
    return "widget_always_update_end_time$widgetId"
}

fun widgetKeyStopSelectedAt(widgetId : Int) : String {
    return "widget_stop_selected_at$widgetId"
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
        .remove(widgetKeyStopSelectedAt(widgetId))
        .remove(widgetKeyUpdateMode(widgetId))
        .remove(widgetKeyAlwaysUpdateEndTime(widgetId))
        .apply()
}

fun putLastLoadData(prefs : SharedPreferences, widgetId: Int, response : Ng.WidgetLoadResponseData) {
    val widgetKey = widgetKeyLastData(widgetId)
    val edit = prefs.edit()
    val data = Base64.encodeToString(response.toByteArray(), 0)
    edit.putString(widgetKey, data).apply()
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

fun storeWidgetConfig(prefs : SharedPreferences, config : Ng.WidgetConfiguration) {
    val widgetKey = widgetKey(config.widgetId.toInt())
    val edit = prefs.edit()
    val data = Base64.encodeToString(config.toByteArray(), 0)
    edit.putString(widgetKey, data).commit()
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

fun getAllWidgetIds(context : Context) : IntArray {
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, StandardWidgetProvider::class.java)
    return manager.getAppWidgetIds(component)
}