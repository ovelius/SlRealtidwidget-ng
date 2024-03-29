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
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.net.NetworkManager
import se.locutus.sl.realtidhem.widget.getWidgetLayoutId
import se.locutus.sl.realtidhem.widget.setPendingIntents
import se.locutus.sl.realtidhem.widget.setWidgetViews

const val WIDGET_CONFIG_UPDATED = "widget_config_updated"

class WidgetBroadcastReceiver  : BroadcastReceiver() {

    companion object {
        var widgetTouchHandler : WidgetTouchHandler? = null
        fun getTouchHandler(context : Context) : TouchHandlerInterface {
            if (widgetTouchHandler == null) {
                widgetTouchHandler = WidgetTouchHandler(context.applicationContext, NetworkManager(context))
            }
            return widgetTouchHandler!!
        }
        val LOG = Logger.getLogger(WidgetBroadcastReceiver::class.java.name)
    }
    override fun onReceive(context: Context?, incomingIntent: Intent?) {
        if (context == null) {
            LOG.warning("Broadcast without context :(")
        }
        getTouchHandler(context!!)
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

    override fun onStartJob(params: JobParameters?): Boolean {
        val widgetId: Int = params!!.extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
        val prefs = getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        WidgetBroadcastReceiver.LOG.info("job started, clearing widget $widgetId!")
        val manager = getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val handler = WidgetBroadcastReceiver.getTouchHandler(this)
        setWidgetViews(this, handler.getInMemoryState().getWidgetConfig(widgetId, prefs),
            manager, prefs, widgetId)
        return false
    }

}