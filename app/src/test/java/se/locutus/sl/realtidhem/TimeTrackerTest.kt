package se.locutus.sl.realtidhem

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.locutus.sl.realtidhem.service.TIME_PREFS
import se.locutus.sl.realtidhem.service.TimeTracker
import java.util.*

@RunWith(RobolectricTestRunner::class)
class TimeTrackerTest {
    private val widgetId = 123
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val tracker = TimeTracker(context)
    private val prefs = context.getSharedPreferences(TIME_PREFS, 0)
    val c = Calendar.getInstance(TimeZone.getTimeZone("SV")).apply {
        timeInMillis = 1543180519973
    }

    @Test
    fun testRecordUpdate() {
        tracker.recordUpdate(widgetId, c)
        assertEquals(mapOf("123:wd:false:21:10:25" to 1),  prefs.all)
        tracker.recordUpdate(widgetId, c)
        assertEquals(mapOf("123:wd:false:21:10:25" to 1),  prefs.all)
        c.add(Calendar.HOUR, 24)
        tracker.recordUpdate(widgetId, c)
        // Change day and we have two records.
        assertEquals(mapOf("123:wd:false:21:10:25" to 1,
            "123:wd:true:21:10:26" to 1),  prefs.all)
        // Change hour.
        c.add(Calendar.HOUR, 1)
        tracker.recordUpdate(widgetId, c)
        assertEquals(mapOf("123:wd:false:21:10:25" to 1,
            "123:wd:true:22:10:26" to 1,
            "123:wd:true:21:10:26" to 1),  prefs.all)

        var records = tracker.buildRecords(widgetId)
        assertEquals(3, records.size)
        assertEquals(TimeTracker.TimeRecord(22, 10, true, 1), records[0])
        assertEquals(TimeTracker.TimeRecord(21, 10, true, 1), records[1])
        assertEquals(TimeTracker.TimeRecord(21, 10, false, 1), records[2])

        // Record over multiple days at 22:10.
        c.add(Calendar.HOUR, 24)
        tracker.recordUpdate(widgetId, c)
        c.add(Calendar.HOUR, 24)
        tracker.recordUpdate(widgetId, c)
        c.add(Calendar.HOUR, 24)
        tracker.recordUpdate(widgetId, c)
        c.add(Calendar.HOUR, 24)
        tracker.recordUpdate(widgetId, c)

        records = tracker.buildRecords(widgetId)
        assertEquals(3, records.size)

        // The record at 22:10 on a weekday was incremented to 5.
        assertEquals(TimeTracker.TimeRecord(22, 10, true, 5), records[0])
        assertEquals(TimeTracker.TimeRecord(21, 10, true, 1), records[1])
        assertEquals(TimeTracker.TimeRecord(21, 10, false, 1), records[2])
    }

    @Test
    fun testRecordKey() {
        assertEquals(c.get(Calendar.HOUR_OF_DAY), 21)
        assertEquals(c.get(Calendar.DAY_OF_WEEK), Calendar.SUNDAY)
        assertEquals(c.get(Calendar.MINUTE), 15)
        assertEquals("123:wd:false:21:10:25", tracker.createRecordKey(widgetId, c))

        c.add(Calendar.MINUTE, 1)
        assertEquals(c.get(Calendar.MINUTE), 16)
        assertEquals("123:wd:false:21:10:25", tracker.createRecordKey(widgetId, c))

        c.add(Calendar.MINUTE, 3)
        assertEquals(c.get(Calendar.MINUTE), 19)
        assertEquals("123:wd:false:21:10:25", tracker.createRecordKey(widgetId, c))

        c.add(Calendar.MINUTE, 1)
        assertEquals(c.get(Calendar.MINUTE), 20)
        assertEquals("123:wd:false:21:10:25", tracker.createRecordKey(widgetId, c))

        c.add(Calendar.MINUTE, 1)
        assertEquals(c.get(Calendar.MINUTE), 21)
        assertEquals("123:wd:false:21:20:25", tracker.createRecordKey(widgetId, c))

        c.add(Calendar.HOUR, 24)
        assertEquals(c.get(Calendar.MINUTE), 21)
        assertEquals(c.get(Calendar.DAY_OF_WEEK), Calendar.MONDAY)
        assertEquals("123:wd:true:21:20:26", tracker.createRecordKey(widgetId, c))
    }
}