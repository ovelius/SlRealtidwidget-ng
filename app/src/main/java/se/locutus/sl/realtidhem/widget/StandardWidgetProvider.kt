package se.locutus.sl.realtidhem.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import se.locutus.sl.realtidhem.R
import java.util.logging.Logger
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService


/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [WidgetConfigureActivity]
 */
class StandardWidgetProvider : AppWidgetProvider() {
    companion object {
        val LOG = Logger.getLogger(StandardWidgetProvider::class.java.name)
    }
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        LOG.info("OnUpdate $appWidgetIds")
        val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        for (id in appWidgetIds) {
            LOG.info("Updating $id")
            updateAppWidget(context, appWidgetManager, prefs, id)
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
            appWidgetManager: AppWidgetManager,
            prefs : SharedPreferences,
            appWidgetId: Int
        ) {
            val widgetConfig = loadWidgetConfigOrDefault(prefs, appWidgetId)
            val lastData = getLastLoadData(prefs, appWidgetId)
            val selectedStopIndex = prefs.getInt(widgetKeySelectedStop(appWidgetId), 0)
            val intent = Intent(context, WidgetBroadcastReceiver::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val widgetText = if (widgetConfig.stopConfigurationCount > 0) widgetConfig.getStopConfiguration(0).stopData.canonicalName else "Corrupt!"
            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, R.layout.widgetlayout_base)
            if (lastData != null) {
                views.setInt(R.id.widgetcolor, "setBackgroundColor", lastData.color)
            }
            views.setTextViewText(R.id.widgettag, widgetText)
            LOG.info("Creating pending intent for widget ${intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)}")
            views.setOnClickPendingIntent(R.id.widgetmain, pendingIntent)
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
}

