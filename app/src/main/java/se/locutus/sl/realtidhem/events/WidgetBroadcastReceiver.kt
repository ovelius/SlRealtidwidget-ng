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
import android.os.PersistableBundle
import se.locutus.sl.realtidhem.widget.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.widget.loadWidgetConfigOrDefault


class WidgetBroadcastReceiver  : BroadcastReceiver() {
    companion object {
        val LOG = Logger.getLogger(WidgetBroadcastReceiver::class.java.name)
    }
    override fun onReceive(context: Context?, incomingIntent: Intent?) {
        val widgetId = incomingIntent!!.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        LOG.info("Received intent $incomingIntent with extra $widgetId")
        val intent = Intent(context, BackgroundUpdaterService::class.java)
        intent.putExtras(incomingIntent)

        val prefs = context!!.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        val widgetConfig = loadWidgetConfigOrDefault(prefs, widgetId)
        val line = if (widgetConfig.stopConfigurationCount > 0) widgetConfig.getStopConfiguration(0).stopData.canonicalName else  "faiiiiiiil"

        ScrollThread(widgetId, line, context!!).start()
        scheduleSingleUpdate(context, widgetId)
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



    internal class ScrollThread(
        private val widgetId: Int,
        private val theLine: String,
        private val context: Context
    ) : Thread() {
        private var running = true

        fun setRunning(r: Boolean) {
            running = r
        }

        override fun run() {
            val manager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
            val views = RemoteViews(context.packageName, R.layout.widgetlayout_base)
            var line2 = theLine

            var s = line2 + "     " + line2
            val set = arrayOfNulls<String>(line2.length + 5)
            for (i in set.indices) {
                set[i] = s.substring(i)
            }

            val scrollSpeed = 100
            val length = line2.length + 5
            var i = 0

            while (i < length && running) {
                s = set[i]!!

                views.setTextViewText(R.id.widgettext, s)
                manager.updateAppWidget(widgetId, views)
                i++

                try {
                    Thread.sleep(scrollSpeed.toLong()) //sakta = 150, snabbt = 70
                } catch (e: InterruptedException) {
                }

            }
            views.setTextViewText(R.id.widgettext, line2)
            manager.updateAppWidget(widgetId, views)
        }
    }
}

@SuppressLint("NewApi")
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
        views.setTextViewText(R.id.widgettext, "done")
        manager.updateAppWidget(widgetId, views)
        stopSelf()
        return true
    }

}