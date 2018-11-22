package se.locutus.sl.realtidhem.events

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.PersistableBundle
import android.widget.RemoteViews
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.net.NetworkManager
import se.locutus.sl.realtidhem.widget.*
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

const val CYCLE_STOP_LEFT = "CYCLE_STOP_LEFT"
const val CYCLE_STOP_RIGHT = "CYCLE_STOP_RIGHT"

class WidgetTouchHandler(val context: Context) {
    companion object {
        const val STALE_MILLIS = 30000
        const val UPDATE_FAIL_STALE = STALE_MILLIS - 10000
        const val RETOUCH_MILLIS = 1000
        const val TOUCH_TO_CONFIG = 3
        const val CLEAR_TIME_MIN_MILLIS = 60 * 1000L
        const val CLEAR_TIME_MAX_MILLIS = 80 * 1000L
        val LOG = Logger.getLogger(WidgetTouchHandler::class.java.name)
    }
    internal val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    internal val networkManager = NetworkManager(context)
    internal val inMemoryState = InMemoryState()

    fun widgetTouched(widgetId :  Int, action : String?) {
        if (!inMemoryState.widgetConfigs.containsKey(widgetId)) {
            LOG.info("Loading config for widget $widgetId")
            inMemoryState.widgetConfigs[widgetId] = loadWidgetConfigOrDefault(prefs, widgetId)
        }
        val widgetConfig = inMemoryState.widgetConfigs[widgetId]
        var selectedStopIndex = prefs.getInt(widgetKeySelectedStop(widgetId), 0)

        if (CYCLE_STOP_LEFT.equals(action)) {
            selectedStopIndex--
            if (selectedStopIndex < 0) {
                selectedStopIndex += widgetConfig!!.stopConfigurationCount
            }
            setSelectedStopIndex(prefs, widgetId, selectedStopIndex)
            configUpdated(widgetId)
        }
        if (CYCLE_STOP_RIGHT.equals(action)) {
            selectedStopIndex++
            if (selectedStopIndex >= widgetConfig!!.stopConfigurationCount) {
                selectedStopIndex -= widgetConfig!!.stopConfigurationCount
            }
            setSelectedStopIndex(prefs, widgetId, selectedStopIndex)
            configUpdated(widgetId)
        }
        LOG.info("Selected stop index is $selectedStopIndex")

        if (inMemoryState.sinceLastUpdate(widgetId) > STALE_MILLIS) {
            LOG.info("Triggering update for config for widget $widgetId")
            loadWidgetData(widgetId, widgetConfig!!.getStopConfiguration(selectedStopIndex))
            scheduleWidgetClearing(context!!, widgetId)
        } else if (!inMemoryState.hasRunningThread(widgetId)) {
            val lastLoadedData = inMemoryState.lastLoadedData[widgetId]
            if (lastLoadedData != null) {
                inMemoryState.scrollMap[widgetId] = ScrollThread(
                    inMemoryState.remoteViews[widgetId]!!,
                    widgetId, lastLoadedData.line2, context!!
                ).apply { start() }
            }
        }

        if (inMemoryState.maybeIncrementTouchCountAndOpenConfig(widgetId)) {
            val intent = Intent(context, WidgetConfigureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            context.startActivity(intent)
            return
        }
        inMemoryState.lastTouch[widgetId] = System.currentTimeMillis()
    }

    fun configUpdated(widgetId: Int) {
        inMemoryState.widgetConfigs.remove(widgetId)
        inMemoryState.lastTouch.remove(widgetId)
        inMemoryState.lastLoadedData.remove(widgetId)
        inMemoryState.updatedAt.remove(widgetId)
    }

    fun loadWidgetData(widgetId :  Int, stopConfig : Ng.StopConfiguration) {
        val scroller = inMemoryState.scrollMap[widgetId]
        if (scroller != null) {
            scroller.running = false
        }
        val manager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val views = inMemoryState.getRemoveViews(widgetId, context)

        views.setTextViewText(R.id.widgetline2, context.getString(R.string.updating))
        views.setTextViewText(R.id.widgettag, stopConfig.stopData.canonicalName)
        manager.updateAppWidget(widgetId, views)

        val stopDataRequest = Ng.StopDataRequest.newBuilder()
            .setSiteId(stopConfig.stopData.siteId)
            .setDeparturesFilter(stopConfig.departuresFilter)
            .build()
        networkManager.doStopDataRequest(stopDataRequest) {
                responseData: Ng.ResponseData, e: Exception? ->
            if (e != null) {
                e.printStackTrace()
                WidgetBroadcastReceiver.LOG.severe("Error loading data $e")
                val errorDetailString = context.getString(R.string.error_details_try_again)
                if (e is com.android.volley.TimeoutError) {
                    setWidgetTextViews(views, context.getString(R.string.error_timeout), "", errorDetailString)
                } else {
                    setWidgetTextViews(views, context.getString(R.string.error), "", errorDetailString)
                }
                inMemoryState.putLastLoadDataInMemory(prefs, widgetId, Ng.WidgetLoadResponseData.newBuilder()
                    .setLine2(errorDetailString).build())
                inMemoryState.updatedAt[widgetId] = System.currentTimeMillis() - UPDATE_FAIL_STALE
            } else {
                val loadResponse = responseData.loadResponse
                setWidgetTextViews(views, loadResponse.line1, loadResponse.minutes, loadResponse.line2)
                views.setInt(R.id.widgetcolor, "setBackgroundColor", responseData.loadResponse.color)

                inMemoryState.clearRunningThread(widgetId)
                inMemoryState.putLastLoadDataInMemory(prefs, widgetId, loadResponse)
                inMemoryState.updatedAt[widgetId] = System.currentTimeMillis()
                inMemoryState.scrollMap[widgetId] = ScrollThread(views, widgetId, responseData.loadResponse.line2, context!!).apply {  start() }
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    fun scheduleWidgetClearing(context: Context, widgetId: Int) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(widgetId)
        val builder = JobInfo.Builder(
            widgetId,
            ComponentName(context, ResetWidget::class.java)
        ).setMinimumLatency(CLEAR_TIME_MIN_MILLIS)
            .setOverrideDeadline(CLEAR_TIME_MAX_MILLIS)
            .setExtras((PersistableBundle().apply {
                putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }))
        scheduler.schedule(builder.build())
    }


    fun setWidgetTextViews(views : RemoteViews, line1 : String? = null, min: String? = null, line2 : String? = null, widgetTag : String? = null) {
        if (line1 != null) {
            views.setTextViewText(R.id.widgetline1, line1)
        }
        if (min != null) {
            views.setTextViewText(R.id.widgetmin, min)
        }
        if (line2 != null) {
            views.setTextViewText(R.id.widgetline2, line2)
        }
        if (widgetTag != null) {
            views.setTextViewText(R.id.widgettag, widgetTag)
        }
    }

    internal class InMemoryState {
        var scrollMap = ConcurrentHashMap<Int, ScrollThread>()
        var lastTouch = ConcurrentHashMap<Int, Long>()
        var updatedAt = ConcurrentHashMap<Int, Long>()
        var touchCount = ConcurrentHashMap<Int, Int>()
        var remoteViews = ConcurrentHashMap<Int, RemoteViews>()
        var widgetConfigs = ConcurrentHashMap<Int, Ng.WidgetConfiguration>()
        var lastLoadedData = ConcurrentHashMap<Int, Ng.WidgetLoadResponseData>()

        fun putLastLoadDataInMemory(prefs : SharedPreferences, widgetId : Int, response : Ng.WidgetLoadResponseData) {
            lastLoadedData[widgetId] = response
            putLastLoadData(prefs, widgetId, response)
        }

        fun getRemoveViews(widgetId: Int, context : Context) : RemoteViews {
            if (remoteViews[widgetId] == null) {
                remoteViews[widgetId] = RemoteViews(context.packageName, R.layout.widgetlayout_base)
            }
            return remoteViews[widgetId]!!
        }

        fun hasRunningThread(widgetId: Int) : Boolean {
            return scrollMap[widgetId] != null && scrollMap[widgetId]!!.running
        }

        fun clearRunningThread(widgetId: Int) {
            val thread = scrollMap[widgetId]
            if (thread != null) {
                thread.running = false
            }
        }

        fun sinceLastTouch(widgetId : Int) : Long {
            if (!lastTouch.containsKey(widgetId)) {
                return Long.MAX_VALUE
            }
            return System.currentTimeMillis() - lastTouch[widgetId]!!
        }

        fun sinceLastUpdate(widgetId : Int) : Long {
            if (!updatedAt.containsKey(widgetId)) {
                return Long.MAX_VALUE
            }
            return System.currentTimeMillis() - updatedAt[widgetId]!!
        }

        fun maybeIncrementTouchCountAndOpenConfig(widgetId : Int) : Boolean {
            val lastTouch = sinceLastTouch(widgetId)
            if (lastTouch > RETOUCH_MILLIS ||  !touchCount.containsKey(widgetId)) {
                touchCount[widgetId] = 1
            } else {
                touchCount[widgetId] = touchCount[widgetId]!! + 1
            }
            return touchCount[widgetId]!! >= TOUCH_TO_CONFIG
        }

        override fun toString() : String {
            return "lastTouch: $lastTouch\n widgetConfigs loaded: ${widgetConfigs.keys}"
        }
    }

    internal class ScrollThread(
        private val views : RemoteViews,
        private val widgetId: Int,
        private val theLine: String,
        private val context: Context
    ) : Thread("ScrollerThread-$widgetId") {
        var running = true

        override fun run() {
            val manager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
            var line2 = theLine

            var s = "$line2     $line2"
            val set = arrayOfNulls<String>(line2.length + 5)
            for (i in set.indices) {
                set[i] = s.substring(i)
            }

            val scrollSpeed = 100L
            val length = line2.length + 5
            var i = 0

            while (i < length && running) {
                s = set[i]!!

                views.setTextViewText(R.id.widgetline2, s)
                manager.updateAppWidget(widgetId, views)
                i++

                try {
                    Thread.sleep(scrollSpeed) //sakta = 150, snabbt = 70
                } catch (e: InterruptedException) {
                }
            }
            views.setTextViewText(R.id.widgetline2, line2)
            manager.updateAppWidget(widgetId, views)
            running = false
        }
    }
}