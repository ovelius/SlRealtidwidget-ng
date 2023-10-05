package se.locutus.sl.realtidhem.activity

import android.Manifest
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.service.TimeTracker
import se.locutus.sl.realtidhem.service.createAlarmKey
import se.locutus.sl.realtidhem.service.deleteAlarmKey
import se.locutus.sl.realtidhem.service.restoreAlarmKey

class UpdatePeriodAdapter(private val activity: WidgetConfigureActivity, private val widgetId : Int) : BaseAdapter() {
    private val list = ArrayList<TimeTracker.TimeRecord>()
    private val inflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    private var undoDeleteData : UndoDeleteData? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root: View = convertView ?: inflater.inflate(R.layout.update_period_item, parent, false)
        val record = list[position]
        val text = root.findViewById<TextView>(R.id.period_time_text)
        root.findViewById<View>(R.id.delete_image_button).setOnClickListener {
            val alarmKey = createAlarmKey(widgetId, record)
            val alarmValue = deleteAlarmKey(activity, alarmKey)
            list.removeAt(position)
            notifyDataSetChanged()
            undoDeleteData = UndoDeleteData(alarmKey, alarmValue, record, position)
            Snackbar.make(root, activity.getString(R.string.will_not_update_undo), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                   restoreAlarmKey(activity, undoDeleteData!!.alarmKey, undoDeleteData!!.alarmCount)
                    list.add(undoDeleteData!!.position, record)
                    notifyDataSetChanged()
                }.show()
        }
        root.findViewById<ImageView>(R.id.period_weekend_image).visibility = if (record.weekday) View.GONE else View.VISIBLE
        val hour = if (record.hour < 10) "0${record.hour}" else "${record.hour}"
        val minutes = if (record.minute < 10) "0${record.minute}" else "${record.minute}"
        text.setText("$hour:$minutes")
        return root
    }

    override fun getItem(position: Int): TimeTracker.TimeRecord {
        return list.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return list.size
    }

    fun add(record : TimeTracker.TimeRecord) {
        list.add(record)
    }

    fun clear() {
        list.clear()
    }

    private class UndoDeleteData(val alarmKey : String, val alarmCount : Int, val record : TimeTracker.TimeRecord, val position : Int)
}