package se.locutus.sl.realtidhem.events

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import java.util.logging.Logger
import android.widget.RemoteViews
import se.locutus.sl.realtidhem.R
import android.content.ComponentName
import android.app.job.JobInfo
import android.graphics.Color
import android.os.PersistableBundle
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.net.NetworkManager
import se.locutus.sl.realtidhem.widget.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.widget.loadWidgetConfigOrDefault
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

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
            widgetTouchHandler = WidgetTouchHandler(context!!)
        }
        val widgetId = incomingIntent!!.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        LOG.info("Received intent $incomingIntent with extra $widgetId")

        widgetTouchHandler!!.widgetTouched(widgetId)

        scheduleSingleUpdate(context!!, widgetId)
    }

    @SuppressLint("NewApi")
    fun scheduleSingleUpdate(context: Context, widgetId: Int) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(widgetId)
        val builder = JobInfo.Builder(
            widgetId,
            ComponentName(context, ResetWidget::class.java)
        ).setMinimumLatency(30 * 1000)
            .setOverrideDeadline(60 * 1000)
            .setExtras((PersistableBundle().apply {
                putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }))
        scheduler.schedule(builder.build())
    }


}

class ResetWidget : JobService() {
    override fun onStopJob(params: JobParameters?): Boolean {
        WidgetBroadcastReceiver.LOG.info("job stopped!")
        return false
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val widgetId: Int = params!!.extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
        WidgetBroadcastReceiver.LOG.info("job started, clearing widget $widgetId!")
        val manager = getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val views = RemoteViews(packageName, R.layout.widgetlayout_base)
        views.setTextViewText(R.id.widgetline2, "done")
        views.setTextViewText(R.id.widgetline1, "done")
        views.setTextViewText(R.id.widgetmin, "")
        manager.updateAppWidget(widgetId, views)
        return false
    }

}