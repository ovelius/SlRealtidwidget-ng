package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.snackbar.Snackbar
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R


class StopListAdapter(private val activity: WidgetConfigureActivity) : BaseAdapter() {
    private var deletedStop: Ng.StopConfiguration = Ng.StopConfiguration.getDefaultInstance()
    private var deleteIndex = -1
    private val inflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return  activity.widgetConfig.getStopConfiguration(position).stopData.siteId
    }

    override fun getCount(): Int {
        return  activity.widgetConfig.stopConfigurationCount
    }

    override fun getItem(position: Int): Ng.StopConfiguration {
        return  activity.widgetConfig.getStopConfiguration(position)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root: View = convertView ?: inflater.inflate(R.layout.stop_list_item, parent, false)
        val nameText: TextView = root.findViewById(R.id.stop_name)
        val rowClickImage = root.findViewById<ImageView>(R.id.row_click_image)
        nameText.text =  activity.widgetConfig.getStopConfiguration(position).stopData.displayName
        rowClickImage.setOnClickListener { view: View ->
            val popup = PopupMenu(activity, view)
            popup.menuInflater.inflate(R.menu.stop_context_menu, popup.menu)
            popup.show()
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_delete_stop -> {
                        deleteWithUndo(position, view)
                    }
                }
                true
            }
        }
        return root
    }

    fun deleteWithUndo(position : Int, view : View) {
        if (deleteStopAt(position)) {
            Snackbar.make(view, R.string.deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    undoDelete()
                }.show()
        }
    }

    private fun undoDelete() {
        if (deletedStop != Ng.StopConfiguration.getDefaultInstance()) {
            val newList =  activity.widgetConfig.stopConfigurationList.toMutableList()
            newList.add(deleteIndex, deletedStop)
            refreshListWithItems(newList)
            notifyDataSetChanged()
        }
    }

    fun refreshListWithItems(items : Collection<Ng.StopConfiguration>) {
        activity.widgetConfig =  activity.widgetConfig.toBuilder().clearStopConfiguration().addAllStopConfiguration(items).build()
        notifyDataSetChanged()
    }

    fun deleteStopAt(position: Int) : Boolean {
        if (activity.widgetConfig.stopConfigurationCount == 1) {
            return false
        }
        val newList =  activity.widgetConfig.stopConfigurationList.toMutableList()
        deletedStop = newList.removeAt(position)
        deleteIndex = position
        refreshListWithItems(newList)
        return true
    }

    fun update(config : Ng.WidgetConfiguration) {
        activity.widgetConfig = config
        notifyDataSetChanged()
    }
}