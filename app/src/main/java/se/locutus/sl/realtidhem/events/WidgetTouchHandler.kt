package se.locutus.sl.realtidhem.events

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.PersistableBundle
import android.view.View
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
const val STALE_MILLIS = 30000
const val UPDATE_RETRY_MILLIS = 10000
const val UPDATE_FAIL_STALE = STALE_MILLIS - 6000
const val RETOUCH_MILLIS = 1000
const val TOUCH_TO_CONFIG = 3
const val CLEAR_TIME_MIN_MILLIS = 60 * 1000L
const val CLEAR_TIME_MAX_MILLIS = 80 * 1000L

class WidgetTouchHandler(val context: Context) {
    companion object {
        val LOG = Logger.getLogger(WidgetTouchHandler::class.java.name)
    }
    internal val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    internal val networkManager = NetworkManager(context)
    internal val inMemoryState = InMemoryState()

    fun widgetTouched(widgetId :  Int, action : String?) {
        val widgetConfig = inMemoryState.getWidgetConfig(widgetId, prefs)
        var selectedStopIndex = prefs.getInt(widgetKeySelectedStop(widgetId), 0)

        if (CYCLE_STOP_LEFT.equals(action)) {
            selectedStopIndex--
            if (selectedStopIndex < 0) {
                selectedStopIndex += widgetConfig.stopConfigurationCount
            }
            setSelectedStopIndex(prefs, widgetId, selectedStopIndex)
            configUpdated(widgetId, false)
        }
        if (CYCLE_STOP_RIGHT.equals(action)) {
            selectedStopIndex++
            if (selectedStopIndex >= widgetConfig.stopConfigurationCount) {
                selectedStopIndex -= widgetConfig.stopConfigurationCount
            }
            setSelectedStopIndex(prefs, widgetId, selectedStopIndex)
            configUpdated(widgetId, false)
        }
        LOG.info("Selected stop index is $selectedStopIndex")

        if (inMemoryState.sinceLastUpdate(widgetId) > STALE_MILLIS) {
            if (inMemoryState.sinceUpdateStarted(widgetId) > UPDATE_RETRY_MILLIS) {
                LOG.info("Triggering update for config for widget $widgetId")
                loadWidgetData(widgetId, widgetConfig.getStopConfiguration(selectedStopIndex))
                scheduleWidgetClearing(context, widgetId)
            } else {
                // Be pesky...
            }
        } else if (!inMemoryState.hasRunningThread(widgetId)) {
            val lastLoadedData = inMemoryState.lastLoadedData[widgetId]
            if (lastLoadedData != null) {
                inMemoryState.replaceThread(ScrollThread(
                    widgetId,
                    inMemoryState.remoteViews[widgetId]!!,
                    lastLoadedData.line2, context
                ).apply { start() })
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

    fun configUpdated(widgetId: Int, selectNewFromLocation : Boolean) {
        inMemoryState.widgetConfigs.remove(widgetId)
        inMemoryState.lastTouch.remove(widgetId)
        inMemoryState.lastLoadedData.remove(widgetId)
        inMemoryState.updatedAt.remove(widgetId)
        inMemoryState.updateStartedAt.remove(widgetId)

        val appWidgetManager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val config = inMemoryState.getWidgetConfig(widgetId, prefs)
        StandardWidgetProvider().apply {
            updateAppWidget(context, config, appWidgetManager, prefs,  widgetId)
            if (config.stopConfigurationCount > 1 && selectNewFromLocation) {
                requestSingleLocationUpdate(
                    context,
                    mapOf(widgetId to config),
                    prefs,
                    appWidgetManager
                )
            }
        }

    }

    fun loadWidgetData(widgetId :  Int, stopConfig : Ng.StopConfiguration) {
        inMemoryState.disposeScroller(widgetId)
        inMemoryState.updateStartedAt[widgetId] = System.currentTimeMillis()
        val manager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val views = inMemoryState.getRemoveViews(widgetId, context)

        setWidgetTextViews(views, context.getString(R.string.updating), "", context.getString(R.string.updating), stopConfig.stopData.canonicalName)
        manager.updateAppWidget(widgetId, views)

        val stopDataRequest = Ng.StopDataRequest.newBuilder()
            .setSiteId(stopConfig.stopData.siteId)
            .setDeparturesFilter(stopConfig.departuresFilter)
            .build()
        val requestId = networkManager.doStopDataRequest(stopDataRequest) {
                incomingRequestId : Int, responseData: Ng.ResponseData, e: Exception? ->
            handleLoadResponse(views, widgetId, incomingRequestId, responseData, e)
        }
        inMemoryState.currentRequestId[widgetId] = requestId
    }

    fun handleLoadResponse(views : RemoteViews, widgetId : Int, incomingRequestId : Int, responseData: Ng.ResponseData, e: Exception?) {
        val currentRequestId = inMemoryState.currentRequestId[widgetId]
        if (currentRequestId != null && incomingRequestId != currentRequestId) {
            LOG.info("Not handling network response due to requestId mismatch got $incomingRequestId wanted $currentRequestId")
            return
        }
        inMemoryState.updateStartedAt.remove(widgetId)
        val manager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
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

            inMemoryState.putLastLoadDataInMemory(prefs, widgetId, loadResponse)
            inMemoryState.updatedAt[widgetId] = System.currentTimeMillis()
            inMemoryState.replaceThread(ScrollThread(
                widgetId, views, responseData.loadResponse.line2, context).apply {  start() })
        }
        manager.updateAppWidget(widgetId, views)
        scheduleWidgetClearing(context, widgetId)
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
        private var scrollThread : ScrollThread? = null
        var lastTouch = ConcurrentHashMap<Int, Long>()
        var updatedAt = ConcurrentHashMap<Int, Long>()
        var updateStartedAt = ConcurrentHashMap<Int, Long>()
        var currentRequestId = ConcurrentHashMap<Int, Int>()
        var touchCount = ConcurrentHashMap<Int, Int>()
        var remoteViews = ConcurrentHashMap<Int, RemoteViews>()
        var widgetConfigs = ConcurrentHashMap<Int, Ng.WidgetConfiguration>()
        var lastLoadedData = ConcurrentHashMap<Int, Ng.WidgetLoadResponseData>()

        fun replaceThread(thread : ScrollThread) {
            if (scrollThread != null) {
                scrollThread!!.running = false
            }
            scrollThread = thread
        }

        fun disposeScroller(widgetId : Int) {
            if (scrollThread?.widgetId == widgetId) {
                scrollThread!!.running = false
            }
        }

        fun threadWidgetId() : Int {
            if (scrollThread != null) {
                return scrollThread!!.widgetId
            }
            return Int.MIN_VALUE
        }

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
            return threadWidgetId() == widgetId
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

        fun sinceUpdateStarted(widgetId : Int) : Long {
            if (!updateStartedAt.containsKey(widgetId)) {
                return Long.MAX_VALUE
            }
            return System.currentTimeMillis() - updateStartedAt[widgetId]!!
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

        fun getWidgetConfig(widgetId : Int, prefs : SharedPreferences) : Ng.WidgetConfiguration {
            if (!widgetConfigs.containsKey(widgetId)) {
                LOG.info("Loading config for widget $widgetId")
                widgetConfigs[widgetId] = loadWidgetConfigOrDefault(prefs, widgetId)
            }
            return widgetConfigs[widgetId]!!
        }

        override fun toString() : String {
            return "lastTouch: $lastTouch\n widgetConfigs loaded: ${widgetConfigs.keys}"
        }
    }

    internal class ScrollThread(
        val widgetId: Int,
        private val views : RemoteViews,
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

            val scrollSpeed = 70L
            val length = line2.length + 5
            var i = 0

            while (i < length && running) {
                s = set[i]!!

                views.setTextViewText(R.id.widgetline2, s)
                /*
                31436-10465/? E/AndroidRuntime: FATAL EXCEPTION: ScrollerThread-19
                Process: se.locutus.sl.realtidhem, PID: 31436
                java.lang.RuntimeException: android.os.TransactionTooLargeException: data parcel size 543860 bytes */

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