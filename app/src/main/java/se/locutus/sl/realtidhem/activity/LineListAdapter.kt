package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R

class LineListAdapter(private val context: Context, private val lineList : ArrayList<List<Ng.DepartureData>>) : BaseAdapter() {
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

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.depature_list_item, parent, false)
        val nameText : CheckBox = root.findViewById(R.id.departure_name_check)
        nameText.setOnClickListener {
            val clicked = getItem(position)[0]
            selected = if (selected == clicked) Ng.DepartureData.getDefaultInstance() else getItem(position)[0]
            notifyDataSetChanged()
        }
        nameText.text = getItem(position)[0].canonicalName
        nameText.isChecked = getItem(position)[0] == selected
        return root
    }

    fun clear() {
        lineList.clear()
    }

    fun add(departure: List<Ng.DepartureData>, selected: Boolean) {
        lineList.add(departure)
        if (selected) {
            this.selected = departure[0]
        }
    }
}