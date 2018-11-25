package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import java.util.*
import java.util.logging.Logger

class DepartureListAdapter(private val activity: AddStopActivity, private val departureList : ArrayList<Ng.DepartureData>) : BaseAdapter() {
    companion object {
        val LOG = Logger.getLogger(DepartureListAdapter::class.java.name)
    }
    private val checks : HashSet<String> = HashSet()
    private val inflater: LayoutInflater
            = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return departureList.size
    }

    override fun getItem(position: Int): String {
        return departureList[position].canonicalName
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.depature_list_item, parent, false)
        val nameText = root.findViewById<TextView>(R.id.departure_name_text)
        val departureName = departureList[position].canonicalName
        val colorView = root.findViewById<ImageView>(R.id.line_list_color)
        val iconView = root.findViewById<ImageView>(R.id.line_list_icon)

        setImageViewIconAndColor(departureList[position], colorView, iconView, root, null)
        root.setOnClickListener {
            if (checks.contains(departureName)) {
                checks.remove(departureName)
                root.setBackgroundColor(0)
            } else {
                setGreenBg(root)
                checks.add(departureName)
            }
        }
        nameText.text = departureName
        if  (checks.contains(departureName)) {
            setGreenBg(root)
        } else {
            root.setBackgroundColor(0)
        }
        return root
    }

    fun sort(colorMap : Map<Int, Int>) {
        departureList.sortWith(Comparator { item1, item2 ->
            val base = (trafficToWeight[item2.trafficType]!! - trafficToWeight[item1.trafficType]!!)
            val color = colorMap[item2.color]!! - colorMap[item1.color]!!
            var total = base + color
            if (total == 0) {
                // Everything is the same, sort by name as tie breaker.
                total = item1.canonicalName.compareTo(item2.canonicalName)
            }
            total
        })
        LOG.info("Sorted ${departureList.size} for display")
    }

    fun clear() {
        departureList.clear()
    }

    fun add(departure: Ng.DepartureData, checked: Boolean) {
        departureList.add(departure)
        if (checked) {
            checks.add(departure.canonicalName)
        }
    }

    fun getCheckedItems() : List<String>  {
        return checks.toMutableList()
    }
}