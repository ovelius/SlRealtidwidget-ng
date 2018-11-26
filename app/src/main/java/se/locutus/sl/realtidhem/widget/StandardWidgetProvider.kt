package se.locutus.sl.realtidhem.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.google.android.gms.location.LocationServices;
import se.locutus.sl.realtidhem.R
import java.util.logging.Logger
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.support.v4.content.ContextCompat
import android.view.View
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.events.CYCLE_STOP_LEFT
import se.locutus.sl.realtidhem.events.CYCLE_STOP_RIGHT
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.service.TimeTracker


/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [WidgetConfigureActivity]
 */
class StandardWidgetProvider : AppWidgetProvider() {
    companion object {
        val LOG = Logger.getLogger(StandardWidgetProvider::class.java.name)
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
            LOG.info("Time records for widget are ${timeTracker.buildRecords(id)}")
        }

        if (widgetsNeedingLocation.isNotEmpty()) {
            LOG.info("Found widgets with multiple stops ${widgetsNeedingLocation.keys}")
            requestSingleLocationUpdate(context, widgetsNeedingLocation, prefs, appWidgetManager)
       }
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
            LOG.info("Setting selected stop for $widgetId to $closestStopIndex based on location")
            setSelectedStopIndex(prefs, widgetId, closestStopIndex)
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

    fun basePendingIntent(context: Context, widgetId : Int, action : String? = null) : PendingIntent {
        val intent = Intent(context, WidgetBroadcastReceiver::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        if (action != null) {
            intent.action = action
        }
        return PendingIntent.getBroadcast(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
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
            val lastData = getLastLoadData(prefs, appWidgetId)
            var selectedStopIndex = prefs.getInt(widgetKeySelectedStop(appWidgetId), 0)
            if (selectedStopIndex >= widgetConfig.stopConfigurationCount) {
                selectedStopIndex = 0
            }
            val pendingIntent = basePendingIntent(context, appWidgetId)
            val leftPendingIntent = basePendingIntent(context, appWidgetId, CYCLE_STOP_LEFT)
            val rightPendingIntent = basePendingIntent(context, appWidgetId, CYCLE_STOP_RIGHT)

            val validConfig = widgetConfig.stopConfigurationCount > 0
            var widgetText =
                when (validConfig) {
                    false -> context.getString(R.string.error_corrupt)
                    true -> widgetConfig.getStopConfiguration(selectedStopIndex).stopData.displayName
                }
            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, R.layout.widgetlayout_base)
            if (lastData != null) {
                views.setInt(R.id.widgetcolor, "setBackgroundColor", lastData.color)
            }
            views.setTextViewText(R.id.widgettag, widgetText)
            views.setTextViewText(R.id.widgetline1, context.getString(R.string.idle_line1))
            views.setTextViewText(R.id.widgetmin, "")
            views.setTextViewText(R.id.widgetline2, context.getString(R.string.idle_line2))
            if (validConfig) {
                views.setOnClickPendingIntent(R.id.widgetmain, pendingIntent)
                views.setOnClickPendingIntent(R.id.larrow, leftPendingIntent)
                views.setOnClickPendingIntent(R.id.rarrow, rightPendingIntent)
            } else {
                LOG.warning("Received update request for widget without configuration $appWidgetId")
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
}

