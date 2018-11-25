package se.locutus.sl.realtidhem.events

import android.app.job.JobParameters
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.logging.Logger
import android.widget.RemoteViews
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.net.NetworkManager

const val WIDGET_CONFIG_UPDATED = "widget_config_updated"

class WidgetBroadcastReceiver  : BroadcastReceiver() {

    companion object {
        var widgetTouchHandler : WidgetTouchHandler? = null
        val LOG = Logger.getLogger(WidgetBroadcastReceiver::class.java.name)
    }
    override fun onReceive(context: Context?, incomingIntent: Intent?) {
        if (context == null) {
            LOG.warning("Broadcast without context :(")
        }
        if (widgetTouchHandler == null) {
            LOG.info("Creating main widget handler")
            widgetTouchHandler = WidgetTouchHandler(context!!, NetworkManager(context))
        }
        val widgetId = incomingIntent!!.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        LOG.info("Received intent $incomingIntent with extra $widgetId action ${incomingIntent.action}")

        if (WIDGET_CONFIG_UPDATED.equals(incomingIntent.action)) {
            LOG.info("Received broadcast about updated config for $widgetId")
            widgetTouchHandler!!.configUpdated(widgetId, true)
        } else {
            widgetTouchHandler!!.widgetTouched(widgetId, incomingIntent.action)
        }
    }

}

class ResetWidget : JobService() {
    override fun onStopJob(params: JobParameters?): Boolean {
        WidgetBroadcastReceiver.LOG.info("job stopped!")
        return false
    }

    fun getRemoveViews(widgetId : Int) : RemoteViews {
        val handler = WidgetBroadcastReceiver.widgetTouchHandler
        if (handler != null) {
            return handler.inMemoryState.getRemoveViews(widgetId, this, false)
        }
        return RemoteViews(packageName, R.layout.widgetlayout_base)
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val widgetId: Int = params!!.extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
        WidgetBroadcastReceiver.LOG.info("job started, clearing widget $widgetId!")
        val manager = getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val views = getRemoveViews(widgetId)
        views.setTextViewText(R.id.widgetline2,  getString(R.string.idle_line2))
        views.setTextViewText(R.id.widgetline1, getString(R.string.idle_line1))
        views.setTextViewText(R.id.widgetmin, "")
        manager.updateAppWidget(widgetId, views)
        return false
    }

}