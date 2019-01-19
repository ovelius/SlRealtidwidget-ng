package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.service.TimeTracker

class UpdatePeriodAdapter(private val activity: WidgetConfigureActivity) : BaseAdapter() {
    private val list = ArrayList<TimeTracker.TimeRecord>()
    private val inflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root: View = convertView ?: inflater.inflate(R.layout.update_period_item, parent, false)
        val record = list.get(position)
        val text = root.findViewById<TextView>(R.id.period_time_text)
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
}