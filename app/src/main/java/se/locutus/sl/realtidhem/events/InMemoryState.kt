package se.locutus.sl.realtidhem.events

import android.content.Context
import android.content.SharedPreferences
import android.widget.RemoteViews
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.widget.getWidgetLayoutId
import se.locutus.sl.realtidhem.widget.loadWidgetConfigOrDefault
import se.locutus.sl.realtidhem.widget.putLastLoadData
import java.util.concurrent.ConcurrentHashMap

/**
 * In memory state related to all widget.
 * Most state can be recovered from settings if needed.
 */
internal class InMemoryState {
    private var scrollThread : WidgetTouchHandler.ScrollThread? = null
    var nextPowerSaveSettings = false
    var lastTouch = ConcurrentHashMap<Int, Long>()
    var updatedAt = ConcurrentHashMap<Int, Long>()
    var updateStartedAt = ConcurrentHashMap<Int, Long>()
    var currentRequestId = ConcurrentHashMap<Int, Int>()
    var touchCount = ConcurrentHashMap<Int, Int>()
    var remoteViews = ConcurrentHashMap<Int, RemoteViews>()
    private var widgetConfigs = ConcurrentHashMap<Int, Ng.WidgetConfiguration>()
    var lastLoadedData = ConcurrentHashMap<Int, Ng.WidgetLoadResponseData>()

    fun replaceAndStartThread(thread : WidgetTouchHandler.ScrollThread?, agressiveOff : Boolean = false) : WidgetTouchHandler.ScrollThread? {
        val replaced = scrollThread
        if (scrollThread != null) {
            scrollThread!!.running = false
            scrollThread!!.agressiveOff = agressiveOff
        }
        scrollThread = thread
        thread?.start()
        return replaced
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

    fun shouldRetry(widgetId: Int, retryMillis : Long) : Boolean {
        // Only retry if there wasn't a recent update.
        return sinceLastUpdate(widgetId) > retryMillis &&
            // And user hasn't tried to update something else recently.
            sinceUpdateStarted(widgetId)  > retryMillis
    }

    fun getRemoveViews(widgetId: Int, prefs: SharedPreferences, context : Context, forceNewViews : Boolean) : RemoteViews {
        if (remoteViews[widgetId] == null || forceNewViews) {
            // RemoteViews will fill with actions over time, so maybe create new views to
            // avoid TransactionTooLargeException and slowdowns.
            remoteViews[widgetId] =
                    RemoteViews(context.packageName, getWidgetLayoutId(prefs, widgetId))
        }
        return remoteViews[widgetId]!!
    }

    fun hasRunningThread(widgetId: Int) : Boolean {
        return threadWidgetId() == widgetId && scrollThread?.running == true
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
        this.lastTouch[widgetId] = System.currentTimeMillis()
        return touchCount[widgetId]!! >= TOUCH_TO_CONFIG
    }

    fun getWidgetConfig(widgetId : Int, prefs : SharedPreferences) : Ng.WidgetConfiguration {
        if (!widgetConfigs.containsKey(widgetId)) {
            WidgetTouchHandler.LOG.info("Loading config for widget $widgetId")
            widgetConfigs[widgetId] = loadWidgetConfigOrDefault(prefs, widgetId)
        }
        return widgetConfigs[widgetId]!!
    }

    fun resetWidgetInMemoryState(widgetId : Int) {
        widgetConfigs.remove(widgetId)
        lastTouch.remove(widgetId)
        lastLoadedData.remove(widgetId)
        updatedAt.remove(widgetId)
        updateStartedAt.remove(widgetId)
        replaceAndStartThread(null, true)
    }

    override fun toString() : String {
        return "lastTouch: $lastTouch\n widgetConfigs loaded: ${widgetConfigs.keys}"
    }
}