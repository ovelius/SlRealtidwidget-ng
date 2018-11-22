package se.locutus.sl.realtidhem.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import se.locutus.sl.realtidhem.R

class DepartureListAdapter(private val context: Context, private val departureList : ArrayList<String>) : BaseAdapter() {
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
        return departureList[position]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.depature_list_item, parent, false)
        val nameText : CheckBox = root.findViewById(R.id.departure_name_check)
        val departureName = departureList[position]
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

    fun clear() {
        departureList.clear()
    }

    fun add(departure: String, checked: Boolean) {
        departureList.add(departure)
        if (checked) {
            checks.add(departure)
        }
    }

    fun getCheckedItems() : List<String>  {
        return checks.toMutableList()
    }
}