package se.locutus.sl.realtidhem.activity.add_stop

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.setColor
import java.lang.IllegalArgumentException
import java.lang.StringBuilder

fun setImageViewIconAndColor(item : Ng.DepartureData, colorView : ImageView,  iconView : ImageView, root : View, directionView : ImageView?) {
    val iconResource = when (item.trafficType) {
        Ng.NgTrafficType.BUS -> R.drawable.ic_icon_transportation_bus
        Ng.NgTrafficType.METRO -> R.drawable.ic_icon_transportation_subway
        Ng.NgTrafficType.TRAIN -> R.drawable.ic_icon_transportation_train
        Ng.NgTrafficType.REGIONAL_TRAIN -> R.drawable.ic_icon_transportation_train
        Ng.NgTrafficType.SPEED_TRAIN -> R.drawable.ic_icon_transportation_train
        Ng.NgTrafficType.TRAM -> R.drawable.ic_icon_transportation_tram
        else -> R.drawable.ic_icon_transportation_bus
    }
    colorView.setBackgroundColor(item.color)
    iconView.setImageResource(iconResource)

    if (directionView != null) {
        if (item.directionId == 1) {
            directionView.setImageResource(R.drawable.ic_baseline_keyboard_arrow_left_24px)
        } else {
            directionView.setImageResource(R.drawable.ic_baseline_keyboard_arrow_right_24px)
        }
    }
}

val trafficToWeight = HashMap<Ng.NgTrafficType, Int>().apply{
    put(Ng.NgTrafficType.METRO, 500)
    put(Ng.NgTrafficType.TRAIN, 400)
    put(Ng.NgTrafficType.SPEED_TRAIN, 50)
    put(Ng.NgTrafficType.REGIONAL_TRAIN, 250)
    put(Ng.NgTrafficType.TRAM, 300)
    put(Ng.NgTrafficType.BUS, 200)
    put(Ng.NgTrafficType.BOAT, 100)
    put(Ng.NgTrafficType.UNKNOWN_TRAFFIC_TYPE, 0)
}

class LineListAdapter(private val activity: AddStopActivity, private val lineList : ArrayList<List<Ng.DepartureData>>) : BaseAdapter() {
    private var selected = HashSet<String>()
    private val inflater: LayoutInflater
            = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return lineList.size
    }

    fun isSelected() : Boolean {
        return selected.isNotEmpty()
    }

    override fun getItem(position: Int): List<Ng.DepartureData> {
        return lineList[position]
    }

    fun firstSelectedIndex() : Int {
        return getIndexForKey(selected.first())
    }

    fun getSelectedItems() : List<Ng.DepartureData> {
        return lineList.asSequence().filter{ selected.contains(toKey(it)) }.map { it[0] }.toList()
    }

    private fun toKey(item : List<Ng.DepartureData>) : String {
        return "${item[0].groupOfLineId}_${item[0].directionId}"
    }

    private fun getIndexForKey(key : String) : Int {
        for (i in lineList.indices) {
            if (toKey(lineList[i]) == key) {
                return i
            }
        }
        throw IllegalArgumentException("No such key $key")
    }

    private fun getItemForKey(key : String) : Ng.DepartureData {
        return lineList[getIndexForKey(key)][0]
    }

    fun clickItem(position: Int) {
        val item = lineList[position]
        val key = toKey(item)
        if (selected.contains(key)) {
            selected.remove(key)
        } else {
            selected.add(key)
            if (selected.size == 1) {
                val item = getItemForKey(selected.first())
                setColor(activity, activity.tabLayout, item.color)
                activity.setColorsAndShowSaveButton(item.color)
            } else if (selected.isNotEmpty()) {
                activity.setColorsAndShowSaveButton(0)
            }
        }

        notifyDataSetChanged()
    }

    private fun buildStopString(items : List<Ng.DepartureData>) : String {
        if (items.isEmpty()) {
            throw IllegalArgumentException("Can't build sublist from 0 items!")
        }
        val sb = StringBuilder()
        for (item in items) {
            sb.append(item.canonicalName)
            if (item != items[items.size - 1]) {
                sb.append(" ")
            }
        }
        return sb.toString()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.line_list_item, parent, false)
        val nameText = root.findViewById<TextView>(R.id.line_list_text)
        val nameSubText = root.findViewById<TextView>(R.id.line_list_text_sub)
        val colorView = root.findViewById<ImageView>(R.id.line_list_color)
        val iconView = root.findViewById<ImageView>(R.id.line_list_icon)
        val directionView = root.findViewById<ImageView>(R.id.line_list_direction_icon)
        val operatorView = root.findViewById<ImageView>(R.id.line_list_operator_icon)

        val data = getItem(position)[0]
        val operatorDrawable = activity.operatorDrawables[data.operator]
        if (operatorDrawable != null) {
            operatorView.setImageDrawable(operatorDrawable)
            operatorView.visibility = View.VISIBLE
        } else {
            operatorView.visibility = View.GONE
        }

        setImageViewIconAndColor(
            data,
            colorView,
            iconView,
            root,
            directionView
        )
        val items = getItem(position)
        nameText.text = items[0].canonicalName
        if (items.size > 1) {
            nameSubText.text = buildStopString(items.subList(1, items.size))
        } else {
            nameSubText.text = ""
        }
        if (selected.contains(toKey(items))) {
            setGreenBg(root)
        } else {
            root.setBackgroundColor(0)
        }
        return root
    }

    fun clear() {
        selected.clear()
        lineList.clear()
    }

    fun sort(colorMap : Map<Int, Int>) {
        lineList.sortWith(Comparator { o1, o2 ->
            val item1 = o1[0]
            val item2 = o2[0]
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

    fun add(departure: List<Ng.DepartureData>, selected: Boolean) {
        lineList.add(departure)
        if (selected) {
            this.selected.add(toKey(departure))
        }
    }
}