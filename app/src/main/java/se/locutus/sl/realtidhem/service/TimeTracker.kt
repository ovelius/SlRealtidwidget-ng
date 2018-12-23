package se.locutus.sl.realtidhem.service

import android.app.AlarmManager
import android.content.Context
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

const val TIME_PREFS = "time_prefs"

/**
 * Helper class for finding/scheduling automatic updates for the widget.
 */
class TimeTracker(val context : Context) {
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

    fun createAlarmKey(widgetId : Int, hour : Int, min : Int, weekday: Boolean) : String {
        return "$widgetId:wd:$weekday:$hour:$min"
    }

    fun createRecordKey(widgetId : Int, c : Calendar) : String {
        return "$widgetId:wd:${isWeekDay(c)}:${c.get(Calendar.HOUR_OF_DAY)}:${translateMinutes(c.get(Calendar.MINUTE))}:${c.get(Calendar.DAY_OF_MONTH)}"
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

    fun getRecords(widgetId: Int) : List<TimeRecord> {
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
                    recordsList.add(TimeRecord(hour, min, weekDay, count))
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

    class TimeRecord(val hour : Int, val minute : Int, val weekday : Boolean,  var count : Int = 1){

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