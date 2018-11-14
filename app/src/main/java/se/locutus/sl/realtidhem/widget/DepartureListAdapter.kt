package se.locutus.sl.realtidhem.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import se.locutus.sl.realtidhem.R

class DepartureListAdapter(private val context: Context, private val departureList : ArrayList<String>) : BaseAdapter() {
    private val checks : HashSet<Int> = HashSet()
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return departureList.size
    }

    override fun getItem(position: Int): String {
        return departureList.get(position)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root : View = convertView ?: inflater.inflate(R.layout.depature_list_item, parent, false)
        val nameText : CheckBox = root.findViewById(R.id.departure_name_check)
        nameText.setOnClickListener {
            if (nameText.isChecked) {
                checks.add(position)
            } else {
                checks.remove(position)
            }
        }
        nameText.text = departureList.get(position)
        nameText.isChecked = checks.contains(position)
        return root
    }

    fun clear() {
        departureList.clear()
    }

    fun add(departure: String) {
        departureList.add(departure)
    }

    fun getCheckedItems() : List<String>  {
        return checks.map { departureList[it] }.toMutableList()
    }
}