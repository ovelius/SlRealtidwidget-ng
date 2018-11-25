package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import java.util.*

class DepartureListAdapter(private val context: Context, private val departureList : ArrayList<Ng.DepartureData>) : BaseAdapter() {
    private val checks : HashSet<String> = HashSet()
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
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
        val nameText : CheckBox = root.findViewById(R.id.departure_name_check)
        val departureName = departureList[position].canonicalName
        val colorView = root.findViewById<ImageView>(R.id.line_list_color)
        val iconView = root.findViewById<ImageView>(R.id.line_list_icon)

        setImageViewIconAndColor(departureList[position], colorView, iconView, root, null)
        nameText.setOnClickListener {
            if (nameText.isChecked) {
                checks.add(departureName)
            } else {
                checks.remove(departureName)
            }
        }
        nameText.text = departureName
        nameText.isChecked = checks.contains(departureName)
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