package se.locutus.sl.realtidhem.events

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.RemoteViews
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.activity.WidgetConfigureActivity
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.service.TimeTracker
import se.locutus.sl.realtidhem.widget.*
import java.lang.Exception
import java.net.SocketTimeoutException
import java.util.logging.Logger
import android.os.PowerManager
import android.view.Gravity
import android.widget.Toast
import java.net.URLEncoder


const val CYCLE_STOP_LEFT = "CYCLE_STOP_LEFT"
const val CYCLE_STOP_RIGHT = "CYCLE_STOP_RIGHT"
const val EXTRA_COLOR_THEME = "EXTRA_COLOR_THEME"
const val EXTRA_RECONFIGURE_WIDGET = "EXTRA_RECONFIGURE_WIDGET"
const val EXTRA_THEME_CONFIG = "EXTRA_THEME_CONFIG"
const val STALE_MILLIS = 30000
const val UPDATE_RETRY_MILLIS = 10000
const val UPDATE_AUTO_RETRY_MILLIS = 2000L
const val UPDATE_FAIL_STALE = STALE_MILLIS - 5000
const val RETOUCH_MILLIS = 1200
const val TOUCH_TO_CONFIG = 3
const val MAX_ATTEMPTS = 3
const val CLEAR_TIME_MIN_MILLIS = 60 * 1000L
const val CLEAR_TIME_MAX_MILLIS = 80 * 1000L

interface TouchHandlerInterface {
    fun widgetTouched(widgetId :  Int, action : String?, userTouch : Boolean = true,
                      // Invoked with the lines we set on the widget.
                      loadedLinesCallback : (String, String, String) -> Unit = {_, _,_ -> })
    fun getInMemoryState() : InMemoryState
}

fun setWidgetTextViews(views : RemoteViews, centerLines: Boolean, line1 : String? = null, min: String? = null, line2 : String? = null, widgetTag : String? = null) {
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
    if (centerLines) {
        views.setInt(R.id.widgetline1, "setGravity", Gravity.CENTER)
        views.setInt(R.id.widgetline2, "setGravity", Gravity.CENTER)
    } else {
        views.setInt(R.id.widgetline1, "setGravity", Gravity.LEFT)
        views.setInt(R.id.widgetline2, "setGravity", Gravity.LEFT)
    }
}

fun openWidgetConfig(context : Context, color : Int?, widgetId: Int) {
    val intent = Intent(context, WidgetConfigureActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        if (color != null) {
            putExtra(EXTRA_COLOR_THEME, color)
            putExtra(EXTRA_RECONFIGURE_WIDGET, true)
        }
    }
    context.startActivity(intent)
}

fun getWidgetSelectedStopIndex(widgetId: Int, prefs: SharedPreferences) : Int {
    return prefs.getInt(widgetKeySelectedStop(widgetId), 0)
}

fun cycleSelectedStop(action : String?, prefs : SharedPreferences, widgetId: Int, configCount : Int) : Int {
    var selectedStopIndex = getWidgetSelectedStopIndex(widgetId, prefs)
    if (CYCLE_STOP_LEFT.equals(action)) {
        selectedStopIndex--
        if (selectedStopIndex < 0) {
            selectedStopIndex += configCount
        }
        setSelectedStopIndexManually(prefs, widgetId, selectedStopIndex)
    }

    if (CYCLE_STOP_RIGHT.equals(action)) {
        selectedStopIndex++
        if (selectedStopIndex >= configCount) {
            selectedStopIndex -= configCount
        }
        setSelectedStopIndexManually(prefs, widgetId, selectedStopIndex)
    }
    if (selectedStopIndex >= configCount) {
        selectedStopIndex = 0
    }
    return selectedStopIndex
}

class WidgetTouchHandler(val context: Context, val networkManager : NetworkInterface, val retryMillis : Long = UPDATE_AUTO_RETRY_MILLIS)
    : TouchHandlerInterface {
    companion object {
        val LOG = Logger.getLogger(WidgetTouchHandler::class.java.name)
    }
    internal val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    internal val timeTracker = TimeTracker(context)
    val mainHandler = Handler(Looper.getMainLooper())
    internal val inMemoryState = InMemoryState()

    override fun getInMemoryState(): InMemoryState {
        return inMemoryState
    }

    override fun widgetTouched(widgetId :  Int, action : String?, userTouch : Boolean, loadedLinesCallback : (String, String, String) -> Unit) {
        val widgetConfig = inMemoryState.getWidgetConfig(widgetId, prefs)
        val selectedStopIndex = cycleSelectedStop (action, prefs, widgetId, widgetConfig.stopConfigurationCount)

        if (CYCLE_STOP_RIGHT.equals(action) ||  (CYCLE_STOP_LEFT.equals(action))) {
            configUpdated(widgetId, false)
        }

        LOG.info("Selected stop index is $selectedStopIndex")

        val manager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (maybeAbortDueToPowerSave(widgetId, manager)) {
                return
            }
        }

        if (inMemoryState.sinceLastUpdate(widgetId) > STALE_MILLIS) {
            if (inMemoryState.sinceUpdateStarted(widgetId) > UPDATE_RETRY_MILLIS) {
                LOG.info("Triggering update for config for widget $widgetId")
                loadWidgetData(widgetId, manager, widgetConfig.getStopConfiguration(selectedStopIndex), 1, userTouch, loadedLinesCallback)
                // Only record if this was an update from a users interaction.
                if (userTouch) {
                    timeTracker.record(widgetId)
                    scheduleWidgetClearing(context, widgetId)
                }
            } else {
                stopTouchingMe(manager, widgetId)
            }
        } else if (userTouch && !inMemoryState.hasRunningThread(widgetId)) {
            val lastLoadedData = inMemoryState.lastLoadedData[widgetId]
            if (lastLoadedData != null) {
                inMemoryState.replaceAndStartThread(ScrollThread(
                    widgetId,
                    inMemoryState.remoteViews[widgetId]!!,
                    lastLoadedData.line2, context
                ))
            }
        } else {
            LOG.info("Widget information not stale, not doing anything $widgetId")
        }

        if (userTouch && inMemoryState.maybeIncrementTouchCountAndOpenConfig(widgetId)) {
            val lastLoadedData = inMemoryState.lastLoadedData[widgetId]
            openWidgetConfig(context, lastLoadedData?.color, widgetId)
        }
        return
    }

    @SuppressLint("NewApi")
    fun isUnlistedPowerSaveMode(pm : PowerManager) : Boolean {
        if (!pm.isPowerSaveMode) {
            return false
        }
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun maybeAbortDueToPowerSave(widgetId : Int, manager : AppWidgetManager) : Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        if (pm != null && isUnlistedPowerSaveMode(pm)) {
            if (inMemoryState.nextPowerSaveSettings) {
                tryLaunchBatteryOptimizationSettings()
                inMemoryState.nextPowerSaveSettings = false
                Toast.makeText(context, R.string.help_set_battery_optimization, Toast.LENGTH_LONG).show()
                return true
            } else {
                LOG.info("Power save mode detected without being whitelisted!")
                val views = inMemoryState.getRemoveViews(widgetId, prefs, context, false)
                val line2 = context.getString(R.string.power_save_mode_no_whitelist)
                setWidgetTextViews(views, false, context.getString(R.string.power_save_mode), "", line2)
                manager.updateAppWidget(widgetId, views)
                inMemoryState.replaceAndStartThread(ScrollThread(
                    widgetId, views, line2, context
                ))
                inMemoryState.nextPowerSaveSettings = true
                return true
            }
        }
        return false
    }

    private fun tryLaunchBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            return
        } catch (e : ActivityNotFoundException) {
            LOG.severe("Can't launch battery optimization settings $e using fallback")
        }
        val query = URLEncoder.encode(context.getString(R.string.disable_battery_optimization_query), "utf-8")
        val url = "https://www.google.com/search?q=$query"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(browserIntent)
    }

    private fun stopTouchingMe(manager : AppWidgetManager, widgetId : Int) {
        val views = inMemoryState.getRemoveViews(widgetId, prefs, context, false)
        val touchCount = inMemoryState.touchCount[widgetId]
        if (touchCount == 1) {
            views.setTextViewText(R.id.widgetline2, context.getString(R.string.message_again))
            views.setTextViewText(R.id.widgetline1, context.getString(R.string.message_again_line1))
        } else if (touchCount == 2) {
            views.setTextViewText(R.id.widgetline2, context.getString(R.string.one_more_time_config))
        }
        manager.updateAppWidget(widgetId, views)
    }

    fun configUpdated(widgetId: Int, selectNewFromLocation : Boolean) {
        inMemoryState.resetWidgetInMemoryState(widgetId)

        val appWidgetManager = AppWidgetManager.getInstance(context)
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

    fun loadWidgetData(widgetId :  Int, manager : AppWidgetManager, stopConfig : Ng.StopConfiguration, attempt : Int, userTouch : Boolean,
                       loadedLinesCallback : (String, String, String) -> Unit) {
        inMemoryState.disposeScroller(widgetId)
        inMemoryState.updateStartedAt[widgetId] = System.currentTimeMillis()
        // Create new views here to make sure we don't overfill the previous views with actions.
        val views = inMemoryState.getRemoveViews(widgetId, prefs, context, true)

        val line1 = if (attempt > 1) context.getString(R.string.updating_attempt, attempt) else context.getString(R.string.updating)
        setWidgetTextViews(views, false, line1, "", context.getString(R.string.updating), stopConfig.stopData.displayName)
        manager.updateAppWidget(widgetId, views)

        val stopDataRequest = Ng.StopDataRequest.newBuilder()
            .setSiteId(stopConfig.stopData.siteId)
            .setSite(stopConfig.stopData.site)
            .setDeparturesFilter(stopConfig.departuresFilter)
            .addAllLineFilter(stopConfig.lineFilterList)
            .build()
        val time = System.currentTimeMillis()
        val requestId = networkManager.doStopDataRequest(stopDataRequest) {
                incomingRequestId : Int, responseData: Ng.ResponseData, e: Exception? ->
            LOG.info("Got response in ${System.currentTimeMillis() - time} ms")
            handleLoadResponse(views, widgetId, incomingRequestId, responseData, e, stopConfig, userTouch, loadedLinesCallback)
        }
        inMemoryState.currentRequestId[widgetId] = requestId

        if (attempt < MAX_ATTEMPTS) {
            mainHandler.postDelayed({
                if (inMemoryState.shouldRetry(widgetId, retryMillis)) {
                    LOG.info("retrying update...")
                    loadWidgetData(widgetId, manager, stopConfig , attempt + 1, userTouch, loadedLinesCallback)
                }
            }, retryMillis * attempt)
        }
    }

    private fun handleLoadResponse(views : RemoteViews, widgetId : Int, incomingRequestId : Int, responseData: Ng.ResponseData, e: Exception?, stopConfig : Ng.StopConfiguration, userTouch : Boolean,
                           loadedLinesCallback : (String, String, String) -> Unit) {
        val currentRequestId = inMemoryState.currentRequestId[widgetId]
        if (currentRequestId != null && incomingRequestId != currentRequestId) {
            LOG.info("Not handling network response due to requestId mismatch got $incomingRequestId wanted $currentRequestId")
            return
        }
        inMemoryState.updateStartedAt.remove(widgetId)
        val manager = AppWidgetManager.getInstance(context)
        if (e != null) {
            handleException(views, widgetId, e, loadedLinesCallback)
        } else {
            if (responseData.hasErrorResponse() && responseData.errorResponse.errorType != Ng.ErrorType.UNKNOWN_ERROR) {
                handleError(views, widgetId, responseData.errorResponse, loadedLinesCallback)
            } else {
                val loadResponse = responseData.loadResponse
                val time = System.currentTimeMillis()
                if (loadResponse.line1.isNotEmpty()) {
                    setWidgetTextViews(views, false, loadResponse.line1, loadResponse.minutes, loadResponse.line2)
                    loadedLinesCallback(loadResponse.line1, loadResponse.minutes, loadResponse.line2)
                    if (!stopConfig.themeData.colorConfig.overrideMainColor) {
                        views.setInt(R.id.widgetcolor, "setBackgroundColor", responseData.loadResponse.color)
                    }
                    // Scroller only runs from user touching widget.
                    if (userTouch) {
                        inMemoryState.replaceAndStartThread(
                            ScrollThread(
                                widgetId, views, responseData.loadResponse.line2, context
                            )
                        )
                    }
                } else {
                    setWidgetTextViews(
                        views,
                        false,
                        context.getString(R.string.no_data),
                        "",
                        context.getString(R.string.no_data_detail)
                    )
                    loadedLinesCallback(context.getString(R.string.no_data), "", context.getString(R.string.no_data_detail))
                }
                inMemoryState.putLastLoadDataInMemory(prefs, widgetId, loadResponse)
                inMemoryState.updatedAt[widgetId] = time
            }
        }
        manager.updateAppWidget(widgetId, views)
        if (userTouch) {
            scheduleWidgetClearing(context, widgetId)
        }
    }

    private fun handleError(views : RemoteViews, widgetId: Int, e : Ng.LoadErrorResponse,
                            loadedLinesCallback : (String, String, String) -> Unit) {
        LOG.warning("Error loading data $e")

        var line2 = context.getString(R.string.error_details_try_again)
        var errorResource = R.string.error
        if (e.errorType == Ng.ErrorType.SL_API_ERROR) {
            line2 = context.getString(R.string.sl_api_error_detail, e.message)
            errorResource = R.string.sl_api_error
        }
        setWidgetTextViews(views, false, context.getString(errorResource), "", line2)
        loadedLinesCallback(context.getString(errorResource), "", line2)

        inMemoryState.putLastLoadDataInMemory(prefs, widgetId, Ng.WidgetLoadResponseData.getDefaultInstance())
        inMemoryState.updatedAt[widgetId] = System.currentTimeMillis() - UPDATE_FAIL_STALE
        inMemoryState.replaceAndStartThread(ScrollThread(
            widgetId, views, line2, context
        ))
    }

    private fun handleException(views : RemoteViews, widgetId: Int, e : Exception,
                                loadedLinesCallback : (String, String, String) -> Unit) {
        LOG.severe("Exception loading data $e")
        e.printStackTrace()
        val errorDetailString = context.getString(R.string.error_details_try_again)
        var errorResource = R.string.error
        if (e is com.android.volley.TimeoutError || e is SocketTimeoutException) {
            errorResource = R.string.error_timeout
        }
        setWidgetTextViews(views, false, context.getString(errorResource), "", errorDetailString)
        loadedLinesCallback(context.getString(errorResource), "", errorDetailString)

        inMemoryState.putLastLoadDataInMemory(prefs, widgetId, Ng.WidgetLoadResponseData.newBuilder()
            .setLine2(errorDetailString).build())
        inMemoryState.updatedAt[widgetId] = System.currentTimeMillis() - UPDATE_FAIL_STALE
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

    class ScrollThread(
        val widgetId: Int,
        private val views : RemoteViews,
        private val theLine: String,
        private val context: Context
    ) : Thread("ScrollerThread-$widgetId") {
        var running = true
        var agressiveOff = false

        override fun run() {
            val manager = AppWidgetManager.getInstance(context)
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
                // java.lang.RuntimeException: android.os.TransactionTooLargeException: data parcel size 558860 bytes
                manager.updateAppWidget(widgetId, views)
                i++

                try {
                    Thread.sleep(scrollSpeed) //sakta = 150, snabbt = 70
                } catch (e: InterruptedException) {
                }
            }

            if (!agressiveOff) {
                views.setTextViewText(R.id.widgetline2, line2)
                manager.updateAppWidget(widgetId, views)
                running = false
            }
        }
    }
}