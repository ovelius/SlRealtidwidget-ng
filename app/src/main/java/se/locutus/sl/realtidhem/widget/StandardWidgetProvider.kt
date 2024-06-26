package se.locutus.sl.realtidhem.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.activity.getInteractionsToLearn
import se.locutus.sl.realtidhem.activity.getLearningPeriods
import se.locutus.sl.realtidhem.activity.getUpdateSequenceLength
import se.locutus.sl.realtidhem.events.CYCLE_STOP_LEFT
import se.locutus.sl.realtidhem.events.CYCLE_STOP_RIGHT
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.events.setWidgetTextViews
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import se.locutus.sl.realtidhem.service.EXTRA_MANUAL_TOUCH
import se.locutus.sl.realtidhem.service.TimeTracker
import se.locutus.sl.realtidhem.service.sortRecordsByTimeAndCutoff
import java.util.logging.Logger


/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [WidgetConfigureActivity]
 */
class StandardWidgetProvider : AppWidgetProvider() {
    companion object {
        val LOG = Logger.getLogger(StandardWidgetProvider::class.java.name)
        fun basePendingIntent(context: Context, widgetId : Int, action : String?, targetService: Boolean) : PendingIntent {
            val target = if (targetService) BackgroundUpdaterService::class.java else WidgetBroadcastReceiver::class.java
            val intent = Intent(context, target).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            if (action != null) {
                intent.action = action
            }
            if (targetService) {
                intent.putExtra(EXTRA_MANUAL_TOUCH, true)
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(
                        context,
                        widgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    PendingIntent.getService(
                        context,
                        widgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                return PendingIntent.getService(context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE  or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            return PendingIntent.getBroadcast(context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE  or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    lateinit var timeTracker : TimeTracker

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        LOG.info("OnUpdate ${appWidgetIds.size} widgets")
        val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        timeTracker = TimeTracker(context)
        val widgetsNeedingLocation = HashMap<Int, Ng.WidgetConfiguration>()
        for (id in appWidgetIds) {
            val widgetConfig = loadWidgetConfigOrDefault(prefs, id)
            if (widgetConfig.stopConfigurationCount > 1) {
                widgetsNeedingLocation[id] = widgetConfig
            }
            LOG.info("Updating $id")
            updateAppWidget(context, widgetConfig, appWidgetManager, prefs, id)
            LOG.info("Compacting widget touch records to ${timeTracker.compactRecords(id)}")
            if (widgetConfig.updateSettings.updateMode == Ng.UpdateMode.LEARNING_UPDATE_MODE)  {
                scheduleWidgetUpdates(id, widgetConfig.updateSettings)
            }
        }

        if (widgetsNeedingLocation.isNotEmpty()) {
            LOG.info("Found widgets with multiple stops ${widgetsNeedingLocation.keys}")
            forceSingleLocationUpdate(context)
            requestSingleLocationUpdate(context, widgetsNeedingLocation, prefs, appWidgetManager)
       }
    }

    private fun scheduleWidgetUpdates(widgetId: Int, updateSettings : Ng.UpdateSettings) {
        val recordsWithCutoff = timeTracker.getRecords(
            widgetId, getInteractionsToLearn(updateSettings))
        val filteredSortedList : MutableList<TimeTracker.TimeRecord> =
            sortRecordsByTimeAndCutoff(
                recordsWithCutoff, getInteractionsToLearn(updateSettings), getLearningPeriods(updateSettings))
        LOG.info("Scheduling ${filteredSortedList.size} update periods for $widgetId")
        timeTracker.scheduleAlarmsFrom(widgetId, filteredSortedList)
    }

    private fun forceSingleLocationUpdate(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            LOG.warning("No permission to access location!")
            return
        }
        val mLocationRequest: LocationRequest = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 120000L)
            .setDurationMillis(10000L)
            .build()
        val mLocationCallback: LocationCallback = object : LocationCallback() {}
        LocationServices.getFusedLocationProviderClient(context)
            .requestLocationUpdates(mLocationRequest, mLocationCallback, null)
    }

    fun requestSingleLocationUpdate(context: Context,
                                    widgetsNeedingLocation : Map<Int, Ng.WidgetConfiguration>,
                                    prefs : SharedPreferences,
                                    appWidgetManager: AppWidgetManager) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            LOG.warning("No permission to access location!")
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                LOG.info("Reported location of $location")
                if (location != null) {
                    setSelectedStopIndexBasedOnLocation(
                        context, widgetsNeedingLocation, prefs, appWidgetManager, location)
                }
            }
    }

    fun setSelectedStopIndexBasedOnLocation(context : Context,
                                            ids : Map<Int, Ng.WidgetConfiguration>,
                                            prefs : SharedPreferences,
                                            appWidgetManager: AppWidgetManager,
                                            location : Location) {
        for (widgetId in ids.keys) {
            val widgetConfig = ids[widgetId]!!
            val closestStopIndex = getStopClosestToLocation(widgetConfig, location)
            val config = widgetConfig.stopConfigurationList[closestStopIndex]
            LOG.info("Setting selected stop for $widgetId to ${config.stopData.canonicalName} based on location")
            setSelectedStopIndexFromLocation(prefs, widgetId,
                closestStopIndex, location, config)
            updateAppWidget(context, widgetConfig, appWidgetManager, prefs, widgetId)
        }
    }


    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        for (id in appWidgetIds) {
            LOG.info("onDeleted $id")
            deleteWidget(prefs, id)
        }
    }

    override fun onEnabled(context: Context) {
        LOG.info("onEnabled")
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
        internal fun updateAppWidget(
            context: Context,
            widgetConfig : Ng.WidgetConfiguration,
            appWidgetManager: AppWidgetManager,
            prefs : SharedPreferences,
            appWidgetId: Int
        ) {
            setWidgetViews(context, widgetConfig, appWidgetManager, prefs, appWidgetId)
        }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        val prefs = context!!.getSharedPreferences(WIDGET_CONFIG_PREFS,  0)
        val currentMinHeight = newOptions!!.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        var layout = R.layout.widgetlayout_base
        val cells = getCellsForSize(currentMinHeight)
        if (cells > 1) {
            layout = R.layout.widgetlayout_double
        }
        LOG.info("Widget $appWidgetId resize event cell $cells ")
        prefs.edit().putBoolean(widgetLargeLayoutKey(appWidgetId), layout == R.layout.widgetlayout_double).apply()

        sendWidgetUpdateBroadcast(context, appWidgetId, null)

        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun getCellsForSize(size: Int): Int {
        var n = 2
        while (70 * n - 30 < size) {
            ++n
        }
        return n - 1
    }
}

fun setWidgetViews(context: Context,
                 widgetConfig : Ng.WidgetConfiguration,
                 appWidgetManager: AppWidgetManager,
                 prefs : SharedPreferences,
                 appWidgetId: Int) {
    val lastData = getLastLoadData(prefs, appWidgetId)
    var selectedStopIndex = prefs.getInt(widgetKeySelectedStop(appWidgetId), 0)
    if (selectedStopIndex >= widgetConfig.stopConfigurationCount) {
        selectedStopIndex = 0
    }

    val validConfig = widgetConfig.stopConfigurationCount > 0
    var widgetText =
        when (validConfig) {
            false -> context.getString(R.string.error_corrupt)
            true -> widgetConfig.getStopConfiguration(selectedStopIndex).stopData.displayName
        }
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, getWidgetLayoutId(prefs, appWidgetId))
    if (lastData != null) {
        views.setInt(R.id.widgetcolor, "setBackgroundColor", lastData.color)
    }
    val alwaysUpdate = widgetConfig.updateSettings.updateMode == Ng.UpdateMode.ALWAYS_UPDATE_MODE
    val line1 = if (alwaysUpdate) context.getString(R.string.idle_line1_auto) else context.getString(R.string.idle_line1)
    var line2 = if (alwaysUpdate) context.getString(R.string.idle_line2_auto, getUpdateSequenceLength(widgetConfig.updateSettings)) else context.getString(R.string.idle_line2)
    if (lastData != null && lastData.idleMessage.isNotEmpty()) {
        line2 = lastData.idleMessage
    }

    if (validConfig) {
        val stopConfig = widgetConfig.getStopConfiguration(selectedStopIndex)
        if (isLegacyStop(stopConfig.stopData)) {
            line2 = context.getString(R.string.idle_line2_legacy)
        }
        setWidgetTextViews(views, true, line1, "", line2, widgetText)
        updateColors(context, views, stopConfig.themeData.colorConfig)
        setPendingIntents(context, views, appWidgetId, alwaysUpdate)
    } else {
        StandardWidgetProvider.LOG.warning("Received update request for widget without configuration $appWidgetId")
    }
    if (widgetConfig.stopConfigurationCount <= 1) {
        views.setViewVisibility(R.id.rarrow, View.GONE)
        views.setViewVisibility(R.id.larrow, View.GONE)
    } else {
        views.setViewVisibility(R.id.rarrow, View.VISIBLE)
        views.setViewVisibility(R.id.larrow, View.VISIBLE)
    }
    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun updateColors(context : Context, views : RemoteViews, colorConfig : Ng.ColorConfig) {
    if (colorConfig.overrideMainColor) {
        views.setInt(R.id.widgetcolor, "setBackgroundColor", colorConfig.mainColor)
    }
    if (colorConfig.overrideBgColor) {
        views.setInt(R.id.widgetbg_layout, "setBackgroundColor", colorConfig.bgColor)
    } else {
        val bgColor = ContextCompat.getColor(context, R.color.baseWidgetGreyBg)
        views.setInt(R.id.widgetbg_layout, "setBackgroundColor", bgColor)
    }
    if (colorConfig.overrideTextColor) {
        views.setTextColor(R.id.widgetline1, colorConfig.textColor)
        views.setTextColor(R.id.widgetline2, colorConfig.textColor)
        views.setTextColor(R.id.widgetmin, colorConfig.textColor)
    } else {
        val textColor = ContextCompat.getColor(context, R.color.baseWidgetText)
        views.setTextColor(R.id.widgetline1, textColor)
        views.setTextColor(R.id.widgetline2,textColor)
        views.setTextColor(R.id.widgetmin, textColor)
    }
    if (colorConfig.overrideMiddleBarColor) {
        views.setInt(R.id.widgetseparator, "setBackgroundColor", colorConfig.middleBarColor)
    } else {
        val color = ContextCompat.getColor(context, R.color.baseWidgetGreyerBg)
        views.setInt(R.id.widgetseparator, "setBackgroundColor", color)
    }
    if (colorConfig.overrideTagTextColor) {
        views.setTextColor(R.id.widgettag, colorConfig.tagTextColor)
    } else {
        val textColor = ContextCompat.getColor(context, R.color.baseWidgetTagText)
        views.setTextColor(R.id.widgettag, textColor)
    }
}

fun setPendingIntents(context: Context, views : RemoteViews, widgetId : Int, targetService: Boolean) {
    val pendingIntent = StandardWidgetProvider.basePendingIntent(context, widgetId, null, targetService)
    val leftPendingIntent =
        StandardWidgetProvider.basePendingIntent(context, widgetId, CYCLE_STOP_LEFT, targetService)
    val rightPendingIntent =
        StandardWidgetProvider.basePendingIntent(context, widgetId, CYCLE_STOP_RIGHT, targetService)
    views.setOnClickPendingIntent(R.id.widgetmain, pendingIntent)
    views.setOnClickPendingIntent(R.id.larrow, leftPendingIntent)
    views.setOnClickPendingIntent(R.id.rarrow, rightPendingIntent)
}
