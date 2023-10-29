package se.locutus.sl.realtidhem.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.util.Base64
import se.locutus.proto.Ng
import se.locutus.proto.Ng.SiteId
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.proto.Ng.StopConfigurationOrBuilder
import se.locutus.proto.Ng.StopData
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.WIDGET_CONFIG_UPDATED
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
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
fun widgetKeyLastLocation(widgetId : Int) : String {
    return "widget_last_location$widgetId"
}
fun widgetKeyLastDistance(widgetId : Int) : String {
    return "widget_last_distance$widgetId"
}

fun widgetKeyStopSelectedAt(widgetId : Int) : String {
    return "widget_stop_selected_at$widgetId"
}

@Deprecated("Should not be used", ReplaceWith("widgetLargeLayoutKey"))
fun widgetKeyLayout(widgetId : Int) : String {
    return "widget_key_layout$widgetId"
}

fun widgetLargeLayoutKey(widgetId : Int) : String {
    return "widget_large_layout_key$widgetId"
}

fun convertFromLegacyFormat(config : Ng.WidgetConfiguration) :  Ng.WidgetConfiguration {
    val configBuilder = Ng.WidgetConfiguration.newBuilder(config)
    configBuilder.clearStopConfiguration()
    for (stopConfig in config.stopConfigurationList) {
        val stopBuilder = StopConfiguration.newBuilder(stopConfig)
        if (!stopConfig.stopData.hasSite()) {
            val stopDataBuilder = Ng.StoredStopData.newBuilder(stopBuilder.stopData)

            stopDataBuilder.setSite(SiteId.newBuilder()
                .setSiteId(stopConfig.stopData.siteId))
            stopBuilder.setStopData(stopDataBuilder)
        }
        configBuilder.addStopConfiguration(stopBuilder)
    }
    return configBuilder.build()
}

fun isSiteConfigured(stop : StopConfigurationOrBuilder) : Boolean {
    val stopData = stop.stopData
    return stopData.site.siteId != 0L || stopData.site.strSiteId.isNotEmpty()
}

fun loadWidgetConfigOrDefault(prefs : SharedPreferences, widgetId : Int) : Ng.WidgetConfiguration {
    val widgetKey = widgetKey(widgetId)
    if (prefs.contains(widgetKey)) {
        val bytes = Base64.decode(prefs.getString(widgetKey, ""), 0)
        return convertFromLegacyFormat(Ng.WidgetConfiguration.parseFrom(bytes))
    }
    return Ng.WidgetConfiguration.newBuilder().setWidgetId(widgetId.toLong()).build()
}

fun deleteWidget(prefs : SharedPreferences, widgetId : Int) {
    val edit = prefs.edit()
    edit.remove(widgetKey(widgetId))
        .remove(widgetKeyLastData(widgetId))
        .remove(widgetKeySelectedStop(widgetId))
        .remove(widgetKeyLayout(widgetId))
        .remove(widgetLargeLayoutKey(widgetId))
        .remove(widgetKeyStopSelectedAt(widgetId))
        .remove(widgetKeyLastLocation(widgetId))
        .remove(widgetKeyLastDistance(widgetId))
        .apply()
}

fun putLastLoadData(prefs : SharedPreferences, widgetId: Int, response : Ng.WidgetLoadResponseData) {
    val widgetKey = widgetKeyLastData(widgetId)
    val edit = prefs.edit()
    val data = Base64.encodeToString(response.toByteArray(), 0)
    edit.putString(widgetKey, data).apply()
}

fun getWidgetLayoutId(prefs : SharedPreferences, widgetId: Int) : Int {
    val largeLayoutKey = widgetLargeLayoutKey(widgetId)
    if (prefs.contains(largeLayoutKey)) {
        if (prefs.getBoolean(widgetLargeLayoutKey(widgetId), false)) {
            return R.layout.widgetlayout_double
        } else {
            return R.layout.widgetlayout_base
        }
    }
    // Legacy behavior.
    val layoutId = prefs.getInt(widgetKeyLayout(widgetId), R.layout.widgetlayout_base)
    if (layoutId != R.layout.widgetlayout_double && layoutId != R.layout.widgetlayout_base) {
        return R.layout.widgetlayout_base
    }
    return layoutId
}

fun getLastLoadData(prefs : SharedPreferences, widgetId: Int) : Ng.WidgetLoadResponseData? {
    val widgetKey = widgetKeyLastData(widgetId)
    if (prefs.contains(widgetKey)) {
        val bytes = Base64.decode(prefs.getString(widgetKey, ""), 0)
        return Ng.WidgetLoadResponseData.parseFrom(bytes)
    }
    return null
}

fun setSelectedStopIndexManually(prefs : SharedPreferences, widgetId: Int, selected : Int) {
    prefs.edit()
        .putInt(widgetKeySelectedStop(widgetId), selected)
        // We no longer consider this stop to be selected via location.
        .remove(widgetKeyLastLocation(widgetId)).apply()
}

fun setSelectedStopIndexFromLocation(prefs : SharedPreferences, widgetId: Int, selected : Int,
                                     location : Location, stopConfig: Ng.StopConfiguration) {
    val stopLocation = Location("StopLocation").apply {
        latitude = stopConfig.stopData.lat
        longitude = stopConfig.stopData.lng
    }
    val distance = location.distanceTo(stopLocation)
    prefs.edit()
        .putInt(widgetKeySelectedStop(widgetId), selected)
        .putLong(widgetKeyLastLocation(widgetId), location.time)
        .putFloat(widgetKeyLastDistance(widgetId), distance)
        .apply()
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

fun sendWidgetUpdateBroadcast(context : Context, widgetId : Int, widgetConfig : Ng.WidgetConfiguration?) {
    val intentUpdate = Intent(context, WidgetBroadcastReceiver::class.java).apply {
        action = WIDGET_CONFIG_UPDATED
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    if (widgetConfig != null &&
        widgetConfig.updateSettings.updateMode == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE) {
        val intent = Intent(context, BackgroundUpdaterService::class.java)
        context!!.stopService(intent)
        context!!.startService(intent)
    }
    context.sendBroadcast(intentUpdate)
}