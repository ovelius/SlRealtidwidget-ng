package se.locutus.sl.realtidhem.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R

class StopListAdapter(private val context: Context, private var widgetConfig : Ng.WidgetConfiguration) : BaseAdapter() {
    private val checks : HashSet<Int> = HashSet()
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return widgetConfig.getStopConfiguration(position).stopData.siteId
    }

    override fun getCount(): Int {
        return widgetConfig.stopConfigurationCount
    }

    override fun getItem(position: Int): Ng.StopConfiguration {
        return widgetConfig.getStopConfiguration(position)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.stop_list_item, parent, false)
        val nameText : TextView = root.findViewById(R.id.stop_name)
        nameText.setOnClickListener {
          // impl
        }
        nameText.text = widgetConfig.getStopConfiguration(position).stopData.canonicalName
        return root
    }

    fun update(config : Ng.WidgetConfiguration) {
        widgetConfig = config
        notifyDataSetChanged()
    }
}