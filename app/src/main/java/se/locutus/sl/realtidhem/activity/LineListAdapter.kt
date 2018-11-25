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
import java.util.Collections.sort

class LineListAdapter(private val context: Context, private val lineList : ArrayList<List<Ng.DepartureData>>) : BaseAdapter() {

    private val trafficToWeight = HashMap<Ng.NgTrafficType, Int>().apply{
        put(Ng.NgTrafficType.METRO, 500)
        put(Ng.NgTrafficType.TRAIN, 400)
        put(Ng.NgTrafficType.TRAM, 300)
        put(Ng.NgTrafficType.BUS, 200)
        put(Ng.NgTrafficType.BOAT, 100)
        put(Ng.NgTrafficType.UNKNOWN, 0)
    }

    internal var selected : Ng.DepartureData = Ng.DepartureData.getDefaultInstance()
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return lineList.size
    }

    fun isSelected() : Boolean {
        return selected.groupOfLineId != 0
    }

    override fun getItem(position: Int): List<Ng.DepartureData> {
        return lineList[position]
    }

    fun setImageViewIconAndColor(position: Int, iconView : ImageView, root : View, directionView : ImageView) {
        val item = getItem(position)[0]
        val trafficType = item.trafficType
        val iconResource = when (trafficType) {
            Ng.NgTrafficType.BUS -> R.drawable.ic_icon_transportation_bus
            Ng.NgTrafficType.METRO -> R.drawable.ic_icon_transportation_subway
            Ng.NgTrafficType.TRAIN -> R.drawable.ic_icon_transportation_train
            Ng.NgTrafficType.TRAM -> R.drawable.ic_icon_transportation_tram
                else -> R.drawable.ic_icon_transportation_bus
        }
        val colorAlpha = (0x4FFFFFFF and item.color)
        root.setBackgroundColor(colorAlpha)
        iconView.setImageResource(iconResource)

        if (item.directionId == 1) {
            directionView.setImageResource(android.R.drawable.arrow_up_float)
        } else {
            directionView.setImageResource(android.R.drawable.arrow_down_float)
        }
    }

    fun clickItem(position: Int) {
        val clicked = getItem(position)[0]
        selected = if (selected == clicked) Ng.DepartureData.getDefaultInstance() else getItem(position)[0]
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.line_list_item, parent, false)
        val nameText : CheckBox = root.findViewById(R.id.departure_name_check)
        val iconView = root.findViewById<ImageView>(R.id.line_list_icon)
        val directionView = root.findViewById<ImageView>(R.id.line_list_direction_icon)
        setImageViewIconAndColor(position, iconView, root, directionView)
        root.setOnClickListener {
            clickItem(position)
        }
        nameText.setOnClickListener {
            clickItem(position)
        }
        nameText.text = getItem(position)[0].canonicalName
        nameText.isChecked = getItem(position)[0] == selected
        return root
    }

    fun clear() {
        lineList.clear()
    }

    fun sort(colorMap : Map<Int, Int>) {
        sort(lineList) { o1, o2 ->
            val item1 = o1[0]
            val item2 = o2[0]
            val base = (trafficToWeight[item2.trafficType]!! - trafficToWeight[item1.trafficType]!!)
            val color = colorMap[item2.color]!! - colorMap[item1.color]!!
            var total = base + color
            if (total == 0) {
                // Everything is the same, sort by name as tie breaker.
                item1.canonicalName.compareTo(item2.canonicalName)
            }

            total
        }
    }

    fun add(departure: List<Ng.DepartureData>, selected: Boolean) {
        lineList.add(departure)
        if (selected) {
            this.selected = departure[0]
        }
    }
}