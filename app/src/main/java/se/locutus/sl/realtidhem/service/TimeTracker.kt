package se.locutus.sl.realtidhem.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import android.app.AlarmManager
import android.os.Build
import java.util.logging.Logger


const val TIME_PREFS = "time_prefs"
// Each timeslot will keep the widget updated for 15 minutes.
const val UPDATE_TIME_MILLIS = 60 * 15 * 1000


fun sortRecordsByTimeAndCutoff(records : ArrayList<TimeTracker.TimeRecord> , countCutoff : Int) : ArrayList<TimeTracker.TimeRecord> {
    records.sortWith(Comparator { o1, o2 ->
        o2.count - o1.count
    })
    val filteredRecords = ArrayList<TimeTracker.TimeRecord>()
    for (record in records) {
        if (record.count >= countCutoff) {
            filteredRecords.add(record)
        }
    }
    filteredRecords.sort()
    return filteredRecords
}

/**
 * Helper class for finding/scheduling automatic updates for the widget.
 */
class TimeTracker(val context : Context) {
    companion object {
        val LOG = Logger.getLogger(TimeTracker::class.java.name)
    }

    private val prefs = context.getSharedPreferences(TIME_PREFS, 0)

    private fun isWeekDay(c : Calendar) : Boolean {
        return c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
    }

    /**
     * Translate to the 10 minutes slot before.
     */
    private fun translateMinutes(min : Int) : Int {
        val base = (min / 10) * 10
        val rest5 = min % 10
        return base + if (rest5 == 0) - 10 else 0
    }

    private fun createAlarmKey(widgetId : Int, hour : Int, min : Int, weekday: Boolean) : String {
        return "$widgetId:wd:$weekday:$hour:$min"
    }

    fun createRecordKey(widgetId : Int, c : Calendar) : String {
        return "$widgetId:wd:${isWeekDay(c)}:${c.get(Calendar.HOUR_OF_DAY)}:${translateMinutes(c.get(Calendar.MINUTE))}:${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun createPendingIntent(widgetId: Int) : PendingIntent {
        val intent = Intent(context, BackgroundUpdaterService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getService(context, widgetId*widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun scheduleAlarmsFrom(widgetId: Int, records : List<TimeRecord>) {
        val intent = createPendingIntent(widgetId)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (record in records) {
            if (record.weekday) {
                LOG.info("Scheduling weekday alarm ${record.hour}:${record.minute}")
                scheduleAlarmForDayRepeating(Calendar.MONDAY, intent, record, alarmManager)
                scheduleAlarmForDayRepeating(Calendar.TUESDAY, intent, record, alarmManager)
                scheduleAlarmForDayRepeating(Calendar.WEDNESDAY, intent, record, alarmManager)
                scheduleAlarmForDayRepeating(Calendar.THURSDAY, intent, record, alarmManager)
                scheduleAlarmForDayRepeating(Calendar.FRIDAY, intent, record, alarmManager)
            } else {
                LOG.info("Scheduling weekend alarm ${record.hour}:${record.minute}")
                scheduleAlarmForDayRepeating(Calendar.SATURDAY, intent, record, alarmManager)
                scheduleAlarmForDayRepeating(Calendar.SUNDAY, intent, record, alarmManager)
            }
        }
    }

    private fun scheduleAlarmForDayRepeating(day : Int, intent : PendingIntent, timeRecord: TimeRecord, alarmManager : AlarmManager) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, day)
        calendar.set(Calendar.HOUR_OF_DAY, timeRecord.hour)
        calendar.set(Calendar.MINUTE, timeRecord.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // Use RTC_WAKEUP ?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, intent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, intent)
        }
    }

    /**
     * Called to record an update of a widget based on user pressing it.
     */
    fun record(widgetId : Int) {
        recordUpdate(widgetId, Calendar.getInstance())
    }

    fun recordUpdate(widgetId : Int, c: Calendar) {
        val key = createRecordKey(widgetId, c)
        if (prefs.getInt(key, 0) == 0) {
            prefs.edit().putInt(key, 1).apply()
        }
    }

    fun getRecords(widgetId: Int, cutoffCount : Int) : ArrayList<TimeRecord> {
        val recordsList = ArrayList<TimeRecord>()
        val widgetStart = "$widgetId:"
        val allPrefs = prefs.all
        for (key in allPrefs.keys) {
            if (key.startsWith(widgetStart)) {
                val split = key.split(":")
                // Right type of record.
                if (split.size == 5) {
                    val weekDay = split[2].toBoolean()
                    val hour = split[3].toInt()
                    val min = split[4].toInt()
                    val count = prefs.getInt(key, 0)
                    if (count >= cutoffCount) {
                        recordsList.add(TimeRecord(hour, min, weekDay, count))
                    }
                }
            }
        }
        return recordsList
    }

    fun compactRecords(widgetId: Int) : List<TimeRecord> {
        val records = buildRecords(widgetId, true)
        return remapRecords(records)
    }

    fun remapRecords(records : Map<String, TimeRecord>) : List<TimeRecord> {
        val result = ArrayList<TimeRecord>()
        val edit = prefs.edit()
        for (key in records.keys) {
            val existing = prefs.getInt(key, 0)
            val record = records[key]!!
            record.count += existing
            result.add(record)
            edit.putInt(key, record.count)
        }
        edit.apply()
        return result
    }

    fun buildRecords(widgetId : Int, delete : Boolean = false) : Map<String, TimeRecord> {
        val recordsMap = HashMap<String, TimeRecord>()
        val widgetStart = "$widgetId:"
        val allPrefs = prefs.all
        val edit = prefs.edit()
        for (key in allPrefs.keys) {
            if (key.startsWith(widgetStart)) {
                val split = key.split(":")
                // Right type of record.
                if (split.size == 6) {
                    val weekDay = split[2].toBoolean()
                    val hour = split[3].toInt()
                    val min = split[4].toInt()
                    val recordKey = createAlarmKey(widgetId, hour, min, weekDay)
                    if (recordsMap.containsKey(recordKey)) {
                        recordsMap[recordKey]!!.count++
                    } else {
                        recordsMap[recordKey] = TimeRecord(hour, min, weekDay)
                    }
                    if (delete) {
                        edit.remove(key)
                    }
                }
            }
        }
        edit.apply()
        return recordsMap
    }

    class TimeRecord(val hour : Int, val minute : Int, val weekday : Boolean,  var count : Int = 1) : Comparable<TimeRecord> {
        override fun compareTo(other: TimeRecord): Int {
            if (other.weekday && !weekday) {
                return -1
            }
            if (!other.weekday && weekday) {
                return 1
            }
            if (other.hour > hour) {
                return -1
            }
            if (other.hour < hour) {
                return 1
            }
            if (other.minute > minute) {
                return -1
            }
            return 1
        }

        override fun toString() : String {
            return "Record(h:$hour, m:$minute, wk:$weekday, c:$count)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TimeRecord

            if (hour != other.hour) return false
            if (minute != other.minute) return false
            if (weekday != other.weekday) return false
            if (count != other.count) return false

            return true
        }

        override fun hashCode(): Int {
            var result = hour
            result = 31 * result + minute
            result = 31 * result + weekday.hashCode()
            result = 31 * result + count
            return result
        }
    }
}